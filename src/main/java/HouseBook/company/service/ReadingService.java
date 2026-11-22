package HouseBook.company.service;

import HouseBook.company.Request.ReadingProgressRequest;
import HouseBook.company.dto.ChapterResponseDTO;
import HouseBook.company.dto.ChapterSummaryDTO;
import HouseBook.company.dto.ReaderStateDTO;
import HouseBook.company.enums.ReadingStatus;
import HouseBook.company.model.Book.Book;
import HouseBook.company.model.Book.BookChapter;
import HouseBook.company.model.Book.BookChapterRepository;
import HouseBook.company.model.User.UserBook;
import HouseBook.company.model.User.UserBookRepository;
import HouseBook.company.util.GutenbergCovers;
import HouseBook.company.util.GutenbergTextProcessor;
import HouseBook.company.util.GutenbergTextProcessor.Chapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Área de leitura: prepara os capítulos de cada livro (baixando do Project Gutenberg na primeira
 * abertura), entrega o sumário e cada capítulo, e guarda onde cada usuário parou.
 */
@Service
@RequiredArgsConstructor
public class ReadingService {

    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final int LOCK_STRIPES = 32;

    private final BookChapterRepository chapterRepository;
    private final UserBookRepository userBookRepository;
    private final GutenbergClient gutenbergClient;

    // Evita baixar o mesmo livro duas vezes se dois usuários abrirem ao mesmo tempo.
    private final Object[] importLocks = createLocks();

    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    // abrir o livro

    /**
     * Abre o livro para leitura. Na primeira vez, baixa o texto do Gutenberg e o divide em capítulos.
     * Não usa transação de propósito: o download não deve segurar uma conexão do banco.
     */
    public ReaderStateDTO open(Long userBookId, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        Book book = userBook.getBook();

        ensureChapters(book);

        List<ChapterSummaryDTO> chapters = chapterRepository.findSummariesByBookId(book.getId());
        if (!chapters.isEmpty()) {
            markAsStarted(userBook);
        }
        return toState(userBook, chapters);
    }

    public List<ChapterSummaryDTO> chapters(Long userBookId, Long userId) {
        return chapterRepository.findSummariesByBookId(ownedBook(userBookId, userId).getBook().getId());
    }

    public ChapterResponseDTO chapter(Long userBookId, Integer number, Long userId) {
        Long bookId = ownedBook(userBookId, userId).getBook().getId();
        BookChapter chapter = chapterRepository.findByBookIdAndChapterNumber(bookId, number).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capítulo não encontrado."));
        return new ChapterResponseDTO(chapter.getId(), chapter.getChapterNumber(), chapter.getTitle(), chapter.getContent());
    }

    private void ensureChapters(Book book) {
        Long bookId = book.getId();
        if (chapterRepository.existsByBookId(bookId)) {
            return;
        }
        String url = book.getContentUrl();
        if (url == null || url.isBlank()) {
            return; // sem texto oficial: o leitor oferece a importação de um arquivo TXT
        }

        synchronized (importLocks[(int) (bookId % LOCK_STRIPES)]) {
            if (chapterRepository.existsByBookId(bookId)) {
                return;
            }
            List<Chapter> chapters = GutenbergTextProcessor.process(gutenbergClient.fetchText(url));
            if (chapters.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O texto baixado está vazio ou ilegível.");
            }
            chapterRepository.saveAll(toEntities(book, chapters));
        }
    }

    // importar um TXT

    @Transactional
    public ReaderStateDTO importText(Long userBookId, MultipartFile file, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        Book book = userBook.getBook();

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo TXT.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter no máximo 5 MB.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Somente arquivos .txt são aceitos.");
        }

