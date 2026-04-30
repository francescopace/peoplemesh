package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
    public Response listSkills(
            @QueryParam("q") @Size(max = 100) String query,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("50") @Min(1) @Max(200) int size) {
        List<SkillDefinitionDto> skills = skillsService.listSkills(query, page, size);
        return Response.ok(skills).build();
    }

    @GET
    @Path("/suggest")
    public Response suggestSkills(
            @QueryParam("q") @Size(max = 100) String query,
            @QueryParam("limit") @DefaultValue("20") @Min(1) @Max(200) int limit) {
        List<SkillDefinitionDto> skills = skillsService.suggestSkills(query, limit);
        return Response.ok(skills).build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response importCsv(InputStream csvStream) throws IOException {
        UUID userId = currentUserService.resolveUserId();
        int count = skillsService.importCsv(userId, csvStream);
        return Response.ok(Map.of("imported", count)).build();
    }

    @POST
    @Path("/cleanup-unused")
    public Response cleanupUnused() {
        UUID userId = currentUserService.resolveUserId();
        int deleted = skillsService.cleanupUnused(userId);
        return Response.ok(Map.of("deleted", deleted)).build();
    }
}
