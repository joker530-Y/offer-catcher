package com.offercatcher.resume;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MultipartFile(String fileName, byte[] content) {
    public static MultipartFile from(String contentType, byte[] body) {
        String boundary = Arrays.stream(contentType.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith("boundary="))
                .map(part -> part.substring("boundary=".length()).replace("\"", ""))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少 multipart boundary。"));
        byte[] marker = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> parts = splitBytes(body, marker);
        for (byte[] part : parts) {
            int headerEnd = indexOf(part, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
            if (headerEnd < 0) continue;
            String headers = new String(part, 0, headerEnd, StandardCharsets.ISO_8859_1);
            if (!headers.contains("filename=")) continue;
            String fileName = extractHeaderValue(headers, "filename");
            int start = headerEnd + 4;
            int end = part.length;
            while (end > start && (part[end - 1] == '\n' || part[end - 1] == '\r' || part[end - 1] == '-')) end--;
            byte[] content = Arrays.copyOfRange(part, start, end);
            if (content.length == 0) throw new IllegalArgumentException("上传文件为空。");
            return new MultipartFile(fileName.isBlank() ? "resume.pdf" : fileName, content);
        }
        throw new IllegalArgumentException("没有找到上传的 PDF 文件字段。");
    }

    private static String extractHeaderValue(String headers, String key) {
        Matcher matcher = Pattern.compile(key + "=\"([^\"]*)\"").matcher(headers);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<byte[]> splitBytes(byte[] body, byte[] marker) {
        List<byte[]> parts = new ArrayList<>();
        int cursor = 0;
        while (cursor < body.length) {
            int start = indexOf(body, marker, cursor);
            if (start < 0) break;
            start += marker.length;
            int next = indexOf(body, marker, start);
            if (next < 0) break;
            parts.add(Arrays.copyOfRange(body, start, next));
            cursor = next;
        }
        return parts;
    }

    private static int indexOf(byte[] source, byte[] target, int from) {
        outer:
        for (int i = Math.max(0, from); i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