        // O livro é compartilhado entre usuários: não deixa sobrescrever o texto oficial do Gutenberg.
        boolean hasOfficialText = book.getContentUrl() != null && !book.getContentUrl().isBlank();
        if (hasOfficialText && chapterRepository.existsByBookId(book.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este livro já tem o texto integral do Project Gutenberg.");
        }

        final String text;
        try {
            text = decode(file.getBytes());
        }
        catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo.", ex);
        }
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo está vazio.");
        }

        // O caractere de formulário (\f) continua separando capítulos manualmente.
        List<Chapter> chapters = text.indexOf('\f') >= 0 ? splitByFormFeed(text) : GutenbergTextProcessor.process(text);
        if (chapters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo não contém texto legível.");
        }

        chapterRepository.deleteAllByBookId(book.getId());
        chapterRepository.saveAll(toEntities(book, chapters));

        if (isNotStarted(userBook.getReadingStatus())) {
            userBook.setReadingStatus(ReadingStatus.READING);
        }
        if (userBook.getStartedAt() == null) {
            userBook.setStartedAt(LocalDateTime.now());
        }
        userBook.setCurrentChapter(1);
        userBook.setCurrentParagraph(0);
        userBookRepository.save(userBook);

        return toState(userBook, chapterRepository.findSummariesByBookId(book.getId()));
    }

    private static List<Chapter> splitByFormFeed(String text) {
        List<String> bodies = new ArrayList<>();
        for (String part : text.split("\\f")) {
            String body = GutenbergTextProcessor.normalizeBody(part);
            if (!body.isBlank()) {
                bodies.add(body);
            }
        }
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < bodies.size(); i++) {
            chapters.add(new Chapter(bodies.size() == 1 ? "Texto completo" : "Parte " + (i + 1), bodies.get(i)));
        }
        return chapters;
    }

    /** UTF-8 quando possível; arquivos antigos em Windows-1252 (comuns em português) como alternativa. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException ex) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    // progresso

    @Transactional
    public void updateProgress(Long userBookId, ReadingProgressRequest request, Long userId) {
        if (request == null || request.currentChapter() == null || request.currentParagraph() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o capítulo e o parágrafo atual.");
        }
        UserBook userBook = ownedBook(userBookId, userId);

        long total = chapterRepository.countByBookId(userBook.getBook().getId());
        if (total == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este livro ainda não possui conteúdo de leitura.");
        }
        if (request.currentChapter() < 1 || request.currentChapter() > total) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capítulo inválido.");
        }
        if (request.currentParagraph() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parágrafo inválido.");
        }

        userBook.setCurrentChapter(request.currentChapter());
        userBook.setCurrentParagraph(request.currentParagraph());
        if (userBook.getReadingStatus() != ReadingStatus.COMPLETED) {
            userBook.setReadingStatus(ReadingStatus.READING);
        }
        if (userBook.getStartedAt() == null) {
            userBook.setStartedAt(LocalDateTime.now());
        }
        userBookRepository.save(userBook);
    }

    @Transactional
    public void complete(Long userBookId, Long userId) {
        UserBook userBook = ownedBook(userBookId, userId);
        userBook.setReadingStatus(ReadingStatus.COMPLETED);
        userBook.setCompletedAt(LocalDateTime.now());
        userBookRepository.save(userBook);
    }

    // auxiliares

    private void markAsStarted(UserBook userBook) {
        boolean changed = false;
        if (isNotStarted(userBook.getReadingStatus())) {
            userBook.setReadingStatus(ReadingStatus.READING);
            changed = true;
        }
        if (userBook.getStartedAt() == null) {
            userBook.setStartedAt(LocalDateTime.now());
            changed = true;
        }
        if (userBook.getCurrentChapter() == null) {
            userBook.setCurrentChapter(1);
            changed = true;
        }
        if (userBook.getCurrentParagraph() == null) {
            userBook.setCurrentParagraph(0);
            changed = true;
        }
        if (changed) {
            userBookRepository.save(userBook);
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isNotStarted(ReadingStatus status) {
        return status == null || status == ReadingStatus.WANT_TO_READ || status == ReadingStatus.NAO_INICIADO;
    }

    private static ReaderStateDTO toState(UserBook userBook, List<ChapterSummaryDTO> chapters) {
        Book book = userBook.getBook();
        int total = chapters.size();
        int chapter = total == 0 ? 0 : Math.min(Math.max(valueOr(userBook.getCurrentChapter(), 1), 1), total);
        int paragraph = Math.max(valueOr(userBook.getCurrentParagraph(), 0), 0);
        boolean officialText = book.getContentUrl() != null && !book.getContentUrl().isBlank();

        return new ReaderStateDTO(
                userBook.getId(),
                book.getTitle(),
                book.getAuthors(),
                GutenbergCovers.orFallback(book.getThumbnail(), book.getGoogleBookId()),
                book.getLanguage(),
                officialText,
                chapters,
                chapter,
                paragraph,
                userBook.getReadingStatus() == null ? null : userBook.getReadingStatus().name()
        );
    }

    private static List<BookChapter> toEntities(Book book, List<Chapter> chapters) {
        List<BookChapter> entities = new ArrayList<>();
        int number = 1;
        for (Chapter chapter : chapters) {
            BookChapter entity = new BookChapter();
            entity.setBook(book);
            entity.setChapterNumber(number++);
            entity.setTitle(chapter.title());
            entity.setContent(chapter.content());
            entities.add(entity);
        }
        return entities;
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private UserBook ownedBook(Long userBookId, Long userId) {
        return userBookRepository.findByIdAndUserId(userBookId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na sua biblioteca."));
    }
}