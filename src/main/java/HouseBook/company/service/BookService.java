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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserBookRepository userBookRepository;

    // Cliente HTTP com limites de conexão e leitura para evitar requisições presas no Render.
    private final RestClient restClient = createGutendexClient();

    private static RestClient createGutendexClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl("https://gutendex.com")
                .requestFactory(requestFactory)
                .build();
    }

    // ---------------------------------------------------------------- biblioteca

    @Transactional(readOnly = true)
    public List<BookResponseDTO> getUserBooks(Long userId) {
        User user = findUser(userId);
        return userBookRepository.findAllByUserOrderByAcquiredAtDesc(user).stream()
                .map(BookResponseDTO::from)
                .toList();
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

    // ------------------------------------------------------------------ catálogo

    public JsonNode searchBooks(String query, String language, String topic, String mimeType) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe título, autor ou assunto.");
        }

        try {
            return fetchSearch(query, language, topic, mimeType);
        }
        catch (RestClientResponseException ex) {
            log.error("Gutendex respondeu HTTP {} ao pesquisar livros.", ex.getStatusCode().value(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A Gutendex respondeu com HTTP " + ex.getStatusCode().value() + ". Tente novamente mais tarde.", ex);
        }
        catch (RestClientException ex) {
            log.error("Falha de conexão ao consultar a Gutendex.", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O servidor não conseguiu se conectar à Gutendex. Verifique os logs do Render.", ex);
        }
    }

    public JsonNode getNewBooks() {
        try {
            JsonNode response = restClient.get()
                    .uri(uri -> uri.path("/books/").queryParam("sort", "popular").build())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.path("results").isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
            }
            return response;
        }
        catch (RestClientResponseException ex) {
            log.error("Gutendex respondeu HTTP {} ao carregar a seleção inicial.", ex.getStatusCode().value(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A Gutendex respondeu com HTTP " + ex.getStatusCode().value() + ". Tente novamente mais tarde.", ex);
        }
        catch (RestClientException ex) {
            log.error("Falha de conexão ao carregar livros populares da Gutendex.", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O servidor não conseguiu se conectar à Gutendex. Verifique os logs do Render.", ex);
        }
    }

    // Consulta o catálogo com os nomes de parâmetros definidos pela Gutendex.
    private JsonNode fetchSearch(String query, String language, String topic, String mimeType) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/books/")
                        .queryParam("search", query.trim())
                        .queryParamIfPresent("languages", nonBlank(language))
                        .queryParamIfPresent("topic", nonBlank(topic))
                        .queryParamIfPresent("mime_type", nonBlank(mimeType))
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("results").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
        }
        return response;
    }

    private static Optional<String> nonBlank(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    // --------------------------------------------------- gravação do livro (Gutendex)

    private Book saveBookFromGutendex(String id) {
        try {
            JsonNode item = restClient.get().uri("/books/{id}/", id).retrieve().body(JsonNode.class);
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
            book.setContentUrl(plainTextUrl(formats));
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

    /** Escolhe o melhor texto puro disponível, nessa ordem de preferência. */
    private static String plainTextUrl(JsonNode formats) {
        for (String key : List.of("text/plain; charset=utf-8", "text/plain; charset=us-ascii", "text/plain")) {
            String url = formats.path(key).asText("");
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
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