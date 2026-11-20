package HouseBook.company.dto;

import java.util.List;

/** Tudo que o leitor precisa para abrir um livro: sumário e posição onde o usuário parou. */
public record ReaderStateDTO(Long userBookId, String title, String authors, String thumbnail, String language, boolean officialTextAvailable, List<ChapterSummaryDTO> chapters, int currentChapter, int currentParagraph, String readingStatus) {
}