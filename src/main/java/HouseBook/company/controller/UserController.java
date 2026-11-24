
package HouseBook.company.controller;

import HouseBook.company.model.User.User;
import HouseBook.company.service.UserService;

import jakarta.servlet.http.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@ApiResponse(responseCode = "429", description = "Limite de requisições excedido. Consulte Retry-After para saber quando tentar novamente.")
@RestController
@Tag(name = "Conta e autenticação", description = "Cadastro, login, sessão, perfil e token CSRF.")
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Entrar", description = "Autentica por e-mail e senha e cria uma sessão HTTP. O cookie JSESSIONID deve ser mantido pelo cliente.")
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam(required = false) String email, @RequestParam(required = false) String password, HttpServletRequest request, HttpServletResponse response) {

        Optional<User> usuario = userService.login(email, password);
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("E-mail ou senha inválidos.");
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();

        var auth = new UsernamePasswordAuthenticationToken(usuario.get().getId(), null, List.of());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        session.setAttribute("userId", usuario.get().getId());
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        return ResponseEntity.ok("Login realizado com sucesso!");
    }

    @Operation(summary = "Cadastrar usuário", description = "Cria uma conta com nome, e-mail, senha e foto opcional (JPG, PNG ou WEBP, até 2 MB).")
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> register(@RequestParam String name, @RequestParam String email, @RequestParam String password, @RequestParam(value = "foto", required = false) MultipartFile foto) {
        try {
            boolean registered = userService.register(name, email, password, foto);
            if (registered) {
                return ResponseEntity.status(HttpStatus.CREATED).body("Usuário cadastrado com sucesso!");
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Este e-mail já está cadastrado.");
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @Operation(summary = "Obter foto do perfil", description = "Retorna a imagem do usuário autenticado, quando cadastrada.")
    @SecurityRequirement(name = "sessionCookie")
    @GetMapping("/foto")
    public ResponseEntity<byte[]> foto(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return userService.buscarPorId(userId).filter(user -> user.getFoto() != null && user.getFotoContentType() != null).map(user -> ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").contentType(MediaType.parseMediaType(user.getFotoContentType())).body(user.getFoto())).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Atualizar perfil", description = "Atualiza o nome e, opcionalmente, a foto do usuário autenticado.")
    @SecurityRequirement(name = "sessionCookie")
    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> updateProfile(@RequestParam String name, @RequestParam(value = "foto", required = false) MultipartFile foto, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sua sessão expirou. Entre novamente.");
        }

        try {
            userService.atualizarPerfil(userId, name, foto);
            return ResponseEntity.ok("Perfil atualizado com sucesso!");
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @Operation(summary = "Consultar minha conta", description = "Retorna identificador, nome, e-mail e indicador de foto da sessão autenticada.")
    @SecurityRequirement(name = "sessionCookie")
    @GetMapping("/me")
    public ResponseEntity<?> meusDados(Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return userService.buscarPorId(userId).map(user -> ResponseEntity.ok(new UsuarioResponse(user.getId(), user.getName(), user.getEmail(), user.getFoto() != null))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record UsuarioResponse(Long id, String name, String email, boolean hasPhoto) {}


    @Operation(summary = "Sair", description = "Invalida a sessão HTTP atual.")
    @SecurityRequirement(name = "sessionCookie")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logout realizado com sucesso!");
    }

    @Operation(summary = "Obter token CSRF", description = "Retorna o token CSRF necessário para requisições protegidas por CSRF.")
    @GetMapping("/csrf")
    public ResponseEntity<CsrfToken> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(csrfToken);
    }
}