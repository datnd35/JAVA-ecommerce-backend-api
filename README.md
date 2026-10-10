# JAVA-ecommerce-backend-api-MEMBER

Backend API cho hệ thống ecommerce theo kiến trúc multi-module Maven:

- `myshop-framework`: module dùng chung (entity/mapper/service core)
- `myshop-module-manager`: Spring Boot app chạy API manager (port `1122`)

Project đã tích hợp monitoring với:

- Spring Boot Actuator + Micrometer Prometheus
- Prometheus (Docker, port `9090`)
- Grafana (Docker, port `3000`)
- Node Exporter (Docker, port `9100`)

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

- `http://localhost:1122`

### Actuator endpoints đã expose

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/info`
- `http://localhost:8080/actuator/metrics`
- `http://localhost:8080/actuator/prometheus`
- `http://localhost:1122/actuator/health`
- `http://localhost:1122/actuator/info`
- `http://localhost:1122/actuator/metrics`
- `http://localhost:1122/actuator/prometheus`

### Swagger/OpenAPI

> Mỗi service Spring Boot (`myshop-module-manager`, `myshop-media-service`,
> `myshop-ai-evaluation-service`...) là 1 process/JAR **độc lập**, chạy port
> riêng và có Swagger UI riêng. Service nào **chưa được start** thì Swagger UI
> của nó sẽ không truy cập được — phải `mvn spring-boot:run -pl <module>`
> trước.

| Service                                  | Port   | Swagger UI                                    | OpenAPI JSON                        |
| ---------------------------------------- | ------ | --------------------------------------------- | ----------------------------------- |
| `myshop-module-manager`                  | `1122` | `http://localhost:1122/swagger-ui/index.html` | `http://localhost:1122/v3/api-docs` |
| `myshop-media-service` (Phase 1)         | `8081` | `http://localhost:8081/swagger-ui/index.html` | `http://localhost:8081/v3/api-docs` |
| `myshop-media-worker` (Phase 1)          | `8082` | _(consumer only, không có REST API/Swagger)_  | -                                   |
| `myshop-ai-evaluation-service` (Phase 2) | `8083` | `http://localhost:8083/swagger-ui/index.html` | `http://localhost:8083/v3/api-docs` |

Chạy từng service (mỗi service 1 terminal riêng):

```bash
mvn spring-boot:run -pl myshop-module-manager
mvn spring-boot:run -pl myshop-media-service -am
mvn spring-boot:run -pl myshop-media-worker -am
mvn spring-boot:run -pl myshop-ai-evaluation-service -am
```

> Lưu ý: `myshop-media-service`/`myshop-media-worker` cần `media-postgres` +
> `media-rabbitmq` đang chạy (xem mục 5); `myshop-ai-evaluation-service` cần
> `ai-postgres` đang chạy — tất cả đã có sẵn trong `docker-compose.yml`.

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
- Node Exporter: `http://localhost:9100/metrics`
- MySQL: `localhost:3306`

## 6) Cấu hình scrape Prometheus

File: `monitoring/prometheus/prometheus.yml`

- scrape interval: `10s`
- target app: `host.docker.internal:1122`
- metrics path: `/actuator/prometheus`

Thêm target Node Exporter:

- job: `node`
- target: `node-exporter:9100`

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
curl http://localhost:1122/actuator/health
curl http://localhost:1122/actuator/prometheus
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
3. Target `myshop-module-manager` và `node` phải ở trạng thái `UP`

### 8.3 Verify Grafana

1. Mở `http://localhost:3000`
2. Đăng nhập Grafana
3. Vào `Connections` / `Data Sources`
4. Kiểm tra datasource `Prometheus` đã có và `Save & test` thành công

### 8.4 Setup dashboard Node Exporter Full (ID `1860`)

1. Trong Grafana chọn `Dashboards` → `New` → `Import`
2. Nhập dashboard ID: `1860`
3. Chọn datasource: `Prometheus`
4. Bấm `Import`

Sau khi import, chọn biến:

- `job`: `node`
- `instance`: `node-exporter:9100`

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
  - App: `1122`
  - Prometheus: `9090`
  - Grafana: `3000`
  - Node Exporter: `9100`
- Monitoring được bổ sung mà không thay đổi business logic API hiện tại.

Thêm đoạn ngắn này vào README:

````markdown
## 12) Load Testing với wrk

Cài đặt:

```bash
brew install wrk
```

Chạy load test:

```bash
wrk -t4 -c100 -d30s http://localhost:1122/ticket/1/detail
```

- `-t4`: 4 threads
- `-c100`: 100 concurrent connections
- `-d30s`: chạy trong 30 giây

Tăng tải để kiểm tra giới hạn:

```bash
wrk -t4 -c50  -d30s http://localhost:1122/ticket/1/detail
wrk -t4 -c100 -d30s http://localhost:1122/ticket/1/detail
wrk -t4 -c200 -d30s http://localhost:1122/ticket/1/detail
wrk -t4 -c500 -d30s http://localhost:1122/ticket/1/detail
```

Theo dõi `Latency`, `Requests/sec`, `Non-2xx/3xx` và CPU/Memory/DB trên Grafana.
````

Sau đó đổi các mục phía dưới từ **9, 10, 11** thành **10, 11, 12**.

```

```

Chạy load test:

```bash
wrk -t4 -c100 -d30s http://localhost:1122/ticket/1/detail
```

- `-t4`: 4 threads
- `-c100`: 100 concurrent connections
- `-d30s`: chạy trong 30 giây

Tăng tải để kiểm tra giới hạn:

```bash
wrk -t4 -c50  -d30s http://localhost:1122/ticket/1/detail
wrk -t4 -c100 -d30s http://localhost:1122/ticket/1/detail
wrk -t4 -c200 -d30s http://localhost:1122/ticket/1/detail
wrk -t4 -c500 -d30s http://localhost:1122/ticket/1/detail
```

Theo dõi `Latency`, `Requests/sec`, `Non-2xx/3xx` và CPU/Memory/DB trên Grafana.

```

Sau đó đổi các mục phía dưới từ **9, 10, 11** thành **10, 11, 12**.
```
