package org.peoplemesh.api.resource;

import io.smallrye.common.annotation.Blocking;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.Priority;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;

@Path("/")
@Priority(Priorities.USER + 1000)
@Blocking
public class StaticResource {

    private static final String FRONTEND_DIR = "src/main/web";

    private static final java.nio.file.Path BASE_DIR = Paths.get(FRONTEND_DIR).toAbsolutePath().normalize();

    @ConfigProperty(name = "peoplemesh.frontend.enabled", defaultValue = "false")
    boolean staticFrontendEnabled;

    @GET
    @Path("{path: .*}")
    public Response serveFile(@PathParam("path") String path) throws IOException {
        if (!staticFrontendEnabled) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        java.nio.file.Path filePath;
        try {
            filePath = BASE_DIR.resolve(path).normalize();
        } catch (InvalidPathException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (!filePath.startsWith(BASE_DIR)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (!Files.isDirectory(BASE_DIR, LinkOption.NOFOLLOW_LINKS)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(filePath)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        java.nio.file.Path realBase = BASE_DIR.toRealPath(LinkOption.NOFOLLOW_LINKS);
        java.nio.file.Path realFile = filePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realFile.startsWith(realBase)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        String contentType = Files.probeContentType(realFile);
        return Response.ok(Files.readAllBytes(realFile))
                .type(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }
}
