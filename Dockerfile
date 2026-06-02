FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY src ./src
ARG PDFBOX_VERSION=3.0.3
RUN mkdir -p lib build/classes \
    && wget -q "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/${PDFBOX_VERSION}/pdfbox-app-${PDFBOX_VERSION}.jar" -O "lib/pdfbox-app-${PDFBOX_VERSION}.jar" \
    && javac -encoding UTF-8 -cp "lib/*" -d build/classes $(find src/main/java -name "*.java")

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV PORT=8080
ENV STATIC_DIR=/app/static
COPY --from=build /workspace/build/classes /app/classes
COPY --from=build /workspace/lib /app/lib
COPY src/main/resources/static /app/static
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD wget -qO- http://localhost:8080/healthz || exit 1
CMD ["java", "-cp", "/app/classes:/app/lib/*", "com.offercatcher.OfferCatcherApplication"]
