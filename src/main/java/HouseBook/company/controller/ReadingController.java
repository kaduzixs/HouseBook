package HouseBook.company.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

public class ReadingController {

    @GetMapping("/reader/{userBookId}/chapters")
    public String getChapters(@PathVariable Long userBookId) {

        return "Chapters for userBookId: " + userBookId;
    }

    @GetMapping("reader/{userBookId}/chapters/{number}")

    @PatchMapping("reader/{userBookId}/progress")
}
