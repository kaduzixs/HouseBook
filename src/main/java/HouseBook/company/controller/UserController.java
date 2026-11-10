
package HouseBook.company.controller;


import HouseBook.company.model.User;
import HouseBook.company.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;


import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam(required = false) String email, @RequestParam(required = false) String password, HttpServletRequest request, HttpServletResponse response){

        Optional<User> usuario = userService.login(email, password);

        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("E-mail ou senha inválidos.");
        }

        // Cria ou recupera a sessão
        HttpSession session = request.getSession(true);

        // Proteção contra Session Fixation
        request.changeSessionId();

        // Cria a autenticação do usuário
        var auth = new UsernamePasswordAuthenticationToken(usuario.get().getId(), null, List.of());

        // Cria o contexto de segurança
        var context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(auth);

        // Registra o contexto no Spring Security
        SecurityContextHolder.setContext(context);

        // Mantém o ID do usuário na sessão
        session.setAttribute("userId", usuario.get().getId());

        // Persiste a autenticação na sessão HTTP
        var securityContextRepository = new HttpSessionSecurityContextRepository();

        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok("Login realizado com sucesso!");
    }



    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> register(@RequestParam String name, @RequestParam String email, @RequestParam String password, @RequestParam String fotoUrl) {

        try {
            boolean registered = userService.register(name, email, password, fotoUrl);

            if (registered) {
                return ResponseEntity.status(HttpStatus.CREATED).body("Usuário cadastrado com sucesso!");
            }

            return ResponseEntity.status(HttpStatus.CONFLICT).body("Este e-mail já está cadastrado.");

        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());

        }
        catch (IOException ex) {
            return ResponseEntity.internalServerError().body("Erro ao processar a foto do usuário.");
        }
    }


    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("Logout realizado com sucesso!");
    }
}