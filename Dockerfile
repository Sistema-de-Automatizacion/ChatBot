# syntax=docker/dockerfile:1.7
#
# Imagen multi-stage para el ChatBot de Motos del Caribe.
# - Etapa "build": JDK + Maven wrapper para compilar el .jar.
# - Etapa "run":   JRE liviano para ejecutar el .jar (no se incluyen las
#                  dependencias del build, asi la imagen final pesa ~200MB
#                  en vez de ~600MB).
#
# Build:  docker build -t motosdelcaribe-chatbot .
# Run:    docker run -p 8081:8081 \
#           -e DB_URL="jdbc:mysql://..." \
#           -e DB_USERNAME=... \
#           -e DB_PASSWORD=... \
#           -e APP_API_KEY=... \
#           motosdelcaribe-chatbot

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copiar primero pom.xml y wrapper para aprovechar el cache de capas: si pom no
# cambia, Docker reusa la capa con dependencias bajadas.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Ahora si copiar el codigo fuente y empaquetar.
COPY src/ src/
RUN ./mvnw -B package -DskipTests \
    && cp target/motosdelcaribe-*.jar app.jar

# ---------- Run stage ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Usuario no-root para reducir la superficie de ataque si alguien escapa del JVM.
RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

COPY --from=build --chown=app:app /app/app.jar app.jar

# El default es 8081; en Render/Azure la plataforma setea PORT y application.properties
# resuelve server.port=${PORT:8081}, asi que tambien funciona bindear en otro puerto.
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
