package pe.pucp.paqrap.backend.persistencia;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carga atomica e idempotente: errores de linea se conservan; fallas de BD revierten todo (LE008/009/011). */
@Service
public class ServicioCargaArchivos {
    private final RepositorioCarga repositorio;
    private final AnalizadorArchivo analizador = new AnalizadorArchivo();

    public ServicioCargaArchivos(RepositorioCarga repositorio) {
        this.repositorio = repositorio;
    }

    /** Carga maestros antes de simular; averias requieren una ejecucion CONFIGURADA con flota creada. */
    @Transactional
    public ResultadoCarga cargar(TipoArchivo tipo, String nombre, String contenido, Long ejecucionId) {
        long inicio = System.nanoTime();
        if (tipo == null || (tipo == TipoArchivo.AVERIAS) != (ejecucionId != null)) {
            throw new IllegalArgumentException(
                    "Solo averias requiere ejecucion; ventas, bloqueos y mantenimiento son maestros");
        }
        repositorio.bloquearCargas();
        var fechaInicio = ejecucionId == null ? null : repositorio.inicioConfigurable(ejecucionId);
        var archivo = analizador.analizar(tipo, nombre, contenido,
                fechaInicio == null ? null : fechaInicio.toLocalDate());
        var previo = repositorio.buscar(archivo, ejecucionId);
        if (previo.isPresent()) {
            if (!previo.get().getHashSha256().equals(archivo.hash())) {
                throw new IllegalArgumentException("El periodo ya tiene otro archivo: no se reemplazan datos cargados");
            }
            return ResultadoCarga.de(previo.get(), true);
        }
        var errores = new ArrayList<>(archivo.errores());
        var validos = new ArrayList<ArchivoAnalizado.Registro>();
        var vehiculos = new LinkedHashMap<String, Long>();
        for (var registro : archivo.registros()) {
            if (registro instanceof ArchivoAnalizado.Averia averia) {
                var vehiculo = repositorio.buscarVehiculo(ejecucionId, averia.vehiculo());
                if (vehiculo.isEmpty() || averia.fecha().isBefore(fechaInicio)) {
                    errores.add(new ArchivoAnalizado.ErrorLinea(averia.linea(), averia.vehiculo(),
                            vehiculo.isEmpty() ? "Unidad inexistente en la flota" : "Averia anterior al inicio"));
                    continue;
                }
                vehiculos.put(averia.vehiculo(), vehiculo.get());
            }
            validos.add(registro);
        }
        var carga = new ArchivoCarga(archivo, ejecucionId);
        repositorio.guardar(carga);
        for (var registro : validos) {
            switch (registro) {
                case ArchivoAnalizado.Venta venta -> repositorio.guardarVenta(carga.getId(), venta);
                case ArchivoAnalizado.Cierre cierre -> repositorio.guardarBloqueo(carga.getId(), cierre);
                case ArchivoAnalizado.Mantenimiento mantenimiento -> {
                    repositorio.guardarMantenimiento(carga.getId(), mantenimiento, mantenimiento.fecha(), false);
                    // Se ancla cada repeticion a la fecha original para evitar deriva tras febrero.
                    for (int meses = 2; ; meses += 2) {
                        LocalDate fecha = mantenimiento.fecha().plusMonths(meses);
                        if (fecha.isAfter(LocalDate.of(2029, 12, 31))) {
                            break;
                        }
                        repositorio.guardarMantenimiento(carga.getId(), mantenimiento, fecha, true);
                    }
                }
                case ArchivoAnalizado.Averia averia -> repositorio.guardarAveria(carga.getId(), ejecucionId,
                        vehiculos.get(averia.vehiculo()), averia);
            }
        }
        errores.sort(java.util.Comparator.comparingInt(ArchivoAnalizado.ErrorLinea::linea));
        errores.forEach(error -> repositorio.guardarError(carga.getId(), error));
        carga.finalizar(validos.size(), errores.size(), (System.nanoTime() - inicio) / 1_000_000);
        return ResultadoCarga.de(carga, false);
    }
}
