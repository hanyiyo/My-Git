package cn.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;

class HttpFixture {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String PASSWORD = "fictional-patient-password";
    record Reply(int status, JsonNode body, HttpHeaders headers) {}
    static String identifier() { return "demo-" + UUID.randomUUID(); }

    static class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String base;
        String csrf;
        String access;
        Browser(int port) { base = "http://127.0.0.1:" + port; }
        Reply call(String method, String path, Object data) throws Exception {
            if (!method.equals("GET")) {
                var csrfResponse = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
                csrf = JSON.readTree(csrfResponse.body()).path("token").asText();
            }
            var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json");
            if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
            if (access != null) builder.header("Authorization", "Bearer " + access);
            builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(data)));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body()), response.headers());
        }
        Reply register(String name, String role) throws Exception { return call("POST", "/api/v1/auth/register", Map.of("identifier", name, "password", PASSWORD, "role", role)); }
        JsonNode challenge() throws Exception { return call("GET", "/api/v1/auth/captcha", null).body(); }
        Reply login(String name, String password, String challenge, String answer) throws Exception {
            return call("POST", "/api/v1/auth/login", Map.of("identifier", name, "password", password, "challengeId", challenge, "captcha", answer));
        }
        Reply login(String name, String password) throws Exception {
            var reply = login(name, password, challenge().path("challengeId").asText(), "AB7K");
            if (reply.status() == 200) access = reply.body().path("accessToken").asText();
            return reply;
        }
        Browser copyCookies(int port) {
            var copy = new Browser(port);
            for (var cookie : cookies.getCookieStore().getCookies()) copy.cookies.getCookieStore().add(URI.create(base), (HttpCookie) cookie.clone());
            copy.csrf = csrf;
            return copy;
        }
    }
}
