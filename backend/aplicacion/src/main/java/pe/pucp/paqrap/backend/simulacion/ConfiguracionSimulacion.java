package pe.pucp.paqrap.backend.simulacion;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;

/** Parametros de una ejecucion, independientes del reloj real y del transporte HTTP (LE053/067/069, B-06). */
public record ConfiguracionSimulacion(Escenario escenario, LocalDateTime inicio, Map<TipoVehiculo, Integer> flota,
        Map<String, Integer> capacidades, int saMinutos, double aceleracion, long semilla,
        ParametrosOperacion operacion, boolean considerarIncidencias, boolean averiasAleatorias,
        double tasaAveriasDia, boolean trasvaseHabilitado) {

    public enum Escenario { DIA_A_DIA, SIMULACION_5D, COLAPSO }

    public ConfiguracionSimulacion(Escenario escenario, LocalDateTime inicio, Map<TipoVehiculo, Integer> flota,
            Map<String, Integer> capacidades, int saMinutos, double aceleracion, long semilla,
            ParametrosOperacion operacion) {
        this(escenario, inicio, flota, capacidades, saMinutos, aceleracion, semilla, operacion, false, false, 0.0, false);
    }

    public ConfiguracionSimulacion {
        Objects.requireNonNull(escenario);
        Objects.requireNonNull(inicio);
        Objects.requireNonNull(operacion);
        flota = Map.copyOf(flota);
        capacidades = Map.copyOf(capacidades);
        int unidades = 0;
        for (var tipo : TipoVehiculo.values()) {
            Integer cantidad = flota.get(tipo);
            if (cantidad == null || cantidad < 0 || cantidad > 99) {
                throw new IllegalArgumentException("Flota por tipo entre 0 y 99");
            }
            unidades += cantidad;
        }
        if (unidades == 0 || saMinutos <= 0 || saMinutos > 1440 || !Double.isFinite(aceleracion)
                || aceleracion <= 0 || aceleracion > 240) {
            throw new IllegalArgumentException("Flota, ciclo o aceleracion invalida");
        }
        if (tasaAveriasDia < 0) {
            throw new IllegalArgumentException("Tasa de averias no puede ser negativa");
        }
        for (String almacen : new String[] {"NOROESTE", "ESTE"}) {
            if (!capacidades.containsKey(almacen) || capacidades.get(almacen) <= 0) {
                throw new IllegalArgumentException("Capacidades de almacenes deben ser positivas");
            }
        }
    }

    public LocalDateTime finHorizonte() {
        return escenario == Escenario.SIMULACION_5D ? inicio.plusDays(5) : null;
    }

    public double minutosPorSegundo() {
        return escenario == Escenario.DIA_A_DIA ? 1.0 / 60 : aceleracion;
    }
}
