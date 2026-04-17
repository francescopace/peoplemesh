package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillCatalogRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class SkillCatalogService {

    private static final Logger LOG = Logger.getLogger(SkillCatalogService.class);
    private static final Pattern ALIAS_PATTERN = Pattern.compile("\\(([^)]+)\\)$");

    @Inject
    EmbeddingService embeddingService;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    @Inject
    SkillCatalogRepository skillCatalogRepository;

    @Transactional
    public SkillCatalog createCatalog(String name, String description,
                                       Map<String, Object> levelScale, String source) {
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = name;
        catalog.description = description;
        catalog.levelScale = levelScale;
        catalog.source = source;
        skillCatalogRepository.persist(catalog);
        return catalog;
    }

    @Transactional
    public SkillCatalog updateCatalog(UUID catalogId, String name, String description,
                                      Map<String, Object> levelScale, String source) {
        SkillCatalog catalog = skillCatalogRepository.findById(catalogId)
                .orElseThrow(() -> new NotFoundException("Catalog not found"));
        catalog.name = name;
        catalog.description = description;
        catalog.source = source;
        if (levelScale != null && !levelScale.isEmpty()) {
            catalog.levelScale = levelScale;
        }
        return catalog;
    }

    @Transactional
    public int importFromCsv(UUID catalogId, InputStream csvStream) throws IOException {
        SkillCatalog catalog = skillCatalogRepository.findById(catalogId)
                .orElseThrow(() -> new NotFoundException("Catalog not found"));

        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null) return 0;
            CsvColumnMapping mapping = CsvColumnMapping.fromHeader(parseCsvLine(header));

            String line;
            Map<String, SkillDefinition> existingByName = findCatalogSkills(catalogId).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            sd -> sd.name.toLowerCase(Locale.ROOT),
                            sd -> sd,
                            (a, b) -> a,
                            LinkedHashMap::new));
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = parseCsvLine(line);
                String category = mapping.readCategory(parts);
                String rawName = mapping.readName(parts);
                String lxpRecommendation = mapping.readLxpRecommendation(parts);

                if (category.isEmpty() || rawName.isEmpty()) continue;

                String name = rawName;
                List<String> aliases = new ArrayList<>();
                Matcher m = ALIAS_PATTERN.matcher(rawName);
                if (m.find()) {
                    String alias = m.group(1).trim();
                    aliases.add(alias);
                    name = rawName.substring(0, m.start()).trim();
                }
                aliases.addAll(mapping.readAliases(parts));
                aliases = aliases.stream().filter(s -> !s.isBlank()).distinct().toList();

                SkillDefinition existing = existingByName.get(name.toLowerCase(Locale.ROOT));
                if (existing != null) {
                    SkillDefinition sd = existing;
                    sd.category = category;
                    if (lxpRecommendation != null) sd.lxpRecommendation = lxpRecommendation;
                    if (!aliases.isEmpty()) sd.aliases = aliases;
                } else {
                    SkillDefinition sd = new SkillDefinition();
                    sd.catalogId = catalogId;
                    sd.category = category;
                    sd.name = name;
                    sd.aliases = aliases.isEmpty() ? null : aliases;
                    sd.lxpRecommendation = lxpRecommendation;
                    skillDefinitionRepository.upsert(sd);
                    existingByName.put(name.toLowerCase(Locale.ROOT), sd);
                    count++;
                }
            }
        }

        LOG.infof("Imported %d skill definitions into catalog %s", count, catalog.name);
        return count;
    }

    @Transactional
    public void generateEmbeddings(UUID catalogId) {
        List<SkillDefinition> skills = findCatalogSkills(catalogId).stream()
                .filter(sd -> sd.embedding == null)
                .toList();
        int generated = 0;
        int batchSize = 24;
        for (int i = 0; i < skills.size(); i += batchSize) {
            List<SkillDefinition> batch = skills.subList(i, Math.min(i + batchSize, skills.size()));
            List<String> texts = batch.stream().map(SkillCatalogService::toEmbeddingText).toList();
            try {
                List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
                generated += persistEmbeddingBatch(batch, embeddings);
            } catch (Exception e) {
                LOG.warnf("Batch skill embedding failed, fallback to single-item mode: %s", e.getMessage());
                generated += generateEmbeddingSingleFallback(batch);
            }
        }
        LOG.infof("Generated %d embeddings for catalog %s (of %d total skills)",
                generated, catalogId, skills.size());
    }

    private static String toEmbeddingText(SkillDefinition sd) {
        String text = sd.category + ": " + sd.name;
        if (sd.aliases != null && !sd.aliases.isEmpty()) {
            text += " (" + String.join(", ", sd.aliases) + ")";
        }
        return text;
    }

    private int persistEmbeddingBatch(List<SkillDefinition> batch, List<float[]> embeddings) {
        int generated = 0;
        int upTo = Math.min(batch.size(), embeddings.size());
        for (int i = 0; i < upTo; i++) {
            float[] embedding = embeddings.get(i);
            if (embedding == null) {
                continue;
            }
            SkillDefinition skill = batch.get(i);
            skill.embedding = embedding;
            persistSkillDefinition(skill);
            generated++;
        }
        return generated;
    }

    private int generateEmbeddingSingleFallback(List<SkillDefinition> batch) {
        int generated = 0;
        for (SkillDefinition skill : batch) {
            try {
                float[] embedding = embeddingService.generateEmbedding(toEmbeddingText(skill));
                if (embedding == null) {
                    continue;
                }
                skill.embedding = embedding;
                persistSkillDefinition(skill);
                generated++;
            } catch (Exception e) {
                LOG.warnf("Failed to generate embedding for skill %s: %s", skill.name, e.getMessage());
            }
        }
        return generated;
    }

    @Transactional
    public void deleteCatalog(UUID catalogId) {
        SkillCatalog catalog = skillCatalogRepository.findById(catalogId)
                .orElseThrow(() -> new NotFoundException("Catalog not found"));
        skillCatalogRepository.delete(catalog);
    }

    public List<SkillCatalog> listCatalogs() {
        return skillCatalogRepository.findAllSorted();
    }

    public Optional<SkillCatalog> getCatalog(UUID catalogId) {
        return skillCatalogRepository.findById(catalogId);
    }

    public List<SkillDefinition> listSkills(UUID catalogId, String category, int page, int size) {
        return skillDefinitionRepository.listSkills(catalogId, category, page, size);
    }

    private List<SkillDefinition> findCatalogSkills(UUID catalogId) {
        List<SkillDefinition> skills = skillDefinitionRepository.findByCatalog(catalogId);
        return skills != null ? skills : List.of();
    }

    private void persistSkillDefinition(SkillDefinition skillDefinition) {
        skillDefinitionRepository.upsert(skillDefinition);
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private static String normalizeHeader(String header) {
        return header == null
                ? ""
                : header.trim().toLowerCase(Locale.ROOT)
                .replace("\uFEFF", "")
                .replace(' ', '_')
                .replace('-', '_');
    }

    private static String safeCell(String[] parts, int index) {
        if (index < 0 || index >= parts.length) return "";
        return parts[index] == null ? "" : parts[index].trim();
    }

    private static String normalizeCategoryValue(String rawCategory) {
        String normalized = rawCategory == null ? "" : rawCategory.trim();
        if (normalized.isEmpty()) return "";
        if ("skill".equalsIgnoreCase(normalized)) return "Skill";
        if ("knowledge".equalsIgnoreCase(normalized)) return "Knowledge";
        return normalized;
    }

    private record CsvColumnMapping(
            int categoryIndex,
            int nameIndex,
            int lxpRecommendationIndex,
            int aliasesIndex
    ) {
        private static int firstPresentIndex(Map<String, Integer> indexes, String... candidates) {
            for (String candidate : candidates) {
                Integer idx = indexes.get(candidate);
                if (idx != null) return idx;
            }
            return -1;
        }

        static CsvColumnMapping fromHeader(String[] headerParts) {
            Map<String, Integer> indexes = new HashMap<>();
            for (int i = 0; i < headerParts.length; i++) {
                indexes.put(normalizeHeader(headerParts[i]), i);
            }

            // Skills Base export format: [category|category_name],name,[lxp_recommendation|lxp],[aliases]
            int category = firstPresentIndex(indexes, "category", "category_name");
            int skillName = firstPresentIndex(indexes, "name");
            if (category >= 0 && skillName >= 0) {
                int lxp = indexes.getOrDefault("lxp_recommendation", indexes.getOrDefault("lxp", -1));
                int aliases = indexes.getOrDefault("aliases", -1);
                return new CsvColumnMapping(category, skillName, lxp, aliases);
            }

            // ESCO export format: uri,title,preferred_label_en,skill_type,reuse_level
            if (indexes.containsKey("uri")
                    && (indexes.containsKey("preferred_label_en") || indexes.containsKey("title"))
                    && indexes.containsKey("skill_type")) {
                int name = indexes.getOrDefault("preferred_label_en", indexes.get("title"));
                int lxp = indexes.getOrDefault("reuse_level", -1);
                return new CsvColumnMapping(indexes.get("skill_type"), name, lxp, -1);
            }

            throw new IllegalArgumentException(
                    "Unsupported skills CSV format. Supported headers: " +
                            "Skills Base export ('category,name,...' or 'category_name,name,...') or ESCO ('uri,title,preferred_label_en,skill_type,reuse_level').");
        }

        String readCategory(String[] parts) {
            return normalizeCategoryValue(safeCell(parts, categoryIndex));
        }

        String readName(String[] parts) {
            String value = safeCell(parts, nameIndex);
            return value;
        }

        String readLxpRecommendation(String[] parts) {
            String value = safeCell(parts, lxpRecommendationIndex);
            return value.isEmpty() ? null : value;
        }

        List<String> readAliases(String[] parts) {
            String raw = safeCell(parts, aliasesIndex);
            if (raw.isEmpty()) return List.of();
            return Arrays.stream(raw.split("\\s*[;|]\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }
}
