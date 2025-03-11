# Base 이미지 설정 (JDK 17 사용)
FROM openjdk:17-jdk-slim

# JAR 파일을 컨테이너 내부로 복사
COPY build/libs/XChangePass-0.0.1-SNAPSHOT.jar app.jar

# 컨테이너에서 실행될 명령어
ENTRYPOINT ["java", "-jar", "/app.jar"]
