package HouseBook.company.util;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Locale;

/** Resolve a capa usando o campo image/jpeg fornecido pela Gutendex em formats. */
public final class GutenbergCovers {

    private GutenbergCovers() {
    }

    public static String urlFor(String gutenbergId) {
        if (gutenbergId == null || !gutenbergId.matches("\\d+")) {
            return "";
        }
        return "https://www.gutenberg.org/cache/epub/" + gutenbergId + "/pg" + gutenbergId + ".cover.medium.jpg";
    }

    public static String fromFormats(JsonNode formats, String gutenbergId) {
        if (formats != null && formats.isObject()) {
            for (var entry : formats.properties()) {
                if ("image/jpeg".equals(entry.getKey().toLowerCase(Locale.ROOT))) {
                    String imageUrl = entry.getValue().asText("");
                    if (isGutenbergImage(imageUrl)) {
                        return imageUrl;
                    }
                }
            }
        }
        return urlFor(gutenbergId);
    }

    public static String orFallback(String thumbnail, String gutenbergId) {
        return thumbnail != null && isGutenbergImage(thumbnail) ? thumbnail : urlFor(gutenbergId);
    }

    private static boolean isGutenbergImage(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && (host.equals("gutenberg.org") || host.endsWith(".gutenberg.org")) && (uri.getPath() == null || uri.getPath().toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp)$"));
        }
        catch (IllegalArgumentException ex) {
            return false;
        }
    }
}