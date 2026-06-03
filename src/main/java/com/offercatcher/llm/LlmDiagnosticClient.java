package com.offercatcher.llm;

import com.offercatcher.model.Job;
import com.offercatcher.model.MatchReport;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LlmDiagnosticClient {
    private static final int TIMEOUT_SECONDS = 12;
    private final LlmConfig config;
    private final HttpClient client;

    public LlmDiagnosticClient(LlmConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean available() {
        return config.available();
    }

    public String provider() {
        return config.provider();
    }

    public Optional<MatchReport> enhance(StudentProfile profile, Job job, MatchReport ruleReport) {
        return diagnose(profile, job, ruleReport).report();
    }

    public LlmDiagnosticResult diagnose(StudentProfile profile, Job job, MatchReport ruleReport) {
        if (!config.enabled()) {
            return LlmDiagnosticResult.skipped(config, "disabled", "大模型未启用，当前使用规则诊断。");
        }
        if (config.apiKey().isBlank()) {
            return LlmDiagnosticResult.skipped(config, "missing_key", "大模型已选择但没有 API Key，当前使用规则诊断。");
        }
        try {
            String content = callModel(profile, job, ruleReport);
            Map<String, Object> parsed = parseModelJson(content);
            MatchReport enhanced = toReport(job, ruleReport, parsed);
            return LlmDiagnosticResult.enhanced(config, enhanced);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return LlmDiagnosticResult.fallback(config, "interrupted", "大模型调用被中断，已回退规则诊断。");
        } catch (IOException ex) {
            return LlmDiagnosticResult.fallback(config, "request_failed", userMessage(ex));
        } catch (RuntimeException ex) {
            return LlmDiagnosticResult.fallback(config, "invalid_response", "大模型已调用，但返回内容不是预期 JSON，已回退规则诊断。");
        }
    }

    private static String userMessage(IOException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("401")) return "大模型认证失败，请检查 API Key。";
        if (message.contains("403")) return "大模型接口无权限，请检查账号额度、模型权限或 API Key。";
        if (message.contains("404")) return "大模型接口或模型名不可用，请检查模型选择。";
        if (message.contains("429")) return "大模型限流或额度不足，已回退规则诊断。";
        if (message.contains("timeout") || message.contains("timed out")) return "大模型请求超时，已回退规则诊断。";
        return "大模型调用失败，已回退规则诊断。";
    }

    private String callModel(StudentProfile profile, Job job, MatchReport ruleReport) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.model());
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", 900);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(profile, job, ruleReport))
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.chatCompletionsUrl()))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(requestBody), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LLM request failed: " + response.statusCode());
        }
        Map<String, Object> body = asMap(Json.parse(response.body()));
        List<?> choices = asList(body.get("choices"));
        if (choices.isEmpty()) throw new IllegalArgumentException("LLM response has no choices");
        Map<String, Object> choice = asMap(choices.get(0));
        Map<String, Object> message = asMap(choice.get("message"));
        String content = String.valueOf(message.getOrDefault("content", "")).trim();
        if (content.isBlank()) throw new IllegalArgumentException("LLM response content is blank");
        return content;
    }

    private static String systemPrompt() {
        return """
                你是学生求职诊断助手。请只基于用户提供的真实简历画像、岗位信息和规则诊断提出建议。
                禁止编造不存在的项目、奖项、证书、技能、实习或工作经历。
                输出必须是一个 JSON 对象，不要使用 Markdown，不要添加 JSON 之外的文字。
                JSON 字段必须包含 explanation、strengths、gaps、risks、resumeAdvice。
                strengths、gaps、risks、resumeAdvice 必须是中文字符串数组，每项短句、可执行。
                """;
    }

    private static String userPrompt(StudentProfile profile, Job job, MatchReport report) {
        return """
                请为以下学生和岗位生成岗位诊断 JSON。

                学生画像：
                姓名：%s
                学校：%s
                专业：%s
                届别：%s
                目标岗位：%s
                城市偏好：%s
                技能：%s
                实习经历：%s
                项目经历：%s
                职业兴趣：%s
                简历正文摘要：%s

                岗位信息：
                公司：%s
                岗位：%s
                城市：%s
                技能要求：%s
                职责/领域：%s
                来源链接：%s

                规则诊断：
                匹配分：%d
                优先级：%s
                命中点：%s
                缺口：%s
                风险：%s
                原建议：%s
                """.formatted(
                profile.name(),
                profile.school(),
                profile.major(),
                profile.grade(),
                String.join("、", profile.targetRoles()),
                String.join("、", profile.cities()),
                String.join("、", profile.skills()),
                clip(String.join("；", profile.internships()), 700),
                clip(String.join("；", profile.projects()), 900),
                String.join("、", profile.interests()),
                clip(profile.resumeText(), 1000),
                job.company(),
                job.title(),
                job.city(),
                String.join("、", job.hardSkills()),
                clip(String.join("；", merge(job.domains(), job.responsibilities())), 900),
                job.sourceUrl(),
                report.totalScore(),
                report.priority(),
                String.join("；", report.strengths()),
                String.join("；", report.gaps()),
                String.join("；", report.risks()),
                String.join("；", report.resumeAdvice())
        );
    }

    private static Map<String, Object> parseModelJson(String content) {
        String json = extractJson(content);
        return asMap(Json.parse(json));
    }

    private static String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("LLM response is not JSON");
        return trimmed.substring(start, end + 1);
    }

    private static MatchReport toReport(Job job, MatchReport ruleReport, Map<String, Object> parsed) {
        String explanation = string(parsed, "explanation");
        List<String> strengths = stringList(parsed, "strengths");
        List<String> gaps = stringList(parsed, "gaps");
        List<String> risks = stringList(parsed, "risks");
        List<String> advice = stringList(parsed, "resumeAdvice");
        if (explanation.isBlank() || strengths.isEmpty() || gaps.isEmpty() || advice.isEmpty()) {
            throw new IllegalArgumentException("LLM diagnostic JSON misses required content");
        }
        return new MatchReport(
                job,
                ruleReport.totalScore(),
                ruleReport.dimensions(),
                strengths,
                gaps,
                risks.isEmpty() ? ruleReport.risks() : risks,
                advice,
                ruleReport.priority(),
                explanation
        );
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static List<String> stringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = String.valueOf(item).trim();
                if (!text.isBlank()) result.add(clip(text, 180));
            }
        } else if (value instanceof String text) {
            for (String item : text.split("[\\n；;]+")) {
                String trimmed = item.trim();
                if (!trimmed.isBlank()) result.add(clip(trimmed, 180));
            }
        } else if (value != null) {
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) result.add(clip(text, 180));
        }
        return result.stream().limit(6).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("Expected JSON object");
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) return list;
        return List.of();
    }

    private static List<String> merge(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>(left);
        merged.addAll(right);
        return merged;
    }

    private static String clip(String value, int maxLength) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "…";
    }
}
