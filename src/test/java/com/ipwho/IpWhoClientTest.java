package com.ipwho;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a local stand-in server that replays recorded API responses.
 * No API key and no network access are needed.
 */
class IpWhoClientTest {
    private static final String KEY = "sk.test_secret_value";

    private HttpServer server;
    private String url;
    private IpWhoClient client;
    private final List<String> targets = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private final List<String> userAgents = new ArrayList<>();
    private int status;
    private String body;

    @BeforeEach
    void start() throws IOException {
        reply(200, fixture("lookup"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            targets.add(exchange.getRequestURI().toString());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        client = clientFor(KEY, url);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static IpWhoClient clientFor(String key, String baseUrl) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return new IpWhoClient(key, baseUrl, http, Duration.ofSeconds(5));
    }

    private void reply(int status, String body) {
        this.status = status;
        this.body = body;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = IpWhoClientTest.class.getResourceAsStream("/" + name + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void keyIsInTheHeaderAndNeverInTheUrl() throws IOException {
        client.lookup("8.8.8.8");
        client.me();
        reply(200, fixture("bulk"));
        client.bulk(List.of("8.8.8.8", "1.1.1.1"));

        assertEquals(3, targets.size());
        for (int i = 0; i < targets.size(); i++) {
            assertEquals("Bearer " + KEY, authorizations.get(i));
            assertEquals("ipwho-java-sdk/1.0.0", userAgents.get(i));
            assertFalse(targets.get(i).contains(KEY), targets.get(i));
            assertFalse(targets.get(i).toLowerCase().contains("apikey"), targets.get(i));
        }
    }

    @Test
    void lookupMapsTheResponse() {
        IpGeoResponse.GeoData data = client.lookup("8.8.8.8").getData();
        assertEquals("/ip/8.8.8.8", targets.get(0));
        assertEquals("8.8.8.8", data.getIp());
        assertEquals("United States", data.getGeoLocation().getCountry());
        assertEquals("US", data.getGeoLocation().getCountryCode());
        assertEquals("+1", data.getGeoLocation().getDialCode());
        assertEquals(15169, data.getConnection().getAsnNumber().intValue());
        assertEquals("America/Chicago", data.getTimezone().getTimeZone());
        assertEquals("USD", data.getCurrency().getCode());
    }

    @Test
    void lookupAcceptsANullCurrency() throws IOException {
        reply(200, fixture("lookup-null-currency"));
        IpGeoResponse.GeoData data = client.lookup("1.1.1.1").getData();
        assertEquals("1.1.1.1", data.getIp());
        assertNull(data.getCurrency());
    }

    @Test
    void bulkReturnsTheDataForEveryAddress() throws IOException {
        reply(200, fixture("bulk"));
        List<IpGeoResponse> rows = client.bulk(List.of("8.8.8.8", "1.1.1.1", "192.168.0.1", "9.9.9.9"));
        assertEquals(List.of("8.8.8.8", "1.1.1.1", "192.168.0.1", "9.9.9.9"),
                rows.stream().map(row -> row.getData().getIp()).collect(Collectors.toList()));

        assertTrue(rows.get(0).isSuccess());
        assertEquals("United States", rows.get(0).getData().getGeoLocation().getCountry());
        assertEquals(15169, rows.get(0).getData().getConnection().getAsnNumber().intValue());

        assertTrue(rows.get(1).isSuccess());
        assertNull(rows.get(1).getData().getCurrency());

        assertFalse(rows.get(2).isSuccess());
        assertNull(rows.get(2).getData().getGeoLocation());
        assertEquals("Reserved range/Invalid IP address", rows.get(2).getMessage());

        assertFalse(rows.get(3).isSuccess());
        assertTrue(rows.get(3).getMessage().contains("quota"));
    }

    @Test
    void fieldsAndFormatAreTheOnlyQueryParameters() {
        client.lookup("8.8.8.8", null, "geoLocation,timezone");
        assertEquals("/ip/8.8.8.8?get=geoLocation%2Ctimezone", targets.get(0));
        reply(200, "ip,8.8.8.8");
        assertEquals("ip,8.8.8.8", client.lookup("8.8.8.8", "csv", null).getData().getIp());
        assertEquals("/ip/8.8.8.8?format=csv", targets.get(1));
    }

    @Test
    void httpErrors() {
        reply(404, "{\"success\":false,\"message\":\"Reserved range/Invalid IP address\"}");
        assertThrows(NotFoundException.class, () -> client.lookup("8.8.8.8"));
        reply(429, "{\"error\":\"rate_limit_exceeded\"}");
        assertThrows(RateLimitException.class, () -> client.lookup("8.8.8.8"));
        reply(401, "{\"error\":\"invalid_api_key\"}");
        IpWhoException error = assertThrows(IpWhoException.class, () -> client.lookup("8.8.8.8"));
        assertEquals(401, error.getStatusCode());
        assertEquals("invalid_api_key", error.getMessage());
        assertFalse(error.getMessage().contains(KEY));
    }

    @Test
    void connectionFailureThrowsWithoutTheKey() {
        IpWhoException error = assertThrows(IpWhoException.class,
                () -> clientFor(KEY, "http://127.0.0.1:1").lookup("8.8.8.8"));
        assertEquals(0, error.getStatusCode());
        assertFalse(String.valueOf(error.getMessage()).contains(KEY));
        assertFalse(String.valueOf(error.getCause()).contains(KEY));
    }

    @Test
    void keyWithSurroundingWhitespaceIsTrimmed() {
        clientFor("  " + KEY + "\n", url).lookup("8.8.8.8");
        assertEquals("Bearer " + KEY, authorizations.get(0));
    }

    @Test
    void keyWithInvalidCharactersIsRejectedWithoutRepeatingIt() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new IpWhoClient("sk.secret_part\nmore"));
        assertFalse(error.getMessage().contains("secret_part"));
        assertThrows(IllegalArgumentException.class, () -> new IpWhoClient("  \n"));
    }
}
