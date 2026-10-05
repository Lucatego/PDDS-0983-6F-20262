package pe.pucp.paqrap.backend.persistencia;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;

/** Congela los parametros efectivos antes de cargar incidencias o iniciar el reloj (B-05, DD-04). */
@Repository
public class RepositorioConfiguracionEjecucion {
    @PersistenceContext private EntityManager entidad;

    /** Conflicto de negocio, distinto de un fallo de acceso a PostgreSQL. */
    public static final class EjecucionActivaException extends RuntimeException {
        public EjecucionActivaException() {
            super("Debe finalizar la ejecucion activa antes de configurar otra");
        }
    }

    /** Crea todo el agregado en una transaccion; nunca deja una ejecucion sin flota o configuracion. */
    @Transactional
    public long crear(ConfiguracionSimulacion configuracion, ConfiguracionTabu algoritmo) {
        if (configuracion.semilla() != algoritmo.semilla()) {
            throw new IllegalArgumentException("La semilla del planificador debe coincidir con la ejecucion");
        }
        consultar("SELECT pg_advisory_xact_lock(20262, 5)").getResultList();
        if (!consultar("SELECT id FROM ejecucion WHERE estado IN "
                + "('CONFIGURADA','ESPERANDO_PEDIDO','EN_CURSO','PAUSADA')").getResultList().isEmpty()) {
            throw new EjecucionActivaException();
        }
        List<?> centrales = consultar("SELECT x,y FROM almacen WHERE id = 'CENTRAL' AND es_ilimitado").getResultList();
        if (centrales.size() != 1) throw new IllegalStateException("Falta el almacen central del catalogo");
        Object[] central = (Object[]) centrales.getFirst();
        long id = ((Number) consultar("""
                INSERT INTO ejecucion (escenario,fecha_inicio,fecha_fin_prevista,fecha_actual,semilla,
                    fecha_real_creacion) VALUES (?1,?2,?3,?2,?4,CURRENT_TIMESTAMP) RETURNING id
                """, configuracion.escenario().name(), configuracion.inicio(), configuracion.finHorizonte(),
                configuracion.semilla()).getSingleResult()).longValue();
        var operacion = configuracion.operacion();
        ejecutar("""
                INSERT INTO configuracion_ejecucion (ejecucion_id,sa_minutos,aceleracion_reloj,duracion_dias,
                    servicio_minutos,plazo_incluye_servicio,turno_minutos,descanso_desde_min,descanso_hasta_min,
                    descanso_minutos,tamanio_parte,costo_fijo_vehiculo,penalizacion_paquete_pendiente)
                VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13)
                """, id, configuracion.saMinutos(), configuracion.escenario() == ConfiguracionSimulacion.Escenario.DIA_A_DIA
                        ? 1.0 : configuracion.aceleracion(), configuracion.finHorizonte() == null ? null : 5,
                operacion.servicioMinutos(), operacion.plazoIncluyeServicio(), operacion.turnoMinutos(),
                operacion.descansoDesde(), operacion.descansoHasta(), operacion.descansoMinutos(),
                operacion.tamanioParte(), operacion.costoFijoVehiculo(), operacion.penalizacionPaquetePendiente());
        ejecutar("""
                INSERT INTO configuracion_algoritmo (ejecucion_id,max_iteraciones,sin_mejora_max,presupuesto_ms,
                    tenencia_tabu,candidatos_por_iteracion) VALUES (?1,?2,?3,?4,?5,?6)
                """, id, algoritmo.maxIteraciones(), algoritmo.sinMejoraMax(), algoritmo.presupuestoMs(),
                algoritmo.tenenciaTabu(), algoritmo.candidatosPorIteracion());
        for (int turno = 0; turno < 1440 / operacion.turnoMinutos(); turno++) {
            ejecutar("INSERT INTO turno_ejecucion (ejecucion_id,numero,minuto_inicio,duracion_min) VALUES (?1,?2,?3,?4)",
                    id, turno + 1, (operacion.inicioTurnoMinuto() + turno * operacion.turnoMinutos()) % 1440,
                    operacion.turnoMinutos());
        }
        for (var tipo : TipoVehiculo.values()) {
            ejecutar("""
                    INSERT INTO flota_ejecucion (ejecucion_id,tipo_vehiculo,cantidad,capacidad,costo_km)
                    VALUES (?1,?2,?3,?4,?5)
                    """, id, tipo.name(), configuracion.flota().get(tipo), tipo.capacidad(), tipo.costoPorKm());
            ejecutar("""
                    INSERT INTO velocidad_historial (ejecucion_id,tipo_vehiculo,velocidad_kmh,fecha_registro,
                        fecha_vigencia,fecha_real_registro) VALUES (?1,?2,?3,?4,?4,CURRENT_TIMESTAMP)
                    """, id, tipo.name(), operacion.velocidades().get(tipo), configuracion.inicio());
            for (int numero = 1; numero <= configuracion.flota().get(tipo); numero++) {
                ejecutar("""
                        INSERT INTO vehiculo (ejecucion_id,codigo,tipo_vehiculo,ubicacion_x,ubicacion_y,
                            disponible_desde,fecha_ultimo_cambio_estado) VALUES (?1,?2,?3,?4,?5,?6,?6)
                        """, id, String.format(java.util.Locale.ROOT, "%s%02d", tipo, numero), tipo.name(),
                        central[0], central[1], configuracion.inicio());
            }
        }
        ejecutar("INSERT INTO almacen_ejecucion (ejecucion_id,almacen_id) VALUES (?1,'CENTRAL')", id);
        for (String almacen : List.of("NOROESTE", "ESTE")) {
            ejecutar("""
                    INSERT INTO almacen_ejecucion (ejecucion_id,almacen_id,capacidad,stock_inicial,stock_actual)
                    VALUES (?1,?2,?3,?3,?3)
                    """, id, almacen, configuracion.capacidades().get(almacen));
        }
        return id;
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
