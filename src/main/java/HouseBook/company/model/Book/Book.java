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

    @Column(unique = true)
    private String googleBookId;

    private String title;

    private String authors;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String publisher;

    private String isbn;

    private String thumbnail;
}
