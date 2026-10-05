package pe.pucp.paqrap.backend.persistencia;

/** Resumen de servicio, independiente del DTO HTTP que se incorporara en B-08. */
public record ResultadoCarga(long archivoId, int aceptados, int rechazados, boolean reutilizado,
        String estado, String mensaje) {
    static ResultadoCarga de(ArchivoCarga archivo, boolean reutilizado) {
        return new ResultadoCarga(archivo.getId(), archivo.getRegistrosValidos(), archivo.getRegistrosRechazados(),
                reutilizado, archivo.getEstado(), archivo.getMensaje());
    }
}
