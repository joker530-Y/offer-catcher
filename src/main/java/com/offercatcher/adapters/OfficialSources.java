package com.offercatcher.adapters;

import com.offercatcher.model.OfficialSource;

import java.util.List;

public final class OfficialSources {
    private OfficialSources() {
    }

    public static List<OfficialSource> load() {
        return List.of(
                source("tencent", "腾讯", "企业招聘官网", "https://join.qq.com/", List.of("深圳", "北京", "上海", "广州", "成都", "武汉"), List.of("后端开发", "前端开发", "算法工程师", "测试开发", "产品经理"), List.of("Java", "Go", "C++", "Redis", "MySQL", "Linux", "机器学习"), List.of("社交", "游戏", "云服务", "广告")),
                source("huawei", "华为", "校园招聘官网", "https://career.huawei.com/reccampportal/portal5/index.html", List.of("深圳", "东莞", "上海", "北京", "杭州", "南京"), List.of("软件开发", "算法工程师", "测试开发", "云计算开发"), List.of("Java", "C++", "Python", "Linux", "云原生", "网络"), List.of("通信", "云服务", "终端", "AI")),
                source("bytedance", "字节跳动", "校园招聘官网", "https://jobs.bytedance.com/campus/position", List.of("北京", "上海", "深圳", "杭州", "广州", "成都"), List.of("后端开发", "前端开发", "算法工程师", "数据分析", "产品经理"), List.of("Java", "Go", "Python", "SQL", "推荐系统", "数据分析"), List.of("内容平台", "推荐系统", "广告", "电商")),
                source("alibaba", "阿里巴巴", "校园招聘官网", "https://talent.alibaba.com/campus/home", List.of("杭州", "北京", "上海", "深圳", "广州"), List.of("后端开发", "算法工程师", "数据开发", "产品经理"), List.of("Java", "分布式", "MySQL", "Redis", "大数据", "机器学习"), List.of("电商", "云计算", "供应链", "数据平台")),
                source("meituan", "美团", "校园招聘官网", "https://zhaopin.meituan.com/web/campus", List.of("北京", "上海", "深圳", "成都", "武汉"), List.of("后端开发", "算法工程师", "数据分析", "产品经理"), List.of("Java", "Go", "Redis", "MySQL", "SQL", "机器学习"), List.of("本地生活", "交易系统", "配送", "到店")),
                source("jd", "京东", "校园招聘官网", "https://campus.jd.com/", List.of("北京", "上海", "深圳", "成都", "西安"), List.of("后端开发", "算法工程师", "数据分析", "产品经理"), List.of("Java", "Spring", "MySQL", "Redis", "SQL", "大数据"), List.of("电商", "物流", "供应链", "零售科技")),
                source("xiaomi", "小米", "校园招聘官网", "https://hr.xiaomi.com/campus", List.of("北京", "武汉", "上海", "深圳", "南京"), List.of("软件开发", "Android", "iOS", "测试开发", "算法工程师"), List.of("Java", "Android", "Kotlin", "C++", "Python", "自动化测试"), List.of("智能硬件", "手机", "IoT", "互联网服务")),
                source("baidu", "百度", "校园招聘官网", "https://talent.baidu.com/jobs/list?projectType=3&recruitType=GRADUATE", List.of("北京", "上海", "深圳", "广州", "成都"), List.of("算法工程师", "后端开发", "数据分析", "测试开发"), List.of("Python", "机器学习", "深度学习", "Java", "C++", "SQL"), List.of("搜索", "AI", "自动驾驶", "云服务")),
                source("kuaishou", "快手", "校园招聘官网", "https://zhaopin.kuaishou.cn/", List.of("北京", "深圳", "上海", "杭州"), List.of("后端开发", "算法工程师", "数据分析", "产品经理"), List.of("Java", "Go", "Python", "推荐系统", "SQL", "数据分析"), List.of("短视频", "直播", "推荐系统", "电商")),
                source("netease", "网易", "校园招聘官网", "https://hr.163.com/", List.of("杭州", "广州", "上海", "北京"), List.of("后端开发", "前端开发", "游戏开发", "测试开发", "产品经理"), List.of("Java", "C++", "JavaScript", "Unity", "测试", "产品"), List.of("游戏", "邮箱", "教育", "音乐")),
                source("nio", "蔚来", "校园招聘官网", "https://nio.jobs.feishu.cn/campus", List.of("上海", "北京", "合肥", "深圳"), List.of("软件开发", "算法工程师", "数据分析", "产品经理"), List.of("C++", "Python", "自动驾驶", "数据分析", "Linux", "云平台"), List.of("智能汽车", "自动驾驶", "车联网", "能源")),
                source("huaweicloud", "华为云", "校招专题页", "https://www.huaweicloud.com/lab/paas/sacampusrecruitment.html", List.of("深圳", "北京", "上海", "杭州", "南京"), List.of("云计算开发", "AI工程师", "后端开发", "数据开发"), List.of("Java", "Python", "云原生", "Kubernetes", "Linux", "AI"), List.of("云计算", "AI", "PaaS", "开发者生态"))
        );
    }

    private static OfficialSource source(String id, String company, String sourceType, String sourceUrl,
                                         List<String> cities, List<String> roleFamilies, List<String> skillHints, List<String> domains) {
        return new OfficialSource(id, company, sourceType, sourceUrl, cities, roleFamilies, skillHints, domains);
    }
}
