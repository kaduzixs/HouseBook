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

    @Column(nullable = false, unique = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "foto_url", columnDefinition = "BYTEA")
    private byte[] fotoUrl;

    protected User() {
    }

    public User(String name, String email, String password, byte[] fotoUrl) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.fotoUrl = fotoUrl;
    }
}