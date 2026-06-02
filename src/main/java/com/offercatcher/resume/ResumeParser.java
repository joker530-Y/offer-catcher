package com.offercatcher.resume;

import com.offercatcher.util.Keyword;
import com.offercatcher.util.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResumeParser {
    private static final List<String> CITIES = List.of("北京", "上海", "深圳", "广州", "杭州", "南京", "苏州", "成都", "武汉", "西安", "天津", "重庆", "厦门", "长沙", "合肥");
    private static final List<String> MAJORS = List.of("软件工程", "计算机科学与技术", "人工智能", "数据科学", "信息管理", "电子信息", "自动化", "通信工程", "金融学", "统计学", "市场营销");
    private static final List<String> SECTION_NAMES = List.of("求职意向", "教育背景", "自我评价", "专业技能", "技能", "实习经历", "项目经历", "校园经历", "竞赛与荣誉", "证书技能", "荣誉证书");
    private static final Pattern DATE_RANGE = Pattern.compile("\\d{4}\\.\\d{1,2}\\s*-\\s*\\d{4}\\.\\d{1,2}");

    private ResumeParser() {
    }

    public static ResumeParseResult parse(MultipartFile file) {
        String text = PdfTextExtractor.extract(file.content());
        List<String> warnings = new ArrayList<>();
        if (text.length() < 40 || PdfTextExtractor.isLikelyGarbled(text)) {
            warnings.add("未能从 PDF 中提取到足够可读文本。请换用可复制文本型 PDF，图片型或扫描件暂不支持 OCR。");
            text = "";
        }
        Map<String, Object> profile = inferProfile(text);
        int confidence = text.length() < 40 ? 25 : Math.min(95, 50 + Keyword.extract(text).size() * 3);
        return new ResumeParseResult(file.fileName(), text, profile, warnings, confidence);
    }

    private static Map<String, Object> inferProfile(String text) {
        Map<String, List<String>> sections = sections(text);
        Map<String, Object> profile = new LinkedHashMap<>();
        String educationText = sectionText(sections, "教育背景");
        String intentText = sectionText(sections, "求职意向");
        String skillsText = sectionText(sections, "专业技能") + "\n" + sectionText(sections, "技能");
        String internshipText = sectionText(sections, "实习经历");
        String projectText = sectionText(sections, "项目经历");

        profile.put("name", inferName(text));
        profile.put("school", inferSchool(text, educationText));
        profile.put("major", inferMajor(text, educationText));
        profile.put("grade", inferGrade(text));
        profile.put("targetRoles", inferRoles(text, intentText));
        profile.put("cities", CITIES.stream().filter(text::contains).limit(4).toList());
        profile.put("skills", inferSkills(text, skillsText + "\n" + projectText + "\n" + internshipText));
        profile.put("internships", extractExperience(internshipText));
        profile.put("projects", extractExperience(projectText));
        profile.put("interests", inferInterests(text));
        profile.put("constraints", List.of());
        profile.put("resumeText", text);
        return profile;
    }

    private static Map<String, List<String>> sections(String text) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = "基本信息";
        sections.put(current, new ArrayList<>());
        for (String raw : text.split("\\R+")) {
            String line = raw.trim();
            if (line.isBlank()) continue;
            String heading = SECTION_NAMES.stream()
                    .filter(name -> line.equals(name) || line.replace("：", "").equals(name))
                    .findFirst()
                    .orElse(null);
            if (heading != null) {
                current = heading;
                sections.putIfAbsent(current, new ArrayList<>());
            } else {
                sections.computeIfAbsent(current, key -> new ArrayList<>()).add(line);
            }
        }
        return sections;
    }

    private static String sectionText(Map<String, List<String>> sections, String name) {
        return String.join("\n", sections.getOrDefault(name, List.of()));
    }

    private static String inferName(String text) {
        String name = firstGroup(text, "姓名[:：\\s]*([\\u4e00-\\u9fa5A-Za-z]{2,20})");
        if (!name.isBlank()) return name;
        name = firstGroup(text, "Name[:：\\s]*([A-Za-z][A-Za-z\\s]{1,28})");
        if (!name.isBlank()) return name;
        for (String line : text.split("\\R+")) {
            String trimmed = line.trim();
            if (trimmed.matches("[\\u4e00-\\u9fa5]{2,4}") || trimmed.matches("[A-Za-z][A-Za-z\\s]{1,28}")) {
                return trimmed;
            }
        }
        return "";
    }

    private static String inferSchool(String text, String educationText) {
        String school = firstGroup(educationText, "([\\u4e00-\\u9fa5A-Za-z]{2,30}(大学|学院))");
        if (school.isBlank()) school = firstGroup(text, "([\\u4e00-\\u9fa5A-Za-z]{2,30}(大学|学院))");
        return school;
    }

    private static String inferMajor(String text, String educationText) {
        Matcher edu = Pattern.compile("大学\\s*[|｜]\\s*([^|｜\\n]{2,20})\\s*[|｜]").matcher(educationText);
        if (edu.find()) return edu.group(1).trim();
        String major = firstKnown(educationText, MAJORS);
        if (major.isBlank()) major = firstKnown(text, MAJORS);
        if (major.isBlank() && text.toLowerCase(Locale.ROOT).contains("software engineering")) major = "软件工程";
        return major;
    }

    private static String inferGrade(String text) {
        String grade = firstGroup(text, "(20\\d{2}\\s*届)");
        if (!grade.isBlank()) return grade.replace(" ", "");
        String graduationYear = firstGroup(text, "预计毕业[:：\\s]*(20\\d{2})");
        if (graduationYear.isBlank()) graduationYear = firstGroup(text, "(20\\d{2})\\.\\d{1,2}\\s*[（(]?预计");
        if (!graduationYear.isBlank()) return graduationYear + "届";
        return "";
    }

    private static String firstKnown(String text, List<String> candidates) {
        return candidates.stream().filter(text::contains).findFirst().orElse("");
    }

    private static List<String> inferRoles(String text, String intentText) {
        String combined = intentText.isBlank() ? text : intentText;
        String explicit = firstGroup(combined, "求职意向[:：\\s]*([^|｜\\n]{2,30})");
        if (!explicit.isBlank()) return List.of(normalizeRole(explicit));
        String fromIntentSection = firstIntentRole(intentText);
        if (!fromIntentSection.isBlank()) return List.of(normalizeRole(fromIntentSection));

        Map<String, String> roleSignals = new LinkedHashMap<>();
        roleSignals.put("后端开发", "Java Spring 后端 服务端 微服务 分布式 Node.js");
        roleSignals.put("前端开发", "JavaScript TypeScript React Vue CSS 前端 HTML");
        roleSignals.put("算法工程师", "机器学习 深度学习 推荐系统 PyTorch 算法 模型");
        roleSignals.put("数据分析", "SQL Python 数据分析 可视化 统计 A/B MySQL");
        roleSignals.put("产品经理", "需求分析 原型 用户研究 产品");
        roleSignals.put("测试开发", "测试 自动化测试 接口测试 CI/CD 单元测试");
        roleSignals.put("用户运营", "运营 活动策划 社群 用户");
        List<String> roles = roleSignals.entrySet().stream()
                .filter(entry -> signalScore(TextUtils.normalize(text), List.of(entry.getValue().split("\\s+"))) >= 25)
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();
        return roles.isEmpty() ? List.of("后端开发") : roles;
    }

    private static String firstIntentRole(String intentText) {
        if (intentText == null || intentText.isBlank()) return "";
        for (String line : intentText.split("\\R+")) {
            String candidate = line.split("[|｜]")[0]
                    .replaceAll("(求职意向|应聘岗位)[:：\\s]*", "")
                    .trim();
            if (candidate.length() >= 2 && candidate.length() <= 30) return candidate;
        }
        return "";
    }

    private static String normalizeRole(String value) {
        String role = value.replaceAll("\\s+", "").replace("实习生", "").replace("实习", "");
        if (role.contains("后端")) return "后端开发";
        if (role.contains("前端")) return "前端开发";
        if (role.contains("数据")) return "数据分析";
        if (role.contains("算法")) return "算法工程师";
        return role.isBlank() ? value.trim() : role;
    }

    private static int signalScore(String normalizedText, List<String> terms) {
        if (terms.isEmpty()) return 0;
        long hits = terms.stream().filter(term -> TextUtils.containsTerm(normalizedText, term)).count();
        return Math.round(hits * 100f / terms.size());
    }

    private static List<String> inferSkills(String fullText, String focusedText) {
        String normalized = TextUtils.normalize(focusedText.isBlank() ? fullText : focusedText);
        List<String> skills = new ArrayList<>(Keyword.skillVocabulary().stream()
                .filter(skill -> TextUtils.containsTerm(normalized, skill))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList());
        Matcher stack = Pattern.compile("技术栈[:：]\\s*([^\\n]+)").matcher(fullText);
        while (stack.find()) {
            skills.addAll(TextUtils.split(stack.group(1).replace("+", ",")));
        }
        return TextUtils.distinctNormalized(skills).stream().limit(20).toList();
    }

    private static List<String> extractExperience(String sectionText) {
        if (sectionText.isBlank()) return List.of();
        List<String> lines = Arrays.stream(sectionText.split("\\R+"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            boolean startsNew = !line.startsWith("-") && (line.contains("|") || line.contains("｜") || DATE_RANGE.matcher(line).find());
            if (startsNew && current.length() > 0) {
                items.add(current.toString().trim());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(" ");
            current.append(line);
        }
        if (current.length() > 0) items.add(current.toString().trim());
        return items.stream().limit(6).toList();
    }

    private static List<String> inferInterests(String text) {
        List<String> interests = new ArrayList<>();
        if (text.contains("云") || text.contains("Docker") || text.contains("Kubernetes")) interests.add("云服务");
        if (text.contains("高并发") || text.contains("分布式") || text.contains("Redis")) interests.add("高并发");
        if (text.contains("推荐") || text.contains("机器学习")) interests.add("推荐系统");
        if (text.contains("数据")) interests.add("数据分析");
        if (text.contains("Vue") || text.contains("React")) interests.add("前端工程");
        return interests.stream().distinct().limit(5).toList();
    }

    private static String firstGroup(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", " ").trim() : "";
    }
}
