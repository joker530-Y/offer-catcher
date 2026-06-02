package com.offercatcher.matching;

import com.offercatcher.model.Job;
import com.offercatcher.model.MatchReport;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.Keyword;
import com.offercatcher.util.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MatchEngine {
    private static final Pattern QUANTIFIED_RESULT = Pattern.compile("\\d+|%|万|千|用户|qps|性能|准确率", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_VERB = Pattern.compile("负责|设计|实现|优化|上线|分析|协作|主导|开发|部署");

    public MatchReport analyze(StudentProfile profile, Job job) {
        String profileText = TextUtils.normalize(profile.allText());
        int skill = coverage(profileText, job.hardSkills());
        int project = coverage(profileText, TextUtils.merge(job.domains(), job.responsibilities()));
        int preference = preferenceScore(profile, job);
        int constraint = constraintScore(profile, job);
        int interest = coverage(TextUtils.normalize(String.join(" ", profile.interests())), TextUtils.merge(job.domains(), job.responsibilities()));
        int resumeQuality = resumeQuality(profile.resumeText(), profile.projects());

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("技能匹配", skill);
        dimensions.put("项目经历", project);
        dimensions.put("偏好匹配", preference);
        dimensions.put("硬约束", constraint);
        dimensions.put("职业兴趣", interest);
        dimensions.put("简历表达", resumeQuality);

        int total = TextUtils.clamp(Math.round(skill * 0.30f + project * 0.25f + preference * 0.15f
                + constraint * 0.15f + interest * 0.10f + resumeQuality * 0.05f));

        List<String> matchedSkills = TextUtils.matched(profileText, job.hardSkills());
        List<String> missingSkills = TextUtils.missing(profileText, job.hardSkills());
        List<String> matchedDomains = TextUtils.matched(profileText, TextUtils.merge(job.domains(), job.responsibilities()));

        List<String> strengths = new ArrayList<>();
        if (!matchedSkills.isEmpty()) strengths.add("技能命中：" + String.join("、", matchedSkills));
        if (!matchedDomains.isEmpty()) strengths.add("经历/兴趣覆盖：" + String.join("、", matchedDomains.subList(0, Math.min(4, matchedDomains.size()))));
        if (TextUtils.containsAny(profile.cities(), job.city())) strengths.add("城市偏好与岗位地点一致：" + job.city());
        if (strengths.isEmpty()) strengths.add("当前画像与岗位存在基础相关性，但需要补充更多项目和技能证据。");

        List<String> gaps = new ArrayList<>();
        if (!missingSkills.isEmpty()) gaps.add("缺少或未突出关键词：" + String.join("、", missingSkills));
        if (project < 55) gaps.add("项目经历与岗位职责的语义覆盖不足，建议补充可量化项目成果。");
        if (resumeQuality < 60) gaps.add("简历表达偏弱，建议增加动作动词、指标、技术栈和结果。");
        if (gaps.isEmpty()) gaps.add("主要硬技能已覆盖，下一步应强化岗位相关项目证据。");

        List<String> risks = new ArrayList<>();
        if (!profile.grade().isBlank() && !job.grades().contains("不限") && !TextUtils.containsAny(job.grades(), profile.grade())) {
            risks.add("届别可能不完全匹配，请回到企业官网确认投递资格。");
        }
        if (!profile.cities().isEmpty() && !"未指定".equals(job.city()) && !TextUtils.containsAny(profile.cities(), job.city())) {
            risks.add("岗位地点不在当前城市偏好内。");
        }
        if (job.demo()) {
            risks.add("这是实时检索入口，不是完整岗位缓存。真实投递前请以来源链接的企业官网信息为准。");
        }

        List<String> advice = resumeAdvice(job, missingSkills, matchedSkills, profile);
        String priority = total >= 82 ? "优先投递" : total >= 68 ? "可投递，先优化简历" : total >= 52 ? "备选，补齐缺口后投递" : "暂缓投递";
        String explanation = "综合判断：" + priority + "。该岗位主要看重 " + String.join("、", job.hardSkills().stream().limit(4).toList())
                + "，当前画像得分最高维度为 " + bestDimension(dimensions) + "。";

        return new MatchReport(job, total, dimensions, strengths, gaps, risks, advice, priority, explanation);
    }

    private static List<String> resumeAdvice(Job job, List<String> missingSkills, List<String> matchedSkills, StudentProfile profile) {
        List<String> advice = new ArrayList<>();
        if (!matchedSkills.isEmpty()) {
            advice.add("在简历项目标题或项目描述中前置已具备技能：" + String.join("、", matchedSkills.stream().limit(4).toList()) + "。");
        }
        if (!missingSkills.isEmpty()) {
            advice.add("不要虚构经历；若确实使用过，可把 " + String.join("、", missingSkills.stream().limit(4).toList()) + " 补入技能栏和对应项目证据。");
        }
        advice.add("针对 " + job.title() + " 增加 STAR 表达：任务背景、个人动作、技术方案、量化结果。");
        advice.add("保留企业官网投递链接，按 JD 原文核对届别、城市、实习周期和截止时间。");
        if (profile.projects().isEmpty() && profile.resumeText().length() < 80) {
            advice.add("当前输入缺少项目细节，建议补充课程项目、实习、竞赛或开源经历后重新匹配。");
        }
        return advice;
    }

    private static int coverage(String text, List<String> terms) {
        List<String> expanded = TextUtils.expandTerms(terms);
        if (expanded.isEmpty()) return 50;
        long hit = expanded.stream().filter(term -> TextUtils.containsTerm(text, term)).count();
        return TextUtils.clamp(Math.round(hit * 100f / expanded.size()));
    }

    private static int preferenceScore(StudentProfile profile, Job job) {
        int role = TextUtils.containsAny(profile.targetRoles(), job.title()) ? 60 : 0;
        int city = profile.cities().isEmpty() || "未指定".equals(job.city()) ? 30 : TextUtils.containsAny(profile.cities(), job.city()) ? 40 : 10;
        return TextUtils.clamp(role + city);
    }

    private static int constraintScore(StudentProfile profile, Job job) {
        int score = 65;
        if (profile.grade().isBlank() || job.grades().contains("不限") || TextUtils.containsAny(job.grades(), profile.grade())) score += 25;
        if (profile.constraints().stream().anyMatch(item -> TextUtils.normalize(item).contains("远程")) && TextUtils.normalize(job.city()).contains("远程")) score += 10;
        return TextUtils.clamp(score);
    }

    private static int resumeQuality(String resumeText, List<String> projects) {
        String text = resumeText + " " + String.join(" ", projects);
        int score = 35;
        if (text.length() > 120) score += 20;
        if (QUANTIFIED_RESULT.matcher(text).find()) score += 20;
        if (ACTION_VERB.matcher(text).find()) score += 15;
        if (Keyword.extract(text).size() >= 8) score += 10;
        return TextUtils.clamp(score);
    }

    private static String bestDimension(Map<String, Integer> dimensions) {
        return dimensions.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("综合匹配");
    }
}
