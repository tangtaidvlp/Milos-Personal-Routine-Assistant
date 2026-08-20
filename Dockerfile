FROM eclipse-temurin:25
LABEL authors="tommtang2106"
RUN mkdir /opt/app
COPY build/libs/routinemanager-0.0.3-SNAPSHOT.jar /opt/app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "./opt/app/app.jar"]
#  docker run --name database -p 5469:5432 -e POSTGRES_PASSWORD=postgres -d postgres:trixie