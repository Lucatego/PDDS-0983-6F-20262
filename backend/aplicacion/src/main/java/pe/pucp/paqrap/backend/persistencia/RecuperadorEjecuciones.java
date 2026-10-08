package pe.pucp.paqrap.backend.persistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar el servidor cierra como interrumpidas las ejecuciones que quedaron activas en la base (LE060).
 * La memoria del servidor empieza sin motor, de modo que una ejecucion {@code EN_CURSO} u otra activa heredada
 * de un cierre abrupto nunca se podria continuar y bloquearia {@code POST /api/simulacion/configuracion}.
 * Se desactiva con {@code paqrap.ejecucion.cerrar-huerfanas-al-arrancar=false} (p. ej. si dos servidores
 * comparten la misma base, algo no previsto).
 */
@Component
public class RecuperadorEjecuciones {
    private static final Logger LOG = LoggerFactory.getLogger(RecuperadorEjecuciones.class);

    static final String MENSAJE = "Interrumpida: el servidor se reinicio mientras la ejecucion estaba activa";

    private final ObjectProvider<RepositorioConfiguracionEjecucion> repositorio;
    private final boolean habilitado;

    public RecuperadorEjecuciones(ObjectProvider<RepositorioConfiguracionEjecucion> repositorio,
            @Value("${paqrap.ejecucion.cerrar-huerfanas-al-arrancar:true}") boolean habilitado) {
        this.repositorio = repositorio;
        this.habilitado = habilitado;
    }

    /** Cierra las ejecuciones huerfanas; no hace nada si esta desactivado o no hay base de datos. */
    @EventListener(ApplicationReadyEvent.class)
    public int cerrarHuerfanas() {
        var repo = repositorio.getIfAvailable();
        if (!habilitado || repo == null) return 0;
        int cerradas = repo.cerrarEjecucionesHuerfanas(MENSAJE);
        if (cerradas > 0) LOG.warn("{} ejecucion(es) activa(s) heredada(s) se marcaron como ERROR (interrumpidas)", cerradas);
        return cerradas;
    }
}
