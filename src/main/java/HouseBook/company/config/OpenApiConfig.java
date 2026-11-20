package HouseBook.company.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "HouseBook API", version = "1.0.0", description = "API da plataforma HouseBook para autenticação, biblioteca pessoal, catálogo e leitura de livros. A documentação descreve os contratos disponíveis no código da aplicação.", contact = @Contact(name = "João — proprietário e desenvolvedor")))
@SecurityScheme(name = "sessionCookie", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE, paramName = "JSESSIONID", description = "Cookie de sessão criado pelo login em /user/login. Envie-o nas requisições protegidas.")
public class OpenApiConfig {
}