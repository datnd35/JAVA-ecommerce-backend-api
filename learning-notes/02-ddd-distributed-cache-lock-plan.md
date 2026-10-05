# 🛡️ JAVA DDD #05 — Plan áp dụng Distributed Cache & Distributed Lock vào project `JAVA-ecommerce-backend-api-MEMBER`

> Mục tiêu: biến kiến thức **Cache → Lock → Double Check → DB Protection** thành implementation thực tế trong project hiện tại.

---

## 1) Hiện trạng project (đã đối chiếu code hiện tại)

- Multi-module Maven:
  - `myshop-framework` (entity/mapper/service)
  - `myshop-module-manager` (Spring Boot API)
- Có MySQL + Prometheus + Grafana trong `docker-compose.yml`
- Chưa có Redis/Redisson trong dependencies
- Chưa có cache/lock trong flow `ProductBrand`
- Endpoint phù hợp để pilot:
  - `GET /manager/product/brand/{id}`
  - `PUT /manager/product/brand/{id}`
  - `PUT /manager/product/brand/disable/{id}`
  - `DELETE /manager/product/brand/{ids}`

---

## 2) Contract triển khai (để tránh làm lan man)

- **Input:** `brandId`
- **Output:** `ProductBrand` (hoặc not found)
- **Success criteria:**
  1. Cache hit trả nhanh, không chạm DB
  2. Cache miss đồng thời cho cùng key: chỉ 1 request xuống DB
  3. Write thành công thì cache key liên quan được invalidate chính xác
- **Error mode:**
  - Redis lỗi: fallback có kiểm soát
  - Lock timeout: fail-fast/degrade có log + metric rõ ràng

---

## 3) Plan áp dụng theo 4 pha

## Pha A — Foundation (hạ tầng + wiring)

### Mục tiêu

Có Redis + Redisson chạy được với app hiện tại.

### Việc cần làm

1. Thêm Redis vào `docker-compose.yml`
2. Thêm dependencies:
   - `spring-boot-starter-data-redis`
   - `org.redisson:redisson-spring-boot-starter`
3. Bổ sung config Redis vào `myshop-module-manager/src/main/resources/application.yml`
4. Tạo cấu hình infra trong framework:
   - `RedisTemplate` (JSON serializer)
   - `RedissonClient` (nếu cần custom)

### Kết quả kỳ vọng

- App start ổn khi Redis up
- Khi Redis down: lỗi rõ ràng, không crash dây chuyền

---

## Pha B — Pilot use case: Read-through cache + distributed lock + double-check

### Mục tiêu

Chống cache stampede cho `getById`.

### Nơi áp dụng

- `myshop-framework/src/main/java/com/myshop/modules/product/serviceimpl/ProductBrandServiceImpl.java`
- Method: `getById(String id)`

### Flow chuẩn

1. Cache check #1 (`myshop:product-brand:{id}`)
2. MISS → acquire lock (`myshop:lock:product-brand:{id}`)
3. Lock success → cache check #2
4. Nếu vẫn MISS → query MySQL → set cache (TTL)
5. `finally` unlock

### Cấu hình khuyến nghị ban đầu

- Data TTL: `5-15 phút`
- Lock lease: `5-10 giây`
- Wait lock: `50-200ms`

---

## Pha C — Invalidation & consistency sau write

### Mục tiêu

Giảm stale data sau cập nhật.

### Việc cần làm

- Trong `create/update/disable/deleteByIds`, sau DB success:
  - `DEL myshop:product-brand:{id}`
- Nếu có cache cho list (`getAll`) thì invalidate key list liên quan

### Rule đơn giản

**Write success ⇒ invalidate read cache liên quan.**

---

## Pha D — Resilience + Monitoring + Load Test

### Mục tiêu

Biến solution thành production-ready.

### Việc cần làm

1. Resilience4j:
   - timeout/retry có kiểm soát cho cache client
   - circuit breaker (tránh cascading failure)
2. Metrics cần có:
   - cache hit/miss ratio
   - lock acquire success/fail
   - lock wait time
   - DB query count cho cùng key dưới tải cao
3. Grafana dashboard:
   - hit ratio
   - p95/p99 latency endpoint nóng
   - lock contention

---

## 4) Edge cases bắt buộc cover

1. **Cache penetration** (id không tồn tại bị gọi liên tục)
   - Cache null ngắn hạn (`30-60s`)
2. **Hot key expire cùng lúc**
   - TTL jitter (ví dụ + random `0-60s`)
3. **Request chết giữa critical section**
   - `try/finally` + lease time
4. **Redis chập chờn/down tạm thời**
   - Fallback DB + giới hạn truy cập để bảo vệ DB
5. **Scale nhiều instance**
   - Dùng distributed lock (Redis/Redisson), không dùng local lock cho logic cross-instance

---

## 5) Mapping theo hướng DDD (phù hợp repo hiện tại)

Đề xuất phân tách nhẹ, không cần lật kiến trúc:

- `com.myshop.modules.product.service`
  - Giữ use case/service contract
- `com.myshop.modules.product.serviceimpl`
  - Điều phối flow cache-lock-doublecheck
- `com.myshop.cache` (đang trống, nên dùng)
  - `CacheKeyFactory`
  - `CacheTtlPolicy`
  - `ProductBrandCacheRepository`
- `com.myshop.distributed` (tạo mới)
  - `DistributedLockService` wrapper Redisson

---

## 6) Lộ trình 7 ngày (học + làm song song)

- **Day 1:** setup Redis/Redisson, app boot ổn
- **Day 2:** implement double-check locking cho `getById`
- **Day 3:** implement invalidate cache cho các write APIs
- **Day 4:** thêm metrics và log lock/cache
- **Day 5:** chạy load test endpoint nóng, so sánh trước/sau
- **Day 6:** thêm resilience policies (timeout/retry/circuit)
- **Day 7:** viết runbook sự cố (Redis down, lock contention cao)

---

## 7) Definition of Done

Một vòng triển khai được xem là đạt khi:

- Đồng thời cao vào cùng `id` không gây DB spike tương ứng số request
- Hit ratio tăng theo thời gian warm-up
- p95 ổn định hơn so với trước khi có cache+lock
- Redis lỗi không làm sập API toàn hệ thống
- Có dashboard và log đủ để quan sát contention + degradation

---

## 8) Checklist triển khai production

- [ ] Có cache key naming convention rõ ràng
- [ ] Có TTL policy + jitter
- [ ] Có distributed lock key theo resource scope
- [ ] Có double-check sau khi acquire lock
- [ ] Có unlock trong `finally`
- [ ] Có invalidation cho mọi write path
- [ ] Có fallback khi Redis lỗi
- [ ] Có lock/cache metrics + alert ngưỡng contention
- [ ] Có load test report trước/sau

---

## 9) Câu chốt tư duy

**Đừng chỉ hỏi: “Code có chạy đúng không?”**

Hãy hỏi:

**“Nếu 10,000 request cùng lúc vào một key nóng, hệ thống của mình phản ứng thế nào và có tự bảo vệ DB được không?”**
