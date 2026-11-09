
package controller;

import model.User;
import service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam(required = false) String email, @RequestParam(required = false) String password, HttpServletRequest request) {
        Optional<User> usuario = userService.login(email, password);

        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("E-mail ou senha inválidos.");
        }

        HttpSession session = request.getSession(true);

        request.changeSessionId();

        session.setAttribute("userId", usuario.get().getId());

        return ResponseEntity.ok("Login realizado com sucesso!");
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestParam(required = false) String email, @RequestParam(required = false) String password) {
        try {
            boolean registered = userService.register(email, password);

            if (registered) {
                return ResponseEntity.status(HttpStatus.CREATED).body("Usuário cadastrado com sucesso!");
            }

            return ResponseEntity.status(HttpStatus.CONFLICT).body("Este e-mail já está cadastrado.");

        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("Logout realizado com sucesso!");
    }
}