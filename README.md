# JAVA-ecommerce-backend-api-MEMBER

Backend API cho hệ thống ecommerce theo kiến trúc multi-module Maven:

- `myshop-framework`: module dùng chung (entity/mapper/service core)
- `myshop-module-manager`: Spring Boot app chạy API manager (port `8080`)

Project đã tích hợp monitoring với:

- Spring Boot Actuator + Micrometer Prometheus
- Prometheus (Docker, port `9090`)
- Grafana (Docker, port `3000`)

## 1) Yêu cầu môi trường

- Java `21`
- Maven `3.9+`
- Docker Desktop + Docker Compose
- macOS/Linux (README này dùng command tương thích `zsh`)

## 2) Cấu trúc chính

```text
.
├── docker-compose.yml
├── pom.xml
├── monitoring/
│   ├── prometheus/
│   │   └── prometheus.yml
│   └── grafana/
│       └── provisioning/
│           └── datasources/
│               └── prometheus.yml
├── myshop-framework/
└── myshop-module-manager/
		└── src/main/resources/application.yml
```

## 3) Build project

Tại root project:

```bash
mvn clean install -DskipTests
```

Nếu build thành công, bạn sẽ thấy `BUILD SUCCESS`.

## 4) Chạy Spring Boot app

Chạy module manager:

```bash
mvn spring-boot:run -pl myshop-module-manager
```

App chạy tại:

- `http://localhost:8080`

### Actuator endpoints đã expose

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/info`
- `http://localhost:8080/actuator/metrics`
- `http://localhost:8080/actuator/prometheus`

## 5) Chạy monitoring stack (Prometheus + Grafana)

Tại root project:

```bash
docker compose config
docker compose up -d
docker compose ps
```

Services:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- MySQL: `localhost:3306`

## 6) Cấu hình scrape Prometheus

File: `monitoring/prometheus/prometheus.yml`

- scrape interval: `15s`
- target app: `host.docker.internal:8080`
- metrics path: `/actuator/prometheus`

Lý do dùng `host.docker.internal`: Spring Boot app chạy trên **host**, Prometheus chạy trong **Docker container**.

## 7) Grafana datasource

Project đã có provisioning datasource tự động:

- File: `monitoring/grafana/provisioning/datasources/prometheus.yml`
- URL datasource: `http://prometheus:9090`
- Datasource name: `Prometheus`

Sau khi `docker compose up -d`, vào Grafana để kiểm tra datasource đã xuất hiện.

## 8) Verify nhanh end-to-end

> Đảm bảo app Spring Boot đang chạy trước khi verify Prometheus target.

### 8.1 Verify Actuator

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

Endpoint prometheus phải trả về text metrics dạng:

```text
# HELP ...
# TYPE ...
...
```

### 8.2 Verify Prometheus Target

1. Mở `http://localhost:9090`
2. Vào `Status` → `Targets`
3. Target `myshop-module-manager` phải ở trạng thái `UP`

### 8.3 Verify Grafana

1. Mở `http://localhost:3000`
2. Đăng nhập Grafana
3. Vào `Connections` / `Data Sources`
4. Kiểm tra datasource `Prometheus` đã có và `Save & test` thành công

## 9) Xem logs monitoring

```bash
docker compose logs prometheus
docker compose logs grafana
```

## 10) Dừng services

### Dừng app Spring Boot

Nhấn `Ctrl + C` tại terminal đang chạy app.

### Dừng stack Docker

```bash
docker compose down
```

Nếu muốn xóa luôn volumes data:

```bash
docker compose down -v
```

## 11) Lưu ý

- Không cần đổi port mặc định:
  - App: `8080`
  - Prometheus: `9090`
  - Grafana: `3000`
- Monitoring được bổ sung mà không thay đổi business logic API hiện tại.
