package HouseBook.company.util;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolve a capa usando o campo image/jpeg fornecido pela Gutendex em formats. */
public final class GutenbergCovers {

    private GutenbergCovers() {
    }

    public static String urlFor(String gutenbergId) {
        return urlFor(gutenbergId, "medium");
    }

    /** size: "medium" (capa padrão) ou "small". */
    public static String urlFor(String gutenbergId, String size) {
        if (gutenbergId == null || !gutenbergId.matches("\\d+")) {
            return "";
        }
        return "https://www.gutenberg.org/cache/epub/" + gutenbergId + "/pg" + gutenbergId + ".cover." + size + ".jpg";
    }

    /**
     * Endereços a tentar, em ordem e sem repetição: o que a Gutendex informou para o livro
     * (a capa original), depois o padrão médio e o pequeno do Gutenberg.
     */
    public static List<String> candidates(String savedThumbnail, String gutenbergId) {
        List<String> urls = new ArrayList<>();
        if (isGutenbergImage(savedThumbnail)) {
            urls.add(savedThumbnail.trim());
        }
        for (String size : List.of("medium", "small")) {
            String url = urlFor(gutenbergId, size);
            if (!url.isEmpty() && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    /** Tipo da imagem pelos primeiros bytes; null se não for JPEG, PNG, GIF nem WebP (ex.: página de erro em HTML). */
    public static String contentTypeOf(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
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

    public static boolean isGutenbergImage(String value) {
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