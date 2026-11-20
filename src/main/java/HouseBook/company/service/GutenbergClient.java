package HouseBook.company.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/** Baixa o texto integral de um livro do Project Gutenberg, com limites de tempo e de tamanho. */
@Component
public class GutenbergClient {

    private static final Logger log = LoggerFactory.getLogger(GutenbergClient.class);
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private final RestClient restClient;

    public GutenbergClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String fetchText(String url) {
        URI uri = validate(url);
        try {
            byte[] body = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Project Gutenberg devolveu um texto vazio.");
            }
            if (body.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Este livro é grande demais para ler no site.");
            }
            return new String(body, StandardCharsets.UTF_8);
        }
        catch (RestClientResponseException ex) {
            log.error("Project Gutenberg respondeu HTTP {} para {}.", ex.getStatusCode().value(), uri, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível baixar o texto do livro. Tente novamente em instantes.", ex);
        }
        catch (RestClientException ex) {
            log.error("Falha de conexão ao baixar {}.", uri, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O servidor não conseguiu se conectar ao Project Gutenberg.", ex);
        }
    }

    /** Só aceita endereços do próprio Gutenberg, para que a URL salva nunca aponte para outro lugar. */
    private static URI validate(String url) {
        try {
            URI uri = URI.create(url.trim().replaceFirst("^http://", "https://"));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean gutenberg = host.equals("gutenberg.org") || host.endsWith(".gutenberg.org");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !gutenberg) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço de texto não permitido.");
            }
            return uri;
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço de texto inválido.", ex);
        }
    }
}