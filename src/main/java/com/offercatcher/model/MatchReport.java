package com.offercatcher.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MatchReport(
        Job job,
        int totalScore,
        Map<String, Integer> dimensions,
        List<String> strengths,
        List<String> gaps,
        List<String> risks,
        List<String> resumeAdvice,
        String priority,
        String explanation
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("job", job.toMap());
        map.put("totalScore", totalScore);
        map.put("dimensions", dimensions);
        map.put("strengths", strengths);
        map.put("gaps", gaps);
        map.put("risks", risks);
        map.put("resumeAdvice", resumeAdvice);
        map.put("priority", priority);
        map.put("explanation", explanation);
        return map;
    }
}
