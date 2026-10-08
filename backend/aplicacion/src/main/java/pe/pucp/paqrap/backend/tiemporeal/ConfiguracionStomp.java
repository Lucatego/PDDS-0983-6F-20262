package pe.pucp.paqrap.backend.tiemporeal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTiempoReal;

/**
 * Configuración de WebSocket/STOMP (B-09): endpoint {@value #ENDPOINT} sin SockJS (el frontend usa
 * {@code brokerURL} con WebSocket nativo) y broker simple en memoria para los destinos {@code /topic/**}.
 *
 * <p>El prefijo {@code /api} de {@code ConfiguracionWeb} solo afecta a los controladores REST: {@value #ENDPOINT} se
 * publica tal cual, como espera el proxy de Vite ({@code /ws} con {@code ws: true}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSocketMessageBroker
public class ConfiguracionStomp implements WebSocketMessageBrokerConfigurer {

    /** Ruta del endpoint WebSocket (sin prefijo {@code /api}). */
    public static final String ENDPOINT = "/ws";

    /** Heartbeat en ms que pide el cliente del frontend (entrada y salida). */
    private static final long HEARTBEAT_MS = 10_000L;

    private final PropiedadesTiempoReal propiedades;
    private final TaskScheduler planificadorBroker;

    /**
     * Crea la configuración.
     *
     * @param propiedades                parámetros del canal (orígenes permitidos)
     * @param messageBrokerTaskScheduler planificador del broker para los heartbeats (bean de Spring Messaging)
     */
    public ConfiguracionStomp(PropiedadesTiempoReal propiedades,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
        this.propiedades = propiedades;
        this.planificadorBroker = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint(ENDPOINT).setAllowedOriginPatterns(propiedades.patronesOrigen());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(planificadorBroker);
    }
}
