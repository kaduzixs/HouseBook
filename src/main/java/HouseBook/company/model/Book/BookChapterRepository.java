package HouseBook.company.model.Book;

import HouseBook.company.dto.ChapterSummaryDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BookChapterRepository extends JpaRepository<BookChapter, Long> {

    /** Sumário do livro sem carregar o texto dos capítulos. */
    @Query("select new HouseBook.company.dto.ChapterSummaryDTO(c.chapterNumber, c.title) " + "from BookChapter c where c.book.id = :bookId order by c.chapterNumber asc")
    List<ChapterSummaryDTO> findSummariesByBookId(@Param("bookId") Long bookId);

    Optional<BookChapter> findByBookIdAndChapterNumber(Long bookId, Integer chapterNumber);

    boolean existsByBookId(Long bookId);

    long countByBookId(Long bookId);

    /**
     * DELETE em massa, executado na hora. Um deleteBy derivado só apaga no flush, depois dos INSERTs,
     * o que viola a chave única (book_id, chapter_number) ao substituir o conteúdo de um livro.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from BookChapter c where c.book.id = :bookId")
    void deleteAllByBookId(@Param("bookId") Long bookId);
}