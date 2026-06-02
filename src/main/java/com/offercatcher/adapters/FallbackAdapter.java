package com.offercatcher.adapters;

import com.offercatcher.model.Job;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.SourceStatus;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

final class FallbackAdapter {
    private FallbackAdapter() {
    }

    static List<Job> generate(StudentProfile profile, OfficialSource source) {
        List<String> roles = LiveJobFinder.inferRoleFamilies(profile, source).stream().limit(2).toList();
        List<String> cities = preferredCities(profile, source).stream().limit(2).toList();
        List<Job> jobs = new ArrayList<>();
        for (String role : roles) {
            String city = cities.isEmpty() ? "未指定" : cities.get(0);
            String query = role + " " + String.join(" ", profile.skills().stream().limit(4).toList());
            jobs.add(new Job(
                    source.id() + "-fallback-" + TextUtils.normalizeId(role),
                    source.company(),
                    role + "相关岗位（官网检索入口）",
                    city,
                    source.sourceType() + "（实时检索入口）",
                    HttpUtils.appendQuery(source.sourceUrl(), query),
                    List.of(profile.grade().isBlank() ? "不限" : profile.grade()),
                    LiveJobFinder.mergeRelevantSkills(profile, source, role),
                    List.of("沟通", "协作", "学习能力"),
                    source.domains(),
                    List.of(
                            "当前本地服务不保存岗位候选池，将根据简历生成官网检索入口。",
                            "建议在官网按岗位、城市、届别再次筛选，并以官网 JD 原文为准。",
                            "检索词：" + query
                    ),
                    "以官网页面为准",
                    true,
                    SourceStatus.FALLBACK
            ));
        }
        return jobs;
    }

    private static List<String> preferredCities(StudentProfile profile, OfficialSource source) {
        List<String> matched = profile.cities().stream()
                .filter(city -> TextUtils.containsAny(source.cities(), city))
                .toList();
        return matched.isEmpty() ? source.cities() : matched;
    }
}
