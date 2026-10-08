package pe.pucp.paqrap.backend.simulacion;

import java.time.Duration;
import java.time.LocalDateTime;

/** Aplica el catálogo de duración de averías al instante programado (DD-05/LE072). */
public final class ReglaAveria {
    public static LocalDateTime calcularFin(String regla, LocalDateTime inicio, Integer minutosFijos,
            Integer diasMinimos, Integer minutoTurnoRetorno, int turnoMinutos, int inicioTurnoMinuto) {
        return switch (regla) {
            case "DURACION_FIJA" -> inicio.plusMinutes(requerido(minutosFijos, "minutos_inoperativa"));
            case "FIN_TURNO_SIGUIENTE" -> finTurnoSiguiente(inicio, turnoMinutos, inicioTurnoMinuto);
            case "DIAS_Y_TURNO" -> primerTurnoTras(inicio.plusDays(requerido(diasMinimos, "dias_minimos")),
                    requerido(minutoTurnoRetorno, "minuto_inicio_turno_retorno"));
            default -> throw new IllegalArgumentException("Regla de avería desconocida: " + regla);
        };
    }

    private static LocalDateTime finTurnoSiguiente(LocalDateTime inicio, int turnoMinutos, int inicioTurnoMinuto) {
        if (turnoMinutos <= 0 || 1440 % turnoMinutos != 0) {
            throw new IllegalArgumentException("Duración de turno inválida");
        }
        LocalDateTime ancla = inicio.toLocalDate().atStartOfDay().plusMinutes(inicioTurnoMinuto);
        if (ancla.isAfter(inicio)) ancla = ancla.minusDays(1);
        long turnosTranscurridos = Math.floorDiv(Duration.between(ancla, inicio).toMinutes(), turnoMinutos);
        return ancla.plusMinutes((turnosTranscurridos + 2) * turnoMinutos);
    }

    private static LocalDateTime primerTurnoTras(LocalDateTime umbral, int minutoInicio) {
        LocalDateTime retorno = umbral.toLocalDate().atStartOfDay().plusMinutes(minutoInicio);
        return retorno.isBefore(umbral) ? retorno.plusDays(1) : retorno;
    }

    private static int requerido(Integer valor, String campo) {
        if (valor == null || valor <= 0) throw new IllegalArgumentException("Falta " + campo + " en el catálogo");
        return valor;
    }

    private ReglaAveria() { }
}
