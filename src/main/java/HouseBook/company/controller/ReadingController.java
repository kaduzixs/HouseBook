package HouseBook.company.controller;

import HouseBook.company.Request.ReadingProgressRequest;
import HouseBook.company.dto.ChapterResponseDTO;
import HouseBook.company.dto.ChapterSummaryDTO;
import HouseBook.company.dto.ReaderStateDTO;
import HouseBook.company.service.ReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    @GetMapping("/{userBookId}")
    public ResponseEntity<ReaderStateDTO> open(@PathVariable Long userBookId, Authentication auth) {
        return ResponseEntity.ok(readingService.open(userBookId, userId(auth)));
    }

    @GetMapping("/{userBookId}/chapters")
    public ResponseEntity<List<ChapterSummaryDTO>> chapters(@PathVariable Long userBookId, Authentication auth) {
        return ResponseEntity.ok(readingService.chapters(userBookId, userId(auth)));
    }

    @GetMapping("/{userBookId}/chapters/{number}")
    public ResponseEntity<ChapterResponseDTO> chapter(@PathVariable Long userBookId, @PathVariable Integer number, Authentication auth) {
        return ResponseEntity.ok(readingService.chapter(userBookId, number, userId(auth)));
    }

    @PostMapping(value = "/{userBookId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReaderStateDTO> importContent(@PathVariable Long userBookId, @RequestParam("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(readingService.importText(userBookId, file, userId(auth)));
    }

    @PatchMapping("/{userBookId}/progress")
    public ResponseEntity<Void> progress(@PathVariable Long userBookId, @Valid @RequestBody ReadingProgressRequest request, Authentication auth) {
        readingService.updateProgress(userBookId, request, userId(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userBookId}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long userBookId, Authentication auth) {
        readingService.complete(userBookId, userId(auth));
        return ResponseEntity.noContent().build();
    }
}