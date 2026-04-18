package org.peoplemesh.util;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.ClusterName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterNamingHeuristicsTest {

    @Test
    void fromTraits_emptyMap_returnsEmpty() {
        assertEquals(Optional.empty(), ClusterNamingHeuristics.fromTraits(Map.of()));
    }

    @Test
    void fromTraits_onlyEmptyTraitLists_returnsEmpty() {
        Map<String, List<String>> traits = new LinkedHashMap<>();
        traits.put("skills", List.of());
        traits.put("hobbies", List.of());
        assertEquals(Optional.empty(), ClusterNamingHeuristics.fromTraits(traits));
    }

    @Test
    void fromTraits_singleSkill_buildsTitleDescriptionAndTags() {
        Map<String, List<String>> traits = Map.of("skills", List.of("java"));
        Optional<ClusterName> result = ClusterNamingHeuristics.fromTraits(traits);
        assertTrue(result.isPresent());
        ClusterName name = result.get();
        assertEquals("Java Community", name.title());
        assertEquals("An auto-discovered community of people interested in java.", name.description());
        assertEquals(List.of("java"), name.tags());
    }

    @Test
    void fromTraits_skillsAndHobbies_combinesKeywordsInTraitKeyOrder() {
        Map<String, List<String>> traits = new LinkedHashMap<>();
        traits.put("skills", List.of("java"));
        traits.put("hobbies", List.of("photography"));
        Optional<ClusterName> result = ClusterNamingHeuristics.fromTraits(traits);
        assertTrue(result.isPresent());
        ClusterName name = result.get();
        assertEquals("Java, Photography Community", name.title());
        assertEquals("An auto-discovered community of people interested in java, photography.", name.description());
        assertEquals(List.of("java", "photography"), name.tags());
    }

    @Test
    void fromTraits_moreThanThreeUniqueKeywords_titleUsesFirstThreeOnly() {
        Map<String, List<String>> traits = new LinkedHashMap<>();
        traits.put("skills", List.of("a", "b", "c"));
        traits.put("hobbies", List.of("d", "e", "f"));
        Optional<ClusterName> result = ClusterNamingHeuristics.fromTraits(traits);
        assertTrue(result.isPresent());
        ClusterName name = result.get();
        assertEquals("A, B, C Community", name.title());
        assertEquals(List.of("a", "b", "c", "d", "e"), name.tags());
    }

    @Test
    void fromTraits_moreThanFiveUniqueKeywords_tagsLimitedToFive() {
        Map<String, List<String>> traits = new LinkedHashMap<>();
        traits.put("skills", List.of("1", "2", "3"));
        traits.put("hobbies", List.of("4", "5", "6"));
        traits.put("topics", List.of("7"));
        Optional<ClusterName> result = ClusterNamingHeuristics.fromTraits(traits);
        assertTrue(result.isPresent());
        assertEquals(List.of("1", "2", "3", "4", "5"), result.get().tags());
    }

    @Test
    void titleCase_null_returnsNull() {
        assertEquals(null, ClusterNamingHeuristics.titleCase(null));
    }

    @Test
    void titleCase_empty_returnsEmpty() {
        assertEquals("", ClusterNamingHeuristics.titleCase(""));
    }

    @Test
    void titleCase_normal_uppercasesFirstCharacterOnly() {
        assertEquals("Java", ClusterNamingHeuristics.titleCase("java"));
        assertEquals("McDonald", ClusterNamingHeuristics.titleCase("mcDonald"));
    }
}
