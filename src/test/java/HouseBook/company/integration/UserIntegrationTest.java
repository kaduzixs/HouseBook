
package HouseBook.company.integration;

import HouseBook.company.model.User;
import HouseBook.company.model.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // TESTE 1: Nome vazio deve ser rejeitado
    @Test
    void naoDeveCadastrarUsuarioComNomeVazio() throws Exception {

        long quantidadeAntes = userRepository.count();

        mockMvc.perform(multipart("/user/register")
                        .param("name", "   ")
                        .param("email", "teste1@email.com")
                        .param("password", "SenhaForte123")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        assertEquals(quantidadeAntes, userRepository.count());
    }

    // TESTE 2: E-mail inválido deve ser rejeitado
    @Test
    void notRegister() throws Exception {

        long quantidadeAntes = userRepository.count();

        mockMvc.perform(multipart("/user/register").param("name", "João").param("email", "email-invalido").param("password", "SenhaForte123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isBadRequest());
        assertEquals(quantidadeAntes, userRepository.count());
    }

    // TESTE 3: Senha curta deve ser rejeitada
    @Test
    void naoDeveCadastrarUsuarioComSenhaCurta() throws Exception {

        long quantidadeAntes = userRepository.count();

        mockMvc.perform(multipart("/user/register")
                        .param("name", "João")
                        .param("email", "teste3@email.com")
                        .param("password", "123")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        assertEquals(quantidadeAntes, userRepository.count());
    }

    // TESTE 4: Não deve permitir e-mail duplicado
    @Test
    void naoDevePermitirEmailDuplicado() throws Exception {

        mockMvc.perform(multipart("/user/register")
                        .param("name", "João")
                        .param("email", "duplicado@email.com")
                        .param("password", "SenhaForte123")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/user/register")
                        .param("name", "Outro usuário")
                        .param("email", "duplicado@email.com")
                        .param("password", "OutraSenha123")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict());

        assertEquals(
                1,
                userRepository.count()
        );
    }

    // TESTE 5: Banco não deve aceitar nome nulo
    @Test
    void bancoNaoDevePermitirNomeNulo() {

        User usuario = new User(
                null,
                "nulo@email.com",
                "senhaCriptografada",
                null,
                null
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(usuario)
        );
    }

    // TESTE 6: Cadastro válido deve funcionar
    @Test
    void deveCadastrarUsuarioComDadosValidos() throws Exception {

        mockMvc.perform(multipart("/user/register")
                        .param("name", "João Guilherme")
                        .param("email", "valido@email.com")
                        .param("password", "SenhaForte123")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());

        User usuario = userRepository
                .findByEmail("valido@email.com")
                .orElseThrow();

        assertEquals("João Guilherme", usuario.getName());
        assertEquals("valido@email.com", usuario.getEmail());

        assertNotEquals("SenhaForte123", usuario.getPassword());

        assertTrue(
                passwordEncoder.matches(
                        "SenhaForte123",
                        usuario.getPassword()
                )
        );
    }
}
