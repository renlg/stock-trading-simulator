package com.stocktrade.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NewsFeedClient {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedClient.class);
    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient http;
    private final CookieManager cookieManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile boolean loggedIn = false;

    public NewsFeedClient(@Value("${newsfeed.base-url}") String baseUrl,
                          @Value("${newsfeed.username}") String username,
                          @Value("${newsfeed.password}") String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<Map<String, Object>> financial(String code) {
        return fetchList("/astocks/" + code + "/api/financial?page=0&size=50");
    }

    public List<Map<String, Object>> events(String code) {
        return fetchList("/astocks/" + code + "/api/events?page=0&size=50");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchList(String path) {
        try {
            ensureLoggedIn();
            String body = doGet(path);
            if (body == null) {
                loggedIn = false;
                ensureLoggedIn();
                body = doGet(path);
                if (body == null) return Collections.emptyList();
            }
            Map<String, Object> page = mapper.readValue(body, Map.class);
            Object content = page.get("content");
            if (content instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("news-feed调用失败 path={}: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String doGet(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) return resp.body();
            log.warn("news-feed GET {} 返回 {}", path, resp.statusCode());
            return null;
        } catch (Exception e) {
            log.warn("news-feed GET {} 异常: {}", path, e.getMessage());
            return null;
        }
    }

    private synchronized void ensureLoggedIn() {
        if (loggedIn) return;
        try {
            String loginPage = fetchLoginPage();
            String csrf = extractCsrf(loginPage);
            if (csrf == null) {
                log.warn("news-feed登录页无法提取CSRF token");
                return;
            }
            String formBody = "username=" + username + "&password=" + password + "&_csrf=" + csrf;
            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/login"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> resp = http.send(loginReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 302 || resp.statusCode() == 200) {
                loggedIn = true;
                log.info("news-feed登录成功");
            } else {
                log.warn("news-feed登录失败 status={}", resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("news-feed登录异常: {}", e.getMessage());
        }
    }

    private String fetchLoginPage() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/login"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.body();
        } catch (Exception e) {
            log.warn("news-feed获取登录页失败: {}", e.getMessage());
            return "";
        }
    }

    private String extractCsrf(String html) {
        if (html == null) return null;
        Matcher m = CSRF_PATTERN.matcher(html);
        return m.find() ? m.group(1) : null;
    }
}
