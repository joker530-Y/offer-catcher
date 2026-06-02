package com.offercatcher.util;

import java.util.Arrays;
import java.util.List;

public final class HtmlUtils {
    private HtmlUtils() {
    }

    public static String htmlDecode(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#x27;", "'");
    }

    public static List<String> cleanHtml(String html) {
        String text = html.replaceAll("<script[\\s\\S]*?</script>", " ")
                .replaceAll("<style[\\s\\S]*?</style>", " ")
                .replaceAll("<[^>]+>", "\n")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
        return Arrays.stream(text.split("[\\r\\n]+"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    }
}
