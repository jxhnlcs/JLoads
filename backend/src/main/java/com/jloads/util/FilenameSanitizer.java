package com.jloads.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converte textos arbitrários (ex.: título vindo do YouTube) em nomes de arquivo seguros para Windows, Linux
 * e macOS. O resultado nunca contém separadores de caminho, "..", caracteres de controle ou nomes reservados.
 */
public final class FilenameSanitizer {

    public static final String FALLBACK = "download";
    /** Limite em bytes UTF-8 para o nome base (sem extensão), abaixo do limite de 255 da maioria dos FS. */
    public static final int MAX_BASENAME_BYTES = 150;

    private static final Pattern FORBIDDEN = Pattern.compile("[\\p{Cntrl}<>:\"/\\\\|?*\\u0000]");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\p{Co}\\p{Cn}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,5}$");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FilenameSanitizer() {
    }

    /** Sanitiza um nome base (sem extensão). */
    public static String sanitizeBaseName(String input) {
        if (input == null) {
            return FALLBACK;
        }
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC);
        value = FORBIDDEN.matcher(value).replaceAll(" ");
        value = INVISIBLE.matcher(value).replaceAll("");
        value = WHITESPACE.matcher(value).replaceAll(" ").strip();
        value = stripDotsAndSpaces(value);
        value = truncateUtf8(value, MAX_BASENAME_BYTES);
        value = stripDotsAndSpaces(value);

        if (value.isEmpty()) {
            return FALLBACK;
        }
        String stem = value.contains(".") ? value.substring(0, value.indexOf('.')) : value;
        if (WINDOWS_RESERVED.contains(stem.strip().toUpperCase(Locale.ROOT))) {
            value = "_" + value;
        }
        return value;
    }

    /** Monta um nome de arquivo completo a partir de um título e de uma extensão conhecida. */
    public static String toFilename(String title, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!EXTENSION.matcher(ext).matches()) {
            throw new IllegalArgumentException("invalid extension");
        }
        return sanitizeBaseName(title) + "." + ext;
    }

    private static String stripDotsAndSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '.' || value.charAt(start) == ' ')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == ' ')) {
            end--;
        }
        return value.substring(start, end);
    }

    private static String truncateUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) {
                break;
            }
            out.append(ch);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }
}
