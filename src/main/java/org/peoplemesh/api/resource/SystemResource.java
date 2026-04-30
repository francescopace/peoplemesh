package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.SystemStatisticsService;

@Path("/api/v1/system")
@Produces(MediaType.APPLICATION_JSON)
public class SystemResource {

    @Inject
    SystemStatisticsService systemStatisticsService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Authenticated
    @Path("/statistics")
    public Response getStatistics() {
        var userId = currentUserService.resolveUserId();
        SystemStatisticsDto statistics = systemStatisticsService.loadStatisticsForUser(userId);
        return Response.ok(statistics).build();
    }
}
