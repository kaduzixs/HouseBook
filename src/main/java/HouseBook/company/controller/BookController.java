package HouseBook.company.controller;

import HouseBook.company.dto.BookResponseDTO;
import HouseBook.company.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
import java.time.Duration;

@ApiResponse(responseCode = "429", description = "Limite de requisições excedido. Consulte Retry-After para saber quando tentar novamente.")
@RestController
@Tag(name = "Livros e biblioteca", description = "Pesquisa no catálogo Gutendex e gerenciamento da biblioteca individual.")
@SecurityRequirement(name = "sessionCookie")
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
    @Operation(summary = "Listar minha biblioteca", description = "Retorna os livros associados à conta autenticada.")
    @GetMapping("/mine")
    public ResponseEntity<List<BookResponseDTO>> getMyBooks(Authentication authentication) {
        return ResponseEntity.ok(bookService.getUserBooks(getAuthenticatedUserId(authentication)));
    }

    // 1. Pesquisar livros na Gutendex / Project Gutenberg
    @Operation(summary = "Pesquisar livros", description = "Pesquisa títulos, autores ou assuntos no catálogo Gutendex. Parâmetro q obrigatório; language, topic e mimeType são opcionais.")
    @GetMapping("/search")
    public ResponseEntity<JsonNode> searchBooks(@RequestParam("q") String q, @RequestParam(value = "language", required = false) String language, @RequestParam(value = "topic", required = false) String topic, @RequestParam(value = "mimeType", required = false) String mimeType) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do livro.");
        }
        return ResponseEntity.ok(bookService.searchBooks(q, language, topic, mimeType));
    }

    // 2. Adquirir livro e adicionar à biblioteca
    @Operation(summary = "Adicionar livro à biblioteca", description = "Obtém os metadados do livro na Gutendex e o associa ao usuário autenticado.")
    @PostMapping("/acquire/{googleBookId}")
    public ResponseEntity<BookResponseDTO> acquireBook(@PathVariable String googleBookId, Authentication authentication) {
        if (googleBookId == null || googleBookId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador do livro inválido.");
        }
        BookResponseDTO response = bookService.acquireBook(googleBookId, getAuthenticatedUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 3. Marcar livro como concluído (histórico)
    @Operation(summary = "Marcar livro como concluído", description = "Atualiza o status do livro da biblioteca para COMPLETED.")
    @PostMapping("/history/{userBookId}")
    public ResponseEntity<BookResponseDTO> addToHistory(@PathVariable Long userBookId, Authentication authentication) {
        return ResponseEntity.ok(bookService.addToHistory(userBookId, getAuthenticatedUserId(authentication)));
    }

    // 4. Remover livro da biblioteca
    @Operation(summary = "Remover livro da biblioteca", description = "Remove o vínculo do livro com a biblioteca do usuário autenticado.")
    @DeleteMapping("/{userBookId}")
    public ResponseEntity<Void> removeBook(@PathVariable Long userBookId, Authentication authentication) {
        bookService.removeBook(userBookId, getAuthenticatedUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    // Capa do livro: o backend busca a imagem externa e a entrega no mesmo domínio.
    @Operation(summary = "Obter capa de livro", description = "Retorna a capa original do Project Gutenberg pelo identificador numérico.")
    @GetMapping("/cover/{googleBookId}")
    public ResponseEntity<byte[]> getBookCover(@PathVariable String googleBookId) {
        BookService.CoverImage cover = bookService.getCoverImage(googleBookId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(cover.contentType())).cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(cover.data());
    }

    // 5. Livros populares do catálogo
    @Operation(summary = "Listar livros populares", description = "Retorna a seleção popular fornecida pela Gutendex.")
    @GetMapping("/new")
    public ResponseEntity<JsonNode> getNewBooks() {
        return ResponseEntity.ok(bookService.getNewBooks());
    }
}