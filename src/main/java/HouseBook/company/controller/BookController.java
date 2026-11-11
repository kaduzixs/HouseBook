
package HouseBook.company.controller;

import HouseBook.company.service.BookService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

    // 1. Pesquisar livros na Google Books
    @GetMapping("/search")
    public ResponseEntity<?> searchBooks(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do livro.");
        }

        return ResponseEntity.ok(bookService.searchBooks(q));
    }

    // 2. Adquirir livro e adicionar à biblioteca
    @PostMapping("/acquire/{googleBookId}")
    public ResponseEntity<?> acquireBook(@PathVariable String googleBookId, Authentication authentication) {

        if (googleBookId == null || googleBookId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador do livro inválido.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.acquireBook(googleBookId, getAuthenticatedUserId(authentication)));
    }

    // 3. Adicionar livro ao histórico
    @PostMapping("/history/{userBookId}")
    public ResponseEntity<?> addToHistory(@PathVariable Long userBookId, Authentication authentication) {

        return ResponseEntity.ok(bookService.addToHistory(userBookId, getAuthenticatedUserId(authentication)));
    }

    // 4. Remover livro da biblioteca
    @DeleteMapping("/{userBookId}")
    public ResponseEntity<Void> removeBook(@PathVariable Long userBookId, Authentication authentication) {
        bookService.removeBook(userBookId, getAuthenticatedUserId(authentication));

        return ResponseEntity.noContent().build();
    }

    // 5. Buscar livros novos
    @GetMapping("/new")
    public ResponseEntity<?> getNewBooks() {
        return ResponseEntity.ok(bookService.getNewBooks()
        );
    }
}
