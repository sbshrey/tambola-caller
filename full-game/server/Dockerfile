# Build the verified installDist first; only runtime files enter the image.
FROM eclipse-temurin:17-jre-jammy@sha256:97137382c6f0c30427d9b7c44ad8b2d55ac823b0768c171d81a643ed219023c5
# Reviewed Ubuntu security revisions absent from the pinned base; update these with the base review.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends --only-upgrade \
        libc6=2.35-0ubuntu3.15 libc-bin=2.35-0ubuntu3.15 perl-base=5.34.0-3ubuntu1.9 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 tambola \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin tambola
WORKDIR /app
COPY server/build/install/server/ /app/
USER 10001:10001
ENV PORT=8080 TAMBOLA_BIND_HOST=0.0.0.0
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD ["java", "-Xmx32m", "-cp", "/app/lib/*", "io.github.sbshrey.tambola.server.HealthProbe", "live"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-cp", "/app/lib/*", "io.github.sbshrey.tambola.server.ServerKt"]
