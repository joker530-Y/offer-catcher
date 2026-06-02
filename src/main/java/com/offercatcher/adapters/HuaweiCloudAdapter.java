package com.offercatcher.adapters;

import com.offercatcher.model.Job;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.SourceStatus;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.TextUtils;

import java.net.http.HttpClient;
import java.util.List;

final class HuaweiCloudAdapter implements OfficialJobAdapter {
    private static final List<String> STATIC_TITLES = List.of(
            "AI软件工程师", "AI算法工程师", "AI数据工程师", "AI应用工程师",
            "AI安全工程师", "AI Infra工程师", "软件开发工程师", "云计算开发工程师"
    );

    @Override
    public List<Job> fetch(StudentProfile profile, OfficialSource source, HttpClient httpClient) {
        String html = HttpUtils.get(httpClient, source.sourceUrl());
        if (html.isBlank()) return List.of();
        return STATIC_TITLES.stream()
                .filter(html::contains)
                .map(title -> new Job(
                        source.id() + "-parsed-" + TextUtils.normalizeId(title),
                        source.company(),
                        title,
                        "深圳",
                        source.sourceType() + "（已解析静态岗位页）",
                        source.sourceUrl(),
                        List.of("2026届", "2027届", "不限"),
                        HttpUtils.inferJobSkills(title, source.skillHints()),
                        List.of("沟通", "协作", "学习能力"),
                        source.domains(),
                        List.of(
                                "来自华为云公开校招专题页，岗位名称已解析。",
                                "请回到官网确认完整 JD、城市、届别和投递截止时间。"
                        ),
                        "以官网页面为准",
                        false,
                        SourceStatus.PARSED
                ))
                .limit(10)
                .toList();
    }
}
