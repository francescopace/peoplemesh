package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.domain.dto.CatalogCreateRequest;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.EntitlementService;
import org.peoplemesh.service.SkillCatalogService;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Path("/api/v1/skills")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class SkillsResource {

    @Inject
    SkillCatalogService catalogService;

    @Inject
    UserResolver userResolver;

    @Inject
    EntitlementService entitlementService;

    @GET
    public Response listCatalogs() {
        List<SkillCatalog> catalogs = catalogService.listCatalogs();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillCatalog c : catalogs) {
            result.add(catalogToMap(c));
        }
        return Response.ok(result).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createCatalog(@Valid CatalogCreateRequest body) {
        assertCanManageSkills();

        Map<String, Object> levelScale = body.levelScale();
        if (levelScale == null || levelScale.isEmpty()) {
            levelScale = defaultLevelScale();
        }

        SkillCatalog catalog = catalogService.createCatalog(
                body.name(), body.description(), levelScale, body.source());
        return Response.status(Response.Status.CREATED).entity(catalogToMap(catalog)).build();
    }

    @PUT
    @Path("/{catalogId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCatalog(@PathParam("catalogId") UUID catalogId, @Valid CatalogCreateRequest body) {
        assertCanManageSkills();
        SkillCatalog updated = catalogService.updateCatalog(
                catalogId, body.name(), body.description(), body.levelScale(), body.source());
        return Response.ok(catalogToMap(updated)).build();
    }

    @GET
    @Path("/{catalogId}")
    public Response getCatalog(@PathParam("catalogId") UUID catalogId) {
        return catalogService.getCatalog(catalogId)
                .map(c -> Response.ok(catalogToMap(c)).build())
                .orElse(Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "Catalog not found"))
                        .build());
    }

    @POST
    @Path("/{catalogId}/import")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response importCsv(@PathParam("catalogId") UUID catalogId, InputStream csvStream) throws IOException {
        assertCanManageSkills();
        int count = catalogService.importFromCsv(catalogId, csvStream);
        catalogService.generateEmbeddings(catalogId);
        return Response.ok(Map.of("imported", count)).build();
    }

    @GET
    @Path("/{catalogId}/definitions")
    public Response listSkills(@PathParam("catalogId") UUID catalogId,
                               @QueryParam("category") String category,
                               @QueryParam("page") @DefaultValue("0") int page,
                               @QueryParam("size") @DefaultValue("50") int size) {
        List<SkillDefinition> skills = catalogService.listSkills(catalogId, category, page, Math.min(size, 200));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillDefinition sd : skills) {
            result.add(skillToMap(sd));
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("/{catalogId}/categories")
    public Response listCategories(@PathParam("catalogId") UUID catalogId) {
        List<String> categories = SkillDefinition.listCategories(catalogId);
        return Response.ok(categories).build();
    }

    @DELETE
    @Path("/{catalogId}")
    public Response deleteCatalog(@PathParam("catalogId") UUID catalogId) {
        assertCanManageSkills();
        catalogService.deleteCatalog(catalogId);
        return Response.noContent().build();
    }

    private static Map<String, Object> catalogToMap(SkillCatalog c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("name", c.name);
        m.put("description", c.description);
        m.put("level_scale", c.levelScale);
        m.put("source", c.source);
        m.put("skill_count", SkillDefinition.countByCatalog(c.id));
        m.put("created_at", c.createdAt != null ? c.createdAt.toString() : null);
        m.put("updated_at", c.updatedAt != null ? c.updatedAt.toString() : null);
        return m;
    }

    private static Map<String, Object> skillToMap(SkillDefinition sd) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sd.id);
        m.put("category", sd.category);
        m.put("name", sd.name);
        m.put("aliases", sd.aliases);
        m.put("lxp_recommendation", sd.lxpRecommendation);
        m.put("has_embedding", sd.embedding != null);
        return m;
    }

    private void assertCanManageSkills() {
        UUID userId = userResolver.resolveUserId();
        if (!entitlementService.canManageSkills(userId)) {
            throw new ForbiddenException("Skill catalog management requires can_manage_skills entitlement");
        }
    }

    private static Map<String, Object> defaultLevelScale() {
        Map<String, Object> scale = new LinkedHashMap<>();
        scale.put("0", "None");
        scale.put("1", "Aware");
        scale.put("2", "Beginner");
        scale.put("3", "Practitioner");
        scale.put("4", "Advanced");
        scale.put("5", "Expert");
        return scale;
    }
}
