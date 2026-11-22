package HouseBook.company.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o texto bruto de um livro (normalmente do Project Gutenberg) em capítulos prontos
 * para leitura: remove o cabeçalho/rodapé de licença, detecta títulos de capítulo, junta as
 * linhas quebradas de cada parágrafo e garante que nenhum capítulo fique grande demais.
 *
 * Classe sem dependências de Spring, para ser fácil de testar.
 */
public final class GutenbergTextProcessor {

    /** Um capítulo já limpo; o conteúdo usa uma linha em branco entre parágrafos. */
    public record Chapter(String title, String content) {
    }

    public static final int MAX_TITLE_LENGTH = 150;

    private static final int MIN_SECTION_CHARS = 300;
    private static final int MAX_CHAPTER_CHARS = 45_000;
    private static final int FALLBACK_CHUNK_CHARS = 12_000;
    private static final int MAX_HEADING_LINE = 70;
    private static final int VERSE_LINE_LIMIT = 55;
    private static final int TOC_LOOKAHEAD_LINES = 15;

    private static final Pattern START_MARKER = Pattern.compile("\\*{3}\\s*START OF[^\\n]*?PROJECT GUTENBERG[^\\n]*\\n?", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_MARKER = Pattern.compile("\\*{3}\\s*END OF[^\\n]*?PROJECT GUTENBERG", Pattern.CASE_INSENSITIVE);

    // Numeral romano válido e não vazio (evita confundir palavras como "CIVIL" com numerais).
    private static final String ROMAN = "(?=[MDCLXVI])M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})";

    private static final String KEYWORDS = "(?iu:chapter|chapitre|cap[ií]tulo|capitolo|kapitel|book|livro|libro|livre|part|parte|partie" + "|teil|act|ato|acto|acte|stave|canto|section|se[cç][aã]o)";

    private static final String NUMBER_WORDS = "(?iu:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen" + "|sixteen|seventeen|eighteen|nineteen|twenty(?:-\\w+)?|thirty(?:-\\w+)?|first|second|third" + "|fourth|fifth|sixth|seventh|eighth|ninth|tenth|um|dois|tr[eê]s|quatro|cinco|seis|sete|oito" + "|nove|dez|primeiro|primeira|segundo|segunda|terceiro|terceira|quarto|quinto|sexto" + "|s[eé]timo|oitavo|nono|d[eé]cimo|uno|dos|cuatro|siete|ocho|nueve|diez|primero|tercero" + "|premier|premi[eè]re|deux|trois|quatre)";

    // "CHAPTER I.", "Capítulo 3 — O encontro", "BOOK ONE"...
    private static final Pattern KEYWORD_HEADING = Pattern.compile("^(?:" + KEYWORDS + ")\\s+(?:" + ROMAN + "|\\d{1,3}|" + NUMBER_WORDS + ")" + "(?:\\s*[.:\\-\\u2014\\u2013]\\s*\\S.{0,60}|\\s*\\.?)$");

    // "I. A SCANDAL IN BOHEMIA", "XII.", "7"
    private static final Pattern NUMERAL_HEADING = Pattern.compile("^(?:" + ROMAN + "|\\d{1,3})(?:\\.\\s*\\S.{0,60}|\\.?)$");
    private static final Pattern ROMAN_WORD = Pattern.compile(ROMAN);
    private static final Set<String> SMALL_WORDS = Set.of("a", "an", "the", "of", "in", "on", "at", "to", "and", "or", "for", "de", "da", "do", "das", "dos", "e", "em", "no", "na", "nos", "nas", "o", "os", "as", "um", "uma", "el", "la", "los", "las", "del", "y");
    private static final Pattern ILLUSTRATION = Pattern.compile("^\\[(?:illustration|ilustra[cç][aã]o|illustrazione)[^\\]]*]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDERSCORE_ITALIC = Pattern.compile("(?<![\\w])_([^_\\n]+?)_(?![\\w])");

    private record Heading(int line, int bodyStart, String title) {
    }

    private GutenbergTextProcessor() {
    }

    /** Texto completo de um livro → capítulos. Lista vazia se não houver conteúdo. */
    public static List<Chapter> process(String raw) {
        if (raw == null) {
            return List.of();
        }
        String text = normalizeNewlines(stripBoilerplate(raw));
        if (text.isBlank()) {
            return List.of();
        }

        String[] lines = text.split("\n", -1);
        List<Chapter> chapters = splitByHeadings(lines, KEYWORD_HEADING, 2);
        if (chapters.isEmpty()) {
            chapters = splitByHeadings(lines, NUMERAL_HEADING, 3);
        }
        if (chapters.isEmpty()) {
            chapters = splitBySize(text);
        }
        return limitChapterSize(chapters);
    }

    /** Limpa um trecho de texto e devolve os parágrafos separados por uma linha em branco. */
    public static String normalizeBody(String body) {
        return String.join("\n\n", paragraphs(normalizeNewlines(body)));
    }

    //  limpeza

    private static String stripBoilerplate(String raw) {
        Matcher start = START_MARKER.matcher(raw);
        int from = start.find() ? start.end() : 0;
        Matcher end = END_MARKER.matcher(raw);
        int to = end.find(from) ? end.start() : raw.length();
        // O marcador de fim fica no começo de uma linha; descarta a linha "*** END OF ..." inteira.
        return raw.substring(from, to);
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').replace('\u00A0', ' ').strip();
    }

    //  por capítulos

    private static List<Chapter> splitByHeadings(String[] lines, Pattern pattern, int minHeadings) {
        List<Heading> candidates = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Heading heading = detectHeading(lines, i, pattern);
            if (heading != null) {
                candidates.add(heading);
                i = heading.bodyStart() - 1;
            }
        }

        // 1) Descarta entradas de sumário: vários títulos seguidos em poucas linhas.
        List<Heading> afterToc = new ArrayList<>();
        for (Heading heading : candidates) {
            if (!looksLikeTocEntry(lines, heading, pattern)) {
                afterToc.add(heading);
            }
        }

        // 2) Descarta títulos sem corpo de verdade (sumários restantes, títulos soltos).
        List<Heading> kept = new ArrayList<>();
        for (int k = 0; k < afterToc.size(); k++) {
            int end = k + 1 < afterToc.size() ? afterToc.get(k + 1).line() : lines.length;
            if (visibleChars(lines, afterToc.get(k).bodyStart(), end) >= MIN_SECTION_CHARS) {
                kept.add(afterToc.get(k));
            }
        }
        if (kept.size() < minHeadings) {
            return List.of();
        }

        List<Chapter> chapters = new ArrayList<>();
        if (visibleChars(lines, 0, kept.get(0).line()) >= MIN_SECTION_CHARS) {
            chapters.add(new Chapter("Abertura", normalizeBody(join(lines, 0, kept.get(0).line()))));
        }
        for (int k = 0; k < kept.size(); k++) {
            Heading heading = kept.get(k);
            int end = k + 1 < kept.size() ? kept.get(k + 1).line() : lines.length;
            String body = normalizeBody(join(lines, heading.bodyStart(), end));
            if (!body.isBlank()) {
                chapters.add(new Chapter(formatTitle(heading.title()), body));
            }
        }
        return chapters;
    }

    private static Heading detectHeading(String[] lines, int i, Pattern pattern) {
        String line = lines[i].strip();
        boolean previousBlank = i == 0 || lines[i - 1].isBlank();
        if (line.isEmpty() || line.length() > MAX_HEADING_LINE || !previousBlank) {
            return null;
        }
        if (!pattern.matcher(line).matches()) {
            return null;
        }
        if (i + 1 >= lines.length || lines[i + 1].isBlank()) {
            return new Heading(i, i + 1, line);
        }
        // Subtítulo na linha seguinte ("CHAPTER I.\nDown the Rabbit-Hole"), seguido de linha em branco.
        String next = lines[i + 1].strip();
        boolean subtitle = next.length() <= MAX_HEADING_LINE && (i + 2 >= lines.length || lines[i + 2].isBlank());
        return subtitle ? new Heading(i, i + 2, line + " " + next) : null;
    }

    private static boolean looksLikeTocEntry(String[] lines, Heading heading, Pattern pattern) {
        int matches = 0;
        int limit = Math.min(lines.length, heading.bodyStart() + TOC_LOOKAHEAD_LINES);
        for (int j = heading.bodyStart(); j < limit; j++) {
            String candidate = lines[j].strip();
            if (!candidate.isEmpty() && candidate.length() <= MAX_HEADING_LINE && pattern.matcher(candidate).matches()) {
                matches++;
            }
        }
        return matches >= 2;
    }

    //  por tamanho

    private static List<Chapter> splitBySize(String text) {
        List<String> paragraphs = paragraphs(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() >= FALLBACK_CHUNK_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        // Evita um último capítulo minúsculo: junta-o ao anterior.
        if (chunks.size() > 1 && chunks.get(chunks.size() - 1).length() < MIN_SECTION_CHARS) {
            String last = chunks.remove(chunks.size() - 1);
            chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + "\n\n" + last);
        }

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            chapters.add(new Chapter(chunks.size() == 1 ? "Texto completo" : "Parte " + (i + 1), chunks.get(i)));
        }
        return chapters;
    }

