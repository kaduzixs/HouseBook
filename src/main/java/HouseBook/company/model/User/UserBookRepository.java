package HouseBook.company.model.User;

import HouseBook.company.model.Book.Book;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBookRepository extends JpaRepository<UserBook, Long> {

    boolean existsByUserAndBook(User user, Book book);

    Optional<UserBook> findByIdAndUser(Long id, User user);

    /** Já traz o livro junto (evita LazyInitializationException e consultas extras). */
    @EntityGraph(attributePaths = "book")
    Optional<UserBook> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "book")
    List<UserBook> findAllByUserOrderByAcquiredAtDesc(User user);
}