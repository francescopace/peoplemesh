package org.peoplemesh.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClusterNamingLlm {

    record ClusterName(String title, String description, List<String> tags) {}

    /**
     * Given representative traits of a cluster, generate a community name, description, and tags.
     */
    Optional<ClusterName> generateName(Map<String, List<String>> traits);
}
