package pe.pucp.paqrap.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend del Centro de Operaciones de PaqRap.
 *
 * <p>Arquitectura cliente-servidor en capas (DAS, {@code 23.dis.arquitectura.solucion}): expone la API REST bajo
 * {@code /api} en el puerto 8080 y usa el módulo {@code planificador} (Tabu Search) como biblioteca.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaqRapAplicacion {

    /**
     * Arranca la aplicación Spring Boot.
     *
     * @param args argumentos de línea de comandos (se pueden sobrescribir propiedades con {@code --clave=valor})
     */
    public static void main(String[] args) {
        SpringApplication.run(PaqRapAplicacion.class, args);
    }
}
