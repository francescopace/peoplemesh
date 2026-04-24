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

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/v1/info")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class InfoResource {

    @Inject
    AppConfig appConfig;

    @GET
    public Response info() {
        AppConfig.OrganizationConfig org = appConfig.organization();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("organizationName", org.name().orElse(null));
        result.put("contactEmail", org.contactEmail().orElse(null));
        result.put("dpoName", org.dpoName().orElse(null));
        result.put("dpoEmail", org.dpoEmail().orElse(null));
        result.put("dataLocation", org.dataLocation().orElse(null));
        result.put("governingLaw", org.governingLaw().orElse(null));
        return Response.ok(result).build();
    }
}
