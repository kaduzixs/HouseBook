package HouseBook.company.util;

/** Capas do Project Gutenberg: usa a URL da Gutendex e, na falta dela, o padrão público do site. */
public final class GutenbergCovers {

    private GutenbergCovers() {
    }

    /** URL padrão da capa de um livro do Gutenberg, ou texto vazio se o id não for numérico. */
    public static String urlFor(String gutenbergId) {
        if (gutenbergId == null || !gutenbergId.matches("\\d+")) {
            return "";
        }
        return "https://www.gutenberg.org/cache/epub/" + gutenbergId + "/pg" + gutenbergId + ".cover.medium.jpg";
    }

    /** Devolve a capa informada ou, se estiver vazia, a URL padrão do Gutenberg. */
    public static String orFallback(String thumbnail, String gutenbergId) {
        return thumbnail != null && !thumbnail.isBlank() ? thumbnail : urlFor(gutenbergId);
    }
}