    private static List<Chapter> limitChapterSize(List<Chapter> chapters) {
        List<Chapter> result = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (chapter.content().length() <= MAX_CHAPTER_CHARS) {
                result.add(chapter);
                continue;
            }

            List<String> pieces = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String paragraph : chapter.content().split("\n\n")) {
                for (String part : splitLongParagraph(paragraph)) {
                    if (current.length() > 0 && current.length() + part.length() + 2 > MAX_CHAPTER_CHARS) {
                        pieces.add(current.toString());
                        current.setLength(0);
                    }
                    if (current.length() > 0) {
                        current.append("\n\n");
                    }
                    current.append(part);
                }
            }
            if (current.length() > 0) {
                pieces.add(current.toString());
            }

            for (int i = 0; i < pieces.size(); i++) {
                String suffix = " (" + (i + 1) + "/" + pieces.size() + ")";
                result.add(new Chapter(truncate(chapter.title(), MAX_TITLE_LENGTH - suffix.length()) + suffix, pieces.get(i)));
            }
        }
        return result;
    }

    private static List<String> splitLongParagraph(String paragraph) {
        List<String> parts = new ArrayList<>();
        String rest = paragraph;
        while (rest.length() > MAX_CHAPTER_CHARS / 2) {
            int cut = rest.lastIndexOf(' ', MAX_CHAPTER_CHARS / 2);
            if (cut < 1) {
                cut = MAX_CHAPTER_CHARS / 2;
            }
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    //  parágrafos

    private static List<String> paragraphs(String text) {
        List<String> result = new ArrayList<>();
        for (String block : text.split("\\n\\s*\\n")) {
            String trimmed = block.strip();
            if (trimmed.isEmpty() || ILLUSTRATION.matcher(trimmed).matches()) {
                continue;
            }
            String[] lines = trimmed.split("\n");
            boolean verse = lines.length > 1 && Arrays.stream(lines).allMatch(l -> l.strip().length() <= VERSE_LINE_LIMIT);
            String paragraph;
            if (verse) {
                paragraph = Arrays.stream(lines).map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
            } else {
                paragraph = trimmed.replaceAll("\\s*\\n\\s*", " ").replaceAll(" {2,}", " ");
                paragraph = UNDERSCORE_ITALIC.matcher(paragraph).replaceAll("$1");
            }
            if (!paragraph.isBlank()) {
                result.add(paragraph);
            }
        }
        return result;
    }

    //  títulos

    private static String formatTitle(String raw) {
        String title = raw.strip().replaceAll("\\s+", " ");
        title = title.replaceAll("[.\\s]+$", "");
        title = toTitleCase(title);
        return truncate(title.isBlank() ? raw.strip() : title, MAX_TITLE_LENGTH);
    }

    /**
     * Títulos do Gutenberg costumam vir em CAIXA ALTA. Em um título todo maiúsculo, todas as
     * palavras são ajustadas; em um título misto ("CHAPTER I. Down the Rabbit-Hole") só a
     * primeira palavra, preservando siglas e nomes. Numerais romanos são mantidos.
     */
    private static String toTitleCase(String title) {
        boolean mixed = title.chars().anyMatch(Character::isLowerCase);
        String[] words = title.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String core = word.replaceAll("[.,:;!?]+$", "");
            boolean hasLetters = word.chars().anyMatch(Character::isLetter);
            boolean allCaps = word.equals(word.toUpperCase(Locale.ROOT));
            boolean convert = hasLetters && allCaps && (i == 0 || !mixed) && !ROMAN_WORD.matcher(core).matches();

            // A primeira palavra do título de verdade (logo após "I." ou "Chapter 3:") fica maiúscula.
            boolean startsTitle = i == 0 || words[i - 1].matches(".*[.:]$");

            if (out.length() > 0) {
                out.append(' ');
            }
            if (!convert) {
                out.append(word);
            } else if (!startsTitle && SMALL_WORDS.contains(core.toLowerCase(Locale.ROOT))) {
                out.append(word.toLowerCase(Locale.ROOT));
            } else {
                out.append(capitalizeWord(word));
            }
        }
        return out.toString();
    }

    private static String capitalizeWord(String word) {
        StringBuilder out = new StringBuilder();
        boolean startOfPart = true;
        for (char c : word.toCharArray()) {
            if (Character.isLetter(c)) {
                out.append(startOfPart ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startOfPart = false;
            } else {
                out.append(c);
                startOfPart = c == '-' || c == '(' || c == '"' || c == '\u201C';
            }
        }
        return out.toString();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int cut = value.lastIndexOf(' ', max - 1);
        return value.substring(0, cut > max / 2 ? cut : max - 1).strip() + "\u2026";
    }

    // utilitários

    private static String join(String[] lines, int from, int to) {
        return String.join("\n", Arrays.copyOfRange(lines, from, Math.max(from, to)));
    }

    private static int visibleChars(String[] lines, int from, int to) {
        int count = 0;
        for (int i = from; i < to && i < lines.length; i++) {
            count += lines[i].strip().length();
        }
        return count;
    }
}