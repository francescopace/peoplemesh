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

    @ConfigProperty(name = "peoplemesh.frontend.enabled", defaultValue = "false")
    boolean staticFrontendEnabled;

    @ConfigProperty(name = "peoplemesh.frontend.dir", defaultValue = "src/main/web")
    String frontendDir;

    @GET
    @Path("{path: .*}")
    public Response serveFile(@PathParam("path") String path) throws IOException {
        if (!staticFrontendEnabled) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        java.nio.file.Path baseDir;
        try {
            baseDir = Paths.get(frontendDir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        java.nio.file.Path filePath;
        try {
            filePath = baseDir.resolve(path).normalize();
        } catch (InvalidPathException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (!filePath.startsWith(baseDir)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (!Files.isDirectory(baseDir, LinkOption.NOFOLLOW_LINKS)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(filePath)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Follow symlinks when resolving canonical paths so symlinked parent directories cannot escape BASE_DIR.
        java.nio.file.Path realBase = baseDir.toRealPath();
        java.nio.file.Path realFile = filePath.toRealPath();
        if (!realFile.startsWith(realBase)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        String contentType = Files.probeContentType(realFile);
        return Response.ok(Files.readAllBytes(realFile))
                .type(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }
}
