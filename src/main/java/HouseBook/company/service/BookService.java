package HouseBook.company.service;

import HouseBook.company.dto.BookResponseDTO;
import HouseBook.company.enums.ReadingStatus;
import HouseBook.company.model.Book.Book;
import HouseBook.company.model.Book.BookRepository;
import HouseBook.company.model.User.User;
import HouseBook.company.model.User.UserBook;
import HouseBook.company.model.User.UserBookRepository;
import HouseBook.company.model.User.UserRepository;
import HouseBook.company.util.GutenbergCovers;
import HouseBook.company.util.GutenbergUrls;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private static final int GUTENDEX_ATTEMPTS = 2;
    private static final long GUTENDEX_RETRY_PAUSE_MS = 800;

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserBookRepository userBookRepository;

    // Cliente HTTP com limites de conexão e leitura para evitar requisições presas no Render.
    private final RestClient restClient = createGutendexClient();

    private static RestClient createGutendexClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl("https://gutendex.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "HouseBook/1.0 (leitor de livros; Project Gutenberg)")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    //  biblioteca

    @Transactional(readOnly = true)
    public List<BookResponseDTO> getUserBooks(Long userId) {
        User user = findUser(userId);
        return userBookRepository.findAllByUserOrderByAcquiredAtDesc(user).stream().map(BookResponseDTO::from).toList();
    }

    @Transactional
    public BookResponseDTO acquireBook(String bookId, Long userId) {
        if (bookId == null || !bookId.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador Gutendex inválido.");
        }
        User user = findUser(userId);
        Book book = bookRepository.findByGoogleBookId(bookId).orElseGet(() -> saveBookFromGutendex(bookId));

        if (userBookRepository.existsByUserAndBook(user, book)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este livro já está na sua biblioteca.");
        }

        UserBook userBook = new UserBook();
        userBook.setUser(user);
        userBook.setBook(book);
        userBook.setAcquiredAt(LocalDateTime.now());
        userBook.setReadingStatus(ReadingStatus.WANT_TO_READ);
        return BookResponseDTO.from(userBookRepository.saveAndFlush(userBook));
    }

    @Transactional
    public BookResponseDTO addToHistory(Long userBookId, Long userId) {
        UserBook userBook = findUserBook(userBookId, findUser(userId));
        userBook.setReadingStatus(ReadingStatus.COMPLETED);
        userBook.setCompletedAt(LocalDateTime.now());
        return BookResponseDTO.from(userBookRepository.save(userBook));
    }

    @Transactional
    public void removeBook(Long userBookId, Long userId) {
        userBookRepository.delete(findUserBook(userBookId, findUser(userId)));
    }

    // catálogo

    public JsonNode searchBooks(String query, String language, String topic, String mimeType) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe título, autor ou assunto.");
        }

        try {
            return fetchSearch(query, language, topic, mimeType);
        }
        catch (RestClientResponseException ex) {
            log.error("Gutendex respondeu HTTP {} ao pesquisar livros.", ex.getStatusCode().value(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex respondeu com HTTP " + ex.getStatusCode().value() + ". Tente novamente mais tarde.", ex);
        }
        catch (RestClientException ex) {
            log.error("Falha de conexão ao consultar a Gutendex.", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O servidor não conseguiu se conectar à Gutendex. Verifique os logs do Render.", ex);
        }
    }

    public JsonNode getNewBooks() {
        try {
            JsonNode response = withRetry(() -> restClient.get().uri("/books/").retrieve().body(JsonNode.class)); // a Gutendex já ordena por popularidade

            if (response == null || !response.path("results").isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
            }
            return response;
        }
        catch (RestClientResponseException ex) {
            log.error("Gutendex respondeu HTTP {} ao carregar a seleção inicial.", ex.getStatusCode().value(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex respondeu com HTTP " + ex.getStatusCode().value() + ". Tente novamente mais tarde.", ex);
        }
        catch (RestClientException ex) {
            log.error("Falha de conexão ao carregar livros populares da Gutendex.", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O servidor não conseguiu se conectar à Gutendex. Verifique os logs do Render.", ex);
        }
    }

    // Consulta o catálogo com os nomes de parâmetros definidos pela Gutendex.
    // Os valores entram como variáveis de template para serem codificados por completo ("&", "+", "#" etc.
    // numa busca como "Romeo & Juliet" quebravam a URL antes).
    private JsonNode fetchSearch(String query, String language, String topic, String mimeType) {
        JsonNode response = withRetry(() -> restClient.get().uri(uri -> {
            Map<String, Object> vars = new HashMap<>();
            uri.path("/books/").queryParam("search", "{search}");
            vars.put("search", query.trim());
            nonBlank(language).ifPresent(value -> {
                uri.queryParam("languages", "{languages}");
                vars.put("languages", value.trim());
            });
            nonBlank(topic).ifPresent(value -> {
                uri.queryParam("topic", "{topic}");
                vars.put("topic", value.trim());
            });
            nonBlank(mimeType).ifPresent(value -> {
                uri.queryParam("mime_type", "{mimeType}");
                vars.put("mimeType", value.trim());
            });
            return uri.build(vars);
        }).retrieve().body(JsonNode.class));

        if (response == null || !response.path("results").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
        }
        return response;
    }

    /** A Gutendex oscila (timeouts e 5xx são comuns): uma nova tentativa antes de desistir. */
    private static <T> T withRetry(Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            }
            catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (attempt >= GUTENDEX_ATTEMPTS || !retryable) {
                    throw ex;
                }
                log.warn("Gutendex respondeu HTTP {}; tentando de novo ({}/{}).", status, attempt, GUTENDEX_ATTEMPTS);
            }
            catch (ResourceAccessException ex) {
                if (attempt >= GUTENDEX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("Falha de conexão com a Gutendex; tentando de novo ({}/{}).", attempt, GUTENDEX_ATTEMPTS);
            }
            try {
                Thread.sleep(GUTENDEX_RETRY_PAUSE_MS);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Optional<String> nonBlank(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    // - gravação do livro (Gutendex)

    private Book saveBookFromGutendex(String id) {
        try {
            JsonNode item = withRetry(() -> restClient.get().uri("/books/{id}/", id).retrieve().body(JsonNode.class));
            if (item == null || item.isEmpty() || item.path("id").isMissingNode()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado no catálogo gratuito.");
            }

            JsonNode formats = item.path("formats");
            JsonNode subjects = item.path("subjects");
            JsonNode languages = item.path("languages");

            Book book = new Book();
            book.setGoogleBookId(id);
            book.setTitle(item.path("title").asText("Título não informado"));
            book.setAuthors(joinAuthors(item.path("authors")));
            book.setDescription(joinTexts(subjects, " • "));
            book.setPublisher("Project Gutenberg");
            book.setIsbn("");
            book.setFormatsJson(formats.toString());
            book.setThumbnail(coverUrl(formats, id));
            book.setContentUrl(plainTextUrl(formats, id));
            book.setLanguage(languages.isArray() && languages.size() > 0 ? languages.get(0).asText() : "");
            book.setSubjects(joinTexts(subjects, " | "));
            book.setDownloadCount(item.path("download_count").asLong(0));
            return bookRepository.saveAndFlush(book);
        }
        catch (RestClientResponseException ex) {
            log.error("Gutendex respondeu HTTP {} ao buscar o livro {}.", ex.getStatusCode().value(), id, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível obter os dados do livro na Gutendex.", ex);
        }
        catch (RestClientException ex) {
            log.error("Falha de conexão ao buscar o livro {} na Gutendex.", id, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao consultar o catálogo Gutendex.", ex);
        }
    }

    /** A Gutendex entrega a capa em formats["image/jpeg"]; sem ela, usa o endereço padrão do Gutenberg. */
    private static String coverUrl(JsonNode formats, String id) {
        return GutenbergCovers.orFallback(formats.path("image/jpeg").asText(""), id);
    }

    /**
     * Escolhe o melhor texto puro disponível. Além de utf-8/us-ascii, aceita qualquer "text/plain; charset=..."
     * (iso-8859-1 e windows-1252 são comuns em livros em português). Sem nenhum, mas com o livro disponível em
     * HTML/EPUB, usa o texto padrão que o Gutenberg gera para o título.
     */
    private static String plainTextUrl(JsonNode formats, String id) {
        for (String key : List.of("text/plain; charset=utf-8", "text/plain; charset=us-ascii", "text/plain")) {
            String url = formats.path(key).asText("");
            if (GutenbergUrls.isUsablePlainText(url)) {
                return url;
            }
        }
        for (var entry : formats.properties()) {
            String url = entry.getValue().asText("");
            if (entry.getKey().startsWith("text/plain") && GutenbergUrls.isUsablePlainText(url)) {
                return url;
            }
        }
        boolean hasTextEdition = formats.has("text/html") || formats.has("application/epub+zip") || formats.has("application/x-mobipocket-ebook");
        return hasTextEdition ? GutenbergUrls.canonicalText(id) : "";
    }

    /** "Austen, Jane" (formato do Gutenberg) vira "Jane Austen". */
    private static String joinAuthors(JsonNode authors) {
        if (!authors.isArray()) {
            return "";
        }
        return StreamSupport.stream(authors.spliterator(), false).map(author -> displayName(author.path("name").asText())).collect(Collectors.joining(", "));
    }

    private static String displayName(String name) {
        int comma = name.indexOf(',');
        if (comma < 0 || name.indexOf(',', comma + 1) >= 0) {
            return name.strip();
        }
        return (name.substring(comma + 1).strip() + " " + name.substring(0, comma).strip()).strip();
    }

    private static String joinTexts(JsonNode values, String separator) {
        if (!values.isArray()) {
            return "";
        }
        return StreamSupport.stream(values.spliterator(), false).map(JsonNode::asText).collect(Collectors.joining(separator));
    }

    //  auxiliares

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    private UserBook findUserBook(Long id, User user) {
        return userBookRepository.findByIdAndUser(id, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na sua biblioteca."));
    }
}