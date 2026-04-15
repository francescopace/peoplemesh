package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.service.SystemStatisticsService;

@Path("/api/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class SystemResource {

    @Inject
    SystemStatisticsService systemStatisticsService;

    @GET
    @Authenticated
    @Path("/statistics")
    public Response getStatistics() {
        SystemStatisticsDto statistics = systemStatisticsService.loadStatistics();
        return Response.ok(statistics).build();
    }
}
