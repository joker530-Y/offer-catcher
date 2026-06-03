package com.offercatcher;

import com.offercatcher.adapters.LiveJobFinder;
import com.offercatcher.adapters.OfficialSources;
import com.offercatcher.llm.LlmConfig;
import com.offercatcher.llm.LlmDiagnosticResult;
import com.offercatcher.llm.LlmDiagnosticClient;
import com.offercatcher.matching.CompliancePolicy;
import com.offercatcher.matching.MatchEngine;
import com.offercatcher.model.Job;
import com.offercatcher.model.MatchReport;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.resume.MultipartFile;
import com.offercatcher.resume.ResumeParseResult;
import com.offercatcher.resume.ResumeParser;
import com.offercatcher.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfferCatcherApplication {
    private static final String STATIC_DIR = System.getenv().getOrDefault("STATIC_DIR", "src/main/resources/static");
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private static final long MATCH_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final LiveJobFinder FINDER = new LiveJobFinder(OfficialSources.load());
    private static final MatchEngine ENGINE = new MatchEngine();
    private static final LlmDiagnosticClient LLM = new LlmDiagnosticClient(LlmConfig.fromEnv());
    private static final Map<String, CachedMatch> MATCH_CACHE = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/healthz", OfferCatcherApplication::handleHealth);
        server.createContext("/api/jobs", OfferCatcherApplication::handleJobs);
        server.createContext("/api/resume/upload", OfferCatcherApplication::handleResumeUpload);
        server.createContext("/api/match", OfferCatcherApplication::handleMatch);
        server.createContext("/api/analyze", OfferCatcherApplication::handleAnalyze);
        server.createContext("/api/feedback", OfferCatcherApplication::handleFeedback);
        server.createContext("/", OfferCatcherApplication::handleStatic);
        ExecutorService httpExecutor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        server.setExecutor(httpExecutor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(3);
            FINDER.shutdown();
            httpExecutor.shutdown();
        }, "offer-catcher-shutdown"));
        server.start();
        System.out.println("Offer Catcher started on http://0.0.0.0:" + port);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        sendJson(exchange, 200, Map.of(
                "status", "ok",
                "service", "offer-catcher",
                "time", Instant.now().toString()
        ));
    }

    private static void handleJobs(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        sendJson(exchange, 200, Map.of(
                "sources", FINDER.sources().stream().map(OfficialSource::toMap).toList(),
                "compliance", CompliancePolicy.toMap()
        ));
    }

    private static void handleResumeUpload(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("multipart/form-data")) {
            sendJson(exchange, 400, Map.of("error", "bad_request", "message", "请使用 multipart/form-data 上传 PDF 简历。"));
            return;
        }
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readAllBytes();
        }
        if (body.length > 8 * 1024 * 1024) {
            sendJson(exchange, 413, Map.of("error", "payload_too_large", "message", "PDF 文件不能超过 8MB。"));
            return;
        }
        try {
            MultipartFile file = MultipartFile.from(contentType, body);
            if (!file.fileName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                sendJson(exchange, 400, Map.of("error", "bad_request", "message", "当前只支持 PDF 简历上传。"));
                return;
            }
            ResumeParseResult result = ResumeParser.parse(file);
            sendJson(exchange, 200, result.toMap());
        } catch (RuntimeException ex) {
            sendJson(exchange, 400, Map.of("error", "bad_request", "message", ex.getMessage()));
        }
    }

    private static void handleMatch(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            StudentProfile profile = StudentProfile.fromMap(readJsonObject(exchange));
            var matched = FINDER.search(profile);
            cacheMatch(profile, matched);
            sendJson(exchange, 200, Map.of(
                    "profile", profile.toPublicMap(),
                    "reports", matched.stream().map(MatchReport::toMap).toList(),
                    "sourceCount", FINDER.sources().size(),
                    "searchedSources", FINDER.sources().stream().map(OfficialSource::toMap).toList(),
                    "searchQueries", FINDER.searchQueries(profile),
                    "generatedAt", Instant.now().toString()
            ));
        } catch (RuntimeException ex) {
            sendJson(exchange, 400, Map.of("error", "bad_request", "message", ex.getMessage()));
        }
    }

    private static void handleAnalyze(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            Map<String, Object> body = readJsonObject(exchange);
            StudentProfile profile = StudentProfile.fromMap(asMap(body.get("profile")));
            String jobId = asString(body.get("jobId"));
            String customJd = asString(body.get("customJd"));
            Job job = customJd.isBlank() ? findCachedJob(profile, jobId).orElseGet(() -> FINDER.findGeneratedJob(profile, jobId)) : Job.fromCustomJd(customJd);
            MatchReport ruleReport = ENGINE.analyze(profile, job);
            LlmDiagnosticClient llm = LlmConfig.fromRequest(body.get("llmConfig"))
                    .map(LlmDiagnosticClient::new)
                    .orElse(LLM);
            LlmDiagnosticResult llmResult = llm.diagnose(profile, job, ruleReport);
            MatchReport report = llmResult.report().orElse(ruleReport);
            sendJson(exchange, 200, Map.of(
                    "job", job.toMap(),
                    "report", report.toMap(),
                    "llmEnhanced", llmResult.enhanced(),
                    "llmAttempted", llmResult.attempted(),
                    "llmProvider", llmResult.provider(),
                    "llmModel", llmResult.model(),
                    "llmStatus", llmResult.status(),
                    "llmMessage", llmResult.message(),
                    "generatedAt", Instant.now().toString()
            ));
        } catch (RuntimeException ex) {
            sendJson(exchange, 400, Map.of("error", "bad_request", "message", ex.getMessage()));
        }
    }

    private static void handleFeedback(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            Map<String, Object> body = readJsonObject(exchange);
            String action = asString(body.get("action"));
            String jobId = asString(body.get("jobId"));
            String adjustment = switch (action) {
                case "save" -> "已记录收藏倾向。";
                case "unsave" -> "已取消收藏。";
                case "ignore" -> "已记录忽略倾向：后续可降低相似岗位或不匹配城市的推荐权重。";
                case "applied" -> "已记录投递结果：后续可结合面试反馈校准简历优化建议。";
                default -> "已接收反馈。";
            };
            sendJson(exchange, 200, Map.of("jobId", jobId, "action", action, "message", adjustment));
        } catch (RuntimeException ex) {
            sendJson(exchange, 400, Map.of("error", "bad_request", "message", ex.getMessage()));
        }
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        URI uri = exchange.getRequestURI();
        String rawPath = uri.getPath().equals("/") ? "/index.html" : uri.getPath();
        Path base = Path.of(STATIC_DIR).toAbsolutePath().normalize();
        Path target = base.resolve(rawPath.substring(1)).normalize();
        if (!target.startsWith(base) || !Files.exists(target) || Files.isDirectory(target)) {
            sendText(exchange, 404, "Not found", "text/plain; charset=utf-8");
            return;
        }
        String type = contentType(target);
        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", cacheControl(target));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String raw;
        try (InputStream in = exchange.getRequestBody()) {
            raw = new String(readLimited(in, MAX_JSON_BYTES), StandardCharsets.UTF_8);
        }
        Object value = Json.parse(raw.isBlank() ? "{}" : raw);
        return asMap(value);
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("JSON 请求体不能超过 256KB。");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object");
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(Path target) {
        String name = target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static String cacheControl(Path target) {
        String name = target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "no-cache";
        if (name.endsWith(".css") || name.endsWith(".js") || name.endsWith(".svg")) return "public, max-age=3600";
        return "public, max-age=300";
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) return false;
        setCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void cacheMatch(StudentProfile profile, List<MatchReport> reports) {
        cleanupMatchCache();
        MATCH_CACHE.put(profileKey(profile), new CachedMatch(System.currentTimeMillis() + MATCH_CACHE_TTL_MILLIS, List.copyOf(reports)));
    }

    private static Optional<Job> findCachedJob(StudentProfile profile, String jobId) {
        cleanupMatchCache();
        CachedMatch cached = MATCH_CACHE.get(profileKey(profile));
        if (cached == null || cached.expiresAtMillis() < System.currentTimeMillis()) return Optional.empty();
        if (jobId == null || jobId.isBlank()) {
            return cached.reports().stream().findFirst().map(MatchReport::job);
        }
        return cached.reports().stream()
                .map(MatchReport::job)
                .filter(job -> job.id().equals(jobId))
                .findFirst();
    }

    private static void cleanupMatchCache() {
        long now = System.currentTimeMillis();
        MATCH_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private static String profileKey(StudentProfile profile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(profile.allText().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) builder.append(String.format("%02x", value));
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(profile.allText().hashCode());
        }
    }

    private record CachedMatch(long expiresAtMillis, List<MatchReport> reports) {
    }
}
