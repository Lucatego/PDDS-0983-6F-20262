package pe.pucp.paqrap.backend.tiemporeal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion.Escenario;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion;

/**
 * Evento de la bitácora en la forma del contrato del frontend ({@code LogEvent} de {@code src/domain/types.ts}),
 * difundido por {@code /topic/simulacion/eventos} (LE054, B-09).
 *
 * @param id     secuencia del evento dentro de la ejecución (la misma que persiste la bitácora, B-07)
 * @param simMin minutos de simulación desde las 00:00 del día de inicio
 * @param text   texto para el usuario; admite {@code **negrita**}
 * @param kind   {@code good}, {@code warning}, {@code critical} o {@code accent}
 */
public record EventoLog(long id, long simMin, String text, String kind) {

    /**
     * Convierte un evento del motor en el {@code LogEvent} del frontend.
     *
     * @param evento     evento registrado por {@link MotorSimulacion}
     * @param medianoche instante 00:00 del día de inicio de la ejecución (minuto 0 del contrato)
     * @param escenario  escenario de la ejecución (el texto de cierre depende de él)
     * @return evento listo para serializar
     */
    public static EventoLog desde(MotorSimulacion.Evento evento, LocalDateTime medianoche, Escenario escenario) {
        long minuto = ChronoUnit.MINUTES.between(medianoche, evento.fecha());
        return new EventoLog(evento.secuencia(), minuto, texto(evento, escenario), clase(evento.tipo()));
    }

    private static String texto(MotorSimulacion.Evento evento, Escenario escenario) {
        String mensaje = evento.mensaje() == null ? evento.tipo() : evento.mensaje();
        return switch (evento.tipo()) {
            // El frontend reconoce estos dos textos para mostrar avisos (SimulationBridge.tsx).
            case "COLAPSO" -> "**Colapso logístico**" + (evento.pedido() == null ? "" : ": el pedido **"
                    + evento.pedido() + "** no pudo cumplirse") + " (" + mensaje + ").";
            case "EJECUCION_FINALIZADA" -> escenario == Escenario.SIMULACION_5D
                    ? "**Simulación 5D completada** (" + mensaje + ")."
                    : "**Ejecución finalizada** (" + mensaje + ").";
            case "EJECUCION_DETENIDA" -> "DETENIDA_POR_USUARIO".equals(mensaje)
                    ? "**Ejecución detenida** por el usuario."
                    : "**Ejecución detenida** (" + mensaje + ").";
            default -> resaltar(mensaje, evento.pedido(), evento.vehiculo(), evento.almacen());
        };
    }

    /** Pone en negrita la primera aparición de cada código (pedido, unidad, almacén) dentro del mensaje. */
    private static String resaltar(String mensaje, String... codigos) {
        String resultado = mensaje;
        for (String codigo : codigos) {
            if (codigo == null || codigo.isBlank() || resultado.contains("**" + codigo + "**")) continue;
            int posicion = resultado.indexOf(codigo);
            if (posicion >= 0) {
                resultado = resultado.substring(0, posicion) + "**" + codigo + "**"
                        + resultado.substring(posicion + codigo.length());
            }
        }
        return resultado;
    }

    private static String clase(String tipo) {
        return switch (tipo) {
            case "COLAPSO", "PEDIDO_NO_CUMPLIDO", "AVERIA_REGISTRADA" -> "critical";
            case "BLOQUEO_REGISTRADO", "MANTENIMIENTO_INICIADO", "EJECUCION_PAUSADA", "EJECUCION_DETENIDA" -> "warning";
            case "PEDIDO_ENTREGADO", "VEHICULO_RETORNO", "EJECUCION_FINALIZADA" -> "good";
            default -> "accent";
        };
    }
}
