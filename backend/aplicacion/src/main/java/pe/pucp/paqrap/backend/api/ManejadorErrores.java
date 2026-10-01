package pe.pucp.paqrap.backend.api;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce las excepciones de la API al cuerpo {@code { "mensaje": "..." }} del contrato del frontend.
 *
 * <p>Los errores de validación de entrada responden 400 (el estándar de programación pide validar en el punto de
 * carga); los recursos inexistentes, 404; los métodos no admitidos, 405; cualquier otro error, 500 sin exponer
 * detalles internos. La infactibilidad de una planificación no es un error: se informa como estado del resultado.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    /**
     * Cuerpo JSON que no se pudo leer (sintaxis o tipos incorrectos).
     *
     * @param ex excepción de Spring MVC
     * @return 400 con mensaje
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido o está mal formado.");
    }

    /**
     * Violaciones de Bean Validation en el cuerpo de la solicitud ({@code @Valid}).
     *
     * @param ex excepción con los errores de campo
     * @return 400 con la lista de campos inválidos, en orden alfabético para que el mensaje sea estable
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> argumentoInvalido(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + mensajeDe(error))
                .sorted()
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos. " + detalle);
    }

    /**
     * Violaciones de Bean Validation en parámetros validados fuera del cuerpo.
     *
     * @param ex excepción con las violaciones
     * @return 400 con la lista de violaciones, ordenada
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespuestaError> restriccionesVioladas(ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos. " + detalle);
    }

    /**
     * Parámetro de ruta o de consulta con tipo incorrecto.
     *
     * @param ex excepción de conversión
     * @return 400 con el nombre del parámetro
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaError> tipoIncorrecto(MethodArgumentTypeMismatchException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Valor inválido para el parámetro '" + ex.getName() + "'.");
    }

    /**
     * Validaciones del dominio: los records del planificador lanzan {@link IllegalArgumentException} cuando los
     * datos de entrada son inválidos (nodo fuera del mapa, plazo no permitido, etc.).
     *
     * @param ex excepción de validación del dominio
     * @return 400 con el mensaje de la validación
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespuestaError> argumentoIlegal(IllegalArgumentException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage() == null ? "Solicitud inválida." : ex.getMessage());
    }

    /**
     * Ruta inexistente bajo la API.
     *
     * @param ex excepción de Spring MVC
     * @return 404 con mensaje
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaError> noEncontrado(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado: /" + ex.getResourcePath());
    }

    /**
     * Método HTTP no admitido por la ruta.
     *
     * @param ex excepción de Spring MVC
     * @return 405 con mensaje
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespuestaError> metodoNoAdmitido(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " no admitido para esta ruta.");
    }

    /**
     * Errores con estado explícito lanzados por los controladores o servicios.
     *
     * @param ex excepción con estado y motivo
     * @return el estado indicado y su motivo
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RespuestaError> conEstado(ResponseStatusException ex) {
        String motivo = ex.getReason() != null ? ex.getReason() : mensajeGenerico(ex.getStatusCode());
        return responder(ex.getStatusCode(), motivo);
    }

    /**
     * Cualquier otro error. Las excepciones estándar de Spring MVC que ya conocen su estado HTTP (tipo de contenido
     * no admitido, parámetro faltante, etc.) conservan ese estado con un mensaje genérico; el resto se registra en el
     * log y responde 500 sin exponer detalles internos al cliente.
     *
     * @param ex excepción no controlada por los manejadores anteriores
     * @return el estado de la excepción estándar, o 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> errorNoPrevisto(Exception ex) {
        if (ex instanceof ErrorResponse estandar && !estandar.getStatusCode().is5xxServerError()) {
            return responder(estandar.getStatusCode(), mensajeGenerico(estandar.getStatusCode()));
        }
        LOG.error("Error no controlado en la API", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
    }

    private static ResponseEntity<RespuestaError> responder(HttpStatusCode estado, String mensaje) {
        return ResponseEntity.status(estado).body(new RespuestaError(mensaje));
    }

    private static String mensajeDe(FieldError error) {
        return error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage();
    }

    private static String mensajeGenerico(HttpStatusCode estado) {
        if (estado.is5xxServerError()) {
            return "Error interno del servidor.";
        }
        return switch (estado.value()) {
            case 400 -> "Solicitud inválida.";
            case 404 -> "Recurso no encontrado.";
            case 405 -> "Método no admitido para esta ruta.";
            case 406 -> "Formato de respuesta no disponible.";
            case 415 -> "Tipo de contenido no admitido.";
            default -> "La solicitud no se pudo procesar.";
        };
    }
}
