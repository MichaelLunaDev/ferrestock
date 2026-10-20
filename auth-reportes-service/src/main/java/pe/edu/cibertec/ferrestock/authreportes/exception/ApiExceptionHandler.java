package pe.edu.cibertec.ferrestock.authreportes.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiProblem.class) public ResponseEntity<Map<String,String>> problem(ApiProblem ex){return response(ex.status(),ex.code());}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class,jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception ignored){return response(400,"SOLICITUD_INVALIDA");}
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String,String>> unsupported(Exception ignored){return response(415,"TIPO_CONTENIDO_NO_SOPORTADO");}
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String,String>> unacceptable(Exception ignored){return response(406,"FORMATO_RESPUESTA_NO_ADMITIDO");}
    @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,String>> unexpected(Exception ignored){return response(503,"SERVICIO_NO_DISPONIBLE");}
    private ResponseEntity<Map<String,String>> response(int status,String code){
        var builder=ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore());
        if(status==429) builder.header("Retry-After","60");
        if(status==401) builder.header(HttpHeaders.WWW_AUTHENTICATE,"Bearer");
        return builder.body(Map.of("code",code));
    }
}
