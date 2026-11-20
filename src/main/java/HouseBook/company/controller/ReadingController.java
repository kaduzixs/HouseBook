package HouseBook.company.controller;

import HouseBook.company.Request.ReadingProgressRequest;
import HouseBook.company.dto.ChapterResponseDTO;
import HouseBook.company.dto.ChapterSummaryDTO;
import HouseBook.company.dto.ReaderStateDTO;
import HouseBook.company.service.ReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Tag(name = "Leitura", description = "Abertura de livros, consulta de capítulos, importação de texto e acompanhamento de progresso.")
@SecurityRequirement(name = "sessionCookie")
@RequestMapping("/api/reader")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    private Long userId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }
        return id;
    }

    /** Abre o livro: prepara os capítulos (se preciso) e devolve sumário + posição salva. */
    @Operation(summary = "Abrir livro", description = "Prepara o conteúdo quando disponível e retorna o estado de leitura e o sumário.")
    @GetMapping("/{userBookId}")
    public ResponseEntity<ReaderStateDTO> open(@PathVariable Long userBookId, Authentication auth) {
        return ResponseEntity.ok(readingService.open(userBookId, userId(auth)));
    }

    @Operation(summary = "Listar capítulos", description = "Retorna os títulos e metadados dos capítulos do livro.")
    @GetMapping("/{userBookId}/chapters")
    public ResponseEntity<List<ChapterSummaryDTO>> chapters(@PathVariable Long userBookId, Authentication auth) {
        return ResponseEntity.ok(readingService.chapters(userBookId, userId(auth)));
    }

    @Operation(summary = "Consultar capítulo", description = "Retorna o conteúdo integral de um capítulo específico.")
    @GetMapping("/{userBookId}/chapters/{number}")
    public ResponseEntity<ChapterResponseDTO> chapter(@PathVariable Long userBookId, @PathVariable Integer number, Authentication auth) {
        return ResponseEntity.ok(readingService.chapter(userBookId, number, userId(auth)));
    }

    @Operation(summary = "Importar conteúdo TXT", description = "Importa arquivo .txt de até 5 MB. O conteúdo é dividido em capítulos e associado ao livro.")
    @PostMapping(value = "/{userBookId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReaderStateDTO> importContent(@PathVariable Long userBookId, @RequestParam("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(readingService.importText(userBookId, file, userId(auth)));
    }

    @Operation(summary = "Atualizar progresso", description = "Salva o capítulo e o parágrafo atuais. Requer JSON com currentChapter e currentParagraph.")
    @PatchMapping("/{userBookId}/progress")
    public ResponseEntity<Void> progress(@PathVariable Long userBookId, @Valid @RequestBody ReadingProgressRequest request, Authentication auth) {
        readingService.updateProgress(userBookId, request, userId(auth));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Concluir leitura", description = "Marca a leitura como concluída e registra a data de conclusão.")
    @PostMapping("/{userBookId}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long userBookId, Authentication auth) {
        readingService.complete(userBookId, userId(auth));
        return ResponseEntity.noContent().build();
    }
}