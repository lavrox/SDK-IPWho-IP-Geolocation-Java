package com.ipwho;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class IpWhoClient {
    private static final String DEFAULT_BASE_URL = "https://api.ipwho.org";
    private static final String VERSION = "1.0.0";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new IPWho API client.
     *
     * @param apiKey Your IPWho API key (required).
     */
    public IpWhoClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build());
    }

    /**
     * Creates a new IPWho API client with custom base URL and HTTP client.
     * Each request times out after 30 seconds.
     *
     * @param apiKey     Your IPWho API key.
     * @param baseUrl    Override the API base URL.
     * @param httpClient Custom {@link HttpClient}, for example to use a proxy.
     */
    public IpWhoClient(String apiKey, String baseUrl, HttpClient httpClient) {
        this(apiKey, baseUrl, httpClient, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new IPWho API client with a custom request timeout.
     *
     * @param apiKey         Your IPWho API key.
     * @param baseUrl        Override the API base URL.
     * @param httpClient     Custom {@link HttpClient}.
     * @param requestTimeout How long to wait for each response.
     */
    public IpWhoClient(String apiKey, String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        String key = apiKey == null ? "" : apiKey.strip();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        // The key is sent in a header. Reject it here, without repeating it,
        // rather than let the HTTP client quote it in an error message.
        if (!key.chars().allMatch(c -> c >= ' ' && c <= '~')) {
            throw new IllegalArgumentException("apiKey contains characters that are not allowed");
        }
        this.apiKey = key;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Look up geolocation for a specific IP address.
     *
     * @param ip     The IPv4 or IPv6 address.
     * @param format Response format: "json" (default), "xml", "csv".
     * @param fields Comma-separated list of fields to include, or null for all.
     * @return IpGeoResponse with the lookup result.
     * @throws IpWhoException on API or transport errors.
     */
    public IpGeoResponse lookup(String ip, String format, String fields) {
        return execute(buildUri("/ip/" + encode(ip), format, fields), format);
    }

    /**
     * Look up geolocation for a specific IP address (JSON, all fields).
     */
    public IpGeoResponse lookup(String ip) {
        return lookup(ip, null, null);
    }

    /**
     * Look up geolocation for the caller's own IP address.
     *
     * @param format Response format (see {@link #lookup(String, String, String)}).
     * @param fields Fields filter.
     * @return IpGeoResponse with the caller's geolocation.
     */
    public IpGeoResponse me(String format, String fields) {
        return execute(buildUri("/me", format, fields), format);
    }

    /**
     * Look up geolocation for the caller's own IP address (JSON, all fields).
     */
    public IpGeoResponse me() {
        return me(null, null);
    }

    /**
     * Perform geolocation lookups for multiple IP addresses.
     *
     * @param ips List of IPv4 or IPv6 addresses.
     * @return List of IpGeoResponse, one per IP in the same order. Check each row's
     *         {@code isSuccess()}. A failed row has a message, and its data holds only the IP.
     * @throws IpWhoException on API or transport errors.
     */
    public List<IpGeoResponse> bulk(List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            throw new IllegalArgumentException("ips must not be empty");
        }
        String bulkParam = ips.stream().map(IpWhoClient::encode).collect(Collectors.joining(","));
        return executeBulk(buildUri("/bulk/" + bulkParam, null, null));
    }

    // ── Internal ────────────────────────────────────────────────────

    /** The API key is not part of the URL: {@link #send} puts it in a header. */
    private URI buildUri(String path, String format, String fields) {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        char separator = '?';
        if (isRawFormat(format)) {
            url.append(separator).append("format=").append(encode(format));
            separator = '&';
        }
        if (fields != null && !fields.isEmpty()) {
            url.append(separator).append("get=").append(encode(fields));
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean isRawFormat(String format) {
        return format != null && !format.isEmpty() && !"json".equals(format);
    }

    private HttpResponse<String> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "ipwho-java-sdk/" + VERSION)
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IpWhoException("Request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IpWhoException("Request interrupted", e);
        }
    }

    private IpGeoResponse execute(URI uri, String format) {
        HttpResponse<String> response = send(uri);
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode != 200) {
            throw mapError(statusCode, body);
        }

        // Non-JSON format
        if (isRawFormat(format)) {
            IpGeoResponse r = new IpGeoResponse();
            r.setSuccess(true);
            IpGeoResponse.GeoData data = new IpGeoResponse.GeoData();
            data.setIp(body);
            r.setData(data);
            return r;
        }

        try {
            IpGeoResponse resp = objectMapper.readValue(body, IpGeoResponse.class);
            if (!resp.isSuccess()) {
                throw new IpWhoException(statusCode,
                        resp.getMessage() != null ? resp.getMessage() : "API returned success=false");
            }
            return resp;
        } catch (IOException e) {
            throw new IpWhoException("Request failed: " + e.getMessage(), e);
        }
    }

    private List<IpGeoResponse> executeBulk(URI uri) {
        HttpResponse<String> response = send(uri);
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode != 200) {
            throw mapError(statusCode, body);
        }

        try {
            BulkResponse bulkResp = objectMapper.readValue(body, BulkResponse.class);
            if (!bulkResp.isSuccess()) {
                throw new IpWhoException(statusCode, "API returned success=false for bulk request");
            }
            if (bulkResp.getData() == null || bulkResp.getData().getResponseArray() == null) {
                throw new IpWhoException(statusCode, "Bulk response data is null");
            }
            return bulkResp.getData().getResponseArray();
        } catch (IOException e) {
            throw new IpWhoException("Request failed: " + e.getMessage(), e);
        }
    }

    private IpWhoException mapError(int statusCode, String body) {
        String message = "HTTP " + statusCode;
        try {
            ErrorResponse err = objectMapper.readValue(body, ErrorResponse.class);
            if (err.getMessage() != null && !err.getMessage().isEmpty()) {
                message = err.getMessage();
            }
        } catch (Exception ignored) {
            if (!body.isEmpty()) message = body;
        }
        switch (statusCode) {
            case 404: return new NotFoundException(statusCode, message);
            case 429: return new RateLimitException(statusCode, message);
            default:  return new IpWhoException(statusCode, message);
        }
    }
}
