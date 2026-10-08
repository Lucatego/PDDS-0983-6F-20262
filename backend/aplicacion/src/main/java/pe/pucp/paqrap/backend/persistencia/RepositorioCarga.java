package pe.pucp.paqrap.backend.persistencia;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Acceso JPA y SQL PostgreSQL parametrizado para la carga de maestros e incidencias (B-04). */
@Repository
public class RepositorioCarga {
    @PersistenceContext
    private EntityManager entidad;

    /** Serializa cargas concurrentes: un archivo no se duplica entre dispositivos (LE008/009). */
    public void bloquearCargas() {
        consultar("SELECT pg_advisory_xact_lock(20262, 4)").getResultList();
    }

    /** Bloquea la ejecucion para que no arranque mientras se cargan averias. */
    public LocalDateTime inicioConfigurable(long ejecucionId) {
        List<?> filas = consultar("SELECT fecha_inicio, estado FROM ejecucion WHERE id = ?1 FOR UPDATE",
                ejecucionId).getResultList();
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("Ejecucion inexistente");
        }
        Object[] fila = (Object[]) filas.getFirst();
        if (!"CONFIGURADA".equals(fila[1])) {
            throw new IllegalArgumentException("Las averias se cargan antes de iniciar la ejecucion");
        }
        return fila[0] instanceof LocalDateTime fecha ? fecha : ((java.sql.Timestamp) fila[0]).toLocalDateTime();
    }

    /** Busca por periodo maestro o contenido dentro de la ejecucion. */
    public Optional<ArchivoCarga> buscar(ArchivoAnalizado archivo, Long ejecucionId) {
        String filtro = ejecucionId == null
                ? "a.ejecucionId IS NULL AND a.anio = :anio AND a.mes = :mes"
                : "a.ejecucionId = :ejecucion AND a.hashSha256 = :hash";
        var query = entidad.createQuery("SELECT a FROM ArchivoCarga a WHERE a.tipoArchivo = :tipo "
                + "AND a.estado <> 'REEMPLAZADO' AND " + filtro, ArchivoCarga.class)
                .setParameter("tipo", archivo.tipo().name());
        if (ejecucionId == null) {
            query.setParameter("anio", archivo.anio().shortValue()).setParameter("mes", archivo.mes().shortValue());
        } else {
            query.setParameter("ejecucion", ejecucionId).setParameter("hash", archivo.hash());
        }
        return query.getResultStream().findFirst();
    }

    public void guardar(ArchivoCarga archivo) {
        entidad.persist(archivo);
    }

    public void guardarError(long archivoId, ArchivoAnalizado.ErrorLinea error) {
        ejecutar("INSERT INTO archivo_carga_error (archivo_id,numero_linea,contenido,motivo) VALUES (?1,?2,?3,?4)",
                archivoId, error.linea(), error.contenido(), error.motivo());
    }

    public void guardarVenta(long archivoId, ArchivoAnalizado.Venta venta) {
        var pedido = venta.pedido();
        ejecutar("""
                INSERT INTO pedido (codigo,origen,archivo_id,numero_linea,cliente_codigo,fecha_registro,
                    destino_x,destino_y,cantidad,plazo_horas,creado_en)
                VALUES (?1,'ARCHIVO',?2,?3,?4,?5,?6,?7,?8,?9,CURRENT_TIMESTAMP)
                """, pedido.id(), archivoId, venta.linea(), pedido.clienteId(), pedido.fechaRegistro(),
                pedido.ubicacion().x(), pedido.ubicacion().y(), pedido.cantidad(), pedido.plazoHoras());
    }

    public void guardarBloqueo(long archivoId, ArchivoAnalizado.Cierre cierre) {
        var bloqueo = cierre.bloqueo();
        int longitud = 0;
        for (int i = 1; i < bloqueo.puntos().size(); i++) {
            var anterior = bloqueo.puntos().get(i - 1);
            var actual = bloqueo.puntos().get(i);
            longitud += Math.abs(anterior.x() - actual.x()) + Math.abs(anterior.y() - actual.y());
        }
        long id = ((Number) consultar("""
                INSERT INTO bloqueo (codigo,origen,archivo_id,numero_linea,fecha_inicio,fecha_fin,
                    num_vertices,longitud_km,creado_en)
                VALUES (?1,'ARCHIVO',?2,?3,?4,?5,?6,?7,CURRENT_TIMESTAMP) RETURNING id
                """, cierre.codigo(), archivoId, cierre.linea(), bloqueo.inicio(), bloqueo.fin(),
                bloqueo.puntos().size(), longitud).getSingleResult()).longValue();
        for (int i = 0; i < bloqueo.puntos().size(); i++) {
            var nodo = bloqueo.puntos().get(i);
            ejecutar("INSERT INTO bloqueo_vertice (bloqueo_id,orden,x,y) VALUES (?1,?2,?3,?4)",
                    id, i + 1, nodo.x(), nodo.y());
        }
    }

    public void guardarMantenimiento(long archivoId, ArchivoAnalizado.Mantenimiento mantenimiento,
            LocalDate fecha, boolean generado) {
        ejecutar("""
                INSERT INTO mantenimiento_programado (fecha,vehiculo_codigo,origen,archivo_id,numero_linea,creado_en)
                VALUES (?1,?2,?3,?4,?5,CURRENT_TIMESTAMP) ON CONFLICT (fecha,vehiculo_codigo) DO NOTHING
                """, fecha, mantenimiento.vehiculo(), generado ? "GENERADO" : "ARCHIVO", archivoId,
                generado ? null : mantenimiento.linea());
    }

    public Optional<Long> buscarVehiculo(long ejecucionId, String codigo) {
        List<?> valores = consultar("SELECT id FROM vehiculo WHERE ejecucion_id = ?1 AND codigo = ?2",
                ejecucionId, codigo).getResultList();
        return valores.isEmpty() ? Optional.empty() : Optional.of(((Number) valores.getFirst()).longValue());
    }

    public void guardarAveria(long archivoId, long ejecucionId, long vehiculoId, ArchivoAnalizado.Averia averia) {
        ejecutar("""
                INSERT INTO incidencia (ejecucion_id,tipo,origen,estado,archivo_id,numero_linea,
                    vehiculo_id,tipo_averia,fecha_programada,fecha_real_registro)
                VALUES (?1,'AVERIA','ARCHIVO','PROGRAMADA',?2,?3,?4,?5,?6,CURRENT_TIMESTAMP)
                """, ejecucionId, archivoId, averia.linea(), vehiculoId, averia.tipo(), averia.fecha());
    }

    private Query consultar(String sql, Object... parametros) {
        Query consulta = entidad.createNativeQuery(sql);
        for (int i = 0; i < parametros.length; i++) {
            consulta.setParameter(i + 1, parametros[i]);
        }
        return consulta;
    }

    private void ejecutar(String sql, Object... parametros) {
        consultar(sql, parametros).executeUpdate();
    }
}
