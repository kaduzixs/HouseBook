
package HouseBook.company.service;

import HouseBook.company.model.User.User;

import HouseBook.company.model.User.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final long MAX_PHOTO_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_PHOTO_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public boolean register(String name, String email, String password, MultipartFile foto) {
        String nome = name == null ? "" : name.trim();
        String emailNormalizado = normalizarEmail(email);

        if (nome.isBlank() || nome.length() > 100) {
            throw new IllegalArgumentException("Informe um nome válido com até 100 caracteres.");
        }
        if (emailNormalizado.length() > 254 || !EMAIL_PATTERN.matcher(emailNormalizado).matches()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("A senha deve ter entre 8 e 72 caracteres.");
        }

        byte[] fotoBytes = null;
        String contentType = null;

        if (foto != null && !foto.isEmpty()) {
            if (foto.getSize() > MAX_PHOTO_SIZE) {
                throw new IllegalArgumentException("A foto deve ter no máximo 2 MB.");
            }
            contentType = foto.getContentType();
            if (contentType == null || !ALLOWED_PHOTO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("A foto deve ser JPG, PNG ou WEBP.");
            }
            try {
                fotoBytes = foto.getBytes();
            }
            catch (IOException ex) {
                throw new IllegalArgumentException("Não foi possível ler a foto enviada.");
            }
        }

        if (userRepository.findByEmail(emailNormalizado).isPresent()) {
            return false;
        }

        try {
            User usuario = new User(nome, emailNormalizado, passwordEncoder.encode(password), fotoBytes, contentType);userRepository.saveAndFlush(usuario);
            return true;
        }
        catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    public Optional<User> login(String email, String password) {
        if (email == null || password == null || email.isBlank() || password.isEmpty()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(normalizarEmail(email)).filter(usuario -> usuario.getPassword() != null && !usuario.getPassword().isBlank() && passwordEncoder.matches(password, usuario.getPassword()));
    }

    public Optional<User> buscarPorId(Long id) {
        return userRepository.findById(id);
    }
}

