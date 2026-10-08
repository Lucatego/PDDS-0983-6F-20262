package pe.pucp.paqrap.backend.tiemporeal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.pucp.paqrap.backend.configuracion.ConfiguracionWeb;

/**
 * Pide una difusión inmediata del estado tras cada comando REST exitoso (B-09): {@code frontend/README.md} pide
 * «una vez tras cada comando». Cubre configuración, inicio, detención, reinicio, pedidos, archivos e incidencias sin
 * tocar los controladores. La difusión la hace el hilo de {@link DifusionTiempoReal} en su siguiente sondeo.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfiguracionDifusionRest implements WebMvcConfigurer {

    private final ObjectProvider<DifusionTiempoReal> difusion;

    /**
     * Crea la configuración.
     *
     * @param difusion proveedor perezoso de la difusión (evita ciclos de creación con el controlador REST)
     */
    public ConfiguracionDifusionRest(ObjectProvider<DifusionTiempoReal> difusion) {
        this.difusion = difusion;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest solicitud, HttpServletResponse respuesta, Object manejador,
                    Exception ex) {
                if ("POST".equals(solicitud.getMethod()) && respuesta.getStatus() < 400) {
                    DifusionTiempoReal servicio = difusion.getIfAvailable();
                    if (servicio != null) servicio.solicitarEstado();
                }
            }
        }).addPathPatterns(ConfiguracionWeb.PREFIJO_API + "/**");
    }
}
