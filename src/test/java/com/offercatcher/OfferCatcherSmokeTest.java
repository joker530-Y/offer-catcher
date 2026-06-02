package com.offercatcher;

import com.offercatcher.matching.MatchEngine;
import com.offercatcher.model.Job;
import com.offercatcher.model.MatchReport;
import com.offercatcher.model.SourceStatus;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.resume.MultipartFile;
import com.offercatcher.resume.ResumeParseResult;
import com.offercatcher.resume.ResumeParser;
import com.offercatcher.util.Json;
import com.offercatcher.util.TextUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OfferCatcherSmokeTest {
    public static void main(String[] args) {
        testTextSplit();
        testJsonRoundTrip();
        testMatchEngine();
        testStudentResumePdfIfPresent();
        System.out.println("OfferCatcherSmokeTest passed");
    }

    private static void testTextSplit() {
        List<String> values = TextUtils.split("Java, Spring；Redis、MySQL\nDocker");
        require(values.size() == 5, "TextUtils.split should handle Chinese and English separators");
        require(values.contains("Redis"), "TextUtils.split should keep Redis");
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
