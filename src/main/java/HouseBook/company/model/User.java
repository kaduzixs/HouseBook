package HouseBook.company.model;
import jakarta.persistence.*;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "foto_url", nullable = true, length = 500)
    private String fotoUrl;

    protected User() {
    }

    public User(String name, String email, String password, String fotoUrl) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.fotoUrl = fotoUrl;
    }
}