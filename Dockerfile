# ---------- Stage 1: build the React app ----------
    FROM node:22-alpine AS frontend
    WORKDIR /app/frontend
    COPY frontend/package.json frontend/package-lock.json ./
    RUN npm ci
    COPY frontend/ ./
    RUN npm run build
    
    # ---------- Stage 2: build the Spring Boot jar, with React inside ----------
    FROM eclipse-temurin:25-jdk AS backend
    WORKDIR /app/backend
    COPY backend/ ./
    COPY --from=frontend /app/frontend/dist/ src/main/resources/static/
    RUN ./mvnw -q -DskipTests package
    
    # ---------- Stage 3: the small image that actually runs ----------
    FROM eclipse-temurin:25-jre
    WORKDIR /app
    COPY --from=backend /app/backend/target/*.jar app.jar
    ENV SPRING_PROFILES_ACTIVE=prod
    EXPOSE 8080
    ENTRYPOINT ["java", "-jar", "app.jar"]