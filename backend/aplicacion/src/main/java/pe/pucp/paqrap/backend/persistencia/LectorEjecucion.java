package pe.pucp.paqrap.backend.persistencia;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.backend.simulacion.PreparacionSimulacion;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;

/** Reconstruye entradas desde la configuracion efectiva, sin usar defaults globales (B-05, LE008/069). */
@Repository
public class LectorEjecucion {
    @PersistenceContext private EntityManager entidad;

    public static final class PreparacionInvalidaException extends RuntimeException {
        public PreparacionInvalidaException(String mensaje) { super(mensaje); }
    }

    /**
     * Serializa con las cargas para no mezclar maestros de distintos instantes.
     * No inicia, no publica y no vincula archivos: el orquestador debe confirmar el uso al iniciar.
     */
    @Transactional
    public PreparacionSimulacion preparar(long id) {
        consultar("SELECT pg_advisory_xact_lock(20262, 4)").getResultList();
        var ejecuciones = filas("SELECT escenario,fecha_inicio,semilla,estado FROM ejecucion WHERE id=?1 FOR UPDATE", id);
        if (ejecuciones.isEmpty()) throw new PreparacionInvalidaException("Ejecucion inexistente");
        Object[] ejecucion = ejecuciones.getFirst();
        if (!"CONFIGURADA".equals(ejecucion[3])) {
            throw new PreparacionInvalidaException("Solo se puede preparar una ejecucion CONFIGURADA");
        }
        var escenario = ConfiguracionSimulacion.Escenario.valueOf((String) ejecucion[0]);
        LocalDateTime inicio = fecha(ejecucion[1]);
        long semilla = ((Number) ejecucion[2]).longValue();
        Object[] opciones = unica("""
                SELECT c.sa_minutos,c.aceleracion_reloj,c.servicio_minutos,c.plazo_incluye_servicio,
                    c.turno_minutos,t.minuto_inicio,c.descanso_desde_min,c.descanso_hasta_min,c.descanso_minutos,
                    c.tamanio_parte,c.costo_fijo_vehiculo,c.penalizacion_paquete_pendiente,
                    c.averias_aleatorias,c.tasa_averias_dia,c.trasvase_habilitado
                FROM configuracion_ejecucion c JOIN turno_ejecucion t ON t.ejecucion_id=c.ejecucion_id
                WHERE c.ejecucion_id=?1 AND t.numero=1
                """, id);
        Map<TipoVehiculo, Integer> flota = new EnumMap<>(TipoVehiculo.class);
        Map<TipoVehiculo, Double> velocidades = new EnumMap<>(TipoVehiculo.class);
        for (Object[] fila : filas("""
                SELECT f.tipo_vehiculo,f.cantidad,v.velocidad_kmh FROM flota_ejecucion f
                JOIN velocidad_historial v ON v.ejecucion_id=f.ejecucion_id AND v.tipo_vehiculo=f.tipo_vehiculo
                WHERE f.ejecucion_id=?1 AND v.fecha_vigencia=?2 ORDER BY f.tipo_vehiculo
                """, id, inicio)) {
            var tipo = TipoVehiculo.valueOf(fila[0].toString().trim());
            flota.put(tipo, entero(fila[1]));
            velocidades.put(tipo, decimal(fila[2]));
        }
        var almacenes = new ArrayList<Almacen>();
        var capacidades = new LinkedHashMap<String, Integer>();
        for (Object[] fila : filas("""
                SELECT a.id,a.x,a.y,a.es_ilimitado,e.capacidad,e.stock_inicial FROM almacen a
                JOIN almacen_ejecucion e ON e.almacen_id=a.id WHERE e.ejecucion_id=?1 ORDER BY a.id
                """, id)) {
            boolean ilimitado = (Boolean) fila[3];
            if (!ilimitado) capacidades.put((String) fila[0], entero(fila[4]));
            almacenes.add(new Almacen((String) fila[0], new Nodo(entero(fila[1]), entero(fila[2])),
                    ilimitado ? 0 : entero(fila[5]), ilimitado));
        }
        var operacion = new ParametrosOperacion(entero(opciones[2]), (Boolean) opciones[3], entero(opciones[4]),
                entero(opciones[5]), entero(opciones[6]), entero(opciones[7]), entero(opciones[8]),
                entero(opciones[9]), decimal(opciones[10]), decimal(opciones[11]), velocidades);
        boolean averiasAleatorias = (Boolean) opciones[12];
        double tasaAverias = decimal(opciones[13]);
        boolean trasvase = (Boolean) opciones[14];
        var configuracion = new ConfiguracionSimulacion(escenario, inicio, flota, capacidades,
                entero(opciones[0]), decimal(opciones[1]), semilla, operacion,
                averiasAleatorias, averiasAleatorias, tasaAverias, trasvase);
        Object[] tabu = unica("""
                SELECT max_iteraciones,tenencia_tabu,sin_mejora_max,candidatos_por_iteracion,presupuesto_ms,algoritmo
                FROM configuracion_algoritmo WHERE ejecucion_id=?1
                """, id);
        if (!"TS".equals(tabu[5])) throw new PreparacionInvalidaException("Solo esta implementado Tabu Search");
        var algoritmo = new ConfiguracionTabu(entero(tabu[0]), entero(tabu[1]), entero(tabu[2]), entero(tabu[3]),
                ((Number) tabu[4]).longValue(), semilla);
        var pedidos = new ArrayList<Pedido>();
        var idsPedidos = new LinkedHashMap<String, Long>();
        var archivos = new LinkedHashMap<Long, String>();
        var bloqueos = new ArrayList<Bloqueo>();
        var mantenimientos = new ArrayList<Mantenimiento>();
        // Dia a dia no importa demanda historica; sus pedidos ingresaran por registro manual/lote.
        if (escenario != ConfiguracionSimulacion.Escenario.DIA_A_DIA) {
            LocalDateTime fin = configuracion.finHorizonte();
            String limitePedido = fin == null ? "" : " AND p.fecha_registro < ?2";
            Object[] limites = fin == null ? new Object[] {inicio} : new Object[] {inicio, fin};
            for (Object[] fila : filas("""
                    SELECT p.id,p.codigo,p.fecha_registro,p.destino_x,p.destino_y,p.cantidad,p.plazo_horas,
                        p.cliente_codigo,a.id,a.hash_sha256 FROM pedido p JOIN archivo_carga a ON a.id=p.archivo_id
                    WHERE p.origen='ARCHIVO' AND a.estado IN ('CARGADO','CON_ERRORES') AND p.fecha_registro >= ?1
                    """ + limitePedido + " ORDER BY p.fecha_registro,p.codigo", limites)) {
                pedidos.add(new Pedido((String) fila[1], fecha(fila[2]), new Nodo(entero(fila[3]), entero(fila[4])),
                        entero(fila[5]), entero(fila[6]), (String) fila[7]));
                idsPedidos.put((String) fila[1], ((Number) fila[0]).longValue());
                archivos.put(((Number) fila[8]).longValue(), fila[9].toString().trim());
            }
            // Un viaje comprometido puede terminar fuera de 5D: conservar las restricciones futuras,
            // no descartarlas por el horizonte de ingreso de pedidos.
            var puntos = new LinkedHashMap<Long, List<Nodo>>();
            var intervalos = new LinkedHashMap<Long, LocalDateTime[]>();
            for (Object[] fila : filas("""
                    SELECT b.id,b.fecha_inicio,b.fecha_fin,v.x,v.y,a.id,a.hash_sha256
                    FROM bloqueo b JOIN bloqueo_vertice v ON v.bloqueo_id=b.id
                    JOIN archivo_carga a ON a.id=b.archivo_id WHERE b.origen='ARCHIVO'
                    AND a.estado IN ('CARGADO','CON_ERRORES') AND b.fecha_fin > ?1 ORDER BY b.id,v.orden
                    """, inicio)) {
                long bloqueoId = ((Number) fila[0]).longValue();
                puntos.computeIfAbsent(bloqueoId, clave -> new ArrayList<>())
                        .add(new Nodo(entero(fila[3]), entero(fila[4])));
                intervalos.put(bloqueoId, new LocalDateTime[] {fecha(fila[1]), fecha(fila[2])});
                archivos.put(((Number) fila[5]).longValue(), fila[6].toString().trim());
            }
            puntos.forEach((clave, nodos) -> bloqueos.add(new Bloqueo(intervalos.get(clave)[0],
                    intervalos.get(clave)[1], nodos)));
            for (Object[] fila : filas("""
                    SELECT m.vehiculo_codigo,m.fecha,a.id,a.hash_sha256 FROM mantenimiento_programado m
                    JOIN archivo_carga a ON a.id=m.archivo_id JOIN vehiculo v ON v.codigo=m.vehiculo_codigo
                    WHERE v.ejecucion_id=?1 AND m.fecha >= ?2 AND a.estado IN ('CARGADO','CON_ERRORES')
                    ORDER BY m.fecha,m.vehiculo_codigo
                    """, id, inicio.toLocalDate())) {
                LocalDate dia = fila[1] instanceof LocalDate valor ? valor : ((java.sql.Date) fila[1]).toLocalDate();
                mantenimientos.add(new Mantenimiento((String) fila[0], dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()));
                archivos.put(((Number) fila[2]).longValue(), fila[3].toString().trim());
            }
        }
        return new PreparacionSimulacion(id, configuracion, algoritmo, pedidos, almacenes, bloqueos,
                mantenimientos, idsPedidos, archivos);
    }

    private Object[] unica(String sql, Object... parametros) {
        var filas = filas(sql, parametros);
        if (filas.size() != 1) throw new PreparacionInvalidaException("Configuracion persistida incompleta");
        return filas.getFirst();
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

    private static int entero(Object valor) { return ((Number) valor).intValue(); }
    private static double decimal(Object valor) { return ((Number) valor).doubleValue(); }
    private static LocalDateTime fecha(Object valor) {
        return valor instanceof LocalDateTime instante ? instante : ((java.sql.Timestamp) valor).toLocalDateTime();
    }
}
