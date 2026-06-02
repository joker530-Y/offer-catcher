package com.offercatcher.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OfficialSource(
        String id,
        String company,
        String sourceType,
        String sourceUrl,
        List<String> cities,
        List<String> roleFamilies,
        List<String> skillHints,
        List<String> domains
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("company", company);
        map.put("sourceType", sourceType);
        map.put("sourceUrl", sourceUrl);
        map.put("cities", cities);
        map.put("roleFamilies", roleFamilies);
        map.put("skillHints", skillHints);
        map.put("domains", domains);
        return map;
    }
}
