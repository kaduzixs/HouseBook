package HouseBook.company.service;

import HouseBook.company.dto.BookResponseDTO;
import HouseBook.company.enums.ReadingStatus;
import HouseBook.company.model.Book.Book;
import HouseBook.company.model.Book.BookRepository;
import HouseBook.company.model.User.User;
import HouseBook.company.model.User.UserRepository;
import HouseBook.company.model.User.UserBook;
import HouseBook.company.model.User.UserBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class BookService {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserBookRepository userBookRepository;
    // Cliente HTTP com limites de conexão e leitura para evitar requisições presas no Render.
    private static final Logger log = LoggerFactory.getLogger(BookService.class);
    private final RestClient restClient = createGutendexClient();

    private static RestClient createGutendexClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder().baseUrl("https://gutendex.com").requestFactory(requestFactory).build();
    }

    @Transactional(readOnly = true)
    public java.util.List<BookResponseDTO> getUserBooks(Long userId) {
        User user = findUser(userId);
        return userBookRepository.findAllByUserOrderByAcquiredAtDesc(user).stream().map(BookResponseDTO::from).toList();
    }

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

    // Consulta o catálogo com os nomes de parâmetros definidos pela Gutendex.
    private JsonNode fetchSearch(String query, String language, String topic, String mimeType) {
        JsonNode response = restClient.get().uri(uri -> uri.path("/books/").queryParam("search", query.trim()).queryParamIfPresent("languages", java.util.Optional.ofNullable(language).filter(v -> !v.isBlank())).queryParamIfPresent("topic", java.util.Optional.ofNullable(topic).filter(v -> !v.isBlank())).queryParamIfPresent("mime_type", java.util.Optional.ofNullable(mimeType).filter(v -> !v.isBlank())).build()).retrieve().body(JsonNode.class);

        if (response == null || !response.path("results").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
        }
        return response;
    }

    public JsonNode getNewBooks() {
        try {
            JsonNode response = restClient.get().uri(uri -> uri.path("/books/").queryParam("sort", "popular").build()).retrieve().body(JsonNode.class);
            if (response == null || !response.path("results").isArray()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
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

    @Transactional
    public BookResponseDTO acquireBook(String bookId, Long userId) {
        if (bookId == null || !bookId.matches("\\d+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador Gutendex inválido.");
        User user = findUser(userId);
        Book book = bookRepository.findByGoogleBookId(bookId).orElseGet(() -> saveBookFromGutendex(bookId));
        if (userBookRepository.existsByUserAndBook(user, book)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este livro já está na sua biblioteca.");
        UserBook userBook = new UserBook();
        userBook.setUser(user);
        userBook.setBook(book);
        userBook.setAcquiredAt(LocalDateTime.now());
        userBook.setReadingStatus(ReadingStatus.WANT_TO_READ);
        return BookResponseDTO.from(userBookRepository.saveAndFlush(userBook));
    }


    private Book saveBookFromGutendex(String id) {
        try {
            JsonNode item = restClient.get()
                    .uri("/books/{id}/", id)
                    .retrieve()
                    .body(JsonNode.class);

            if (item == null || item.isEmpty() || item.path("id").isMissingNode()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Livro não encontrado no catálogo gratuito."
                );
            }

            Book book = new Book();

            book.setGoogleBookId(id);
            book.setTitle(item.path("title").asText("Título não informado"));

            // Extrai os autores do livro.
            JsonNode authors = item.path("authors");

            if (authors.isArray()) {
                book.setAuthors(
                        StreamSupport.stream(authors.spliterator(), false)
                                .map(a -> a.path("name").asText())
                                .collect(Collectors.joining(", "))
                );
            }

            // Extrai os resumos disponibilizados pela Gutendex.
            JsonNode summaries = item.path("summaries");

            String summary = summaries.isArray()
                    ? StreamSupport.stream(summaries.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining("\n\n"))
                    : "";

            // Extrai os assuntos do livro.
            JsonNode subjectsNode = item.path("subjects");

            String subjects = subjectsNode.isArray()
                    ? StreamSupport.stream(subjectsNode.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining("\n\n"))
                    : "";

            // Utiliza os assuntos como alternativa caso não exista resumo.
            book.setDescription(summary.isBlank() ? subjects : summary);

            book.setPublisher("Project Gutenberg");
            book.setIsbn("");

            // Salva os formatos disponibilizados pela API.
            JsonNode formats = item.path("formats");

            book.setFormatsJson(formats.toString());

            // Obtém a capa do livro.
            book.setThumbnail(formats.path("image/jpeg").asText(""));

            // Procura o formato TXT para disponibilizar a leitura.
            String contentUrl = formats.path("text/plain; charset=utf-8").asText("");

            if (contentUrl.isBlank()) {
                contentUrl = formats.path("text/plain; charset=us-ascii").asText("");
            }

            if (contentUrl.isBlank()) {
                contentUrl = formats.path("text/plain").asText("");
            }

            book.setContentUrl(contentUrl);

            // Obtém o idioma.
            JsonNode languages = item.path("languages");

            book.setLanguage(
                    languages.isArray() && !languages.isEmpty()
                            ? languages.get(0).asText()
                            : ""
            );

            // Salva os assuntos separados por barra.
            book.setSubjects(subjectsNode.isArray()
                    ? StreamSupport.stream(subjectsNode.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining(" | "))
                    : ""
            );

            // Salva a quantidade de downloads.
            book.setDownloadCount(item.path("download_count").asLong(0));

            return bookRepository.saveAndFlush(book);

        } catch (RestClientResponseException ex) {

            log.error(
                    "Gutendex respondeu HTTP {} ao buscar o livro {}.",
                    ex.getStatusCode().value(),
                    id,
                    ex
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível obter os dados do livro na Gutendex.",
                    ex
            );

        } catch (RestClientException ex) {

            log.error("Falha de conexão ao buscar o livro {} na Gutendex.", id, ex);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Falha ao consultar o catálogo Gutendex.",
                    ex
            );
        }
    }


    public String getFreeText(Long userBookId, Long userId) {
        User user = findUser(userId);
        UserBook userBook = findUserBook(userBookId, user);
        String url = userBook.getBook().getContentUrl();
        if (url == null || url.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A Gutendex não disponibiliza texto integral em formato compatível para esta obra.");
        try {
            return restClient.get().uri(url).retrieve().body(String.class);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível carregar o texto integral do Project Gutenberg.", ex);
        }
    }

    @Transactional
    public BookResponseDTO addToHistory(Long userBookId, Long userId) {
        User user = findUser(userId);
        UserBook userBook = findUserBook(userBookId, user);
        userBook.setReadingStatus(ReadingStatus.COMPLETED);
        userBook.setCompletedAt(LocalDateTime.now());
        return BookResponseDTO.from(userBookRepository.save(userBook));
    }

    @Transactional
    public void removeBook(Long userBookId, Long userId) {
        User user = findUser(userId);
        userBookRepository.delete(findUserBook(userBookId, user));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    private UserBook findUserBook(Long id, User user) {
        return userBookRepository.findByIdAndUser(id, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na sua biblioteca."));
    }
}