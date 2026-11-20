FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY RelayServer.java .
RUN javac RelayServer.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/RelayServer.class .
CMD ["java", "RelayServer"]
