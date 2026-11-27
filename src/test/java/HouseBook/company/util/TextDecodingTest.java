package HouseBook.company.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDecodingTest {

    private static final String TEXT = "CAPÍTULO I\n\nA ação, o coração e a emoção não tinham fim.\n";

    private static byte[] withBom(byte[] body, int first, int second) {
        byte[] result = new byte[body.length + 2];
        result[0] = (byte) first;
        result[1] = (byte) second;
        System.arraycopy(body, 0, result, 2, body.length);
        return result;
    }

    @Test
    void utf16WithBomIsDecodedWithoutNulCharacters() {
        // O "Unicode" do Bloco de Notas: antes virava texto com NUL e o PostgreSQL recusava ao salvar.
        String decoded = TextDecoding.decode(withBom(TEXT.getBytes(StandardCharsets.UTF_16LE), 0xFF, 0xFE));
        assertEquals(TEXT, decoded);
        assertFalse(decoded.contains("\u0000"));
        assertEquals(TEXT, TextDecoding.decode(withBom(TEXT.getBytes(StandardCharsets.UTF_16BE), 0xFE, 0xFF)));
    }

    @Test
    void utf16WithoutBomIsDetected() {
        assertEquals(TEXT, TextDecoding.decode(TEXT.getBytes(StandardCharsets.UTF_16LE)));
        assertEquals(TEXT, TextDecoding.decode(TEXT.getBytes(StandardCharsets.UTF_16BE)));
    }

    @Test
    void utf8AndWindows1252KeepAccents() {
        assertEquals(TEXT, TextDecoding.decode(TEXT.getBytes(StandardCharsets.UTF_8)));
        assertEquals(TEXT, TextDecoding.decode(TEXT.getBytes(Charset.forName("windows-1252"))));
    }

    @Test
    void utf8BomAndStrayNulAreRemoved() {
        assertEquals("oi", TextDecoding.decode(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'o', 'i'}));
        assertEquals("ab", TextDecoding.decode(new byte[]{'a', 0, 'b'}));
    }

    @Test
    void emptyInputGivesEmptyText() {
        assertTrue(TextDecoding.decode(null).isEmpty());
        assertTrue(TextDecoding.decode(new byte[0]).isEmpty());
    }
}