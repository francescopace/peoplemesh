package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.repository.ClusteringRepository;

import java.util.*;

@ApplicationScoped
public class ClusteringService {

    private static final Logger LOG = Logger.getLogger(ClusteringService.class);

    @Inject
    AppConfig config;

    @Inject
    ClusteringRepository clusteringRepository;

    public record EmbeddingRow(UUID userId, float[] embedding) {}

    public record ClusterResult(float[] centroid, List<UUID> memberUserIds) {}

    public List<EmbeddingRow> loadPublishedEmbeddings() {
        List<Object[]> rows = clusteringRepository.loadPublishedEmbeddings();

        List<EmbeddingRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            float[] vec = parseVectorString((String) row[1]);
            if (vec != null) result.add(new EmbeddingRow(userId, vec));
        }
        LOG.infof("Loaded %d embeddings for clustering", result.size());
        return result;
    }

    /**
     * K-means clustering on embedding vectors.
     * Returns clusters with at least minClusterSize members and centroid distance < maxCentroidDistance.
     */
    public List<ClusterResult> kMeans(List<EmbeddingRow> data, int k, int maxIterations) {
        if (data.isEmpty() || k <= 0) return Collections.emptyList();
        int n = data.size();
        int dim = data.get(0).embedding.length;
        k = Math.min(k, n);

        float[][] centroids = initCentroids(data, k, dim);
        int[] assignments = new int[n];

        for (int iter = 0; iter < maxIterations; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int nearest = nearestCentroid(data.get(i).embedding, centroids);
                if (nearest != assignments[i]) {
                    assignments[i] = nearest;
                    changed = true;
                }
            }
            if (!changed) {
                LOG.infof("K-means converged at iteration %d", iter);
                break;
            }
            centroids = recomputeCentroids(data, assignments, k, dim);
        }

        int minSize = config.clustering().minClusterSize();
        double maxDist = config.clustering().maxCentroidDistance();

        Map<Integer, List<UUID>> clusterMembers = new HashMap<>();
        for (int i = 0; i < n; i++) {
            double dist = cosineDistance(data.get(i).embedding, centroids[assignments[i]]);
            if (dist <= maxDist) {
                clusterMembers.computeIfAbsent(assignments[i], x -> new ArrayList<>()).add(data.get(i).userId);
            }
        }

        List<ClusterResult> results = new ArrayList<>();
        for (var entry : clusterMembers.entrySet()) {
            if (entry.getValue().size() >= minSize) {
                results.add(new ClusterResult(centroids[entry.getKey()], entry.getValue()));
            }
        }
        LOG.infof("K-means produced %d valid clusters from %d total", results.size(), k);
        return results;
    }

    /**
     * Extract representative profile fields from a cluster's closest members.
     */
    public Map<String, List<String>> extractClusterTraits(List<UUID> memberUserIds, int sampleSize) {
        List<UUID> sample = memberUserIds.size() <= sampleSize
                ? memberUserIds
                : memberUserIds.subList(0, sampleSize);

        List<Object[]> rows = clusteringRepository.loadClusterTraits(sample);

        Map<String, List<String>> traits = new LinkedHashMap<>();
        traits.put("skills", new ArrayList<>());
        traits.put("hobbies", new ArrayList<>());
        traits.put("sports", new ArrayList<>());
        traits.put("causes", new ArrayList<>());
        traits.put("topics", new ArrayList<>());
        traits.put("countries", new ArrayList<>());

        for (Object[] row : rows) {
            collectArray(traits.get("skills"), row[0]);
            collectArray(traits.get("hobbies"), row[1]);
            collectArray(traits.get("sports"), row[2]);
            collectArray(traits.get("causes"), row[3]);
            collectArray(traits.get("topics"), row[4]);
            if (row[5] != null) traits.get("countries").add((String) row[5]);
        }
        return traits;
    }

    private float[][] initCentroids(List<EmbeddingRow> data, int k, int dim) {
        float[][] centroids = new float[k][dim];
        Random rng = new Random(42);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) indices.add(i);
        Collections.shuffle(indices, rng);
        for (int c = 0; c < k; c++) {
            System.arraycopy(data.get(indices.get(c)).embedding, 0, centroids[c], 0, dim);
        }
        return centroids;
    }

    private float[][] recomputeCentroids(List<EmbeddingRow> data, int[] assignments, int k, int dim) {
        float[][] sums = new float[k][dim];
        int[] counts = new int[k];
        for (int i = 0; i < data.size(); i++) {
            int c = assignments[i];
            counts[c]++;
            float[] vec = data.get(i).embedding;
            for (int d = 0; d < dim; d++) sums[c][d] += vec[d];
        }
        float[][] centroids = new float[k][dim];
        for (int c = 0; c < k; c++) {
            if (counts[c] == 0) continue;
            for (int d = 0; d < dim; d++) centroids[c][d] = sums[c][d] / counts[c];
        }
        return centroids;
    }

    private int nearestCentroid(float[] vec, float[][] centroids) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int c = 0; c < centroids.length; c++) {
            double dist = cosineDistance(vec, centroids[c]);
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    double cosineDistance(float[] a, float[] b) {
        return org.peoplemesh.util.VectorMath.cosineDistance(a, b);
    }

    private float[] parseVectorString(String vectorStr) {
        if (vectorStr == null) return null;
        String s = vectorStr.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }

    private void collectArray(List<String> target, Object obj) {
        if (obj == null) return;
        if (obj instanceof String[] arr) {
            target.addAll(Arrays.asList(arr));
        } else if (obj instanceof Object[] arr) {
            for (Object o : arr) if (o != null) target.add(o.toString());
        } else if (obj instanceof List<?> list) {
            for (Object o : list) if (o != null) target.add(o.toString());
        }
    }
}
