
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

    // Normaliza o e-mail para evitar cadastros duplicados
    private String normalizarEmail(String email) {
        if (email == null) {
            return "";
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Valida os dados e cadastra o usuário
    public boolean register(String name, String email, String password) {

        String nomeNormalizado = name == null ? "" : name.trim();
        String emailNormalizado = normalizarEmail(email);

        // Validação do nome
        if (nomeNormalizado.isBlank() || nomeNormalizado.length() > 100) {throw new IllegalArgumentException("Informe um nome válido com até 100 caracteres.");
        }

        // Validação do e-mail
        if (emailNormalizado.length() > 254 || !EMAIL_PATTERN.matcher(emailNormalizado).matches()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }

        // Validação da senha
        if (password == null || password.length() < 8 || password.length() > 72) {

            throw new IllegalArgumentException("A senha deve ter entre 8 e 72 caracteres.");
        }

        // Verifica se o e-mail já está cadastrado
        if (userRepository.findByEmail(emailNormalizado).isPresent()) {
            return false;
        }

        try {
            // Cria o usuário com senha criptografada
            User usuario = new User(nomeNormalizado, emailNormalizado, passwordEncoder.encode(password));

            userRepository.saveAndFlush(usuario);

            return true;

        } catch (DataIntegrityViolationException ex) {
            // Trata possíveis cadastros simultâneos com o mesmo e-mail
            return false;
        }
    }

    // Realiza o login
    public Optional<User> login(String email, String password) {

        if (email == null || password == null || email.isBlank() || password.isEmpty()) {
            return Optional.empty();
        }

        String emailNormalizado = normalizarEmail(email);

        Optional<User> encontrado = userRepository.findByEmail(emailNormalizado);

        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        User usuario = encontrado.get();

        String senhaArmazenada = usuario.getPassword();

        if (senhaArmazenada == null || senhaArmazenada.isBlank()) {
            return Optional.empty();
        }

        // Compara a senha informada com o hash BCrypt armazenado
        if (passwordEncoder.matches(password, senhaArmazenada)) {
            return Optional.of(usuario);
        }

        return Optional.empty();
    }
}
