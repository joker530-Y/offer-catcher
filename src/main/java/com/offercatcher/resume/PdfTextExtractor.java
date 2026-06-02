package com.offercatcher.resume;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PdfTextExtractor {
    static {
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.fontbox").setLevel(Level.SEVERE);
    }

    private PdfTextExtractor() {
    }

    public static String extract(byte[] pdf) {
        String pdfBoxText = extractWithPdfBox(pdf);
        if (!isLikelyGarbled(pdfBoxText)) {
            return cleanText(pdfBoxText);
        }
        String fallback = extractWithByteScan(pdf);
        return isLikelyGarbled(fallback) ? "" : cleanText(fallback);
    }

    static boolean isLikelyGarbled(String text) {
        if (text == null || text.isBlank()) return true;
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("%pdf") || normalized.contains("flatedecode") || normalized.contains("endobj") || normalized.contains("reportlab generated pdf")) {
            return true;
        }
        long replacement = text.chars().filter(ch -> ch == '\uFFFD').count();
        long control = text.chars().filter(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t').count();
        long useful = text.chars().filter(ch -> Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN).count();
        if (replacement > 5 || control > 5) return true;
        return useful < Math.min(30, text.length() / 4);
    }

    private static String extractWithPdfBox(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException | RuntimeException ex) {
            return "";
        }
    }

    private static String extractWithByteScan(byte[] pdf) {
        String rawUtf8 = new String(pdf, StandardCharsets.UTF_8);
        String rawLatin1 = new String(pdf, StandardCharsets.ISO_8859_1);
        Set<String> fragments = new LinkedHashSet<>();
        collectReadableLines(rawUtf8, fragments);
        collectLiteralStrings(rawLatin1, fragments);
        collectHexStrings(rawLatin1, fragments);
        return fragments.stream()
                .map(String::trim)
                .filter(PdfTextExtractor::looksUseful)
                .distinct()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static String cleanText(String text) {
        return Arrays.stream(text.replace('\u00A0', ' ').split("\\R"))
                .map(line -> line.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ").replaceAll("[ \\t]+", " ").trim())
                .filter(line -> !line.isBlank())
                .filter(line -> !looksLikePdfSyntax(line))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static void collectReadableLines(String raw, Set<String> fragments) {
        for (String line : raw.split("[\\r\\n]+")) {
            String cleaned = line.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ").trim();
            if (looksUseful(cleaned) && !looksLikePdfSyntax(cleaned)) fragments.add(cleaned);
        }
    }

    private static void collectLiteralStrings(String raw, Set<String> fragments) {
        Matcher matcher = Pattern.compile("\\((?:\\\\.|[^\\\\)]){2,}\\)").matcher(raw);
        while (matcher.find()) {
            String value = matcher.group();
            value = value.substring(1, value.length() - 1)
                    .replace("\\(", "(")
                    .replace("\\)", ")")
                    .replace("\\\\", "\\");
            if (looksUseful(value)) fragments.add(value);
        }
    }

    private static void collectHexStrings(String raw, Set<String> fragments) {
        Matcher matcher = Pattern.compile("<([0-9A-Fa-f\\s]{8,})>").matcher(raw);
        while (matcher.find()) {
            String hex = matcher.group(1).replaceAll("\\s+", "");
            if (hex.length() % 2 != 0 || hex.length() > 8000) continue;
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            String decoded = hex.startsWith("FEFF") || hex.startsWith("feff")
                    ? new String(Arrays.copyOfRange(bytes, 2, bytes.length), StandardCharsets.UTF_16BE)
                    : new String(bytes, StandardCharsets.UTF_8);
            if (looksUseful(decoded)) fragments.add(decoded);
        }
    }

    private static boolean looksUseful(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.length() < 2 || trimmed.length() > 300) return false;
        long useful = trimmed.chars().filter(ch -> Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN).count();
        return useful >= Math.min(4, trimmed.length());
    }

    private static boolean looksLikePdfSyntax(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("%pdf")
                || lower.matches("\\d+")
                || lower.startsWith("bt ")
                || lower.contains(" tj")
                || lower.contains(" td")
                || lower.contains(" tf")
                || lower.contains(" obj")
                || lower.contains(" endobj")
                || lower.contains("stream")
                || lower.startsWith("xref")
                || lower.startsWith("trailer")
                || lower.startsWith("startxref")
                || lower.contains("/type")
                || lower.contains("/font")
                || lower.contains("/mediabox")
                || lower.matches("\\d{10}\\s+\\d{5}\\s+[nf].*");
    }
}
