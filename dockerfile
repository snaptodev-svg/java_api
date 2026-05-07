# Build stage
FROM maven:3.8.1-openjdk-8 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM tomcat:8.5-jdk8
RUN rm -rf /usr/local/tomcat/webapps/ROOT
COPY --from=builder /app/target/HegmatechAPI.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]