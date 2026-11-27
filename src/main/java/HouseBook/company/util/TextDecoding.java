package HouseBook.company.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Converte os bytes de um arquivo de texto em String sem deixar lixo para trás.
 *
 * Cobre os casos que quebravam a importação:
 * - UTF-16 (o "Unicode" do Bloco de Notas), com ou sem BOM: lido como UTF-8 dava um texto cheio de
 *   caracteres NUL, que o PostgreSQL recusa em colunas TEXT (erro 500 ao salvar os capítulos);
 * - UTF-8 com BOM;
 * - arquivos antigos em Windows-1252/ISO-8859-1 (comuns em português).
 *
 * Classe sem dependências de Spring, para ser fácil de testar.
 */
public final class TextDecoding {

    private static final int SAMPLE_BYTES = 4096;
    private static final double MOSTLY_ZEROS = 0.30;
    private static final double RARELY_ZEROS = 0.05;

    private TextDecoding() {
    }

    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        String text;
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            text = utf8OrWindows1252(bytes, 3);
        }
        else if (startsWith(bytes, 0xFF, 0xFE)) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        else if (startsWith(bytes, 0xFE, 0xFF)) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        else {
            Charset utf16 = guessUtf16WithoutBom(bytes);
            text = utf16 != null ? new String(bytes, utf16) : utf8OrWindows1252(bytes, 0);
        }
        return clean(text);
    }

    /** Remove o que o banco não aceita (NUL) e um eventual BOM que tenha sobrado. */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String result = text.indexOf('\u0000') >= 0 ? text.replace("\u0000", "") : text;
        return result.startsWith("\uFEFF") ? result.substring(1) : result;
    }

    private static String utf8OrWindows1252(byte[] bytes, int offset) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        }
        catch (CharacterCodingException ex) {
            return new String(bytes, offset, bytes.length - offset, Charset.forName("windows-1252"));
        }
    }

    /**
     * UTF-16 sem BOM: em texto latino, um dos dois bytes de cada par é quase sempre zero.
     * Zeros nas posições ímpares = little-endian; nas pares = big-endian.
     */
    private static Charset guessUtf16WithoutBom(byte[] bytes) {
        int length = Math.min(bytes.length, SAMPLE_BYTES) & ~1;
        if (length < 4) {
            return null;
        }
        int evenZeros = 0;
        int oddZeros = 0;
        for (int i = 0; i < length; i += 2) {
            if (bytes[i] == 0) {
                evenZeros++;
            }
            if (bytes[i + 1] == 0) {
                oddZeros++;
            }
        }
        int pairs = length / 2;
        if (oddZeros > pairs * MOSTLY_ZEROS && evenZeros < pairs * RARELY_ZEROS) {
            return StandardCharsets.UTF_16LE;
        }
        if (evenZeros > pairs * MOSTLY_ZEROS && oddZeros < pairs * RARELY_ZEROS) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}