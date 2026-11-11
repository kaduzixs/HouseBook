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

    @Column(name = "foto", columnDefinition = "bytea")
    private byte[] foto;

    @Column(name = "foto_content_type", length = 50)
    private String fotoContentType;

    protected User() {
    }

    public User(String name, String email, String password, byte[] foto, String fotoContentType) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.foto = foto;
        this.fotoContentType = fotoContentType;
    }
}