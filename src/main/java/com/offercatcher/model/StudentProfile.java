package com.offercatcher.model;

import com.offercatcher.util.TextUtils;

import java.util.List;
import java.util.Map;

public record StudentProfile(
        String name,
        String school,
        String major,
        String grade,
        List<String> targetRoles,
        List<String> cities,
        List<String> skills,
        List<String> internships,
        List<String> projects,
        List<String> interests,
        List<String> constraints,
        String resumeText
) {
    public static StudentProfile fromMap(Map<String, Object> map) {
        return new StudentProfile(
                string(map, "name"),
                string(map, "school"),
                string(map, "major"),
                string(map, "grade"),
                list(map, "targetRoles"),
                list(map, "cities"),
                list(map, "skills"),
                list(map, "internships"),
                list(map, "projects"),
                list(map, "interests"),
                list(map, "constraints"),
                string(map, "resumeText")
        );
    }

    public Map<String, Object> toPublicMap() {
        return Map.of(
                "name", name,
                "school", school,
                "major", major,
                "grade", grade,
                "targetRoles", targetRoles,
                "cities", cities,
                "skills", skills,
                "internships", internships,
                "interests", interests
        );
    }

    public String allText() {
        return String.join(" ", List.of(
                name, school, major, grade,
                String.join(" ", targetRoles),
                String.join(" ", cities),
                String.join(" ", skills),
                String.join(" ", internships),
                String.join(" ", projects),
                String.join(" ", interests),
                String.join(" ", constraints),
                resumeText
        ));
    }

    private static String string(Map<String, Object> map, String key) {
        return TextUtils.asString(map.get(key));
    }

    private static List<String> list(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> raw) {
            return raw.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).toList();
        }
        return TextUtils.split(TextUtils.asString(value));
    }
}
