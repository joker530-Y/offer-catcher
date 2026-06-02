package com.offercatcher.adapters;

import com.offercatcher.model.Job;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.SourceStatus;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.HtmlUtils;
import com.offercatcher.util.TextUtils;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

class RolePageAdapter implements OfficialJobAdapter {
    private final String pageUrl;

    RolePageAdapter(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    @Override
    public List<Job> fetch(StudentProfile profile, OfficialSource source, HttpClient httpClient) {
        String html = HttpUtils.get(httpClient, pageUrl);
        if (html.isBlank()) return List.of();
        String pageText = String.join(" ", HtmlUtils.cleanHtml(html));
        List<Job> jobs = new ArrayList<>();
        for (String role : LiveJobFinder.inferRoleFamilies(profile, source).stream().limit(2).toList()) {
            boolean pageHasRoleSignal = TextUtils.containsTerm(TextUtils.normalize(pageText), role)
                    || LiveJobFinder.mergeRelevantSkills(profile, source, role).stream().anyMatch(skill -> TextUtils.containsTerm(TextUtils.normalize(pageText), skill));
            if (!pageHasRoleSignal && !TextUtils.containsAny(source.domains(), pageText)) continue;
            String city = preferredCity(profile, source);
            String query = role + " " + String.join(" ", profile.skills().stream().limit(4).toList());
            jobs.add(new Job(
                    source.id() + "-connected-" + TextUtils.normalizeId(role),
                    source.company(),
                    role + "相关岗位（官网已连接）",
                    city,
                    source.sourceType() + "（已连接官网页面）",
                    HttpUtils.appendQuery(pageUrl, query),
                    List.of(profile.grade().isBlank() ? "不限" : profile.grade()),
                    LiveJobFinder.mergeRelevantSkills(profile, source, role),
                    List.of("沟通", "协作", "学习能力"),
                    source.domains(),
                    List.of(
                            "已实时请求招聘官网：" + source.sourceUrl(),
                            "官网首屏未暴露稳定职位列表字段，本地只生成与简历相关的官网检索入口。",
                            "检索词：" + query
                    ),
                    "以官网页面为准",
                    false,
                    SourceStatus.CONNECTED
            ));
        }
        return jobs;
    }

    private static String preferredCity(StudentProfile profile, OfficialSource source) {
        return profile.cities().stream()
                .filter(city -> TextUtils.containsAny(source.cities(), city))
                .findFirst()
                .orElse(source.cities().isEmpty() ? "未指定" : source.cities().get(0));
    }
}
