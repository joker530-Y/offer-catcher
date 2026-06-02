package com.offercatcher.matching;

import java.util.List;
import java.util.Map;

public final class CompliancePolicy {
    private CompliancePolicy() {
    }

    public static Map<String, Object> toMap() {
        return Map.of(
                "mode", "public_official_sources_with_compliance_controls",
                "principles", List.of(
                        "优先使用官方 API、RSS、站点地图和公开校招页面",
                        "遵守网站用户协议、robots.txt、访问频率限制和反爬策略",
                        "不绕过登录、验证码、风控、权限校验或技术保护措施",
                        "仅存储岗位匹配所需字段，并保留企业官网来源链接",
                        "真实投递以企业招聘官网原页面为准"
                ),
                "reservedCollectorFields", List.of("sourceUrl", "robotsAllowed", "termsCheckedAt", "rateLimitPolicy", "lastFetchedAt")
        );
    }
}
