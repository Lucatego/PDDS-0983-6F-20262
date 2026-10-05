/**
 * Persistencia PostgreSQL mediante JPA y carga de archivos (B-03/B-04).
 *
 * <p>La carga conserva identificadores deterministas (LE008/009), registra errores por linea (LE011)
 * y usa una transaccion por archivo. Las migraciones requieren activacion explicita tras revisar el SQL.
 */
package pe.pucp.paqrap.backend.persistencia;
