package pe.pucp.paqrap.backend.simulacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcula los indicadores consolidados y el resumen de la ejecución (B-07, LE056/061-064).
 */
public final class GeneradorResumen {

    public record IndicadorPlazo(int plazoHoras, int pedidosTotales, int pedidosEntregados,
            int pedidosEnPlazo, double enPlazoPct) { }

    public record Resumen(
            boolean esParcial,
            double duracionSimuladaDias,
            long duracionRealMs,
            int ciclos,
            int ejecucionesPlanificador,
            double taTotalMs,
            double taPromedioMs,
            double taMaxMs,
            int pedidosTotales,
            int pedidosEntregados,
            int pedidosEntregadosEnPlazo,
            int pedidosNoCumplidos,
            int pedidosPendientesCierre,
            int pedidosReprogramados,
            int pedidosIncumplidosReprogramados,
            double cumplimientoPct,
            double incumplidosReprogramacionPct,
            int paquetesEntregados,
            Double holguraRealPromedioMin,
            Double holguraRealMinimaMin,
            Double tiempoEntregaPromedioH,
            Double tiempoEntregaMinimoH,
            Double tiempoEntregaMaximoH,
            int vehiculosDisponibles,
            int vehiculosUtilizados,
            double utilizacionFlotaPct,
            double utilizacionCapacidadPct,
            int rutasDespachadas,
            double distanciaTotalKm,
            double tiempoRutasMin,
            double costoTotal,
            int incidenciasTotales,
            int bloqueosTotales,
            int averiasTotales,
            int averiasTipo1,
            int averiasTipo2,
            int averiasTipo3,
            int mantenimientosTotales,
            int incidenciasActivasCierre,
            Map<Integer, IndicadorPlazo> indicadoresPlazo
    ) { }

