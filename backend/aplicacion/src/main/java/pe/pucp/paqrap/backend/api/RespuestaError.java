package pe.pucp.paqrap.backend.api;

/**
 * Cuerpo de error de la API, según el contrato del frontend ({@code frontend/README.md}): estado HTTP 4xx/5xx con
 * {@code { "mensaje": "..." }}.
 *
 * @param mensaje descripción legible del error, en español
 */
public record RespuestaError(String mensaje) {
}
