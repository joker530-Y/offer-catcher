package com.offercatcher.adapters;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HttpUtils {
    private HttpUtils() {
    }

    static String get(HttpClient httpClient, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(7))
                    .header("User-Agent", "OfferCatcher/0.1 local student job matcher")
                    .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.7")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return response.body();
            }
            return "";
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return "";
        }
    }

    static String appendQuery(String url, String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return url + (url.contains("?") ? "&" : "?") + "q=" + encoded;
    }

    static List<String> inferJobSkills(String title, List<String> sourceHints) {
        String normalized = title.toLowerCase();
        if (normalized.contains("算法") || normalized.contains("ai") || normalized.contains("大模型")) {
            return List.of("Python", "机器学习", "深度学习", "PyTorch", "数据分析");
        }
        if (normalized.contains("前端")) {
            return List.of("JavaScript", "TypeScript", "React", "Vue", "CSS", "工程化");
        }
        if (normalized.contains("测试")) {
            return List.of("自动化测试", "接口测试", "Python", "Linux", "CI/CD");
        }
        if (normalized.contains("数据")) {
            return List.of("SQL", "Python", "数据分析", "统计学", "数据可视化");
        }
        if (normalized.contains("产品")) {
            return List.of("需求分析", "原型设计", "用户研究", "数据分析");
        }
        if (normalized.contains("运营")) {
            return List.of("用户运营", "活动策划", "数据分析", "Excel");
        }
        return sourceHints.isEmpty() ? List.of("Java", "Spring", "MySQL", "Redis", "Linux") : sourceHints.stream().limit(8).toList();
    }

    static String extractCities(String segment, List<String> sourceCities) {
        for (String city : sourceCities) {
            if (segment.contains(city)) return city;
        }
        return sourceCities.isEmpty() ? "未指定" : sourceCities.get(0);
    }

    static String firstRegex(String text, String regex, String fallback) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
