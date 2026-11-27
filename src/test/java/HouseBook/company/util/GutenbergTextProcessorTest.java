package HouseBook.company.util;

import HouseBook.company.util.GutenbergTextProcessor.Chapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GutenbergTextProcessorTest {

    private static final String[] ROMANS = {"I", "II", "III", "IV"};

    private static String wrap(String text, int width) {
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width) {
                out.append(line).append('\n');
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        return out.append(line).toString();
    }

    private static String paragraph(int sentences) {
        String sentence = "Alice was beginning to get very tired of sitting by her sister on the bank, ";
        return wrap((sentence.repeat(sentences)).trim() + ".", 70);
    }

    private static String header() {
        return "The Project Gutenberg eBook of Test\n\n*** START OF THE PROJECT GUTENBERG EBOOK TEST ***\n\n";
    }

    private static String footer() {
        return "\n\n*** END OF THE PROJECT GUTENBERG EBOOK TEST ***\n\nLicense text that must vanish.\n";
    }

    @Test
    void ignoresTableOfContentsAndLicenseAndSplitsChapters() {
        StringBuilder book = new StringBuilder(header());
        book.append("CONTENTS\n\nCHAPTER I.    Down the Rabbit-Hole\nCHAPTER II.   The Pool of Tears\n").append("CHAPTER III.  A Caucus-Race\nCHAPTER IV.   The Rabbit\n\n\n");
        String[] names = {"Down the Rabbit-Hole", "The Pool of Tears", "A Caucus-Race", "The Rabbit"};
        for (int i = 0; i < 4; i++) {
            book.append("CHAPTER ").append(ROMANS[i]).append(".\n").append(names[i]).append("\n\n").append(paragraph(6)).append("\n\n").append(paragraph(4)).append("\n\n\n");
        }
        book.append(footer());

        List<Chapter> chapters = GutenbergTextProcessor.process(book.toString());

        assertEquals(4, chapters.size());
        assertEquals("Chapter I. Down the Rabbit-Hole", chapters.get(0).title());
        assertEquals(2, chapters.get(0).content().split("\n\n").length);
        assertTrue(chapters.stream().noneMatch(c -> c.content().contains("License text")));
        assertTrue(chapters.stream().noneMatch(c -> c.content().contains("START OF")));
    }

    @Test
    void detectsRomanNumeralHeadingsAndFixesCapitalization() {
        StringBuilder book = new StringBuilder(header());
        String[] titles = {"A SCANDAL IN BOHEMIA", "THE RED-HEADED LEAGUE", "A CASE OF IDENTITY"};
        for (int i = 0; i < 3; i++) {
            book.append(ROMANS[i]).append(". ").append(titles[i]).append("\n\n").append(paragraph(8)).append("\n\n\n");
        }

        List<Chapter> chapters = GutenbergTextProcessor.process(book.append(footer()).toString());

        assertEquals(3, chapters.size());
        assertEquals("I. A Scandal in Bohemia", chapters.get(0).title());
        assertEquals("III. A Case of Identity", chapters.get(2).title());
    }

    @Test
    void booksWithoutChaptersAreSplitBySizeAtParagraphBoundaries() {
        StringBuilder book = new StringBuilder(header());
        for (int i = 0; i < 120; i++) {
            book.append(paragraph(5)).append("\n\n");
        }

        List<Chapter> chapters = GutenbergTextProcessor.process(book.append(footer()).toString());

        assertTrue(chapters.size() > 2);
        assertEquals("Parte 1", chapters.get(0).title());
        assertTrue(chapters.stream().allMatch(c -> c.content().startsWith("Alice")));
        assertTrue(chapters.stream().allMatch(c -> c.content().length() < 45_000));
    }

    @Test
    void hugeChaptersAreSplitAndKeepTitlesWithinTheColumnLimit() {
        StringBuilder book = new StringBuilder(header());
        for (int i = 0; i < 2; i++) {
            book.append("CHAPTER ").append(ROMANS[i]).append(". Big\n\n");
            for (int k = 0; k < 130; k++) {
                book.append(paragraph(6)).append("\n\n");
            }
        }

        List<Chapter> chapters = GutenbergTextProcessor.process(book.append(footer()).toString());

        assertTrue(chapters.size() > 2);
        assertTrue(chapters.stream().allMatch(c -> c.content().length() <= 45_000));
        assertTrue(chapters.stream().allMatch(c -> c.title().length() <= GutenbergTextProcessor.MAX_TITLE_LENGTH));
    }

    @Test
    void sentencesStartingWithPartOrBookAreNotChapters() {
        String book = header() + paragraph(8) + "\n\nPart of the way home\nhe ran.\n\n" + paragraph(8) + "\n\nBook shelves were everywhere in the room and she stared.\n\n" + paragraph(8) + footer();

        List<Chapter> chapters = GutenbergTextProcessor.process(book);

        assertEquals(1, chapters.size());
        assertEquals("Texto completo", chapters.get(0).title());
    }

    @Test
    void understandsPortugueseHeadings() {
        StringBuilder book = new StringBuilder(header());
        for (int i = 0; i < 3; i++) {
            book.append("CAPÍTULO ").append(ROMANS[i]).append("\n\n").append(paragraph(7)).append("\n\n");
        }

        List<Chapter> chapters = GutenbergTextProcessor.process(book.append(footer()).toString());

        assertEquals(3, chapters.size());
        assertEquals("Capítulo II", chapters.get(1).title());
    }

    @Test
    void keepsLineBreaksOfVerseAndStripsItalicMarkersAndIllustrations() {
        List<Chapter> verse = GutenbergTextProcessor.process("Roses are red,\nViolets are blue,\nSugar is sweet,\nAnd so are you.");
        assertTrue(verse.get(0).content().startsWith("Roses are red,\nViolets are blue"));

        List<Chapter> prose = GutenbergTextProcessor.process("She said _hello_ to him.\n\n[Illustration: a rabbit]\n\nThe end of it.");
        assertEquals("She said hello to him.\n\nThe end of it.", prose.get(0).content());
    }

    @Test
    void emptyInputProducesNoChapters() {
        assertTrue(GutenbergTextProcessor.process(null).isEmpty());
        assertTrue(GutenbergTextProcessor.process("  \n ").isEmpty());
        assertFalse(GutenbergTextProcessor.process("Um texto curtinho.").isEmpty());
    }

    @Test
    void nulCharactersNeverReachTheChapters() {
        StringBuilder book = new StringBuilder(header());
        for (int i = 0; i < 3; i++) {
            book.append("CHAPTER ").append(ROMANS[i]).append(".\n\n").append(paragraph(6)).append("\u0000\n\n\n");
        }
        List<Chapter> chapters = GutenbergTextProcessor.process(book.append(footer()).toString());

        assertFalse(chapters.isEmpty());
        assertTrue(chapters.stream().noneMatch(c -> c.content().indexOf('\u0000') >= 0 || c.title().indexOf('\u0000') >= 0));
    }
}