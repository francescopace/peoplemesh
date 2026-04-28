package org.peoplemesh.api.resource;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.InfoResponse;
import org.peoplemesh.service.OAuthLoginService;

@Path("/api/v1/info")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class InfoResource {

    @Inject
    AppConfig appConfig;

    @Inject
    OAuthLoginService oAuthLoginService;

    @GET
    public Response info() {
        AppConfig.OrganizationConfig org = appConfig.organization();
        InfoResponse response = new InfoResponse(
                org.name().orElse(null),
                org.contactEmail().orElse(null),
                org.dpoName().orElse(null),
                org.dpoEmail().orElse(null),
                org.dataLocation().orElse(null),
                org.governingLaw().orElse(null),
                oAuthLoginService.providers()
        );
        return Response.ok(response).build();
    }
}
