package org.peoplemesh.api;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotBlockFilterTest {

    BotBlockFilter filter = new BotBlockFilter();

    @Mock RoutingContext ctx;
    @Mock HttpServerRequest request;
    @Mock HttpServerResponse response;

    private Method filterMethod;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(ctx.request()).thenReturn(request);
        lenient().when(ctx.response()).thenReturn(response);
        lenient().when(response.setStatusCode(anyInt())).thenReturn(response);
        lenient().when(response.putHeader(anyString(), anyString())).thenReturn(response);

        filterMethod = BotBlockFilter.class.getDeclaredMethod("filter", RoutingContext.class);
        filterMethod.setAccessible(true);
    }

    private void invokeFilter() throws Exception {
        filterMethod.invoke(filter, ctx);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/me", "/api/v1/auth/login/google", "/q/health", "/mcp/tools"})
    void allowedPrefixes_passThrough(String path) throws Exception {
        when(ctx.normalizedPath()).thenReturn(path);
        invokeFilter();
        verify(ctx).next();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/wp-admin/", "/wp-login.php", "/phpmyadmin/setup",
            "/.git/config", "/.env", "/admin/login",
            "/vendor/phpunit", "/cgi-bin/test",
            "/backup/db.sql", "/config/database.yml"
    })
    void blockedPaths_rejected(String path) throws Exception {
        when(ctx.normalizedPath()).thenReturn(path);
        when(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET);
        invokeFilter();
        verify(response).setStatusCode(404);
        verify(response).end("Not Found");
        verify(ctx, never()).next();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/robots.txt.php", "/test.asp", "/index.aspx",
            "/data.sql", "/backup.zip", "/archive.tar.gz",
            "/config.yml", "/settings.ini", "/app.log",
            "/old.bak", "/file.swp"
    })
    void blockedExtensions_rejected(String path) throws Exception {
        when(ctx.normalizedPath()).thenReturn(path);
        when(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET);
        invokeFilter();
        verify(response).setStatusCode(404);
        verify(ctx, never()).next();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/favicon.ico", "/some/page"})
    void nonBlockedNonAllowedPaths_passThrough(String path) throws Exception {
        when(ctx.normalizedPath()).thenReturn(path);
        invokeFilter();
        verify(ctx).next();
    }
}
