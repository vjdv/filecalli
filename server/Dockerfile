FROM amazoncorretto:17-alpine-jdk
WORKDIR /app
ENV datapath=/data
RUN mkdir -p $datapath
COPY target/filecalli.jar ./filecalli.jar
ENTRYPOINT ["java", "-jar", "filecalli.jar"]
