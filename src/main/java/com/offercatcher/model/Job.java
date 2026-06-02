package com.offercatcher.model;

import com.offercatcher.util.Keyword;
import com.offercatcher.util.TextUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Job(
        String id,
        String company,
        String title,
        String city,
        String sourceType,
        String sourceUrl,
        List<String> grades,
        List<String> hardSkills,
        List<String> softSkills,
        List<String> domains,
        List<String> responsibilities,
        String deadline,
        boolean demo,
        SourceStatus sourceStatus
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("company", company);
        map.put("title", title);
        map.put("city", city);
        map.put("sourceType", sourceType);
        map.put("sourceUrl", sourceUrl);
        map.put("grades", grades);
        map.put("hardSkills", hardSkills);
        map.put("softSkills", softSkills);
        map.put("domains", domains);
        map.put("responsibilities", responsibilities);
        map.put("deadline", deadline);
        map.put("demo", demo);
        map.put("sourceStatus", sourceStatus.name());
        return map;
    }

    public static Job fromCustomJd(String jd) {
        List<String> terms = Keyword.extract(jd);
        List<String> hard = TextUtils.distinctNormalized(terms.stream().filter(Keyword::looksLikeSkill).toList()).stream().limit(10).toList();
        List<String> domains = TextUtils.distinctNormalized(terms.stream()
                .filter(term -> !TextUtils.containsNormalized(hard, term))
                .filter(term -> !Keyword.isStopWord(term))
                .toList()).stream().limit(6).toList();
        return new Job("custom-jd", "自定义岗位", "用户粘贴 JD", "未指定", "用户输入", "",
                List.of("不限"), hard.isEmpty() ? terms.stream().limit(8).toList() : hard,
                List.of("沟通", "协作", "学习能力"), domains,
                TextUtils.splitSentences(jd).stream().limit(5).toList(), "以原 JD 为准", false, SourceStatus.FALLBACK);
    }
}
