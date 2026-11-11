package HouseBook.company.model.User;

import HouseBook.company.model.Book.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserBookRepository extends JpaRepository<UserBook, Long> {

    boolean existsByUserAndBook(User user, Book book);

    Optional<UserBook> findByIdAndUser(Long id, User user);
}