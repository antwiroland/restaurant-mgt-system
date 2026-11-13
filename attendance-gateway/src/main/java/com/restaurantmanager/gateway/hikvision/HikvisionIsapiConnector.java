package com.restaurantmanager.gateway.hikvision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.gateway.device.CapturedEvent;
import com.restaurantmanager.gateway.device.DeviceConnector;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hikvision access-control / attendance terminals over ISAPI (HTTP with Digest authentication):
 * <ul>
 *   <li>{@code GET /ISAPI/Event/notification/alertStream}: live events (multipart stream that never ends)</li>
 *   <li>{@code POST /ISAPI/AccessControl/AcsEvent?format=json}: stored events between two times, in pages</li>
 *   <li>{@code GET /ISAPI/System/deviceInfo} and {@code /ISAPI/System/time}: identity and clock</li>
 * </ul>
 * Terminals with HTTPS usually have a self-signed certificate; it is accepted for the device's own address only.
 */
public class HikvisionIsapiConnector implements DeviceConnector {
    private static final Logger log = LoggerFactory.getLogger(HikvisionIsapiConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PAGE = 30;
    private static final Pattern XML_SERIAL = Pattern.compile("<serialNumber>([^<]*)</serialNumber>");
    private static final Pattern XML_MODEL = Pattern.compile("<model>([^<]*)</model>");
    private static final Pattern XML_LOCAL_TIME = Pattern.compile("<localTime>([^<]*)</localTime>");

    private final String baseUrl;
    private final CloseableHttpClient http;
    private final CloseableHttpClient streamHttp;
    private final HikvisionEventParser parser;
    private final ZoneId deviceZone;
    private volatile CloseableHttpResponse openStream;

    public HikvisionIsapiConnector(String serialNumber, String host, int port, boolean https, String username, String password,
                                   Set<Integer> acceptedMinors, ZoneId deviceZone) {
        this.baseUrl = (https ? "https://" : "http://") + host + ":" + port;
        this.deviceZone = deviceZone;
        this.parser = new HikvisionEventParser(serialNumber, acceptedMinors, deviceZone);
        BasicCredentialsProvider credentials = new BasicCredentialsProvider();
        credentials.setCredentials(new AuthScope(new HttpHost(https ? "https" : "http", host, port)),
                new UsernamePasswordCredentials(username, password == null ? new char[0] : password.toCharArray()));
        this.http = client(credentials, https, Duration.ofSeconds(30));
        // The alert stream stays open; devices send a heartbeat part regularly, so a long silence means it is dead.
        this.streamHttp = client(credentials, https, Duration.ofMinutes(3));
    }

    private static CloseableHttpClient client(BasicCredentialsProvider credentials, boolean https, Duration readTimeout) {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(10))
                        .setSocketTimeout(Timeout.of(readTimeout))
                        .build());
        if (https) {
            try {
                connections.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build())
                        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build());
            } catch (Exception e) {
                throw new IllegalStateException("Could not set up HTTPS for the device", e);
            }
        }
        return HttpClients.custom()
                .setConnectionManager(connections.build())
                .setDefaultCredentialsProvider(credentials)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(readTimeout)).build())
                .disableAutomaticRetries()
                .build();
    }

    @Override
    public DeviceProbe probe() throws IOException {
        String info = get("/ISAPI/System/deviceInfo");
        String serial = first(XML_SERIAL, info);
        String model = first(XML_MODEL, info);
        Integer offset = null;
        try {
            String time = first(XML_LOCAL_TIME, get("/ISAPI/System/time"));
            Instant deviceNow = time == null ? null : parser.parseTime(time);
            if (deviceNow != null) {
                offset = (int) Duration.between(Instant.now(), deviceNow).toSeconds();
            }
        } catch (IOException noClock) {
            log.debug("Device {} did not report its time: {}", baseUrl, noClock.getMessage());
        }
        return new DeviceProbe(serial, model, offset);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void listen(Consumer<CapturedEvent> onEvent) throws IOException {
        HttpGet request = new HttpGet(baseUrl + "/ISAPI/Event/notification/alertStream");
        try (CloseableHttpResponse response = streamHttp.execute(request)) { // stays open: the stream is read as it arrives
            openStream = response;
            if (response.getCode() != 200) {
                throw new IOException("Alert stream refused: HTTP " + response.getCode());
            }
            String contentType = response.getEntity().getContentType();
            try (InputStream body = response.getEntity().getContent()) {
                MultipartStreamReader reader = new MultipartStreamReader(body, MultipartStreamReader.boundaryOf(contentType));
                MultipartStreamReader.Part part;
                while ((part = reader.next()) != null) {
                    if (part.contentType().toLowerCase().contains("json")) {
                        parser.fromAlert(part.text()).ifPresent(onEvent);
                    }
                }
            }
        } finally {
            openStream = null;
        }
    }

    @Override
    public List<CapturedEvent> search(Instant from, Instant to) throws IOException {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(deviceZone);
        String searchId = UUID.randomUUID().toString();
        List<CapturedEvent> events = new ArrayList<>();
        int position = 0;
        for (int page = 0; page < 1000; page++) {
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("searchID", searchId);
            cond.put("searchResultPosition", position);
            cond.put("maxResults", PAGE);
            cond.put("major", HikvisionEventParser.MAJOR_EVENT);
            cond.put("minor", 0);
            cond.put("startTime", format.format(from));
            cond.put("endTime", format.format(to));
            HttpPost request = new HttpPost(baseUrl + "/ISAPI/AccessControl/AcsEvent?format=json");
            request.setEntity(new StringEntity(MAPPER.writeValueAsString(Map.of("AcsEventCond", cond)), ContentType.APPLICATION_JSON));
            JsonNode acs = http.execute(request, response -> {
                String text = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
                if (response.getCode() != 200) {
                    throw new IOException("Event search refused: HTTP " + response.getCode());
                }
                return MAPPER.readTree(text).path("AcsEvent");
            });
            events.addAll(parser.fromSearch(acs));
            int matches = acs.path("numOfMatches").asInt(acs.path("InfoList").size());
            position += matches;
            if (!"MORE".equalsIgnoreCase(acs.path("responseStatusStrg").asText()) || matches == 0) {
                break;
            }
        }
        return events;
    }

    @Override
    public void close() {
        CloseableHttpResponse stream = openStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        try {
            http.close();
            streamHttp.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }

    private String get(String path) throws IOException {
        return http.execute(new HttpGet(baseUrl + path), response -> {
            String text = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            if (response.getCode() != 200) {
                throw new IOException(path + ": HTTP " + response.getCode());
            }
            return text;
        });
    }

    private static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? m.group(1).trim() : null;
    }
}
