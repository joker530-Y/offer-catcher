package com.offercatcher.resume;

import java.util.List;
import java.util.Map;

public record ResumeParseResult(String fileName, String extractedText, Map<String, Object> profile, List<String> warnings, int confidence) {
    public Map<String, Object> toMap() {
        return Map.of(
                "fileName", fileName,
                "extractedText", extractedText,
                "profile", profile,
                "warnings", warnings,
                "confidence", confidence
        );
    }
}
