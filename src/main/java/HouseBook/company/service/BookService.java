
package HouseBook.company.service;

import HouseBook.company.model.Book.Book;
import HouseBook.company.model.Book.BookRepository;
import HouseBook.company.model.User.User;
import HouseBook.company.model.User.UserRepository;
import HouseBook.company.model.User.UserBook;
import HouseBook.company.model.User.UserBookRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
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

    // 1. Pesquisar livros
    public JsonNode searchBooks(String query) {

        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do livro.");
        }

        return restClient.get().uri(uriBuilder -> uriBuilder.path("/volumes").queryParam("q", query).queryParam("maxResults", 20).queryParam("printType", "books").build()).retrieve().body(JsonNode.class);
    }

    // 2. Adquirir livro
    @Transactional
    public UserBook acquireBook(String googleBookId, Long userId) {

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

        return userBookRepository.save(userBook);
    }

    // 3. Adicionar livro ao histórico
    @Transactional
    public UserBook addToHistory(Long userBookId, Long userId) {

        User user = findUser(userId);

        UserBook userBook = findUserBook(userBookId, user);

        userBook.setReadingStatus("LIDO");

        return userBookRepository.save(userBook);
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

        return restClient.get().uri(uriBuilder -> uriBuilder.path("/volumes").queryParam("q", "subject:fiction").queryParam("orderBy", "newest").queryParam("maxResults", 20).build()).retrieve().body(JsonNode.class);
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

        JsonNode response = restClient.get().uri("/volumes/{id}", googleBookId).retrieve().body(JsonNode.class);

        if (response == null || response.isEmpty() || response.path("volumeInfo").isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado na Google Books.");
        }
        JsonNode volume = response.path("volumeInfo");
        Book book = new Book();

        book.setGoogleBookId(googleBookId);
        book.setTitle(volume.path("title").asText(""));
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
}
