package HouseBook.company.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimithing extends OncePerRequestFilter {

    private static final long MINUTE = Duration.ofMinutes(1).toMillis();
    private static final long HOUR = Duration.ofHours(1).toMillis();

    private static final Limit LOGIN_LIMIT = new Limit(5, MINUTE);
    private static final Limit REGISTER_LIMIT = new Limit(3, HOUR);
    private static final Limit GENERAL_LIMIT = new Limit(120, MINUTE);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isStaticResource(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Limit limit = resolveLimit(request);
        String clientIp = request.getRemoteAddr();
        String key = clientIp + ":" + resolveGroup(request);
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (ignored, current) -> current == null || now >= current.resetAt ? new Window(now + limit.windowMillis) : current);

        synchronized (window) {
            if (window.requests >= limit.maxRequests) {
                long retryAfterMillis = Math.max(1, window.resetAt - now);
                long retryAfterSeconds = Math.max(1,
                        (long) Math.ceil(retryAfterMillis / 1000.0));

                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit.maxRequests));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Reset", String.valueOf((window.resetAt + 999) / 1000));

                response.getWriter().write("{\\"status\\":429," + "\\"message\\":\\"Limite de requisições excedido. Tente novamente mais tarde.\\"," + "\\"retryAfterSeconds\\":" + retryAfterSeconds + "}");
                return;
            }

            window.requests++;
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit.maxRequests));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(limit.maxRequests - window.requests));
            response.setHeader("X-RateLimit-Reset", String.valueOf((window.resetAt + 999) / 1000));
        }

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now >= entry.getValue().resetAt);
        }

        filterChain.doFilter(request, response);
    }

    private Limit resolveLimit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/user/login".equals(path)) {
            return LOGIN_LIMIT;
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/user/register".equals(path)) {
            return REGISTER_LIMIT;
        }
        return GENERAL_LIMIT;
    }

    private String resolveGroup(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/user/login".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            return "login";
        }
        if ("/user/register".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            return "register";
        }
        return "general";
    }

    private boolean isStaticResource(String path) {
        return path.equals("/") || path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".ico") || path.startsWith("/images/") || path.startsWith("/assets/");
    }

    private record Limit(int maxRequests, long windowMillis) {}

    private static final class Window {
        private int requests;
        private final long resetAt;

        private Window(long resetAt) {
            this.resetAt = resetAt;
        }
    }
}