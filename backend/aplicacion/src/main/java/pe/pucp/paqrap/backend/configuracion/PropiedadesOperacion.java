package pe.pucp.paqrap.backend.configuracion;

import java.util.EnumMap;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;

/**
 * Parámetros operativos compartidos por el planificador, leídos de {@code paqrap.planificador.operacion}.
 *
 * <p>Los valores por defecto de {@code application.yml} coinciden con {@link ParametrosOperacion#porDefecto()}:
 * servicio de 60 min por entrega, turnos de 8 h desde las 07:00, refrigerio de 60 min que inicia entre la 1.ª y la
 * 7.ª hora del turno, partes de hasta 4 paquetes y velocidades 40/25/12 km/h (LE067–LE070). Las reglas que
 * relacionan varios campos las valida el constructor de {@link ParametrosOperacion} al arrancar.
 *
 * @param servicioMinutos              minutos de atención por entrega (también por entrega parcial)
 * @param plazoIncluyeServicio         si el fin del servicio debe caer antes del plazo (discrepancia abierta con
 *                                     la respuesta 11 del Q&amp;A del curso)
 * @param turnoMinutos                 duración del turno en minutos (divisor de 1440)
 * @param inicioTurnoMinuto            minuto del día en que empieza el primer turno (420 = 07:00)
 * @param descansoDesde                inicio más temprano del refrigerio, en minutos desde el inicio del turno
 * @param descansoHasta                inicio más tardío del refrigerio, en minutos desde el inicio del turno
 * @param descansoMinutos              duración del refrigerio
 * @param tamanioParte                 paquetes máximos por parte de pedido
 * @param costoFijoVehiculo            costo fijo por vehículo utilizado (S/), solo informativo
 * @param penalizacionPaquetePendiente parámetro histórico conservado por compatibilidad
 * @param velocidades                  velocidad en km/h por tipo de vehículo (TA, TM, TB)
 */
@Validated
@ConfigurationProperties(prefix = "paqrap.planificador.operacion")
public record PropiedadesOperacion(
        @PositiveOrZero int servicioMinutos,
        boolean plazoIncluyeServicio,
        @Positive int turnoMinutos,
        @PositiveOrZero @Max(1439) int inicioTurnoMinuto,
        @PositiveOrZero int descansoDesde,
        @PositiveOrZero int descansoHasta,
        @Positive int descansoMinutos,
        @Positive int tamanioParte,
        @PositiveOrZero double costoFijoVehiculo,
        @Positive double penalizacionPaquetePendiente,
        @NotEmpty Map<TipoVehiculo, Double> velocidades) {

    /**
     * Convierte las propiedades en los parámetros inmutables del núcleo.
     *
     * @return parámetros de operación validados
     * @throws IllegalArgumentException si la combinación de valores es inválida (p. ej. refrigerio fuera del turno)
     */
    public ParametrosOperacion aParametros() {
        var porTipo = new EnumMap<TipoVehiculo, Double>(TipoVehiculo.class);
        porTipo.putAll(velocidades);
        return new ParametrosOperacion(servicioMinutos, plazoIncluyeServicio, turnoMinutos, inicioTurnoMinuto,
                descansoDesde, descansoHasta, descansoMinutos, tamanioParte, costoFijoVehiculo,
                penalizacionPaquetePendiente, porTipo);
    }
}
