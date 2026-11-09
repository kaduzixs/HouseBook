package HouseBook.company.model;

import java.util.Optional;

public interface UserRepository extends org.springframework.data.jpa.repository.JpaRepository<User, Long> {

    Optional<User> findByEmail(String emailNormalizado);
}
