package HouseBook.company.service;

import HouseBook.company.util.TextDecoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Baixa o texto integral de um livro do Project Gutenberg, com limites de tempo e de tamanho. */
@Component
public class GutenbergClient {

    private static final Logger log = LoggerFactory.getLogger(GutenbergClient.class);
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final int ATTEMPTS = 2;
    private static final long RETRY_PAUSE_MS = 800;
    // Teto para todas as tentativas juntas: o proxy do Render derruba a requisição antes de 100 s.
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(55);

    private final RestClient restClient;

    /** Texto baixado e o endereço de onde ele veio. */
    public record Fetched(String url, String text) {
    }

    public GutenbergClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(25));

        // O Gutenberg recusa (403) clientes sem User-Agent identificável, como o padrão do Java.
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "HouseBook/1.0 (leitor de livros; Project Gutenberg)")
                .build();
    }

    /**
     * Tenta cada endereço, na ordem, e devolve o primeiro texto encontrado.
     * Vazio = o Gutenberg não tem texto para este livro (todos responderam 404).
     * Lança 502 se algum endereço falhou por outro motivo (rede, 403, 5xx) e nenhum funcionou.
     */
    public Optional<Fetched> fetchFirstAvailable(List<String> urls) {
        ResponseStatusException lastError = null;
        long deadline = System.nanoTime() + TOTAL_BUDGET.toNanos();

        for (String url : urls) {
            if (lastError != null && System.nanoTime() > deadline) {
                log.warn("Tempo total esgotado ao baixar o texto; desistindo dos endereços restantes.");
                break;
            }
            URI uri = validate(url);
            try {
                byte[] body = download(uri);
                if (body == null || body.length == 0) {
                    log.warn("Project Gutenberg devolveu corpo vazio para {}.", uri);
                    continue;
                }
                if (body.length > MAX_BYTES) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Este livro é grande demais para ler no site.");
                }
                return Optional.of(new Fetched(uri.toString(), TextDecoding.decode(body)));
            }
            catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 404 || status == 410) {
                    log.info("Texto não encontrado em {} (HTTP {}).", uri, status);
                    continue;
                }
                if (status == 403 || status == 429) {
                    // O Gutenberg bloqueia por IP o acesso automatizado (principalmente de servidores hospedados).
                    // Todos os endereços ficam no mesmo site: insistir só prolonga o bloqueio.
                    log.error("BLOQUEIO: o Project Gutenberg respondeu HTTP {} para {}. O IP deste servidor provavelmente foi bloqueado por acesso automatizado.", status, uri);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Project Gutenberg bloqueou o acesso do servidor ao texto deste livro. Importe um arquivo TXT enquanto isso.", ex);
                }
                log.error("Project Gutenberg respondeu HTTP {} para {}.", status, uri, ex);
                lastError = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível baixar o texto do livro. Tente novamente em instantes.", ex);
            }
            catch (RestClientException ex) {
                log.error("Falha de conexão ao baixar {}.", uri, ex);
                lastError = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O servidor não conseguiu se conectar ao Project Gutenberg.", ex);
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        return Optional.empty();
    }

    public String fetchText(String url) {
        return fetchFirstAvailable(List.of(url)).map(Fetched::text).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "O texto deste livro não está disponível no Project Gutenberg."));
    }

    /** Uma nova tentativa em falhas de rede, 429 e 5xx (o Gutenberg oscila de vez em quando). */
    private byte[] download(URI uri) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.get().uri(uri).retrieve().body(byte[].class);
            }
            catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (attempt >= ATTEMPTS || !retryable) {
                    throw ex;
                }
            }
            catch (ResourceAccessException ex) {
                if (attempt >= ATTEMPTS) {
                    throw ex;
                }
            }
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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