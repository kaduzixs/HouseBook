package HouseBook.company.controller;

import HouseBook.company.dto.BookResponseDTO;
import HouseBook.company.service.BookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // Recupera o ID do usuário autenticado
    private Long getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }
        return (Long) authentication.getPrincipal();
    }

    // Livros da biblioteca do usuário
    @GetMapping("/mine")
    public ResponseEntity<List<BookResponseDTO>> getMyBooks(Authentication authentication) {
        return ResponseEntity.ok(bookService.getUserBooks(getAuthenticatedUserId(authentication)));
    }

    // 1. Pesquisar livros na Gutendex / Project Gutenberg
    @GetMapping("/search")
    public ResponseEntity<JsonNode> searchBooks(@RequestParam("q") String q, @RequestParam(value = "language", required = false) String language, @RequestParam(value = "topic", required = false) String topic, @RequestParam(value = "mimeType", required = false) String mimeType) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do livro.");
        }
        return ResponseEntity.ok(bookService.searchBooks(q, language, topic, mimeType));
    }

    // 2. Adquirir livro e adicionar à biblioteca
    @PostMapping("/acquire/{googleBookId}")
    public ResponseEntity<BookResponseDTO> acquireBook(@PathVariable String googleBookId, Authentication authentication) {
        if (googleBookId == null || googleBookId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador do livro inválido.");
        }
        BookResponseDTO response = bookService.acquireBook(googleBookId, getAuthenticatedUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 3. Marcar livro como concluído (histórico)
    @PostMapping("/history/{userBookId}")
    public ResponseEntity<BookResponseDTO> addToHistory(@PathVariable Long userBookId, Authentication authentication) {
        return ResponseEntity.ok(bookService.addToHistory(userBookId, getAuthenticatedUserId(authentication)));
    }

    // 4. Remover livro da biblioteca
    @DeleteMapping("/{userBookId}")
    public ResponseEntity<Void> removeBook(@PathVariable Long userBookId, Authentication authentication) {
        bookService.removeBook(userBookId, getAuthenticatedUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    // 5. Livros populares do catálogo
    @GetMapping("/new")
    public ResponseEntity<JsonNode> getNewBooks() {
        return ResponseEntity.ok(bookService.getNewBooks());
    }
}