package HouseBook.company.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Por padrão o Spring não envia a mensagem de ResponseStatusException ao navegador (só "Conflict",
 * "Bad Gateway"...). Este handler devolve o texto que escrevemos, mas apenas o dessas exceções:
 * erros inesperados continuam sem expor detalhes internos.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "Não foi possível concluir a operação.";
        return body(ex.getStatusCode(), message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo é maior que o limite permitido.");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("status", status.value());
        json.put("message", message);
        return ResponseEntity.status(status).body(json);
    }
}