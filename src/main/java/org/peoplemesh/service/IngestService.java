package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.IngestResultDto;
import org.peoplemesh.domain.dto.NodeIngestEntryDto;
import org.peoplemesh.domain.dto.NodesIngestRequestDto;
import org.peoplemesh.domain.dto.UserIngestEntryDto;
import org.peoplemesh.domain.dto.UsersIngestRequestDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.util.EmbeddingTextBuilder;
import org.peoplemesh.service.JobService.IngestJobPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class IngestService {

    private static final Logger LOG = Logger.getLogger(IngestService.class);
    private static final int MAX_BATCH_SIZE = 100;

    @Inject
    JobService jobService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    EmbeddingService embeddingService;

    @Transactional
    public IngestResultDto ingestNodes(NodesIngestRequestDto request) {
        validateNodesRequest(request);
        int upserted = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (NodeIngestEntryDto entry : request.nodes) {
            try {
                NodeType nodeType = NodeType.valueOf(entry.nodeType.trim().toUpperCase());
                if (nodeType == NodeType.JOB) {
                    IngestJobPayload payload = toPayload(entry);
                    jobService.upsertFromIngest(entry.source, entry.externalId, payload);
                } else {
                    upsertGenericNode(nodeType, entry);
                }
                upserted++;
            } catch (Exception e) {
                LOG.warnf("Nodes ingest failed for node_type=%s external_id=%s", entry.nodeType, entry.externalId);
                errors.add(Map.of(
                        "node_type", entry.nodeType != null ? entry.nodeType : "",
                        "external_id", entry.externalId != null ? entry.externalId : "",
                        "error", "Failed to upsert node"
                ));
            }
        }
        LOG.infof("Nodes ingest: %d upserted, %d failed", upserted, errors.size());
        return new IngestResultDto(upserted, errors.size(), errors);
    }

    @Transactional
    public IngestResultDto ingestUsers(UsersIngestRequestDto request) {
        validateUsersRequest(request);
        int upserted = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (UserIngestEntryDto entry : request.users) {
            try {
                upsertUser(entry);
                upserted++;
            } catch (Exception e) {
                LOG.warnf("Users ingest failed for external_id=%s", entry.externalId);
                errors.add(Map.of(
                        "external_id", entry.externalId != null ? entry.externalId : "",
                        "error", "Failed to upsert user"
                ));
            }
        }
        LOG.infof("Users ingest: %d upserted, %d failed", upserted, errors.size());
        return new IngestResultDto(upserted, errors.size(), errors);
    }

    private void validateNodesRequest(NodesIngestRequestDto request) {
        if (request == null) {
            throw new ValidationBusinessException("Request body is required");
        }
        if (request.nodes == null || request.nodes.isEmpty()) {
            throw new ValidationBusinessException("nodes array is required");
        }
        if (request.nodes.size() > MAX_BATCH_SIZE) {
            throw new ValidationBusinessException("batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
    }

    private static IngestJobPayload toPayload(NodeIngestEntryDto entry) {
        return new IngestJobPayload(
                entry.title,
                entry.description,
                entry.requirementsText,
                entry.skillsRequired,
                entry.workMode,
                entry.employmentType,
                entry.country,
                entry.status,
                entry.externalUrl
        );
    }

    private void upsertGenericNode(NodeType nodeType, NodeIngestEntryDto entry) {
        MeshNode node = nodeRepository.findByTypeAndSourceAndExternalId(nodeType, entry.source, entry.externalId)
                .orElseGet(MeshNode::new);

        node.nodeType = nodeType;
        node.externalId = entry.externalId;
        node.title = entry.title;
        node.description = entry.description;
        node.country = entry.country;
        node.tags = entry.tags;
        node.searchable = entry.searchable == null || entry.searchable;

        Map<String, Object> structuredData = new LinkedHashMap<>();
        if (entry.structuredData != null) {
            structuredData.putAll(entry.structuredData);
        }
        structuredData.put("source", entry.source);
        structuredData.put("external_id", entry.externalId);
        node.structuredData = structuredData;
        node.embedding = embeddingService.generateEmbedding(EmbeddingTextBuilder.buildText(node));
        nodeRepository.persist(node);
    }

    private void validateUsersRequest(UsersIngestRequestDto request) {
        if (request == null) {
            throw new ValidationBusinessException("Request body is required");
        }
        if (request.users == null || request.users.isEmpty()) {
            throw new ValidationBusinessException("users array is required");
        }
        if (request.users.size() > MAX_BATCH_SIZE) {
            throw new ValidationBusinessException("batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
    }

    private void upsertUser(UserIngestEntryDto entry) {
        MeshNode node = nodeRepository.findUserByExternalId(entry.externalId)
                .orElseGet(MeshNode::new);
        if (node.id == null && entry.nodeId != null) {
            node.id = entry.nodeId;
        }
        node.nodeType = NodeType.USER;
        node.externalId = entry.externalId;
        node.title = entry.title;
        node.description = entry.description;
        node.country = entry.country;
        node.tags = entry.tags;
        node.searchable = entry.searchable == null || entry.searchable;
        node.structuredData = entry.structuredData != null ? new LinkedHashMap<>(entry.structuredData) : null;
        nodeRepository.persist(node);
    }
}
