FROM eclipse-temurin:21-jre-alpine

# 컨테이너 시각을 한국 시간으로 맞춘다 (로그 · LocalDateTime.now)
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata

# root가 아닌 사용자로 실행한다
RUN addgroup -S spring && adduser -S spring -G spring

# CI에서 ./gradlew bootJar로 만든 실행 jar (plain jar는 build.gradle에서 만들지 않는다)
COPY build/libs/*.jar app.jar

USER spring:spring

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
