package pe.pucp.paqrap.backend.configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración web común. Antepone {@value #PREFIJO_API} a todos los {@link RestController} del paquete
 * {@code pe.pucp.paqrap.backend.api}, de modo que los controladores declaran rutas relativas ({@code /salud},
 * {@code /pedidos}, ...) y el contrato del frontend ({@code frontend/README.md}) se respeta en un solo lugar.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionWeb implements WebMvcConfigurer {

    /** Prefijo de la API REST esperado por el frontend (el proxy de Vite redirige {@code /api} al puerto 8080). */
    public static final String PREFIJO_API = "/api";

    private static final String PAQUETE_API = "pe.pucp.paqrap.backend.api";

    /**
     * Agrega el prefijo {@value #PREFIJO_API} a los controladores REST de la API.
     *
     * @param configurador configurador de coincidencia de rutas de Spring MVC
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurador) {
        configurador.addPathPrefix(PREFIJO_API, tipo -> tipo.isAnnotationPresent(RestController.class)
                && tipo.getPackageName().startsWith(PAQUETE_API));
    }
}
