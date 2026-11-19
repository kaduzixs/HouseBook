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
    private final RestClient restClient = RestClient.builder().baseUrl("https://gutendex.com").build();

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
            JsonNode response = fetchSearch(query, null, null);
            if (response.path("results").isArray() && ((language != null && !language.isBlank()) || (topic != null && !topic.isBlank()) || (mimeType != null && !mimeType.isBlank()))) {
                var filtered = new tools.jackson.databind.node.ArrayNode(tools.jackson.databind.node.JsonNodeFactory.instance);
                for (JsonNode book : response.path("results")) {
                    boolean languageMatch = language == null || language.isBlank() || StreamSupport.stream(book.path("languages").spliterator(), false).anyMatch(value -> value.asText().equalsIgnoreCase(language));
                    boolean topicMatch = topic == null || topic.isBlank() || StreamSupport.stream(book.path("subjects").spliterator(), false).anyMatch(value -> value.asText().toLowerCase().contains(topic.toLowerCase()));
                    boolean formatMatch = mimeType == null || mimeType.isBlank() || StreamSupport.stream(book.path("formats").properties().spliterator(), false).anyMatch(entry -> entry.getKey().toLowerCase().startsWith(mimeType.toLowerCase()));
                    if (languageMatch && topicMatch && formatMatch) filtered.add(book);
                }
                ((tools.jackson.databind.node.ObjectNode) response).set("results", filtered);
            }
            return response;
        }
        catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex respondeu com HTTP " + ex.getStatusCode().value() + ".", ex);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível conectar à Gutendex. Verifique a conexão do servidor.", ex);
        }
    }

    private JsonNode fetchSearch(String query, String language, String topic) {
        JsonNode response = restClient.get().uri(uri -> uri.path("/books/").queryParam("search", query.trim()).queryParam("page_size", 32).queryParamIfPresent("languages", java.util.Optional.ofNullable(language).filter(v -> !v.isBlank())).queryParamIfPresent("topic", java.util.Optional.ofNullable(topic).filter(v -> !v.isBlank())).build()).retrieve().body(JsonNode.class);

        if (response == null || !response.path("results").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
        }
        return response;
    }

    public JsonNode getNewBooks() {
        try {
            JsonNode response = restClient.get().uri(uri -> uri.path("/books/").queryParam("sort", "popular").queryParam("page_size", 32).build()).retrieve().body(JsonNode.class);
            if (response == null || !response.path("results").isArray()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex retornou uma resposta inválida.");
            return response;
        }
        catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Gutendex respondeu com HTTP " + ex.getStatusCode().value() + ".", ex);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível conectar à Gutendex.", ex);
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
            JsonNode item = restClient.get().uri("/books/{id}/", id).retrieve().body(JsonNode.class);
            if (item == null || item.isEmpty() || item.path("id").isMissingNode()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado no catálogo gratuito.");
            Book book = new Book();
            book.setGoogleBookId(id);
            book.setTitle(item.path("title").asText("Título não informado"));
            JsonNode authors = item.path("authors");
            if (authors.isArray()) book.setAuthors(StreamSupport.stream(authors.spliterator(), false).map(a -> a.path("name").asText()).collect(Collectors.joining(", ")));
            JsonNode translators = item.path("translators");
            book.setDescription(item.path("subjects").isArray() ? StreamSupport.stream(item.path("subjects").spliterator(), false).map(JsonNode::asText).collect(Collectors.joining(" • ")) : "");
            book.setPublisher("Project Gutenberg");
            book.setIsbn("");
            JsonNode formats = item.path("formats");
            book.setFormatsJson(formats.toString());
            book.setThumbnail(formats.path("image/jpeg").asText(""));
            String contentUrl = formats.path("text/plain; charset=utf-8").asText("");
            if (contentUrl.isBlank()) contentUrl = formats.path("text/plain; charset=us-ascii").asText("");
            if (contentUrl.isBlank()) contentUrl = formats.path("text/plain").asText("");
            book.setContentUrl(contentUrl);
            book.setLanguage(item.path("languages").isArray() && item.path("languages").size() > 0 ? item.path("languages").get(0).asText() : "");
            book.setSubjects(item.path("subjects").isArray() ? StreamSupport.stream(item.path("subjects").spliterator(), false).map(JsonNode::asText).collect(Collectors.joining(" | ")) : "");
            book.setDownloadCount(item.path("download_count").asLong(0));
            return bookRepository.saveAndFlush(book);
        }
        catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível obter os dados do livro na Gutendex.", ex);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao consultar o catálogo Gutendex.", ex);
        }
    }

    public String getFreeText(Long userBookId, Long userId) {
        User user = findUser(userId);
        UserBook userBook = findUserBook(userBookId, user);
        String url = userBook.getBook().getContentUrl();
        if (url == null || url.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A Gutendex não disponibiliza texto integral em formato compatível para esta obra.");
        try {
            return RestClient.create().get().uri(url).retrieve().body(String.class);
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