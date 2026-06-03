package com.offercatcher.llm;

import com.offercatcher.model.MatchReport;

import java.util.Optional;

public record LlmDiagnosticResult(
        Optional<MatchReport> report,
        boolean attempted,
        boolean enhanced,
        String provider,
        String model,
        String status,
        String message
) {
    public static LlmDiagnosticResult skipped(LlmConfig config, String status, String message) {
        return new LlmDiagnosticResult(
                Optional.empty(),
                false,
                false,
                config.provider(),
                config.model(),
                status,
                message
        );
    }

    public static LlmDiagnosticResult enhanced(LlmConfig config, MatchReport report) {
        return new LlmDiagnosticResult(
                Optional.of(report),
                true,
                true,
                config.provider(),
                config.model(),
                "enhanced",
                "大模型增强诊断已生成。"
        );
    }

    public static LlmDiagnosticResult fallback(LlmConfig config, String status, String message) {
        return new LlmDiagnosticResult(
                Optional.empty(),
                true,
                false,
                config.provider(),
                config.model(),
                status,
                message
        );
    }
}
