package org.peoplemesh.service;

import org.peoplemesh.domain.dto.ClusterName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClusterNamingLlm {

    /**
     * Given representative traits of a cluster, generate a community name, description, and tags.
     */
    Optional<ClusterName> generateName(Map<String, List<String>> traits);
}
