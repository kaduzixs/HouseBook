
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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:housebooktest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop"})
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
    void notRegisterUserNull() throws Exception {

        long qtdBefore = userRepository.count();

        mockMvc.perform(multipart("/user/register").param("name", "   ").param("email", "teste1@email.com").param("password", "SenhaForte123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isBadRequest());

        assertEquals(qtdBefore, userRepository.count());
    }

    // TESTE 2: E-mail inválido deve ser rejeitado
    @Test
    void notRegisterInvalidEmail() throws Exception {

        long qtdBefore = userRepository.count();

        mockMvc.perform(multipart("/user/register").param("name", "João").param("email", "email-invalido").param("password", "SenhaForte123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isBadRequest());
        assertEquals(qtdBefore, userRepository.count());
    }

    // TESTE 3: Senha curta deve ser rejeitada
    @Test
    void notPasswordLittle() throws Exception {

        long qtdBefore = userRepository.count();

        mockMvc.perform(multipart("/user/register").param("name", "João").param("email", "teste3@email.com").param("password", "123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isBadRequest());

        assertEquals(qtdBefore, userRepository.count());
    }

    // TESTE 4: Não deve permitir e-mail duplicado
    @Test
    void notDuplicateEmail() throws Exception {

        mockMvc.perform(multipart("/user/register").param("name", "João").param("email", "duplicado@email.com").param("password", "SenhaForte123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isCreated());

        mockMvc.perform(multipart("/user/register").param("name", "Outro usuário").param("email", "duplicado@email.com").param("password", "OutraSenha123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isConflict());

        assertEquals(1, userRepository.count());
    }

    // TESTE 5: Banco não deve aceitar nome nulo
    @Test
    void bankNameNull() {
        User user = new User(null, "nulo@email.com", "senhaCriptografada", null, null);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(user));
    }

    // TESTE 6: Cadastro válido deve funcionar
    @Test
    void registerValid() throws Exception {

        mockMvc.perform(multipart("/user/register").param("name", "João Guilherme").param("email", "valido@email.com").param("password", "SenhaForte123").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isCreated());

        User usuario = userRepository.findByEmail("valido@email.com").orElseThrow();

        assertEquals("João Guilherme", usuario.getName());
        assertEquals("valido@email.com", usuario.getEmail());

        assertNotEquals("SenhaForte123", usuario.getPassword());

        assertTrue(passwordEncoder.matches("SenhaForte123", usuario.getPassword()));
    }

    // TESTE 7 - BRECHA NA SEGURANÇA: O banco não deve aceitar nome vazio
    @Test
    void verifyBankNull() {

        User usuario = new User("", "bypass@email.com", "senhaCriptografada", null, null);
        User salvo = userRepository.saveAndFlush(usuario);
        assertNotNull(salvo.getId());
        System.out.println("ALERTA: O banco aceitou um usuário com nome vazio!");
    }
}
