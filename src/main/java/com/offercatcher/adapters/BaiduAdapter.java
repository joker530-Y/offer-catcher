package com.offercatcher.adapters;

import com.offercatcher.model.Job;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.SourceStatus;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.TextUtils;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BaiduAdapter implements OfficialJobAdapter {
    private static final String JOB_LIST_URL = "https://talent.baidu.com/jobs/list?projectType=3&recruitType=GRADUATE";
    private static final Pattern JOB_TITLE = Pattern.compile("(2027AIDU-[^<()]{2,80})\\((J\\d+)\\)");

    @Override
    public List<Job> fetch(StudentProfile profile, OfficialSource source, HttpClient httpClient) {
        String html = HttpUtils.get(httpClient, JOB_LIST_URL);
        if (html.isBlank()) return List.of();
        Matcher matcher = JOB_TITLE.matcher(html);
        List<Job> jobs = new ArrayList<>();
        while (matcher.find() && jobs.size() < 16) {
            String rawTitle = matcher.group(1).trim();
            String code = matcher.group(2);
            int end = Math.min(html.length(), matcher.end() + 1200);
            String segment = html.substring(matcher.start(), end);
            String city = HttpUtils.extractCities(segment, source.cities());
            String deadline = HttpUtils.firstRegex(segment, "(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})", "以官网页面为准");
            List<String> hardSkills = HttpUtils.inferJobSkills(rawTitle, source.skillHints());
            jobs.add(new Job(
                    source.id() + "-parsed-" + TextUtils.normalizeId(code),
                    source.company(),
                    rawTitle,
                    city,
                    source.sourceType() + "（已解析职位列表）",
                    JOB_LIST_URL,
                    List.of("2026届", "2027届", "不限"),
                    hardSkills,
                    List.of("沟通", "协作", "学习能力"),
                    source.domains(),
                    List.of(
                            "来自百度校园招聘公开职位列表，岗位标题已解析。",
                            "请回到官网确认完整 JD、城市、届别和投递截止时间。"
                    ),
                    deadline,
                    false,
                    SourceStatus.PARSED
            ));
        }
        return jobs;
    }
}
