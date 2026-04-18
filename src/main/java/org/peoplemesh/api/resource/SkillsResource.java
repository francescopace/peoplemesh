package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.domain.dto.CatalogCreateRequest;
import org.peoplemesh.domain.dto.SkillCatalogDto;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.SkillsService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/skills")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class SkillsResource {

    @Inject
    SkillsService skillsService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    public Response listCatalogs() {
        List<SkillCatalogDto> catalogs = skillsService.listCatalogs();
        return Response.ok(catalogs).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createCatalog(@Valid CatalogCreateRequest body) {
        UUID userId = currentUserService.resolveUserId();
        SkillCatalogDto catalog = skillsService.createCatalog(userId, body);
        return Response.status(Response.Status.CREATED).entity(catalog).build();
    }

    @PUT
    @Path("/{catalogId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCatalog(@PathParam("catalogId") UUID catalogId, @Valid CatalogCreateRequest body) {
        UUID userId = currentUserService.resolveUserId();
        SkillCatalogDto updated = skillsService.updateCatalog(userId, catalogId, body);
        return Response.ok(updated).build();
    }

    @GET
    @Path("/{catalogId}")
    public Response getCatalog(@PathParam("catalogId") UUID catalogId) {
        SkillCatalogDto catalog = skillsService.getCatalog(catalogId);
        return Response.ok(catalog).build();
    }

    @POST
    @Path("/{catalogId}/import")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response importCsv(@PathParam("catalogId") UUID catalogId, InputStream csvStream) throws IOException {
        UUID userId = currentUserService.resolveUserId();
        int count = skillsService.importCsv(userId, catalogId, csvStream);
        return Response.ok(Map.of("imported", count)).build();
    }

    @GET
    @Path("/{catalogId}/definitions")
    public Response listSkills(@PathParam("catalogId") UUID catalogId,
                               @QueryParam("category") @Size(max = 100) String category,
                               @QueryParam("page") @DefaultValue("0") @Min(0) int page,
                               @QueryParam("size") @DefaultValue("50") @Min(1) @Max(200) int size) {
        List<SkillDefinitionDto> skills = skillsService.listSkills(catalogId, category, page, size);
        return Response.ok(skills).build();
    }

    @GET
    @Path("/{catalogId}/categories")
    public Response listCategories(@PathParam("catalogId") UUID catalogId) {
        List<String> categories = skillsService.listCategories(catalogId);
        return Response.ok(categories).build();
    }

    @DELETE
    @Path("/{catalogId}")
    public Response deleteCatalog(@PathParam("catalogId") UUID catalogId) {
        UUID userId = currentUserService.resolveUserId();
        skillsService.deleteCatalog(userId, catalogId);
        return Response.noContent().build();
    }
}
