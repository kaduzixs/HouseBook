package HouseBook.company.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8) String password, String fotoUrl){
}
