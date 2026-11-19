package HouseBook.company.model.Book;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "books")
@Getter
@Setter
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_book_id", unique = true, length = 255)
    private String googleBookId;

    // A Gutendex / Project Gutenberg title can be longer than the default VARCHAR(255).
    @Column(columnDefinition = "TEXT")
    private String title;

    // Authors and publisher are also stored as TEXT to avoid truncation/SQL errors.
    @Column(columnDefinition = "TEXT")
    private String authors;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String publisher;

    @Column(length = 32)
    private String isbn;

    // Image URLs returned by Gutendex / Project Gutenberg may exceed 255 characters.
    @Column(columnDefinition = "TEXT")
    private String thumbnail;

    @Column(name = "formats_json", columnDefinition = "TEXT")
    private String formatsJson;

    @Column(name = "content_url", columnDefinition = "TEXT")
    private String contentUrl;

    @Column(columnDefinition = "TEXT")
    private String language;

    @Column(columnDefinition = "TEXT")
    private String subjects;

    private Long downloadCount;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<BookChapter> chapters = new java.util.ArrayList<>();
}