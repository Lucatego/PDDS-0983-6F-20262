# syntax=docker/dockerfile:1
# Imagen del backend de PaqRap (Spring Boot 4, Java 25). Contexto de construcción: backend/.
# Se usa desde el compose.yaml de la raíz del repositorio; ver README.md («Contenedores»).

# 1) Compilación con el JDK 25 y el Maven wrapper del proyecto.
FROM eclipse-temurin:25-jdk AS compilacion
WORKDIR /fuente
COPY . .
# En Windows, git puede dejar mvnw con fin de línea CRLF: se normaliza para el shell de Linux.
# Las pruebas se ejecutan aparte (./mvnw verify); la imagen solo empaqueta.
RUN --mount=type=cache,target=/root/.m2 \
    sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q -DskipTests package \
    && cp aplicacion/target/paqrap-backend.jar /aplicacion.jar

# 2) Ejecución con el JRE 25, sin las fuentes ni Maven, con un usuario sin privilegios.
FROM eclipse-temurin:25-jre
RUN groupadd --system paqrap && useradd --system --gid paqrap --no-create-home paqrap
WORKDIR /app
COPY --from=compilacion /aplicacion.jar app.jar
USER paqrap
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
