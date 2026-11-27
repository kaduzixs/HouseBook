package HouseBook.company.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GutenbergCoversTest {

    @Test
    void triesTheCatalogCoverFirstThenTheStandardSizes() {
        String saved = "https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg";
        List<String> urls = GutenbergCovers.candidates(saved, "84");
        assertEquals(List.of(saved, "https://www.gutenberg.org/cache/epub/84/pg84.cover.small.jpg"), urls);
    }

    @Test
    void ignoresCoversFromOtherHosts() {
        List<String> urls = GutenbergCovers.candidates("https://evil.example.com/cover.jpg", "84");
        assertEquals(2, urls.size());
        assertTrue(urls.stream().allMatch(url -> url.startsWith("https://www.gutenberg.org/")));
        assertTrue(GutenbergCovers.candidates(null, "abc").isEmpty());
    }

    @Test
    void recognizesRealImagesOnly() {
        assertEquals("image/jpeg", GutenbergCovers.contentTypeOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("image/png", GutenbergCovers.contentTypeOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0, 0, 0, 0}));
        assertNull(GutenbergCovers.contentTypeOf("<html><body>403 Forbidden</body></html>".getBytes()));
    }
}