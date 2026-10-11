package com.wrongquestion.backend.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

class AiVisionHttpClientTest {
    private AiVisionClient client(HttpServer server) {
        return new AiVisionClient(
                new AiProperties(
                        "compatible",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "private-fixture-key",
                        "vision",
                        1,
                        1,
                        3,
                        2,
                        20,
                        false,
                        300,
                        "json_object",
                        null,
                        null),
                new ObjectMapper());
    }

    private HttpServer server() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(
                Executors.newCachedThreadPool(
                        r -> {
                            var thread = new Thread(r);
                            thread.setDaemon(true);
                            return thread;
                        }));
        return server;
    }

    @Test
    void shouldBoundTotalTimeEvenWhenResponseHeadersArriveButBodyStalls() throws Exception {
        var server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    exchange.close();
                });
        server.start();
        try {
            long start = System.nanoTime();
            var error =
                    assertThrows(
                            AiException.class,
                            () -> client(server).recognize(new byte[] {1}, "image/png", "[]"));
            assertEquals("AI_PROVIDER_TIMEOUT", error.getCode());
            assertTrue(error.isRetryable());
            assertTrue((System.nanoTime() - start) / 1_000_000 < 2500);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldStopReadingOversizedProviderResponse() throws Exception {
        var server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] bytes = "a".repeat(1024 * 1024 + 1).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try {
                        exchange.getResponseBody().write(bytes);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try {
            var error =
                    assertThrows(
                            AiException.class,
                            () -> client(server).recognize(new byte[] {1}, "image/png", "[]"));
            assertEquals("AI_INVALID_OUTPUT", error.getCode());
            assertFalse(error.isRetryable());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotExposeProviderBodyOrApiKeyWhenAuthenticationFails() throws Exception {
        var server = server();
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] bytes =
                            "private-fixture-key provider internal details"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        try {
            var error =
                    assertThrows(
                            AiException.class,
                            () -> client(server).recognize(new byte[] {1}, "image/png", "[]"));
            assertEquals("AI_PROVIDER_AUTHENTICATION", error.getCode());
            assertFalse(error.getMessage().contains("private-fixture-key"));
        } finally {
            server.stop(0);
        }
    }
}
