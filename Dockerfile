# 公開用のコンテナ(画面とサーバーを 1 つにまとめる)。使い方は compose.prod.yaml と README を見る。
# 1) 画面を作る → 2) サーバーを作る(画面を中に入れる) → 3) 小さな実行用の入れ物に載せる

# --- 1) 画面(React)のビルド。出力先は ../src/main/resources/static(vite.config.js) ---
FROM node:24-slim AS web
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- 2) サーバー(Spring Boot)のビルド ---
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml ./
# 先に部品だけ取っておく(ソースを変えただけなら、ここは使い回される)
RUN mvn -q -B dependency:go-offline
COPY src/ ./src/
COPY --from=web /build/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B -DskipTests package && mv target/*.jar app.jar

# --- 3) 実行用 ---
FROM eclipse-temurin:21-jre
# 管理者でない専用のユーザーで動かす(破られたときの被害を小さくする)
RUN useradd --system --create-home --uid 10001 sns \
    && mkdir -p /data/uploads /app/logs \
    && chown -R sns:sns /data /app
WORKDIR /app
COPY --from=build --chown=sns:sns /build/app.jar app.jar
USER sns
ENV SERVER_ADDRESS=0.0.0.0 \
    UPLOAD_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-Xmx256m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/auth/config HTTP/1.0\r\n\r\n" >&3 && grep -q " 200" <&3'
ENTRYPOINT ["java", "-jar", "app.jar"]
