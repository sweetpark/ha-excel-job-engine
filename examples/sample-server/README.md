# 🖥️ HA Excel Sample Server

A minimal, runnable Spring Boot backend wiring up **ha-excel-job-engine** end to end: H2
in-memory database, a dummy `ExcelDataProvider`, and the library's own REST controller. Clone
this folder and run it - no other setup required.

It pairs with [`examples/sample-client`](../sample-client) (same `bizNm`, same column names), but
also works standalone via `curl`.

---

## 🚀 Running the Example

```bash
cd examples/sample-server
./gradlew bootRun
```

The server starts on `http://localhost:8080`, backed by an in-memory H2 database and local-disk
file storage (`./sample-server-storage`).

Trigger an export:
```bash
curl -X POST http://localhost:8080/api/excel/orderList \
  -H "Content-Type: application/json" \
  -d '{"fileName":"orders","totalCnt":500,"columns":[{"field":"orderNo","headerName":"Order Number"},{"field":"customerName","headerName":"Customer"},{"field":"amount","headerName":"Amount"}]}'
```

The response contains a `jobId`; poll and download it:
```bash
curl http://localhost:8080/api/excel/<jobId>/status
curl -OJ http://localhost:8080/api/excel/<jobId>/file
```

---

## 🔗 Pairing with the React client

```bash
# terminal 1
cd examples/sample-server && ./gradlew bootRun

# terminal 2
cd examples/sample-client && npm install && npm run dev
```

Open `http://localhost:3000` - the button submits a real export against this server (Vite proxies
`/api/*` to `http://localhost:8080`, see `examples/sample-client/vite.config.ts`).

---

## 📖 What to look at

- [`SampleServerApplication.java`](src/main/java/com/example/haexcel/sample/SampleServerApplication.java) - the entire bootstrap, three lines.
- [`DummyOrderDataProvider.java`](src/main/java/com/example/haexcel/sample/DummyOrderDataProvider.java) - the **only** class you need to write to plug in your own data: implement `ExcelDataProvider`, register it as a `@Component`, done.
- [`application.yml`](src/main/resources/application.yml) - the minimum `ha-excel.*` / `mybatis.mapper-locations` / `spring.sql.init` configuration a consumer needs (the schema and MyBatis mapper XML ship inside the `ha-excel-job-engine` jar itself, so nothing extra to copy).

For a walkthrough of the extension points (`ExcelDataProvider`, `ExcelStreamable`, custom
`StorageProvider`, `ExcelSecurityProvider`), see [`docs/INTERFACES.md`](../../docs/INTERFACES.md)
at the repo root.
