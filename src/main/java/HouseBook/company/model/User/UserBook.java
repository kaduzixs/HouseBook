package HouseBook.company.model.User;

import HouseBook.company.model.Book.Book;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_books", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "book_id"})})
@Setter
@Getter
public class UserBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    private LocalDateTime acquiredAt;

    private String readingStatus;

    private Integer personalRating;
}
