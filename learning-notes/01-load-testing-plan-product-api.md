# Bài 01 — Kế hoạch Load Test API Product (1000 request/concurrency)

## Bối cảnh

Plan này tốt hơn nhiều so với bắt đầu bằng POST tạo 1.000 records.
Với Java/Spring Boot hiện tại, hướng đi này đúng với:

- System Design
- Performance
- Observability

Mục tiêu: benchmark có ý nghĩa, học được rõ tác động của từng tối ưu.

---

## Phase 1 — Baseline: chưa cache

Đo hệ thống nguyên bản:

```text
k6
 │
 │ 100 req/s
 ▼
Spring Boot
 │
 ▼
Controller
 │
 ▼
Service
 │
 ▼
MyBatis
 │
 ▼
MySQL
```

Test:

- 50 VUs
- 100 VUs
- 300 VUs
- 500 VUs

Thu thập:

- RPS
- p50
- p95
- p99
- Error Rate
- CPU
- Memory
- DB Connections

**Quan trọng:** không tối ưu trước khi có baseline.

---

## Phase 2 — Tối ưu DB

Kiểm tra query API Product.

Ví dụ:

```sql
SELECT ...
FROM product
WHERE ...
ORDER BY create_time DESC
LIMIT 20 OFFSET 0;
```

Phân tích với:

```sql
EXPLAIN ANALYZE ...
```

Quan sát:

- Full Table Scan?
- Index?
- Rows examined?
- Sort?

Nếu cần thì thêm index phù hợp.

```text
Before

Request
   ↓
MySQL
   ↓
Scan 100,000 rows
   ↓
Return 20 rows


After

Request
   ↓
Index
   ↓
Find đúng records
   ↓
Return 20 rows
```

Sau đó benchmark lại để thấy tác động thực tế của index.

---

## Phase 3 — Connection Pool (HikariCP)

Ví dụ cấu hình:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
```

Lưu ý: `20` không phải con số mặc định đúng cho mọi hệ thống.

Mục tiêu học:

```text
1000 requests
      ↓
Thread Pool
      ↓
HikariCP
      ↓
20 DB connections
      ↓
MySQL
```

Nếu `Requests > DB connections` thì request sẽ chờ connection.

Theo dõi:

- Hikari Active
- Hikari Idle
- Hikari Pending

---

## Phase 4 — Redis Cache

Trước cache:

```text
1000 requests
      ↓
Spring Boot
      ↓
MySQL
      ↓
MySQL xử lý 1000 lần
```

Sau cache:

```text
             ┌──────────────┐
             │    Redis     │
             └──────┬───────┘
                    │
             Cache HIT?
               /       \
             YES        NO
              │          │
              ▼          ▼
          Response     MySQL
                         │
                         ▼
                       Redis
                         │
                         ▼
                      Response
```

Ví dụ endpoint:

```text
GET /manager/product?page=1&size=20
```

Cache key:

```text
product:list:page=1:size=20
```

TTL đề xuất:

```text
60 seconds
```

---

## Phase 5 — So sánh benchmark

Chạy cùng kịch bản 3 lần:

### Test A — DB trực tiếp

```text
k6
 ↓
Spring Boot
 ↓
MySQL
```

### Test B — DB + Index

```text
k6
 ↓
Spring Boot
 ↓
MySQL + Index
```

### Test C — Redis Cache

```text
k6
 ↓
Spring Boot
 ↓
Redis
 ↓
MySQL chỉ khi cache miss
```

Bảng kết quả:

| Test       | RPS | p95 | Error | DB CPU |
| ---------- | --: | --: | ----: | -----: |
| DB         |   ? |   ? |     ? |      ? |
| DB + Index |   ? |   ? |     ? |      ? |
| Redis      |   ? |   ? |     ? |      ? |

Không giả định trước kết quả — để benchmark trả lời bằng số liệu.

---

## Phase 6 — Load test thực tế

Phân biệt rõ:

### 1) 1000 requests total

- Chạy xong là kết thúc
- Ít phản ánh khả năng chịu tải dài hơi

### 2) 1000 concurrent users (1000 VUs)

- Mô phỏng đồng thời thực tế
- Phù hợp để nghiên cứu bottleneck và độ ổn định

```text
          ┌─ Request
          ├─ Request
          ├─ Request
          ├─ Request
k6 ───────┼─ ...
          ├─ Request
          └─ Request

          1000 VUs
```

---

## Bộ test đề xuất (4 bài)

```text
                    LOAD TEST
                        │
          ┌─────────────┼─────────────┐
          │             │             │
       Baseline      Stress         Spike
          │             │             │
       100 RPS       500 RPS       1000 VUs
          │             │             │
          └─────────────┼─────────────┘
                        │
                    Benchmark
```

- **Test 1 — Baseline:** 100 RPS trong 5 phút
- **Test 2 — Medium:** 300 RPS trong 5 phút
- **Test 3 — Stress:** 500 RPS trong 5 phút
- **Test 4 — Spike:** 100 → 1000 VUs trong 30–60s

Mục tiêu: biết hệ thống bắt đầu degrade ở ngưỡng nào.

---

## Grafana ở trung tâm quan sát

```text
                         ┌─────────────┐
                         │     k6      │
                         └──────┬──────┘
                                │
                         HTTP Requests
                                │
                                ▼
┌─────────────────────────────────────────────────┐
│                  Spring Boot                    │
│                                                 │
│ Controller → Service → Cache → MyBatis          │
└───────────────┬─────────────────┬───────────────┘
                │                 │
                ▼                 ▼
             Redis              MySQL
                │                 │
                └────────┬────────┘
                         │
                      Metrics
                         │
                         ▼
                   Prometheus
                         │
                         ▼
                      Grafana
```

Grafana giúp trả lời **vì sao** performance thay đổi, không chỉ “nhanh/chậm”.

---

## Thứ tự học khuyến nghị

```text
        ┌───────────────┐
        │ 1. Baseline   │
        └───────┬───────┘
                ↓
        ┌───────────────┐
        │ 2. DB Index   │
        └───────┬───────┘
                ↓
        ┌───────────────┐
        │ 3. HikariCP   │
        └───────┬───────┘
                ↓
        ┌───────────────┐
        │ 4. Redis      │
        └───────┬───────┘
                ↓
        ┌───────────────┐
        │ 5. Load Test  │
        └───────┬───────┘
                ↓
        ┌───────────────┐
        │ 6. Grafana    │
        └───────────────┘
```

Nguyên tắc: **benchmark → quan sát → thay đổi → benchmark lại**.

---

## Kết luận bài 01

Endpoint nên chọn đầu tiên cho project hiện tại:

- **GET Product List**

Xây toàn bộ benchmark quanh endpoint này để học sâu:

- Spring Boot performance tuning
- DB optimization
- Connection pool behavior
- Redis caching strategy
- Observability với Prometheus/Grafana
