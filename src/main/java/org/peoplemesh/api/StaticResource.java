package org.peoplemesh.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Priority(Priorities.USER + 1000)
@IfBuildProfile("dev")
public class StaticResource {

    private static final String FRONTEND_DIR = "src/main/web";

    private static final java.nio.file.Path BASE_DIR = Paths.get(FRONTEND_DIR).toAbsolutePath().normalize();

    @GET
    @Path("{path: .*}")
    public Response serveFile(@PathParam("path") String path) throws IOException {
        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        java.nio.file.Path filePath = BASE_DIR.resolve(path).normalize();
        if (!filePath.startsWith(BASE_DIR)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (!Files.exists(filePath)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String contentType = Files.probeContentType(filePath);
        return Response.ok(Files.readAllBytes(filePath))
                .type(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }
}
