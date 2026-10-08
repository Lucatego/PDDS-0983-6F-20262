package pe.pucp.paqrap.backend.persistencia;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion;
import pe.pucp.paqrap.backend.simulacion.PreparacionSimulacion;
import pe.pucp.paqrap.estricto.caminos.Camino;
import pe.pucp.paqrap.estricto.caminos.PasoCamino;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;

/**
 * Persistencia transaccional de ciclos, rutas, pedidos y avance de simulación (B-05, LE014/026/053/060).
 */
@Repository
public class RepositorioSimulacion {

    private static final Nodo NODO_CENTRAL = new Nodo(27, 14);

    @PersistenceContext
    private EntityManager entidad;

    public RepositorioSimulacion() {
    }

    public RepositorioSimulacion(EntityManager entidad) {
        this.entidad = entidad;
    }

    /**
     * Vincula archivos a la ejecución e inserta los pedidos iniciales en {@code pedido_ejecucion}.
     */
    @Transactional
    public void inicializarEjecucion(long ejecucionId, PreparacionSimulacion prep) {
        for (Long archivoId : prep.archivos().keySet()) {
            ejecutar("""
                    INSERT INTO ejecucion_archivo (ejecucion_id, archivo_id)
                    VALUES (?1, ?2) ON CONFLICT DO NOTHING
                    """, ejecucionId, archivoId);
        }

        for (Pedido p : prep.pedidos()) {
            Long pedidoId = prep.pedidosPersistidos().get(p.id());
            if (pedidoId != null) {
                boolean enRiesgo = estaEnRiesgo(p);
                ejecutar("""
                        INSERT INTO pedido_ejecucion (
                            ejecucion_id, pedido_id, estado, cantidad, cantidad_pendiente,
                            cantidad_en_ruta, cantidad_entregada, fecha_ingreso, fecha_limite, en_riesgo
                        ) VALUES (?1, ?2, 'REGISTRADO', ?3, ?3, 0, 0, ?4, ?5, ?6)
                        ON CONFLICT (ejecucion_id, pedido_id) DO NOTHING
                        """, ejecucionId, pedidoId, p.cantidad(), p.fechaRegistro(), p.deadline(), enRiesgo);
            }
        }

        ejecutar("UPDATE ejecucion SET estado = 'EN_CURSO', fecha_real_inicio = COALESCE(fecha_real_inicio, CURRENT_TIMESTAMP) WHERE id = ?1",
                ejecucionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> obtenerMapeoPedidosEjecucion(long ejecucionId) {
        var mapa = new LinkedHashMap<String, Long>();
        for (Object[] fila : filas("""
                SELECT p.codigo, pe.id FROM pedido_ejecucion pe
                JOIN pedido p ON p.id = pe.pedido_id
                WHERE pe.ejecucion_id = ?1
                """, ejecucionId)) {
            mapa.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> obtenerMapeoVehiculos(long ejecucionId) {
        var mapa = new LinkedHashMap<String, Long>();
        for (Object[] fila : filas("""
                SELECT v.codigo, v.id FROM vehiculo v WHERE v.ejecucion_id = ?1
                """, ejecucionId)) {
            mapa.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    /**
     * Registra un pedido manual (por ejemplo en escenario Día a día) en {@code pedido} y {@code pedido_ejecucion}.
     */
    @Transactional
    public long registrarPedidoManual(long ejecucionId, Pedido pedido) {
        long pedidoId = ((Number) consultar("""
                INSERT INTO pedido (codigo, fecha_registro, destino_x, destino_y, cantidad, plazo_horas, cliente_codigo, origen, fecha_real_registro)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'MANUAL', CURRENT_TIMESTAMP)
                RETURNING id
                """, pedido.id(), pedido.fechaRegistro(), pedido.ubicacion().x(), pedido.ubicacion().y(),
                pedido.cantidad(), pedido.plazoHoras(), pedido.clienteId()).getSingleResult()).longValue();

        boolean enRiesgo = estaEnRiesgo(pedido);
        long pedidoEjecucionId = ((Number) consultar("""
                INSERT INTO pedido_ejecucion (
                    ejecucion_id, pedido_id, estado, cantidad, cantidad_pendiente,
                    cantidad_en_ruta, cantidad_entregada, fecha_ingreso, fecha_limite, en_riesgo
                ) VALUES (?1, ?2, 'REGISTRADO', ?3, ?3, 0, 0, ?4, ?5, ?6)
                RETURNING id
                """, ejecucionId, pedidoId, pedido.cantidad(), pedido.fechaRegistro(), pedido.deadline(), enRiesgo).getSingleResult()).longValue();

        return pedidoEjecucionId;
    }

    /**
     * Persiste un ciclo de planificación y sus viajes/rutas, paradas, partes de pedidos y tramos.
     */
    @Transactional
    public long persistirCicloYRutas(long ejecucionId, MotorSimulacion.Ciclo ciclo,
            List<MotorSimulacion.Viaje> viajes,
            Map<String, Long> codigosAPedidoEjecucionId,
            Map<String, Long> vehiculosId) {
        var met = ciclo.resultado().metricas();
        var ev = ciclo.resultado().evaluacion();

        long cicloId = ((Number) consultar("""
                INSERT INTO ciclo_planificacion (
                    ejecucion_id, numero, fecha_ciclo, disparador, algoritmo, estado_resultado, motivo_parada,
                    fecha_real_inicio, fecha_real_fin, ta_ms, iteraciones, iteracion_mejor, candidatos_evaluados,
                    costo_plan, distancia_plan_km, tiempo_plan_rutas_min, vehiculos_plan, utilizacion_capacidad_plan_pct,
                    pedidos_plan, pedidos_completos_plan, cumplimiento_pedidos_pct, cumplimiento_paquetes_pct,
                    paquetes_sin_plan, holgura_plan_promedio_min, holgura_plan_minima_min
                ) VALUES (
                    ?1, ?2, ?3, 'PERIODICO', 'TS', ?4, ?5,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?6, ?7, ?8, ?9,
                    ?10, ?11, ?12, ?13, ?14,
                    ?15, ?16, ?17, ?18,
                    ?19, ?20, ?21
                ) RETURNING id
                """,
                ejecucionId, ciclo.numero(), ciclo.fecha(), met.estadoResultado(), met.parada(),
                met.taMs(), met.iteraciones(), met.iteracionMejor(), met.candidatosEvaluados(),
                ev.costo(), met.distanciaKm(), met.tiempoRutasMinutos(), met.vehiculosUsados(),
                met.utilizacionCapacidad() * 100.0, met.pedidosTotales(), met.pedidosCompletos(),
                met.cumplimientoPedidos() * 100.0, met.cumplimientoPaquetes() * 100.0, met.paquetesPendientes(),
                met.holguraPromedioMin(), met.holguraMinimaMin()
        ).getSingleResult()).longValue();

        for (var viaje : viajes) {
            if (viaje.idPersistido != null) {
                continue;
            }
            Long vehiculoId = vehiculosId.get(viaje.plan.ruta().vehiculo());
            if (vehiculoId == null) {
                continue;
            }

            var tipoVehiculo = TipoVehiculo.desdeCodigo(viaje.plan.ruta().vehiculo());

            long rutaId = ((Number) consultar("""
                    INSERT INTO ruta (
                        ejecucion_id, ciclo_id, vehiculo_id, estado, almacen_origen_id, almacen_retorno_id,
                        fecha_salida, fecha_fin, descanso_inicio, descanso_fin, carga, capacidad,
                        velocidad_kmh, distancia_km, duracion_min, costo
                    ) VALUES (
                        ?1, ?2, ?3, 'PLANIFICADA', ?4, ?5,
                        ?6, ?7, ?8, ?9, ?10, ?11,
                        ?12, ?13, ?14, ?15
                    ) RETURNING id
                    """,
                    ejecucionId, cicloId, vehiculoId, viaje.plan.ruta().almacenOrigen(), viaje.plan.almacenRetorno(),
                    viaje.plan.salida(), viaje.plan.fin(), viaje.plan.descansoInicio(), viaje.plan.descansoFin(),
                    viaje.plan.ruta().carga(), tipoVehiculo.capacidad(),
                    tipoVehiculo.velocidadKmh(), viaje.plan.distanciaKm(), viaje.plan.minutos(),
                    viaje.plan.costo()
            ).getSingleResult()).longValue();

            viaje.idPersistido = rutaId;

            // Persistir paradas y partes
            for (int i = 0; i < viaje.plan.paradas().size(); i++) {
                var paradaPlan = viaje.plan.paradas().get(i);
                if (paradaPlan.partes().isEmpty()) continue;
                String orderCode = paradaPlan.partes().getFirst().pedido().id();
                Long pedidoEjecId = codigosAPedidoEjecucionId.get(orderCode);
                if (pedidoEjecId == null) continue;

                var pedOriginal = paradaPlan.partes().getFirst().pedido();
                int cantTotal = paradaPlan.partes().stream().mapToInt(pe.pucp.paqrap.estricto.modelo.PartePedido::cantidad).sum();
                double holgura = java.time.Duration.between(paradaPlan.finServicio(), pedOriginal.deadline()).toMinutes();

                long paradaId = ((Number) consultar("""
                        INSERT INTO parada (
                            ruta_id, orden, pedido_ejecucion_id, x, y, cantidad,
                            fecha_llegada, fecha_fin_servicio, holgura_min, estado
                        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, 'PENDIENTE')
                        RETURNING id
                        """,
                        rutaId, i + 1, pedidoEjecId, pedOriginal.ubicacion().x(), pedOriginal.ubicacion().y(),
                        cantTotal, paradaPlan.llegada(), paradaPlan.finServicio(), holgura
                ).getSingleResult()).longValue();

                // Persistir partes de pedido correspondientes
                for (var parte : viaje.partes) {
                    if (parte.parada() == i) {
                        Long partePedidoId = ((Number) consultar("""
                                INSERT INTO parte_pedido (
                                    ejecucion_id, pedido_ejecucion_id, numero, codigo, cantidad, estado,
                                    ruta_actual_id, almacen_origen_id, fecha_despacho
                                ) VALUES (?1, ?2, ?3, ?4, ?5, 'EN_RUTA', ?6, ?7, ?8)
                                RETURNING id
                                """,
                                ejecucionId, pedidoEjecId, parte.numero(), parte.codigo(), parte.cantidad(),
                                rutaId, viaje.plan.ruta().almacenOrigen(), viaje.plan.salida()
                        ).getSingleResult()).longValue();

                        ejecutar("INSERT INTO parada_parte (parada_id, parte_pedido_id) VALUES (?1, ?2)",
                                paradaId, partePedidoId);
                    }
                }
            }

            // Persistir ruta_tramo a partir de los caminos
            int ordenTramo = 1;
            for (Camino camino : viaje.plan.caminos()) {
                for (PasoCamino paso : camino.pasos()) {
                    ejecutar("""
                            INSERT INTO ruta_tramo (
                                ruta_id, orden, x_origen, y_origen, x_destino, y_destino, fecha_salida, fecha_llegada
                            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                            """,
                            rutaId, ordenTramo++, paso.origen().x(), paso.origen().y(),
                            paso.destino().x(), paso.destino().y(), paso.salida(), paso.llegada());
                }
            }
        }

        return cicloId;
    }

    /**
     * Actualiza el progreso de pedidos, rutas y estado de la ejecución.
     */
    @Transactional
    public void persistirProgreso(long ejecucionId, MotorSimulacion motor,
            Map<String, Long> codigosAPedidoEjecucionId,
            Map<String, Long> vehiculosId) {
        for (var entry : motor.pedidos().entrySet()) {
            Long pedidoEjecId = codigosAPedidoEjecucionId.get(entry.getKey());
            if (pedidoEjecId == null) continue;
            var pv = entry.getValue();

            Boolean enPlazo = null;
            if (pv.entregada == pv.original.cantidad()) {
                enPlazo = pv.noCumplido == null;
            }

            ejecutar("""
                    UPDATE pedido_ejecucion SET
                        estado = ?1,
                        cantidad_pendiente = ?2,
                        cantidad_en_ruta = ?3,
                        cantidad_entregada = ?4,
                        fecha_primer_despacho = ?5,
                        fecha_llegada_final = ?6,
                        fecha_entrega = ?7,
                        en_plazo = ?8,
                        fecha_no_cumplido = ?9
                    WHERE id = ?10
                    """,
                    pv.estado(), pv.pendiente, pv.enRuta, pv.entregada,
                    pv.primerDespacho, pv.llegadaFinal, pv.entregaFinal,
                    enPlazo, pv.noCumplido, pedidoEjecId);
        }

        for (var viaje : motor.viajes()) {
            if (viaje.idPersistido != null) {
                ejecutar("UPDATE ruta SET estado = ?1, fecha_fin_real = ?2 WHERE id = ?3",
                        viaje.estado(), viaje.terminado ? viaje.plan.fin() : null, viaje.idPersistido);
            }
        }

        // Actualizar vehículos
        for (var v : motor.flotaProyectada()) {
            Long vehiculoId = vehiculosId.get(v.codigo());
            if (vehiculoId != null) {
                ejecutar("""
                        UPDATE vehiculo SET
                            ubicacion_x = ?1, ubicacion_y = ?2, disponible_desde = ?3,
                            fecha_ultimo_cambio_estado = ?4
                        WHERE id = ?5
                        """,
                        v.ubicacionInicial().x(), v.ubicacionInicial().y(), v.disponibleDesde(), motor.reloj(), vehiculoId);
            }
        }

        // Actualizar ejecución
        if (motor.esFinal()) {
            ejecutar("""
                    UPDATE ejecucion SET
                        estado = ?1, fecha_actual = ?2, duracion_real_ms = ?3,
                        motivo_fin = ?4, fecha_fin = ?2, fecha_real_fin = CURRENT_TIMESTAMP
                    WHERE id = ?5
                    """,
                    motor.estado(), motor.reloj(), motor.tiempoRealMs(),
                    motor.motivoFin() != null ? motor.motivoFin() : "FIN_DE_DATOS",
                    ejecucionId);
        } else {
            ejecutar("""
                    UPDATE ejecucion SET estado = ?1, fecha_actual = ?2, duracion_real_ms = ?3 WHERE id = ?4
                    """,
                    motor.estado(), motor.reloj(), motor.tiempoRealMs(), ejecucionId);
        }
    }

    /**
     * Persiste un movimiento de inventario.
     */
    @Transactional
    public void persistirMovimientoInventario(long ejecucionId, String almacenId, LocalDateTime fecha,
            String tipo, int cantidad, Integer stockAnterior, Integer stockResultante,
            Long pedidoEjecucionId, Long rutaId, Long cicloId, String observacion) {
        ejecutar("""
                INSERT INTO movimiento_inventario (
                    ejecucion_id, almacen_id, fecha, tipo, cantidad, stock_anterior, stock_resultante,
                    pedido_ejecucion_id, ruta_id, ciclo_id, observacion
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)
                """,
                ejecucionId, almacenId, fecha, tipo, cantidad, stockAnterior, stockResultante,
                pedidoEjecucionId, rutaId, cicloId, observacion);
    }

    private static boolean estaEnRiesgo(Pedido p) {
        double distancia = NODO_CENTRAL.manhattan(p.ubicacion());
        return (distancia / 40.0) > p.plazoHoras();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> filas(String sql, Object... parametros) {
        return consultar(sql, parametros).getResultList();
    }

    private Query consultar(String sql, Object... parametros) {
        Query consulta = entidad.createNativeQuery(sql);
        for (int i = 0; i < parametros.length; i++) consulta.setParameter(i + 1, parametros[i]);
        return consulta;
    }

    private void ejecutar(String sql, Object... parametros) {
        consultar(sql, parametros).executeUpdate();
    }
}
