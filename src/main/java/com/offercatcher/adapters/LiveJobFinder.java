package com.offercatcher.adapters;

import com.offercatcher.matching.MatchEngine;
import com.offercatcher.model.Job;
import com.offercatcher.model.MatchReport;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.StudentProfile;
import com.offercatcher.util.Keyword;
import com.offercatcher.util.TextUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LiveJobFinder {
    private final List<OfficialSource> sources;
    private final HttpClient httpClient;
    private final MatchEngine engine;
    private final ExecutorService executor;
    private final Map<String, OfficialJobAdapter> adapters;

    public LiveJobFinder(List<OfficialSource> sources) {
        this.sources = List.copyOf(sources);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.engine = new MatchEngine();
        this.executor = Executors.newFixedThreadPool(Math.min(12, Math.max(1, sources.size())));
        this.adapters = Map.ofEntries(
                Map.entry("tencent", new TencentAdapter()),
                Map.entry("huawei", new HuaweiAdapter()),
                Map.entry("bytedance", new ByteDanceAdapter()),
                Map.entry("alibaba", new AlibabaAdapter()),
                Map.entry("meituan", new MeituanAdapter()),
                Map.entry("jd", new JdAdapter()),
                Map.entry("xiaomi", new XiaomiAdapter()),
                Map.entry("baidu", new BaiduAdapter()),
                Map.entry("kuaishou", new KuaishouAdapter()),
                Map.entry("netease", new NeteaseAdapter()),
                Map.entry("nio", new NioAdapter()),
                Map.entry("huaweicloud", new HuaweiCloudAdapter())
        );
    }

    public List<OfficialSource> sources() {
        return sources;
    }

    public List<MatchReport> search(StudentProfile profile) {
        List<CompletableFuture<List<Job>>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> fetchFromSource(profile, source), executor)
                        .orTimeout(8, TimeUnit.SECONDS)
                        .exceptionally(ex -> FallbackAdapter.generate(profile, source)))
                .toList();

        List<Job> jobs = futures.stream()
                .flatMap(future -> future.join().stream())
                .toList();

        List<MatchReport> ranked = jobs.stream()
                .map(job -> engine.analyze(profile, job))
                .sorted(Comparator.comparingInt(MatchReport::totalScore).reversed())
                .toList();

        Map<String, MatchReport> bestByCompany = new LinkedHashMap<>();
        for (MatchReport report : ranked) {
            bestByCompany.putIfAbsent(report.job().company(), report);
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        List<MatchReport> selected = new ArrayList<>();
        for (MatchReport report : bestByCompany.values()) {
            selected.add(report);
            selectedIds.add(report.job().id());
        }
        for (MatchReport report : ranked) {
            if (selected.size() >= 36) break;
            if (selectedIds.add(report.job().id())) selected.add(report);
        }

        return selected.stream()
                .sorted(Comparator.comparingInt(MatchReport::totalScore).reversed())
                .toList();
    }

    public Job findGeneratedJob(StudentProfile profile, String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return search(profile).stream().findFirst().map(MatchReport::job).orElseThrow(() -> new IllegalArgumentException("没有可诊断的岗位。"));
        }
        return search(profile).stream()
                .map(MatchReport::job)
                .filter(job -> job.id().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到该岗位，请先重新执行匹配。"));
    }

    public List<String> searchQueries(StudentProfile profile) {
        Set<String> queries = new LinkedHashSet<>();
        for (OfficialSource source : sources) {
            for (String role : inferRoleFamilies(profile, source).stream().limit(2).toList()) {
                queries.add(source.company() + " " + role + " " + String.join(" ", profile.skills().stream().limit(3).toList()));
            }
        }
        return new ArrayList<>(queries);
    }

    private List<Job> fetchFromSource(StudentProfile profile, OfficialSource source) {
        List<Job> live = adapterFor(source).fetch(profile, source, httpClient);
        return live.isEmpty() ? FallbackAdapter.generate(profile, source) : live;
    }

    private OfficialJobAdapter adapterFor(OfficialSource source) {
        return adapters.getOrDefault(source.id(), (p, s, client) -> List.of());
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static List<String> inferRoleFamilies(StudentProfile profile, OfficialSource source) {
        List<String> roles = new ArrayList<>();
        for (String role : profile.targetRoles()) {
            if (TextUtils.containsAny(source.roleFamilies(), role) || source.roleFamilies().stream().anyMatch(sourceRole -> TextUtils.containsAny(List.of(role), sourceRole))) {
                roles.add(role);
            }
        }
        roles.addAll(source.roleFamilies());
        return TextUtils.distinctNormalized(roles).stream().limit(4).toList();
    }

    public static List<String> mergeRelevantSkills(StudentProfile profile, OfficialSource source, String role) {
        List<String> skills = new ArrayList<>();
        skills.addAll(profile.skills());
        skills.addAll(source.skillHints());
        skills.addAll(HttpUtils.inferJobSkills(role, source.skillHints()));
        List<String> extracted = Keyword.extract(role + " " + String.join(" ", source.domains()));
        skills.addAll(extracted);
        return TextUtils.distinctNormalized(skills).stream().limit(10).toList();
    }
}
