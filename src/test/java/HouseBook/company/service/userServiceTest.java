package HouseBook.company.service;

import HouseBook.company.model.User.User;
import UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class userServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // TESTE 1: Cadastro de usuário
    @Test
    void registerHaveTwo() {

        when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.empty());

        when(passwordEncoder.encode("SenhaForte123")).thenReturn("$2a$hash");

        boolean result = userService.register(" João ", " JOAO@EMAIL.COM ", "SenhaForte123", null);

        assertTrue(result);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(captor.capture());

        User salvo = captor.getValue();

        assertEquals("João", salvo.getName());
        assertEquals("joao@email.com", salvo.getEmail());
        assertEquals("$2a$hash", salvo.getPassword());

        assertNull(salvo.getFoto());

        verify(passwordEncoder).encode("SenhaForte123");
    }

    // TESTE 2: Autenticação do usuário
    @Test
    void autenticatePassword() {

        User usuario = new User("João", "joao@email.com", "$2a$hash", null, null);

        when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaForte123", "$2a$hash")).thenReturn(true);
        when(passwordEncoder.matches("senha-errada", "$2a$hash")).thenReturn(false);

        // Senha correta
        assertTrue(userService.login(" JOAO@EMAIL.COM ", "SenhaForte123").isPresent());

        // Senha incorreta
        assertTrue(userService.login("joao@email.com", "senha-errada").isEmpty());
        verify(userRepository, times(2)).findByEmail("joao@email.com");
        verify(passwordEncoder).matches("SenhaForte123", "$2a$hash");
        verify(passwordEncoder).matches("senha-errada", "$2a$hash");
    }
}
