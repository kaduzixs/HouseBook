package HouseBook.company.service;


import lombok.*;
import org.springframework.stereotype.Service;

@Service
@Getter
@Setter
public class ReadingService{

    private int page;
    private int totalPages;
    private String readingStatus;


    public ReadingService(int page, int totalPages, String readingStatus) {
        this.page = page;
        this.totalPages = totalPages;
        this.readingStatus = readingStatus;
    }

    public ReadingService() {}
}