    public static Resumen generar(MotorSimulacion motor, boolean esParcial) {
        double dias = Duration.between(motor.configuracion().inicio(), motor.reloj()).toMinutes() / 1440.0;
        long realMs = motor.tiempoRealMs();
        int numCiclos = motor.ciclos().size();

        double taTotal = 0;
        double taMax = 0;
        for (var c : motor.ciclos()) {
            double ta = c.resultado().metricas().taMs();
            taTotal += ta;
            if (ta > taMax) taMax = ta;
        }
        double taPromedio = numCiclos > 0 ? taTotal / numCiclos : 0.0;

        int pedidosTotales = motor.pedidos().size();
        int entregados = 0;
        int entregadosEnPlazo = 0;
        int noCumplidos = 0;
        int pendientesCierre = 0;
        int paquetesEntregados = 0;
        int pedidosReprogramados = 0;
        int pedidosIncumplidosReprogramados = 0;
        var holgurasMin = new ArrayList<Double>();
        var tiemposEntregaH = new ArrayList<Double>();

        Map<Integer, int[]> porPlazo = new LinkedHashMap<>(); // [totales, entregados, enPlazo]
        for (int p : new int[]{4, 8, 12, 18, 36}) {
            porPlazo.put(p, new int[3]);
        }

        for (var pv : motor.pedidos().values()) {
            int plazo = pv.original.plazoHoras();
            int[] arr = porPlazo.computeIfAbsent(plazo, k -> new int[3]);
            arr[0]++;

            paquetesEntregados += pv.entregada;
            LocalDateTime fechaCumplimiento = motor.configuracion().operacion().plazoIncluyeServicio()
                    ? pv.entregaFinal : pv.llegadaFinal;
            boolean completo = pv.entregada == pv.original.cantidad();
            boolean enPlazo = completo && fechaCumplimiento != null
                    && !fechaCumplimiento.isAfter(pv.original.deadline());

            if (pv.noCumplido != null) {
                noCumplidos++;
            }
            if (completo) {
                entregados++;
                arr[1]++;
                if (enPlazo) {
                    entregadosEnPlazo++;
                    arr[2]++;
                }
                if (fechaCumplimiento != null) {
                    double horas = Duration.between(pv.original.fechaRegistro(), fechaCumplimiento).toMinutes() / 60.0;
                    tiemposEntregaH.add(horas);
                    holgurasMin.add(Duration.between(fechaCumplimiento, pv.original.deadline()).toSeconds() / 60.0);
                }
            } else if (pv.noCumplido == null) {
                pendientesCierre++;
            }
        }

        double cumplimientoPct = pedidosTotales > 0 ? (entregadosEnPlazo * 100.0 / pedidosTotales) : 100.0;

        Set<String> vehiculosUsados = motor.viajes().stream()
                .filter(v -> v.despachado)
                .map(v -> v.plan.ruta().vehiculo())
                .collect(Collectors.toSet());

        int vehiculosDisponibles = motor.flotaProyectada().size();
        int vehiculosUtilizados = vehiculosUsados.size();
        double utilizacionFlota = vehiculosDisponibles > 0 ? (vehiculosUtilizados * 100.0 / vehiculosDisponibles) : 0.0;

        double cargaDespachada = 0;
        double capacidadDespachada = 0;
        for (var viaje : motor.viajes()) {
            if (!viaje.despachado) continue;
            cargaDespachada += viaje.plan.ruta().carga();
            capacidadDespachada += pe.pucp.paqrap.estricto.modelo.TipoVehiculo
                    .desdeCodigo(viaje.plan.ruta().vehiculo()).capacidad();
        }
        double utilizacionCapacidad = capacidadDespachada > 0 ? cargaDespachada * 100.0 / capacidadDespachada : 0.0;

        int rutasDespachadas = (int) motor.viajes().stream().filter(v -> v.despachado).count();
        double distanciaTotal = motor.viajes().stream().filter(v -> v.despachado).mapToDouble(v -> v.plan.distanciaKm()).sum();
        double tiempoRutas = motor.viajes().stream().filter(v -> v.despachado).mapToDouble(v -> v.plan.minutos()).sum();
        double costoTotal = motor.viajes().stream().filter(v -> v.despachado).mapToDouble(v -> v.plan.costo()).sum();

        int bloqueos = motor.bloqueos().size();
        int averias = motor.averias().size();
        int averiasTipo1 = (int) motor.averias().stream().filter(a -> a.tipo() == 1).count();
        int averiasTipo2 = (int) motor.averias().stream().filter(a -> a.tipo() == 2).count();
        int averiasTipo3 = (int) motor.averias().stream().filter(a -> a.tipo() == 3).count();
        int mantenimientos = motor.mantenimientos().size();
        int incidenciasTotales = bloqueos + averias + mantenimientos;
        int incidenciasActivas = (int) motor.bloqueos().stream().filter(i -> activa(i.inicio(), i.fin(), motor.reloj())).count()
                + (int) motor.averias().stream().filter(i -> activa(i.inicio(), i.fin(), motor.reloj())).count()
                + (int) motor.mantenimientos().stream().filter(i -> activa(i.inicio(), i.fin(), motor.reloj())).count();

        Map<Integer, IndicadorPlazo> indicadores = new LinkedHashMap<>();
        for (var entry : porPlazo.entrySet()) {
            int p = entry.getKey();
            int[] vals = entry.getValue();
            double pct = vals[0] > 0 ? (vals[2] * 100.0 / vals[0]) : 100.0;
            indicadores.put(p, new IndicadorPlazo(p, vals[0], vals[1], vals[2], pct));
        }

        return new Resumen(
                esParcial, dias, realMs, numCiclos, numCiclos,
                taTotal, taPromedio, taMax,
                pedidosTotales, entregados, entregadosEnPlazo, noCumplidos, pendientesCierre,
                pedidosReprogramados, pedidosIncumplidosReprogramados, cumplimientoPct, 0.0, paquetesEntregados,
                promedio(holgurasMin), minimo(holgurasMin), promedio(tiemposEntregaH), minimo(tiemposEntregaH), maximo(tiemposEntregaH),
                vehiculosDisponibles, vehiculosUtilizados, utilizacionFlota, utilizacionCapacidad,
                rutasDespachadas, distanciaTotal, tiempoRutas, costoTotal,
                incidenciasTotales, bloqueos, averias, averiasTipo1, averiasTipo2, averiasTipo3,
                mantenimientos, incidenciasActivas, indicadores
        );
    }

    private static boolean activa(LocalDateTime inicio, LocalDateTime fin, LocalDateTime reloj) {
        return !inicio.isAfter(reloj) && fin.isAfter(reloj);
    }

    private static Double promedio(java.util.List<Double> valores) {
        return valores.isEmpty() ? null : valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static Double minimo(java.util.List<Double> valores) {
        return valores.stream().mapToDouble(Double::doubleValue).min().stream().boxed().findFirst().orElse(null);
    }

    private static Double maximo(java.util.List<Double> valores) {
        return valores.stream().mapToDouble(Double::doubleValue).max().stream().boxed().findFirst().orElse(null);
    }

    private GeneradorResumen() { }
}
