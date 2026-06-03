package com.offercatcher.llm;

import java.util.Map;
import java.util.Optional;

public record LlmConfig(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        String apiKey
) {
    public static final String DEFAULT_BASE_URL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
    public static final String DEFAULT_MODEL = "qwen-plus";

    public static LlmConfig fromEnv() {
        String provider = normalizeProvider(env("LLM_PROVIDER"));
        String apiKey = apiKeyFromEnv(provider);
        return new LlmConfig(
                Boolean.parseBoolean(env("LLM_ENABLED")),
                provider,
                normalizeBaseUrl(provider, env("LLM_BASE_URL")),
                valueOrDefault(env("LLM_MODEL"), defaultModel(provider)),
                apiKey
        );
    }

    @SuppressWarnings("unchecked")
    public static Optional<LlmConfig> fromRequest(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Optional.empty();
        Map<String, Object> map = (Map<String, Object>) raw;
        boolean enabled = asBoolean(map.get("enabled"));
        String apiKey = asString(map.get("apiKey"));
        String provider = normalizeProvider(asString(map.get("provider")));
        String model = valueOrDefault(asString(map.get("model")), defaultModel(provider));
        String baseUrl = normalizeBaseUrl(provider, asString(map.get("baseUrl")));
        return Optional.of(new LlmConfig(enabled, provider, baseUrl, model, apiKey));
    }

    public boolean available() {
        return enabled && !apiKey.isBlank();
    }

    public String chatCompletionsUrl() {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/chat/completions") ? trimmed : trimmed + "/chat/completions";
    }

    private static String env(String name) {
        return System.getenv().getOrDefault(name, "").trim();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static String defaultBaseUrl(String provider) {
        return switch (normalizeProvider(provider)) {
            case "deepseek" -> "https://api.deepseek.com";
            case "zhipu" -> "https://open.bigmodel.cn/api/paas/v4";
            case "siliconflow" -> "https://api.siliconflow.cn/v1";
            default -> DEFAULT_BASE_URL;
        };
    }

    public static String defaultModel(String provider) {
        return switch (normalizeProvider(provider)) {
            case "deepseek" -> "deepseek-v4-flash";
            case "zhipu" -> "glm-4-flash";
            case "siliconflow" -> "Qwen/Qwen2.5-7B-Instruct";
            default -> DEFAULT_MODEL;
        };
    }

    private static String apiKeyFromEnv(String provider) {
        String key = switch (normalizeProvider(provider)) {
            case "deepseek" -> firstEnv("DEEPSEEK_API_KEY");
            case "zhipu" -> firstEnv("ZHIPU_API_KEY", "ZHIPUAI_API_KEY", "BIGMODEL_API_KEY");
            case "siliconflow" -> firstEnv("SILICONFLOW_API_KEY");
            default -> firstEnv("DASHSCOPE_API_KEY", "QWEN_API_KEY");
        };
        return key.isBlank() ? firstEnv("LLM_API_KEY") : key;
    }

    private static String normalizeProvider(String value) {
        String provider = valueOrDefault(value, "qwen").toLowerCase();
        return switch (provider) {
            case "deepseek", "zhipu", "siliconflow", "custom" -> provider;
            case "glm", "bigmodel", "zhipuai" -> "zhipu";
            case "qwen", "dashscope", "aliyun" -> "qwen";
            default -> "qwen";
        };
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(asString(value));
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String normalizeBaseUrl(String provider, String value) {
        String baseUrl = valueOrDefault(value, defaultBaseUrl(provider));
        if (!baseUrl.startsWith("https://")) return defaultBaseUrl(provider);
        return baseUrl;
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = env(name);
            if (!value.isBlank()) return value;
        }
        return "";
    }
}
