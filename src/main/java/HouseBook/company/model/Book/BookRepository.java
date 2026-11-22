package HouseBook.company.model.Book;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByGoogleBookId(String googleBookId);

    /** Atualiza só a URL do texto, sem carregar nem regravar o livro inteiro. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update Book b set b.contentUrl = :url where b.id = :id")
    void updateContentUrl(@Param("id") Long id, @Param("url") String url);
}