package HouseBook.company.dto;

import HouseBook.company.model.User.UserBook;
import java.time.LocalDateTime;

public record BookResponseDTO(Long userBookId, Long bookId, String googleBookId, String title, String authors, String description, String publisher, String isbn, String thumbnail, LocalDateTime acquiredAt, String readingStatus, Integer personalRating, Integer currentChapter, Integer currentParagraph, String language, String subjects, Long downloadCount, boolean contentAvailable, String formatsJson) {
    public static BookResponseDTO from(UserBook userBook) {
        var book = userBook.getBook();
        // Também considera conteúdo importado pelo leitor, mesmo sem TXT na Gutendex.
        boolean hasRemoteText = book.getContentUrl() != null && !book.getContentUrl().isBlank();
        boolean hasSavedChapters = book.getChapters() != null && !book.getChapters().isEmpty();
        return new BookResponseDTO(userBook.getId(), book.getId(), book.getGoogleBookId(), book.getTitle(), book.getAuthors(), book.getDescription(), book.getPublisher(), book.getIsbn(), book.getThumbnail(), userBook.getAcquiredAt(), userBook.getReadingStatus() == null ? null : userBook.getReadingStatus().name(), userBook.getPersonalRating(), userBook.getCurrentChapter(), userBook.getCurrentParagraph(), book.getLanguage(), book.getSubjects(), book.getDownloadCount(), hasRemoteText || hasSavedChapters, book.getFormatsJson());
    }
}