package org.peoplemesh.service;

import dev.langchain4j.model.ModelDisabledException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillDefinitionRepository;
import org.peoplemesh.util.SkillNameNormalizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class SkillsService {

    private static final Logger LOG = Logger.getLogger(SkillsService.class);
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_SUGGEST_LIMIT = 20;
    private static final Pattern ALIAS_PATTERN = Pattern.compile("\\(([^)]+)\\)$");

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    @Inject
    EntitlementService entitlementService;

    @Inject
    EmbeddingService embeddingService;

    public List<SkillDefinitionDto> listSkills(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<SkillDefinition> skills = (query != null && !query.isBlank())
                ? skillDefinitionRepository.suggestByAlias(query, safeSize)
                : skillDefinitionRepository.listSkills(safePage, safeSize);
        return skills.stream()
                .map(this::toDefinitionDto)
                .toList();
    }

    public List<SkillDefinitionDto> suggestSkills(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int safeLimit = limit == null ? DEFAULT_SUGGEST_LIMIT : Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return skillDefinitionRepository.suggestByAlias(query, safeLimit).stream()
                .map(this::toDefinitionDto)
                .toList();
    }

    @Transactional
    public int importCsv(UUID userId, InputStream csvStream) throws IOException {
        ensureIsAdmin(userId);
        if (csvStream == null) {
            throw new ValidationBusinessException("Missing CSV payload");
        }
        int imported = upsertSkillsFromCsv(csvStream);
        int embedded = generateMissingEmbeddings();
        LOG.infof("Imported %d global skills (generated embeddings for %d rows)", imported, embedded);
        return imported;
    }

    @Transactional
    public int cleanupUnused(UUID userId) {
        ensureIsAdmin(userId);
        int deleted = skillDefinitionRepository.deleteUnused();
        LOG.infof("Deleted %d unused skills", deleted);
        return deleted;
    }

    @Transactional
    public Set<String> normalizeAndUpsertSkills(List<String> rawSkills) {
        return new LinkedHashSet<>(canonicalizeSkillList(rawSkills, true));
    }

    public Set<String> normalizeSkills(List<String> rawSkills) {
        return new LinkedHashSet<>(canonicalizeSkillList(rawSkills, false));
    }

    @Transactional
    public List<String> canonicalizeSkillList(List<String> rawSkills, boolean createMissing) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String raw : rawSkills) {
            String term = SkillNameNormalizer.normalize(raw);
            if (term == null) {
                continue;
            }
            Optional<SkillDefinition> existing = skillDefinitionRepository.findByNameOrAlias(term);
            if (existing.isPresent()) {
                canonical.add(existing.get().name);
                continue;
            }
            if (createMissing) {
                SkillDefinition definition = createSkillDefinition(term, raw != null ? raw.trim() : null);
                canonical.add(definition.name);
                continue;
            }
            canonical.add(term);
        }
        return new ArrayList<>(canonical);
    }

    @Transactional
    public void incrementUsage(Set<String> canonicalSkills) {
        if (canonicalSkills == null || canonicalSkills.isEmpty()) {
            return;
        }
        skillDefinitionRepository.incrementUsageCounts(new ArrayList<>(canonicalSkills));
    }

    @Transactional
    public void decrementUsage(Set<String> canonicalSkills) {
        if (canonicalSkills == null || canonicalSkills.isEmpty()) {
            return;
        }
        skillDefinitionRepository.decrementUsageCounts(new ArrayList<>(canonicalSkills));
    }

    private int upsertSkillsFromCsv(InputStream csvStream) throws IOException {
        int imported = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return 0;
            }
            CsvColumnMapping mapping = CsvColumnMapping.fromHeader(parseCsvLine(header));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String[] parts = parseCsvLine(line);
                String rawName = mapping.readName(parts);
                if (rawName == null || rawName.isBlank()) {
                    continue;
                }
                String normalized = SkillNameNormalizer.normalize(rawName);
                if (normalized == null) {
                    continue;
                }
                List<String> aliases = mapping.readAliases(parts);
                Matcher matcher = ALIAS_PATTERN.matcher(rawName);
                if (matcher.find()) {
                    aliases = new ArrayList<>(aliases);
                    aliases.add(matcher.group(1).trim());
                }
                String display = rawName.trim();
                SkillDefinition existing = skillDefinitionRepository.findByName(normalized).orElse(null);
                if (existing == null) {
                    SkillDefinition created = createSkillDefinition(normalized, display);
                    if (!aliases.isEmpty()) {
                        created.aliases = mergeAliases(created.aliases, aliases);
                    }
                    skillDefinitionRepository.upsert(created);
                    imported++;
                } else {
                    List<String> merged = mergeAliases(existing.aliases, aliases);
                    if (!display.equalsIgnoreCase(normalized)) {
                        merged = mergeAliases(merged, List.of(display));
                    }
                    existing.aliases = merged;
                    skillDefinitionRepository.upsert(existing);
                }
            }
        }
        return imported;
    }

    private int generateMissingEmbeddings() {
        List<SkillDefinition> missing = skillDefinitionRepository.findAll().stream()
                .filter(skill -> skill.embedding == null)
                .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        int generated = 0;
        int batchSize = 24;
        try {
            for (int i = 0; i < missing.size(); i += batchSize) {
                List<SkillDefinition> batch = missing.subList(i, Math.min(i + batchSize, missing.size()));
                List<String> texts = batch.stream().map(this::toEmbeddingText).toList();
                List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
                int upTo = Math.min(batch.size(), embeddings.size());
                for (int j = 0; j < upTo; j++) {
                    float[] embedding = embeddings.get(j);
                    if (embedding == null) {
                        continue;
                    }
                    SkillDefinition skill = batch.get(j);
                    skill.embedding = embedding;
                    skillDefinitionRepository.upsert(skill);
                    generated++;
                }
            }
        } catch (ModelDisabledException ex) {
            LOG.warn("Embedding model disabled: skipping skill embedding generation");
        }
        return generated;
    }

    private SkillDefinition createSkillDefinition(String normalizedName, String displayAlias) {
        SkillDefinition created = new SkillDefinition();
        created.name = normalizedName;
        created.usageCount = 0;
        created.aliases = displayAlias != null && !displayAlias.isBlank()
                ? List.of(displayAlias)
                : List.of();
        skillDefinitionRepository.upsert(created);
        return created;
    }

    private List<String> mergeAliases(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            existing.stream()
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (incoming != null) {
            incoming.stream()
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private String toEmbeddingText(SkillDefinition skill) {
        if (skill.aliases == null || skill.aliases.isEmpty()) {
            return skill.name;
        }
        return skill.name + " (" + String.join(", ", skill.aliases) + ")";
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
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static String safeCell(String[] parts, int index) {
        if (index < 0 || index >= parts.length) {
            return "";
        }
        return parts[index] == null ? "" : parts[index].trim();
    }

    private void ensureIsAdmin(UUID userId) {
        if (!entitlementService.isAdmin(userId)) {
            throw new ForbiddenBusinessException("Skills management requires is_admin entitlement");
        }
    }

    private SkillDefinitionDto toDefinitionDto(SkillDefinition definition) {
        return new SkillDefinitionDto(
                definition.id,
                definition.name,
                definition.aliases,
                definition.usageCount,
                definition.embedding != null
        );
    }

    private record CsvColumnMapping(
            int nameIndex,
            int aliasesIndex
    ) {
        private static int firstPresentIndex(java.util.Map<String, Integer> indexes, String... candidates) {
            for (String candidate : candidates) {
                Integer idx = indexes.get(candidate);
                if (idx != null) {
                    return idx;
                }
            }
            return -1;
        }

        static CsvColumnMapping fromHeader(String[] headers) {
            java.util.Map<String, Integer> indexes = new java.util.HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                indexes.put(normalizeHeader(headers[i]), i);
            }
            int name = firstPresentIndex(indexes, "name", "skill", "skill_name", "preferred_label_en", "title");
            int aliases = firstPresentIndex(indexes, "aliases", "alias");
            if (name < 0) {
                throw new ValidationBusinessException("Unsupported CSV format: missing 'name' column");
            }
            return new CsvColumnMapping(name, aliases);
        }

        String readName(String[] parts) {
            return safeCell(parts, nameIndex);
        }

        List<String> readAliases(String[] parts) {
            String raw = safeCell(parts, aliasesIndex);
            if (raw.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(raw.split("\\s*[;|]\\s*"))
                    .map(String::trim)
                    .filter(alias -> !alias.isBlank())
                    .toList();
        }
    }
}
