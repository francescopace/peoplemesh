package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.service.ClusteringService.ClusterResult;
import org.peoplemesh.service.ClusteringService.EmbeddingRow;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClusteringScheduler {

    private static final Logger LOG = Logger.getLogger(ClusteringScheduler.class);

    @Inject
    AppConfig config;

    @Inject
    ClusteringService clusteringService;

    @Inject
    ClusterNamingLlm clusterNamingLlm;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    NodeRepository nodeRepository;

    public void runClustering() {
        if (!config.clustering().enabled()) {
            LOG.debug("Clustering is disabled");
            return;
        }
        LOG.info("Starting auto-community clustering run");
        try {
            List<EmbeddingRow> embeddings = clusteringService.loadPublishedEmbeddings();
            if (embeddings.size() < config.clustering().minClusterSize()) {
                LOG.infof("Not enough profiles (%d) for clustering, skipping", embeddings.size());
                return;
            }

            int k = config.clustering().k();
            List<ClusterResult> clusters = clusteringService.kMeans(embeddings, k, 100);
            LOG.infof("Clustering produced %d valid clusters", clusters.size());

            int created = 0;
            int updated = 0;

            List<MeshNode> existingCommunities = loadCommunityNodes();
            for (ClusterResult cluster : clusters) {
                Map<String, List<String>> traits = clusteringService.extractClusterTraits(cluster.memberUserIds(), 10);
                Optional<ClusterNamingLlm.ClusterName> nameOpt = clusterNamingLlm.generateName(traits);
                if (nameOpt.isEmpty()) continue;

                ClusterNamingLlm.ClusterName name = nameOpt.get();

                MeshNode existing = findExistingAutoNode(name.tags(), existingCommunities);
                if (existing != null) {
                    updateAutoNode(existing, name, cluster);
                    updated++;
                } else {
                    MeshNode createdNode = createAutoNode(name, cluster);
                    existingCommunities.add(createdNode);
                    created++;
                }
            }

            LOG.infof("Clustering run complete: created=%d, updated=%d",
                    created, updated);
        } catch (Exception e) {
            LOG.errorf(e, "Clustering run failed");
        }
    }

    @Transactional
    MeshNode createAutoNode(ClusterNamingLlm.ClusterName name, ClusterResult cluster) {
        MeshNode node = newCommunityNode();
        node.createdBy = cluster.memberUserIds().get(0);
        node.nodeType = NodeType.COMMUNITY;
        node.title = name.title();
        node.description = name.description();
        node.tags = name.tags();
        node.structuredData = Map.of("auto_generated", true, "cluster_size", cluster.memberUserIds().size());
        

        node.embedding = embeddingService.generateEmbedding(buildCommunityEmbeddingText(name));

        persistNode(node);
        LOG.infof("Created auto-community: \"%s\" with %d cluster members", name.title(), cluster.memberUserIds().size());
        return node;
    }

    @Transactional
    void updateAutoNode(MeshNode node, ClusterNamingLlm.ClusterName name, ClusterResult cluster) {
        node.title = name.title();
        node.description = name.description();
        node.tags = name.tags();
        Map<String, Object> data = node.structuredData != null ? new HashMap<>(node.structuredData) : new HashMap<>();
        data.put("auto_generated", true);
        data.put("cluster_size", cluster.memberUserIds().size());
        data.put("last_clustered_at", now().toString());
        node.structuredData = data;

        node.embedding = embeddingService.generateEmbedding(buildCommunityEmbeddingText(name));
        persistNode(node);
    }

    private static String buildCommunityEmbeddingText(ClusterNamingLlm.ClusterName name) {
        return "Type: COMMUNITY. Title: " + name.title() + ". Description: " + name.description()
                + ". Tags: " + String.join(", ", name.tags());
    }

    /**
     * Try to match a new cluster to an existing auto-generated community by tag overlap.
     */
    private MeshNode findExistingAutoNode(List<String> newTags, List<MeshNode> candidates) {
        if (newTags == null || newTags.isEmpty()) return null;

        Set<String> newTagSet = newTags.stream().map(String::toLowerCase).collect(Collectors.toSet());

        for (MeshNode node : candidates) {
            if (node.structuredData == null || !Boolean.TRUE.equals(node.structuredData.get("auto_generated"))) {
                continue;
            }
            if (node.tags != null) {
                Set<String> existingTags = node.tags.stream().map(String::toLowerCase).collect(Collectors.toSet());
                Set<String> intersection = new HashSet<>(newTagSet);
                intersection.retainAll(existingTags);
                double jaccard = (double) intersection.size() / (newTagSet.size() + existingTags.size() - intersection.size());
                if (jaccard >= 0.5) return node;
            }
        }
        return null;
    }

    MeshNode newCommunityNode() {
        return new MeshNode();
    }

    void persistNode(MeshNode node) {
        nodeRepository.persist(node);
    }

    Instant now() {
        return Instant.now();
    }

    List<MeshNode> loadCommunityNodes() {
        return new ArrayList<>(nodeRepository.listCommunities());
    }
}
