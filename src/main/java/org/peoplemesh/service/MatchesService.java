package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchesService {

    @Inject
    EmbeddingService embeddingService;

    @Inject
    MatchingService matchingService;

    @Inject
    SearchService searchService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    NodeAccessPolicyService nodeAccessPolicyService;

    public List<MeshMatchResult> matchFromSchema(UUID userId, ProfileSchema profile, String type, String country) {
        String text = EmbeddingTextBuilder.buildFromSchema(profile);
        float[] embedding = embeddingService.generateEmbedding(text);
        if (embedding == null) {
            throw new ValidationBusinessException("Embedding input was empty");
        }
        return matchingService.findAllMatches(userId, embedding, type, country);
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request) {
        return searchService.search(userId, request.query(), request.country());
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country) {
        return matchingService.findAllMatches(userId, type, country);
    }

    public List<MeshMatchResult> matchFromNode(UUID userId, UUID nodeId, String type, String country) {
        MeshNode node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null || node.embedding == null) {
            throw new NotFoundBusinessException("Node not found or has no embedding");
        }
        if (!nodeAccessPolicyService.canReadNode(userId, node)) {
            throw new ForbiddenBusinessException("You do not have access to this node");
        }
        return matchingService.findAllMatches(userId, node.embedding, type, country);
    }
}
