package HouseBook.company.model.Book;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByGoogleBookId(String googleBookId);
}