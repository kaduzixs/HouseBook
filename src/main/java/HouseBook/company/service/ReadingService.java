package HouseBook.company.service;

import HouseBook.company.dto.ChapterResponseDTO;
import HouseBook.company.Request.ReadingProgressRequest;
import HouseBook.company.enums.ReadingStatus;
import HouseBook.company.model.Book.BookChapter;
import HouseBook.company.model.Book.BookChapterRepository;
import HouseBook.company.model.User.User;
import HouseBook.company.model.User.UserBook;
import HouseBook.company.model.User.UserBookRepository;
import HouseBook.company.model.User.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReadingService {
    private static final long MAX_TEXT_BYTES = 5 * 1024 * 1024;
    private final BookChapterRepository chapterRepository;
    private final UserBookRepository userBookRepository;
    private final UserRepository userRepository;

    // Marca a primeira abertura do leitor como início da leitura, inclusive para TXT remoto.
    @Transactional
    public void startReading(Long userBookId, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        if (userBook.getReadingStatus() != ReadingStatus.COMPLETED) {
            userBook.setReadingStatus(ReadingStatus.READING);
        }
        if (userBook.getStartedAt() == null) {
            userBook.setStartedAt(LocalDateTime.now());
        }
        userBookRepository.save(userBook);
    }

    @Transactional(readOnly = true)
    public List<ChapterResponseDTO> getChapters(Long userBookId, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        return chapterRepository.findAllByBookIdOrderByChapterNumberAsc(userBook.getBook().getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ChapterResponseDTO getChapter(Long userBookId, Integer number, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        BookChapter chapter = chapterRepository.findByBookIdAndChapterNumber(userBook.getBook().getId(), number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capítulo não encontrado."));
        return toDto(chapter);
    }

    @Transactional
    public List<ChapterResponseDTO> importText(Long userBookId, MultipartFile file, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo TXT.");
        if (file.getSize() > MAX_TEXT_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter no máximo 5 MB.");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Somente arquivos .txt são aceitos.");
        final String text;
        try { text = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").trim(); }
        catch (IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo.", ex); }
        if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo está vazio.");

        // Um arquivo simples vira um capítulo; o caractere de formulário (\f) separa capítulos.
        String[] parts = text.split("\\f");

        // Remove os capítulos antigos e executa o DELETE antes dos INSERTs, evitando conflito
        // com a chave única (book_id, chapter_number) ao substituir o conteúdo.
        chapterRepository.deleteAllByBookId(userBook.getBook().getId());

        List<BookChapter> chapters = new ArrayList<>();
        int chapterNumber = 1;
        for (String part : parts) {
            String content = part.trim();
            if (content.isBlank()) continue;

            BookChapter chapter = new BookChapter();
            chapter.setBook(userBook.getBook());
            chapter.setChapterNumber(chapterNumber);
            chapter.setTitle(parts.length == 1 ? "Texto completo" : "Parte " + chapterNumber);
            chapter.setContent(content);
            chapters.add(chapter);
            chapterNumber++;
        }

        if (chapters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo não contém capítulos válidos.");
        }

        // saveAllAndFlush faz o banco validar os INSERTs ainda dentro deste método.
        List<ChapterResponseDTO> saved = chapterRepository.saveAllAndFlush(chapters)
                .stream().map(this::toDto).toList();
        if (userBook.getReadingStatus() == null || userBook.getReadingStatus() == ReadingStatus.WANT_TO_READ) userBook.setReadingStatus(ReadingStatus.READING);
        if (userBook.getStartedAt() == null) userBook.setStartedAt(LocalDateTime.now());
        userBook.setCurrentChapter(1);
        userBook.setCurrentParagraph(0);
        userBookRepository.save(userBook);
        return saved;
    }

    @Transactional
    public void updateProgress(Long userBookId, ReadingProgressRequest request, Long userId) {
        if (request == null || request.currentChapter() == null || request.currentParagraph() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o capítulo e o parágrafo atual.");
        }
        UserBook userBook = ownedBook(userBookId, userId);
        long total = chapterRepository.findAllByBookIdOrderByChapterNumberAsc(userBook.getBook().getId()).size();
        if (total == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este livro ainda não possui conteúdo de leitura.");
        if (request.currentChapter() < 1 || request.currentChapter() > total) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capítulo inválido.");
        }
        if (request.currentParagraph() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parágrafo inválido.");
        }
        userBook.setCurrentChapter(request.currentChapter());
        userBook.setCurrentParagraph(request.currentParagraph());
        if (userBook.getReadingStatus() != ReadingStatus.COMPLETED) userBook.setReadingStatus(ReadingStatus.READING);
        if (userBook.getStartedAt() == null) userBook.setStartedAt(LocalDateTime.now());
        userBookRepository.save(userBook);
    }

    @Transactional
    public void complete(Long userBookId, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        userBook.setReadingStatus(ReadingStatus.COMPLETED);
        userBook.setCompletedAt(LocalDateTime.now());
        userBookRepository.save(userBook);
    }

    private UserBook ownedBook(Long userBookId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
        return userBookRepository.findByIdAndUser(userBookId, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na sua biblioteca."));
    }
    private ChapterResponseDTO toDto(BookChapter c) { return new ChapterResponseDTO(c.getId(), c.getChapterNumber(), c.getTitle(), c.getContent()); }
}