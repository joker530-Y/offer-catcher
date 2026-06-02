package com.offercatcher.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class TextUtils {
    private TextUtils() {
    }

    public static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static String normalize(String text) {
        return Objects.toString(text, "").toLowerCase(Locale.ROOT).replace(" ", "");
    }

    public static String normalizeId(String text) {
        String normalized = Objects.toString(text, "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Pattern.compile("[,，;；、\\n]+").splitAsStream(raw)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public static List<String> splitSentences(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Pattern.compile("[。！？!?；;\\n]+").splitAsStream(raw)
                .map(String::trim)
                .filter(s -> s.length() > 4)
                .toList();
    }

    public static List<String> merge(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    public static List<String> expandTerms(List<String> terms) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String term : terms) {
            if (normalize(term).length() > 14) {
                List<String> extracted = Keyword.extract(term);
                if (extracted.isEmpty()) {
                    expanded.add(term);
                } else {
                    expanded.addAll(extracted);
                }
            } else if (!Keyword.isStopWord(term)) {
                expanded.add(term);
            }
        }
        return distinctNormalized(new ArrayList<>(expanded));
    }

    public static List<String> distinctNormalized(List<String> terms) {
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (String term : terms) {
            String normalized = normalize(term);
            if (!normalized.isBlank()) byNormalized.putIfAbsent(normalized, term);
        }
        return new ArrayList<>(byNormalized.values());
    }

    public static boolean containsNormalized(List<String> terms, String candidate) {
        String normalized = normalize(candidate);
        return terms.stream().map(TextUtils::normalize).anyMatch(normalized::equals);
    }

    public static boolean containsAny(List<String> needles, String haystack) {
        String normalizedHaystack = normalize(haystack);
        return needles.stream().map(TextUtils::normalize).anyMatch(item ->
                !item.isBlank() && (normalizedHaystack.contains(item) || item.contains(normalizedHaystack)));
    }

    public static boolean containsTerm(String normalizedText, String term) {
        return normalizedText.contains(normalize(term));
    }

    public static List<String> matched(String profileText, List<String> terms) {
        return terms.stream().filter(term -> containsTerm(profileText, term)).toList();
    }

    public static List<String> missing(String profileText, List<String> terms) {
        return terms.stream().filter(term -> !containsTerm(profileText, term)).toList();
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static List<String> compact(List<String> items) {
        return items.stream().filter(item -> item != null && !item.isBlank()).toList();
    }
}
