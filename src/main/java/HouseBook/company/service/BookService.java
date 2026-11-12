
package HouseBook.company.service;

import HouseBook.company.dto.BookResponseDTO;
import HouseBook.company.model.Book.Book;
import HouseBook.company.model.Book.BookRepository;
import HouseBook.company.model.User.User;
import HouseBook.company.model.User.UserRepository;
import HouseBook.company.model.User.UserBook;
import HouseBook.company.model.User.UserBookRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

    private final RestClient restClient = RestClient.builder().baseUrl("https://www.googleapis.com/books/v1").build();

    @Transactional(readOnly = true)
    public java.util.List<BookResponseDTO> getUserBooks(Long userId) {
        User user = findUser(userId);
        return userBookRepository.findAllByUserOrderByAcquiredAtDesc(user).stream().map(BookResponseDTO::from).toList();
    }

    // 1. Pesquisar livros
    public JsonNode searchBooks(String query) {

        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do livro.");
        }

        try {
            JsonNode response = restClient.get().uri(uriBuilder -> {
                        var builder = uriBuilder.path("/volumes").queryParam("q", query.trim()).queryParam("maxResults", 20).queryParam("printType", "books");
                        if (hasApiKey()) {
                            builder.queryParam("key", apiKey.trim());
                        }
                        return builder.build();
                    }).retrieve().body(JsonNode.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Google Books não retornou uma resposta válida.");
            }
            return response;
        }
        catch (RestClientResponseException ex) {
            throw googleBooksException(ex);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível conectar à Google Books. Verifique a conexão e tente novamente.", ex);
        }
    }

    // 2. Adquirir livro
    @Transactional
    public BookResponseDTO acquireBook(String googleBookId, Long userId) {

        if (googleBookId == null || googleBookId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador do livro inválido.");
        }

        User user = findUser(userId);

        Book book = bookRepository.findByGoogleBookId(googleBookId).orElseGet(() -> saveBookFromGoogle(googleBookId));

        if (userBookRepository.existsByUserAndBook(user, book)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este livro já está na sua biblioteca.");
        }

        UserBook userBook = new UserBook();

        userBook.setUser(user);
        userBook.setBook(book);
        userBook.setAcquiredAt(LocalDateTime.now());
        userBook.setReadingStatus("NAO_INICIADO");

        UserBook saved = userBookRepository.save(userBook);

        return BookResponseDTO.from(saved);
    }

    // 3. Adicionar livro ao histórico
    @Transactional
    public BookResponseDTO addToHistory(Long userBookId, Long userId) {

        User user = findUser(userId);

        UserBook userBook = findUserBook(userBookId, user);

        userBook.setReadingStatus("LIDO");

        UserBook saved = userBookRepository.save(userBook);

        return BookResponseDTO.from(saved);
    }

    // 4. Remover livro da biblioteca
    @Transactional
    public void removeBook(Long userBookId, Long userId) {

        User user = findUser(userId);

        UserBook userBook = findUserBook(userBookId, user);

        userBookRepository.delete(userBook);
    }

    // 5. Buscar livros novos
    public JsonNode getNewBooks() {

        try {
            JsonNode response = restClient.get().uri(uriBuilder -> {
                        var builder = uriBuilder.path("/volumes").queryParam("q", "subject:fiction").queryParam("orderBy", "newest").queryParam("maxResults", 20).queryParam("printType", "books");
                        if (hasApiKey()) {
                            builder.queryParam("key", apiKey.trim());
                        }
                        return builder.build();
                    }).retrieve().body(JsonNode.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Google Books não retornou uma resposta válida.");
            }
            return response;
        }
        catch (RestClientResponseException ex) {
            throw googleBooksException(ex);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível conectar à Google Books. Verifique a conexão e tente novamente.", ex);
        }
    }

    // Buscar usuário pelo ID autenticado
    private User findUser(Long userId) {

        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    // Buscar livro pertencente ao usuário
    private UserBook findUserBook(Long id, User user) {

        return userBookRepository.findByIdAndUser(id, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na sua biblioteca."));
    }

    // Buscar informações na Google Books e salvar no catálogo
    private Book saveBookFromGoogle(String googleBookId) {

        JsonNode response;
        try {
            response = restClient.get().uri(uriBuilder -> {
                        var builder = uriBuilder.path("/volumes/{id}");
                        if (hasApiKey()) {
                            builder.queryParam("key", apiKey.trim());
                        }
                        return builder.build(googleBookId);}).retrieve().body(JsonNode.class);
        }
        catch (RestClientResponseException ex) {
            throw googleBooksException(ex);
        }
        catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar os dados do livro na Google Books.", ex);
        }

        if (response == null || response.isEmpty() || response.path("volumeInfo").isMissingNode() || response.path("volumeInfo").isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na Google Books.");
        }

        JsonNode volume = response.path("volumeInfo");

        String title = volume.path("title").asText("");

        if (title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O livro retornado não possui um título válido.");
        }

        Book book = new Book();

        book.setGoogleBookId(googleBookId);
        book.setTitle(title);
        book.setDescription(volume.path("description").asText(""));
        book.setPublisher(volume.path("publisher").asText(""));

        // Converter autores em uma String separada por vírgulas
        JsonNode authors = volume.path("authors");

        if (authors.isArray()) {
            book.setAuthors(StreamSupport.stream(authors.spliterator(), false).map(JsonNode::asText).collect(Collectors.joining(", ")));
        }

        // Recuperar ISBN
        JsonNode identifiers = volume.path("industryIdentifiers");

        if (identifiers.isArray()) {
            for (JsonNode identifier : identifiers) {

                if (identifier.path("type").asText().startsWith("ISBN")) {
                    book.setIsbn(identifier.path("identifier").asText());
                    break;
                }
            }
        }

        // Recuperar capa
        book.setThumbnail(volume.path("imageLinks").path("thumbnail").asText(""));

        return bookRepository.save(book);
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    private ResponseStatusException googleBooksException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();

        if (status == 429) {
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A Google Books atingiu o limite de consultas. Aguarde um pouco e tente novamente.", ex);
        }

        if (status == 403) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Google Books recusou a chave. Confira se a Google Books API está ativada e se a chave pertence ao projeto correto.", ex);
        }

        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Google Books respondeu com HTTP " + status + ". Tente novamente mais tarde.", ex);
    }

    @Value("${google.books.api.key:}")
    private String apiKey;
}