package org.peoplemesh.service;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;

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
    EntityManager em;

    @Transactional
    @CacheInvalidateAll(cacheName = "skill-catalogs")
    public SkillCatalog createCatalog(String name, String description,
                                       Map<String, Object> levelScale, String source) {
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = name;
        catalog.description = description;
        catalog.levelScale = levelScale;
        catalog.source = source;
        catalog.persist();
        return catalog;
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "skill-definitions")
    public int importFromCsv(UUID catalogId, InputStream csvStream) throws IOException {
        SkillCatalog catalog = SkillCatalog.findByIdOptional(catalogId)
                .orElseThrow(() -> new NotFoundException("Catalog not found"));

        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null) return 0;
            CsvColumnMapping mapping = CsvColumnMapping.fromHeader(parseCsvLine(header));

            String line;
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

                Optional<SkillDefinition> existing = SkillDefinition.findByCatalogAndName(catalogId, name);
                if (existing.isPresent()) {
                    SkillDefinition sd = existing.get();
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
                    sd.persist();
                    count++;
                }
            }
        }

        LOG.infof("Imported %d skill definitions into catalog %s", count, catalog.name);
        return count;
    }

    @Transactional
    public void generateEmbeddings(UUID catalogId) {
        List<SkillDefinition> skills = SkillDefinition.findByCatalog(catalogId);
        int generated = 0;
        for (SkillDefinition sd : skills) {
            if (sd.embedding != null) continue;
            try {
                String text = sd.category + ": " + sd.name;
                if (sd.aliases != null && !sd.aliases.isEmpty()) {
                    text += " (" + String.join(", ", sd.aliases) + ")";
                }
                sd.embedding = embeddingService.generateEmbedding(text);
                generated++;
            } catch (Exception e) {
                LOG.warnf("Failed to generate embedding for skill %s: %s", sd.name, e.getMessage());
            }
        }
        LOG.infof("Generated %d embeddings for catalog %s (of %d total skills)",
                generated, catalogId, skills.size());
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "skill-catalogs")
    @CacheInvalidateAll(cacheName = "skill-definitions")
    public void deleteCatalog(UUID catalogId) {
        SkillCatalog catalog = SkillCatalog.findByIdOptional(catalogId)
                .orElseThrow(() -> new NotFoundException("Catalog not found"));
        catalog.delete();
    }

    @CacheResult(cacheName = "skill-catalogs")
    public List<SkillCatalog> listCatalogs() {
        return SkillCatalog.findAllSorted();
    }

    public Optional<SkillCatalog> getCatalog(UUID catalogId) {
        return SkillCatalog.findByIdOptional(catalogId);
    }

    @CacheResult(cacheName = "skill-definitions")
    public List<SkillDefinition> listSkills(UUID catalogId, String category, int page, int size) {
        if (category != null && !category.isBlank()) {
            return em.createQuery(
                    "FROM SkillDefinition d WHERE d.catalogId = ?1 AND d.category = ?2 ORDER BY d.name",
                            SkillDefinition.class)
                    .setParameter(1, catalogId)
                    .setParameter(2, category)
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .getResultList();
        }
        return em.createQuery(
                "FROM SkillDefinition d WHERE d.catalogId = ?1 ORDER BY d.category, d.name",
                        SkillDefinition.class)
                .setParameter(1, catalogId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
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
        static CsvColumnMapping fromHeader(String[] headerParts) {
            Map<String, Integer> indexes = new HashMap<>();
            for (int i = 0; i < headerParts.length; i++) {
                indexes.put(normalizeHeader(headerParts[i]), i);
            }

            // Skills Base export format: category,name,[lxp_recommendation|lxp],[aliases]
            if (indexes.containsKey("category") && indexes.containsKey("name")) {
                int lxp = indexes.getOrDefault("lxp_recommendation", indexes.getOrDefault("lxp", -1));
                int aliases = indexes.getOrDefault("aliases", -1);
                return new CsvColumnMapping(indexes.get("category"), indexes.get("name"), lxp, aliases);
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
                            "Skills Base export ('category,name,...') or ESCO ('uri,title,preferred_label_en,skill_type,reuse_level').");
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
