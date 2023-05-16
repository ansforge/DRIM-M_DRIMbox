## Stage 1 : build with maven builder image
# TODO : see if there is a lighter image
FROM quay.io/quarkus/ubi-quarkus-native-image:22.1.0-java11 AS build
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/
USER quarkus
WORKDIR /code
#RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
RUN chmod a+x ./mvnw
RUN ./mvnw install
COPY src /code/src
RUN ./mvnw package -DskipTests

# Get run java script
FROM curlimages/curl AS downloader
ARG RUN_JAVA_VERSION=1.3.8
RUN curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh -o /tmp/run-java.sh


## Stage 2 : create the docker final image
#FROM registry.access.redhat.com/ubi8/openjdk-11:1.11
FROM eclipse-temurin:11-jre

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

COPY --from=downloader --chown=185 /tmp/run-java.sh /deployments/run-java.sh
RUN chmod 540 /deployments/run-java.sh

# We make four distinct layers so if there are application changes the library layers can be re-used
# Open CV needs
COPY --from=build --chown=185 code/target/lib/linux-x86-64/ /lib/
COPY --from=build --chown=185 code/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 code/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 code/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 code/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8081
USER 185 

ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT [ "/deployments/run-java.sh" ]