package HouseBook.company.model.Book;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookChapterRepository extends JpaRepository<BookChapter, Long> {
    List<BookChapter> findAllByBookIdOrderByChapterNumberAsc(Long bookId);
    Optional<BookChapter> findByBookIdAndChapterNumber(Long bookId, Integer chapterNumber);
    void deleteAllByBookId(Long bookId);
}