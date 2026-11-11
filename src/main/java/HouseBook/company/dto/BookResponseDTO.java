package HouseBook.company.dto;

import HouseBook.company.model.User.UserBook;

import java.time.LocalDateTime;

public record BookResponseDTO(Long userBookId, Long bookId, String googleBookId, String title, String authors, String description, String publisher, String isbn, String thumbnail, LocalDateTime acquiredAt, String readingStatus, Integer personalRating) {

    public static BookResponseDTO from(UserBook userBook) {

        var book = userBook.getBook();

        return new BookResponseDTO(userBook.getId(), book.getId(), book.getGoogleBookId(), book.getTitle(), book.getAuthors(), book.getDescription(), book.getPublisher(), book.getIsbn(), book.getThumbnail(), userBook.getAcquiredAt(), userBook.getReadingStatus(), userBook.getPersonalRating());
    }
}