package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StaticResourceTest {

    private static final Path FRONTEND_DIR = Paths.get("src/main/web").toAbsolutePath().normalize();

    private final List<Path> pathsToDelete = new ArrayList<>();
    private StaticResource resource;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(FRONTEND_DIR);
        resource = new StaticResource();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (int i = pathsToDelete.size() - 1; i >= 0; i--) {
            Files.deleteIfExists(pathsToDelete.get(i));
        }
    }

    @Test
    void serveFile_frontendDisabled_returns404() throws IOException {
        Response response = resource.serveFile("index.html");
        assertEquals(404, response.getStatus());
    }

    @Test
    void serveFile_emptyPath_servesIndex() throws IOException {
        resource.staticFrontendEnabled = true;
        Response response = resource.serveFile("");

        assertEquals(200, response.getStatus());
        assertPayloadHasContent(response);
        assertEquals(MediaType.TEXT_HTML, response.getMediaType().toString());
    }

    @Test
    void serveFile_slashPath_servesIndex() throws IOException {
        resource.staticFrontendEnabled = true;
        Response response = resource.serveFile("/");

        assertEquals(200, response.getStatus());
        assertPayloadHasContent(response);
    }

    @Test
    void serveFile_leadingSlashIsStripped() throws IOException {
        resource.staticFrontendEnabled = true;
        Path file = createFile("static-leading-slash-" + UUID.randomUUID() + ".txt", "hello-static");

        Response response = resource.serveFile("/" + file.getFileName());

        assertEquals(200, response.getStatus());
        assertArrayEquals("hello-static".getBytes(StandardCharsets.UTF_8), (byte[]) response.getEntity());
    }

    @Test
    void serveFile_invalidPath_returns400() throws IOException {
        resource.staticFrontendEnabled = true;
        Response response = resource.serveFile("invalid\u0000path");

        assertEquals(400, response.getStatus());
    }

    @Test
    void serveFile_pathTraversal_returns403() throws IOException {
        resource.staticFrontendEnabled = true;
        Response response = resource.serveFile("../../../etc/passwd");

        assertEquals(403, response.getStatus());
    }

    @Test
    void serveFile_missingFile_returns404WhenEnabled() throws IOException {
        resource.staticFrontendEnabled = true;
        Response response = resource.serveFile("nonexistent-file-" + UUID.randomUUID() + ".html");

        assertEquals(404, response.getStatus());
    }

    @Test
    void serveFile_directoryRequest_returns403() throws IOException {
        resource.staticFrontendEnabled = true;
        Path dir = createDirectory("static-resource-dir-" + UUID.randomUUID());

        Response response = resource.serveFile(dir.getFileName().toString());

        assertEquals(403, response.getStatus());
    }

    @Test
    void serveFile_realPathEscapeThroughSymlinkedParent_returns403() throws IOException {
        resource.staticFrontendEnabled = true;

        Path outsideDir = Files.createTempDirectory("static-resource-outside-");
        pathsToDelete.add(outsideDir);
        Path outsideFile = outsideDir.resolve("escape.txt");
        Files.writeString(outsideFile, "escape");
        pathsToDelete.add(outsideFile);

        Path symlinkDir = FRONTEND_DIR.resolve("static-link-" + UUID.randomUUID());
        try {
            Files.createSymbolicLink(symlinkDir, outsideDir);
        } catch (UnsupportedOperationException | FileAlreadyExistsException e) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this filesystem");
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "Cannot create symbolic links in this environment");
        }
        pathsToDelete.add(symlinkDir);

        Response response = resource.serveFile(symlinkDir.getFileName() + "/escape.txt");

        assertEquals(403, response.getStatus());
    }

    private Path createFile(String fileName, String content) throws IOException {
        Path file = FRONTEND_DIR.resolve(fileName);
        Files.writeString(file, content);
        pathsToDelete.add(file);
        return file;
    }

    private Path createDirectory(String dirName) throws IOException {
        Path dir = FRONTEND_DIR.resolve(dirName);
        Files.createDirectory(dir);
        pathsToDelete.add(dir);
        return dir;
    }

    private void assertPayloadHasContent(Response response) {
        assertInstanceOf(byte[].class, response.getEntity());
        assertTrue(((byte[]) response.getEntity()).length > 0);
    }
}
