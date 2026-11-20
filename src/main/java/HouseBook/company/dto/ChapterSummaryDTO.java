package HouseBook.company.dto;

/** Item do sumário do leitor: número e título, sem o texto do capítulo. */
public record ChapterSummaryDTO(Integer chapterNumber, String title) {
}