package pe.pucp.paqrap.backend.persistencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.Pedido;

/** Resultado inmutable de validar un archivo antes de persistirlo (LE011). */
public record ArchivoAnalizado(TipoArchivo tipo, String nombre, Integer anio, Integer mes, Integer mesFin,
        String hash, List<Registro> registros, List<ErrorLinea> errores) {
    public ArchivoAnalizado {
        registros = List.copyOf(registros);
        errores = List.copyOf(errores);
    }

    public sealed interface Registro permits Venta, Cierre, Mantenimiento, Averia {
        int linea();
    }

    public record Venta(int linea, Pedido pedido) implements Registro { }
    public record Cierre(int linea, String codigo, Bloqueo bloqueo) implements Registro { }
    public record Mantenimiento(int linea, LocalDate fecha, String vehiculo) implements Registro { }
    public record Averia(int linea, LocalDateTime fecha, String vehiculo, short tipo) implements Registro { }
    public record ErrorLinea(int linea, String contenido, String motivo) { }
}
