
package HouseBook.company.service;



import HouseBook.company.model.User;
import HouseBook.company.model.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private String normalizarEmail(String email) {
        if (email == null) {
            return "";
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean register(String email, String password) {
        String emailNormalizado = normalizarEmail(email);

        if (emailNormalizado.length() > 254 || !EMAIL_PATTERN.matcher(emailNormalizado).matches()) {
            throw new IllegalArgumentException(
                    "Informe um e-mail válido."
            );
        }

        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException(
                    "A senha deve ter entre 8 e 72 caracteres."
            );
        }

        if (userRepository.findByEmail(emailNormalizado).isPresent()) {
            return false;
        }

        try {
            User usuario = new User(emailNormalizado, passwordEncoder.encode(password));

            userRepository.saveAndFlush(usuario);
            return true;

        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    public Optional<User> login(String email, String password) {
        if (email == null || password == null || email.isBlank() || password.isEmpty()) {
            return Optional.empty();
        }

        String emailNormalizado = normalizarEmail(email);

        Optional<User> encontrado =
                userRepository.findByEmail(emailNormalizado);

        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        User usuario = encontrado.get();
        String senhaArmazenada = usuario.getPassword();

        // Verifica senhas que já estão armazenadas com BCrypt.
        if (senhaArmazenada != null && senhaArmazenada.startsWith("$2") && passwordEncoder.matches(password, senhaArmazenada)) {
            return Optional.of(usuario);
        }

        if (password.equals(senhaArmazenada)) {
            usuario.setPassword(passwordEncoder.encode(password));
            userRepository.save(usuario);

            return Optional.of(usuario);
        }

        return Optional.empty();
    }
}