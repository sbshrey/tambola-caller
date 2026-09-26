# Build the verified installDist first; only runtime files enter the image.
FROM eclipse-temurin:17-jre-jammy@sha256:97137382c6f0c30427d9b7c44ad8b2d55ac823b0768c171d81a643ed219023c5
RUN groupadd --gid 10001 tambola && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin tambola
WORKDIR /app
COPY server/build/install/server/ /app/
USER 10001:10001
ENV PORT=8080 TAMBOLA_BIND_HOST=0.0.0.0
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD ["java", "-Xmx32m", "-cp", "/app/lib/*", "io.github.sbshrey.tambola.server.HealthProbe", "live"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-cp", "/app/lib/*", "io.github.sbshrey.tambola.server.ServerKt"]
