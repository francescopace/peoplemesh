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
import org.peoplemesh.util.EmbeddingTextBuilder;

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
        return matchFromSchema(userId, profile, type, country, null, null);
    }

    public List<MeshMatchResult> matchFromSchema(
            UUID userId, ProfileSchema profile, String type, String country, Integer limit, Integer offset) {
        String text = EmbeddingTextBuilder.buildFromSchema(profile);
        float[] embedding = embeddingService.generateEmbedding(text);
        if (embedding == null) {
            throw new ValidationBusinessException("Embedding input was empty");
        }
        return matchingService.findAllMatches(userId, embedding, type, country, limit, offset);
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request) {
        return matchFromPrompt(userId, request, null, null);
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request, Integer limit, Integer offset) {
        return searchService.search(userId, request.query(), limit, offset);
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country) {
        return matchMyProfile(userId, type, country, null, null);
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country, Integer limit, Integer offset) {
        return matchingService.findAllMatches(userId, type, country, limit, offset);
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
