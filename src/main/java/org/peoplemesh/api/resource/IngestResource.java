package org.peoplemesh.api.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.IngestResultDto;
import org.peoplemesh.domain.dto.NodesIngestRequestDto;
import org.peoplemesh.domain.dto.UsersIngestRequestDto;
import org.peoplemesh.security.MaintenanceAccessGuard;
import org.peoplemesh.service.IngestService;

@Path("/api/v1/maintenance/ingest")
@Produces(MediaType.APPLICATION_JSON)
public class IngestResource {

    @Inject
    AppConfig config;

    @Inject
    IngestService ingestService;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/nodes")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestNodes(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key,
                                @Valid NodesIngestRequestDto request) {
        assertAuthorized(key);
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        IngestResultDto result = ingestService.ingestNodes(request);
        return Response.ok(result).build();
    }

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestUsers(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key,
                                @Valid UsersIngestRequestDto request) {
        assertAuthorized(key);
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        IngestResultDto result = ingestService.ingestUsers(request);
        return Response.ok(result).build();
    }

    private void assertAuthorized(String key) {
        MaintenanceAccessGuard.assertAuthorized(key, config, httpHeaders);
    }
}
