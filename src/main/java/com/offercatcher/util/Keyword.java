package com.offercatcher.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class Keyword {
    private static final Pattern SPLIT = Pattern.compile("[,，;；、\\s/|]+");
    private static final Set<String> STOP_WORDS = Set.of("要求", "岗位", "职责", "参与", "负责", "熟悉", "了解", "优先", "不限");
    private static final Set<String> KNOWN_SKILLS = Set.of(
            "java", "spring", "mysql", "redis", "分布式", "微服务", "linux", "docker", "kubernetes", "云原生",
            "python", "机器学习", "深度学习", "推荐系统", "sql", "pytorch", "a/b实验", "数据可视化",
            "统计学", "excel", "需求分析", "原型设计", "数据分析", "用户研究", "javascript", "typescript",
            "react", "vue", "css", "工程化", "自动化测试", "接口测试", "ci/cd", "用户运营", "活动策划", "社群运营",
            "go", "c++", "c#", "android", "ios", "kotlin", "spark", "flink", "大模型", "llm", "agent", "rag"
    );

    private Keyword() {
    }

    public static List<String> extract(String text) {
        Set<String> terms = new LinkedHashSet<>();
        for (String part : SPLIT.split(text == null ? "" : text)) {
            String cleaned = part.trim();
            if (cleaned.length() >= 2 && cleaned.length() <= 32 && !isStopWord(cleaned)) terms.add(cleaned);
        }
        String normalized = TextUtils.normalize(text);
        for (String skill : KNOWN_SKILLS) {
            if (TextUtils.containsTerm(normalized, skill)) terms.add(canonicalSkill(skill));
        }
        return new ArrayList<>(terms);
    }

    public static boolean looksLikeSkill(String term) {
        return KNOWN_SKILLS.contains(TextUtils.normalize(term)) || Pattern.compile("[A-Za-z+#.]{2,}").matcher(term).find();
    }

    public static boolean isStopWord(String term) {
        return STOP_WORDS.contains(TextUtils.normalize(term));
    }

    public static List<String> skillVocabulary() {
        return KNOWN_SKILLS.stream().map(Keyword::canonicalSkill).distinct().toList();
    }

    private static String canonicalSkill(String term) {
        return switch (TextUtils.normalize(term)) {
            case "java" -> "Java";
            case "spring" -> "Spring";
            case "mysql" -> "MySQL";
            case "redis" -> "Redis";
            case "linux" -> "Linux";
            case "docker" -> "Docker";
            case "kubernetes" -> "Kubernetes";
            case "python" -> "Python";
            case "sql" -> "SQL";
            case "pytorch" -> "PyTorch";
            case "excel" -> "Excel";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "react" -> "React";
            case "vue" -> "Vue";
            case "css" -> "CSS";
            case "ci/cd" -> "CI/CD";
            case "go" -> "Go";
            case "c++" -> "C++";
            case "c#" -> "C#";
            case "android" -> "Android";
            case "ios" -> "iOS";
            case "kotlin" -> "Kotlin";
            case "spark" -> "Spark";
            case "flink" -> "Flink";
            case "llm" -> "LLM";
            case "agent" -> "Agent";
            case "rag" -> "RAG";
            default -> term;
        };
    }
}
