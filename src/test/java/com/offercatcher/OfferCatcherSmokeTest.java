package com.offercatcher;

import com.offercatcher.matching.MatchEngine;
import com.offercatcher.llm.LlmConfig;
import com.offercatcher.llm.LlmDiagnosticClient;
import com.offercatcher.model.Job;
import com.offercatcher.model.MatchReport;
import com.offercatcher.model.SourceStatus;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.resume.MultipartFile;
import com.offercatcher.resume.ResumeParseResult;
import com.offercatcher.resume.ResumeParser;
import com.offercatcher.util.Json;
import com.offercatcher.util.TextUtils;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class OfferCatcherSmokeTest {
    public static void main(String[] args) {
        testTextSplit();
        testJsonRoundTrip();
        testJsonUnicodeEscapes();
        testMatchEngine();
        testLlmDisabledFallback();
        testLlmRequestConfig();
        testLlmEnhancementWithFakeServer();
        testStudentResumePdfIfPresent();
        System.out.println("OfferCatcherSmokeTest passed");
    }

    @SuppressWarnings("unchecked")
    private static void testJsonUnicodeEscapes() {
        Map<String, Object> parsed = (Map<String, Object>) Json.parse("{\"text\":\"\\u4f60\\u597d\",\"items\":[\"\\u7b80\\u5386\"]}");
        require("你好".equals(parsed.get("text")), "Json should parse unicode escapes");
        require(((List<?>) parsed.get("items")).contains("简历"), "Json should parse unicode escapes in arrays");
    }

    private static void testTextSplit() {
        List<String> values = TextUtils.split("Java, Spring；Redis、MySQL\nDocker");
        require(values.size() == 5, "TextUtils.split should handle Chinese and English separators");
        require(values.contains("Redis"), "TextUtils.split should keep Redis");
    }

    private static void testLlmDisabledFallback() {
        LlmDiagnosticClient client = new LlmDiagnosticClient(new LlmConfig(false, "qwen", "https://example.com/v1", "qwen-plus", ""));
        StudentProfile profile = new StudentProfile(
                "林同学",
                "某高校",
                "软件工程",
                "2026届",
                List.of("后端开发"),
                List.of("深圳"),
                List.of("Java", "Spring", "MySQL"),
                List.of("某公司 | 后端开发实习 | 2025.01 - 2025.03"),
                List.of("校园交易平台：负责 Java 接口和 MySQL 表设计。"),
                List.of("后端工程"),
                List.of(),
                "熟悉 Java Spring MySQL。"
        );
        Job job = Job.fromCustomJd("后端开发实习生，要求 Java Spring MySQL，有项目经验。");
        MatchReport report = new MatchEngine().analyze(profile, job);
        require(client.enhance(profile, job, report).isEmpty(), "Disabled LLM client should return empty enhancement");
    }

    private static void testLlmRequestConfig() {
        LlmConfig config = LlmConfig.fromRequest(Map.of(
                "enabled", true,
                "provider", "qwen",
                "baseUrl", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                "model", "qwen-turbo",
                "apiKey", "fake-key"
        )).orElseThrow();
        require(config.available(), "Request LLM config should be available when enabled with key");
        require("qwen-turbo".equals(config.model()), "Request LLM config should keep selected model");
        require(config.chatCompletionsUrl().endsWith("/chat/completions"), "Request LLM config should build chat completions URL");
        LlmConfig deepseek = LlmConfig.fromRequest(Map.of(
                "enabled", true,
                "provider", "deepseek",
                "apiKey", "fake-key"
        )).orElseThrow();
        require("https://api.deepseek.com/chat/completions".equals(deepseek.chatCompletionsUrl()), "DeepSeek should use default OpenAI-compatible endpoint");
        require("deepseek-v4-flash".equals(deepseek.model()), "DeepSeek request config should use default model");
        LlmConfig zhipu = LlmConfig.fromRequest(Map.of("enabled", true, "provider", "zhipu", "apiKey", "fake-key")).orElseThrow();
        require(zhipu.chatCompletionsUrl().contains("open.bigmodel.cn"), "Zhipu should use BigModel default endpoint");
    }

    private static void testLlmEnhancementWithFakeServer() {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String content = Json.stringify(Map.of(
                        "explanation", "大模型诊断：建议优先突出 Java 接口和 MySQL 项目证据。",
                        "strengths", List.of("Java 与岗位技能要求一致", "项目经历包含接口开发"),
                        "gaps", List.of("需要补充更明确的量化结果"),
                        "risks", List.of("投递前仍需以官网 JD 为准"),
                        "resumeAdvice", List.of("在项目经历中写清 Spring 接口、数据库设计和优化结果")
                ));
                String response = Json.stringify(Map.of(
                        "choices", List.of(Map.of("message", Map.of("content", content)))
                ));
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            LlmDiagnosticClient client = new LlmDiagnosticClient(new LlmConfig(true, "qwen", baseUrl, "qwen-plus", "fake-key"));
            StudentProfile profile = new StudentProfile(
                    "林同学",
                    "某高校",
                    "软件工程",
                    "2026届",
                    List.of("后端开发"),
                    List.of("深圳"),
                    List.of("Java", "Spring", "MySQL"),
                    List.of("某公司 | 后端开发实习 | 2025.01 - 2025.03"),
                    List.of("校园交易平台：负责 Java 接口和 MySQL 表设计。"),
                    List.of("后端工程"),
                    List.of(),
                    "熟悉 Java Spring MySQL。"
            );
            Job job = Job.fromCustomJd("后端开发实习生，要求 Java Spring MySQL，有项目经验。");
            MatchReport report = new MatchEngine().analyze(profile, job);
            MatchReport enhanced = client.enhance(profile, job, report).orElseThrow();
            require(enhanced.explanation().contains("大模型诊断"), "Enabled LLM client should enhance explanation");
            require(enhanced.totalScore() == report.totalScore(), "LLM enhancement should keep rule score");
            require(!enhanced.resumeAdvice().isEmpty(), "LLM enhancement should keep advice list");
        } catch (Exception ex) {
            throw new AssertionError("Fake LLM enhancement failed", ex);
        } finally {
            if (server != null) server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private static void testJsonRoundTrip() {
        String raw = Json.stringify(Map.of("name", "林同学", "skills", List.of("Java", "Redis")));
        Map<String, Object> parsed = (Map<String, Object>) Json.parse(raw);
        require("林同学".equals(parsed.get("name")), "Json should parse Chinese string");
        require(((List<?>) parsed.get("skills")).size() == 2, "Json should parse arrays");
    }

    private static void testMatchEngine() {
        StudentProfile profile = new StudentProfile(
                "林同学",
                "某高校",
                "软件工程",
                "2026届",
                List.of("后端开发"),
                List.of("深圳"),
                List.of("Java", "Spring", "MySQL", "Redis"),
                List.of("某公司 | 后端开发实习 | 2025.01 - 2025.03"),
                List.of("负责后端接口、MySQL 表设计和 Redis 缓存，将响应耗时从 800ms 优化到 220ms。"),
                List.of("高并发"),
                List.of(),
                "熟悉 Java Spring MySQL Redis，有后端项目和性能优化经验。"
        );
        Job job = new Job(
                "test-job",
                "测试公司",
                "后端开发实习生",
                "深圳",
                "测试来源",
                "https://example.com",
                List.of("2026届"),
                List.of("Java", "Spring", "MySQL", "Redis"),
                List.of("沟通", "协作"),
                List.of("高并发", "后端工程"),
                List.of("负责服务端接口开发和性能优化"),
                "以官网为准",
                false,
                SourceStatus.FALLBACK
        );
        MatchReport report = new MatchEngine().analyze(profile, job);
        require(report.totalScore() >= 70, "MatchEngine should score a strong backend profile highly");
        require(report.dimensions().size() == 6, "MatchEngine should return six dimensions");
    }

    @SuppressWarnings("unchecked")
    private static void testStudentResumePdfIfPresent() {
        Path pdf = Path.of("D:/cursor workspace/output/pdf/student_resume.pdf");
        if (!Files.exists(pdf)) return;
        try {
            ResumeParseResult result = ResumeParser.parse(new MultipartFile("student_resume.pdf", Files.readAllBytes(pdf)));
            Map<String, Object> profile = result.profile();
            require(!result.extractedText().contains("%PDF"), "PDFBox text should not include PDF header");
            require(!result.extractedText().contains("FlateDecode"), "PDFBox text should not include PDF streams");
            require("金雪".equals(profile.get("name")), "Resume parser should extract name");
            require("华南理工大学".equals(profile.get("school")), "Resume parser should extract school");
            require("人工智能".equals(profile.get("major")), "Resume parser should extract major");
            require("2027届".equals(profile.get("grade")), "Resume parser should infer grade");
            require(((List<String>) profile.get("targetRoles")).stream().anyMatch(item -> item.contains("后端开发")), "Resume parser should extract target role");
            require(((List<String>) profile.get("internships")).stream().anyMatch(item -> item.contains("小米") && item.contains("数据分析实习")), "Resume parser should extract internship");
            require(((List<String>) profile.get("projects")).stream().anyMatch(item -> item.contains("教务选课助手")), "Resume parser should extract project");
        } catch (Exception ex) {
            throw new AssertionError("student_resume.pdf parsing failed", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
