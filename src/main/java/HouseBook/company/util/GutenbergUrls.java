package HouseBook.company.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Endereços públicos de texto do Project Gutenberg. */
public final class GutenbergUrls {

    private GutenbergUrls() {
    }

    /** Texto puro em UTF-8 que o Gutenberg gera para praticamente todo livro em texto (não existe para áudio/partituras). */
    public static String canonicalText(String gutenbergId) {
        if (gutenbergId == null || !gutenbergId.matches("\\d+")) {
            return "";
        }
        return "https://www.gutenberg.org/cache/epub/" + gutenbergId + "/pg" + gutenbergId + ".txt";
    }

    /** Alternativa ao endereço canônico (redireciona para o mesmo arquivo). */
    public static String ebookText(String gutenbergId) {
        if (gutenbergId == null || !gutenbergId.matches("\\d+")) {
            return "";
        }
        return "https://www.gutenberg.org/ebooks/" + gutenbergId + ".txt.utf-8";
    }

    /** Endereços a tentar, em ordem, sem repetições: o salvo no livro e depois os padrões do Gutenberg. */
    public static List<String> candidates(String savedUrl, String gutenbergId) {
        List<String> urls = new ArrayList<>();
        addIfUseful(urls, savedUrl);
        addIfUseful(urls, canonicalText(gutenbergId));
        addIfUseful(urls, ebookText(gutenbergId));
        return urls;
    }

    /** Arquivos .zip não servem: o leitor espera texto puro. */
    public static boolean isUsablePlainText(String url) {
        return url != null && !url.isBlank() && !url.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static void addIfUseful(List<String> urls, String url) {
        if (isUsablePlainText(url) && !urls.contains(url.trim())) {
            urls.add(url.trim());
        }
    }
}