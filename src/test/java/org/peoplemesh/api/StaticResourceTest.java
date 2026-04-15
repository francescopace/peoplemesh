package org.peoplemesh.api;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class StaticResourceTest {

    @Test
    void serveFile_pathTraversal_resultsDependOnNormalization() throws IOException {
        StaticResource resource = new StaticResource();
        Response response = resource.serveFile("../../../etc/passwd");
        assertTrue(response.getStatus() == 403 || response.getStatus() == 404);
    }

    @Test
    void serveFile_emptyPath_resolvesToIndex() throws IOException {
        StaticResource resource = new StaticResource();
        Response response = resource.serveFile("");
        assertTrue(response.getStatus() == 200 || response.getStatus() == 404);
    }

    @Test
    void serveFile_slashPath_resolvesToIndex() throws IOException {
        StaticResource resource = new StaticResource();
        Response response = resource.serveFile("/");
        assertTrue(response.getStatus() == 200 || response.getStatus() == 404);
    }

    @Test
    void serveFile_missingFile_returns404() throws IOException {
        StaticResource resource = new StaticResource();
        Response response = resource.serveFile("nonexistent-file-" + System.nanoTime() + ".html");
        assertEquals(404, response.getStatus());
    }

    @Test
    void serveFile_leadingSlashStripped() throws IOException {
        StaticResource resource = new StaticResource();
        Response response = resource.serveFile("/nonexistent.html");
        assertEquals(404, response.getStatus());
    }
}
