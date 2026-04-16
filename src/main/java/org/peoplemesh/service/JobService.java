package org.peoplemesh.service;

import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JobService {

    private static final Logger LOG = Logger.getLogger(JobService.class);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService auditService;

    @Inject
    NodeRepository nodeRepository;

    /**
     * Upsert a job from an ATS feed. If a JOB node with the given externalId
     * already exists for the owner, it is updated; otherwise a new one is created.
     * Jobs are published immediately.
     */
    @Transactional
    public JobPostingDto upsertFromAts(UUID ownerUserId, String externalId, AtsJobPayload payload) {
        LOG.infof("action=upsertJob userId=%s externalId=%s", ownerUserId, externalId);
        MeshNode node = loadByExternalId(ownerUserId, externalId).orElseGet(() -> {
            MeshNode n = new MeshNode();
            n.createdBy = ownerUserId;
            n.nodeType = NodeType.JOB;

            return n;
        });
        boolean isNew = node.id == null;

        node.title = payload.title() != null ? payload.title().trim() : null;
        node.description = payload.description() != null ? payload.description().trim() : null;
        node.country = payload.country();

        Map<String, Object> sd = new LinkedHashMap<>();
        if (node.structuredData != null) {
            sd.putAll(node.structuredData);
        }
        sd.put("external_id", externalId);
        sd.put("requirements_text", payload.requirementsText());
        sd.put("skills_required", payload.skillsRequired() != null ? payload.skillsRequired() : List.of());
        if (payload.workMode() != null) {
            sd.put("work_mode", payload.workMode());
        }
        if (payload.employmentType() != null) {
            sd.put("employment_type", payload.employmentType());
        }
        if (payload.externalUrl() != null) {
            sd.put("external_url", payload.externalUrl());
        }
        node.structuredData = sd;
        node.tags = payload.skillsRequired() != null ? List.copyOf(payload.skillsRequired()) : null;

        if (isClosedStatus(payload.status())) {
            if (!isNew) {
                deleteNode(node);
                auditService.log(ownerUserId, "JOB_ATS_CLOSED", "ats_ingest");
            }
            return JobPostingDto.fromMeshNode(node);
        }

        node.embedding = generateEmbedding(jobNodeToText(node));
        persistNode(node);

        auditService.log(ownerUserId, isNew ? "JOB_ATS_CREATED" : "JOB_ATS_UPDATED", "ats_ingest");
        return JobPostingDto.fromMeshNode(node);
    }

    Optional<MeshNode> loadByExternalId(UUID ownerUserId, String externalId) {
        return nodeRepository.findJobByExternalId(ownerUserId, externalId);
    }

    float[] generateEmbedding(String text) {
        return embeddingService.generateEmbedding(text);
    }

    void persistNode(MeshNode node) {
        nodeRepository.persist(node);
        nodeRepository.flush();
    }

    void deleteNode(MeshNode node) {
        nodeRepository.delete(node);
        nodeRepository.flush();
    }

    private static boolean isClosedStatus(String atsStatus) {
        if (atsStatus == null) return false;
        return switch (atsStatus.toLowerCase()) {
            case "filled", "hired", "closed", "archived", "cancelled", "deleted" -> true;
            default -> false;
        };
    }

    private String jobNodeToText(MeshNode node) {
        return EmbeddingTextBuilder.buildText(node);
    }

    public record AtsJobPayload(
            String title,
            String description,
            String requirementsText,
            List<String> skillsRequired,
            String workMode,
            String employmentType,
            String country,
            String status,
            String externalUrl
    ) {}
}
