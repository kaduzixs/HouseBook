package HouseBook.company.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "HouseBook API", version = "1.0.0", description = "API da plataforma HouseBook para autenticação, biblioteca pessoal, catálogo e leitura de livros. " + "As rotas estão sujeitas a Rate Limiting por endereço IP: login (5 requisições/minuto), cadastro (3 requisições/hora) " + "e demais rotas dinâmicas (120 requisições/minuto). Ao exceder o limite, a API retorna HTTP 429 e o cabeçalho Retry-After.", contact = @Contact(name = "João — proprietário e desenvolvedor")))
@SecurityScheme(name = "sessionCookie", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE, paramName = "JSESSIONID", description = "Cookie de sessão criado pelo login em /user/login. Envie-o nas requisições protegidas.")
public class OpenApiConfig {
}