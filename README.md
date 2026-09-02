# 📊 HA Excel Job Engine

<p align="center">
  <a href="https://github.com/sweetpark/ha-excel-job-engine/actions/workflows/ci.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/sweetpark/ha-excel-job-engine/ci.yml?branch=main&style=flat-square&logo=github&label=CI" alt="CI Status" />
  </a>
  <a href="https://github.com/sweetpark/ha-excel-job-engine/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square" alt="License" />
  </a>
  <img src="https://img.shields.io/badge/Java-17%20%7C%2021%2B-orange.svg?style=flat-square&logo=openjdk" alt="Java 17 / 21+" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg?style=flat-square&logo=springboot" alt="Spring Boot 3.x" />
  <img src="https://img.shields.io/badge/Coverage-100%25%20Verified-success.svg?style=flat-square" alt="Coverage" />
</p>

<p align="center">
  <b>Redis-Free High-Availability Distributed Job Queue & Out-Of-Memory Proof Excel Export Engine for Spring Boot</b>
</p>

---

## 🌟 Why HA Excel Job Engine?

In large-scale enterprise systems, exporting massive datasets (hundreds of thousands to millions of rows) often triggers catastrophic failures:
- **JVM Heap Exhaustion (OOM Crash)** from buffering large workbook models in memory.
- **Heavy Infrastructure Dependency**: Complex setups requiring Redis distributed locks (Redisson), Celery, or RabbitMQ clusters just to coordinate background workers across multiple nodes.
- **Serverless/Multi-Node Desynchronization**: A file generated on Node A cannot be downloaded by a user routed to Node B without sticky sessions or complex reverse proxies.
- **Unsafe Thread Interruption**: Abruptly terminating long-running POI streams corrupts ZIP packaging and leaves dangling file descriptors.

**HA Excel Job Engine** resolves these challenges with an elegant, zero-external-middleware architecture.

---

## 🚀 Key Highlights

1. **Redis-Free CAS (Compare-And-Swap) Atomic Preemption**:
   - Zero Redis or ZooKeeper required. Coordinates distributed multi-node workers atomically using standard relational database transactions (UPDATE ha_excel_job SET status='RUNNING' WHERE status='PENDING').
2. **Instant Push Dispatch + Virtual Threads**:
   - Jobs are pushed into local in-memory worker queues instantaneously upon creation—eliminating periodic database polling query noise.
   - Leverages Java Virtual Threads for lightweight, high-concurrency background streaming.
3. **Dual Queue Strategy (Single XLSX vs. Chunked ZIP)**:
   - Automatically diverts small/medium exports (< 100k rows) to **Normal Queue** (SXSSFWorkbook streaming).
   - Diverts massive exports (100k ~ 2,000,000+ rows) to **Large Queue**, chunking data across multiple .xlsx workbooks and compressing into a single .zip on the fly.
4. **Pluggable Multi-Storage Architecture (6 Providers)**:
   - Seamlessly store and serve files across all cluster nodes using **Local Disk**, **Shared NAS (NFS/CIFS)**, **AWS S3 / MinIO**, **Naver Cloud Platform (NCP)**, **Azure Blob Storage**, or **Google Cloud Storage (GCS)**.
5. **Crash & Orphan Recovery (Heartbeat Scanner)**:
   - Server restarts automatically detect stale jobs on the restarting node and clean up dangling temporary files.
   - Background orphan scanner reclaims orphaned jobs if an application node abruptly dies.
6. **Safe Cooperative Cancellation Checkpoints**:
   - Periodic row checkpoints (every 1,000 rows) check user cancellation requests, safely closing streams without Thread.interrupt() corruption.

---


---

## 📚 Complete Documentation

| Document | Description |
|---|---|
| 🌐 **[REST API Reference](docs/REST_API.md)** | Detailed specification of all HTTP endpoints, payload schemas, formatters, and status codes |
| 🧩 **[Interfaces & SPI Guide](docs/INTERFACES.md)** | Extension guide for `ExcelDataProvider`, `ExcelStreamable`, custom `StorageProvider`, and `ExcelSecurityProvider` |
| 💻 **[Client Integration Guide](docs/CLIENT_INTEGRATION_GUIDE.md)** | Frontend React custom hook (`useExcelExport`), progress bar handling, and AG Grid column adapter |
| 🏛️ **[Architecture Design](docs/ARCHITECTURE.md)** | Deep dive into DB CAS atomicity, dual queue design, crash recovery, and storage abstraction |
| 📐 **[Coding Conventions](docs/CONVENTIONS.md)** | Code style, Spotless formatting, SpotBugs static analysis, and Conventional Commits |

## 🏛️ Architecture Overview

`
                                 [ Client / Web Browser ]
                                            │
                                            ▼
                                  [ Nginx / API Gateway ]
                                            │
                   ┌────────────────────────┴────────────────────────┐
                   ▼                                                 ▼
          ┌─────────────────┐                               ┌─────────────────┐
          │   App Node 1    │                               │   App Node 2    │
          │ ┌─────────────┐ │                               │ ┌─────────────┐ │
          │ │Push Queue(L)│ │                               │ │Push Queue(L)│ │
          │ └──────┬──────┘ │                               │ └──────┬──────┘ │
          │        ▼        │                               │        ▼        │
          │ Virtual Threads │                               │ Virtual Threads │
          └────────┬────────┘                               └────────┬────────┘
                   │                                                 │
                   │      CAS Atomic Claim (UPDATE ... WHERE)        │
                   ├────────────────────────┬────────────────────────┤
                   ▼                                                 ▼
         ┌───────────────────┐                             ┌───────────────────┐
         │ Relational DB     │                             │ Shared Storage    │
         │ (ha_excel_job)    │                             │ (Local/NAS/S3/NCP)│
         └───────────────────┘                             └───────────────────┘
`

---

## 📦 Getting Started

### 1. Add Dependency

#### Gradle (Kotlin/Groovy)
`groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'io.github.sweetpark:ha-excel-job-engine:1.0.0'
}
`

#### Maven
`xml
<dependency>
    <groupId>io.github.sweetpark</groupId>
    <artifactId>ha-excel-job-engine</artifactId>
    <version>1.0.0</version>
</dependency>
`

---

### 2. Database Schema Setup

Execute the DDL script for your database:
- MySQL / MariaDB: [schema-mysql.sql](src/main/resources/schema-mysql.sql)
- PostgreSQL: [schema-postgresql.sql](src/main/resources/schema-postgresql.sql)
- H2: [schema-h2.sql](src/main/resources/schema-h2.sql)

---

### 3. Configure pplication.yml

`yaml
ha-excel:
  client-threshold: 10000        # Exports under 10k rows can be exported directly by client
  worker-count: 4                # Virtual thread workers for single xlsx queue
  large-worker-count: 2          # Virtual thread workers for chunked zip queue
  zip-threshold: 100000          # Rows >= 100k automatically split into chunked ZIP
  chunk-size: 50000              # Max rows per workbook in ZIP mode
  job-ttl-minutes: 60            # Auto-delete generated files after 60 minutes
  storage-type: S3               # LOCAL | NAS | S3 | NCP | AZURE | GCP
  s3-bucket: my-excel-bucket
  s3-region: ap-northeast-2
  s3-endpoint: http://minio:9000 # Optional (for MinIO)
`

---

### 4. Provide Data (Implement ExcelDataProvider)

Implement ExcelDataProvider as a Spring Bean to supply data for a given izNm:

`java
@Component
public class OrderListDataProvider implements ExcelDataProvider {

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public String getName() {
        return "orderList"; // Matches /api/excel/orderList
    }

    @Override
    public List<Map<String, Object>> fetchData(Map<String, Object> params) {
        return orderRepository.findOrdersByParams(params);
    }
}
`

For ultra-large datasets with MyBatis Cursor streaming:
`java
@Component
public class StreamingOrderDataProvider implements ExcelDataProvider, ExcelStreamable {

    @Override
    public String getName() {
        return "streamingOrders";
    }

    @Override
    public Cursor<Map<String, Object>> streamRows(Map<String, Object> params, SqlSession sqlSession) {
        return sqlSession.selectCursor("com.example.OrderMapper.streamAll", params);
    }
}
`

---

## 🌐 REST API Reference

| Method | Endpoint | Description | Response Code |
|---|---|---|:---:|
| POST | /api/excel/{bizNm} | Submit asynchronous export job | 202 Accepted |
| GET | /api/excel/{jobId}/status | Check job progress, percentage, and queue wait time | 200 OK |
| GET | /api/excel/{jobId}/file | Download completed .xlsx or .zip file | 200 OK |
| DELETE | /api/excel/{jobId}/cancel | Request cancellation of pending or running job | 200 OK |
| GET | /api/excel/config | Retrieve client configuration (e.g., clientThreshold) | 200 OK |

### Sample Request: POST /api/excel/orderList
`json
{
  "fileName": "2026_Q3_Orders",
  "totalCnt": 150000,
  "params": {
    "status": "PAID",
    "startDate": "20260901"
  },
  "columns": [
    { "field": "orderNo", "headerName": "Order Number", "width": 120 },
    { "field": "customerName", "headerName": "Customer", "width": 100 },
    { "field": "amount", "headerName": "Total (KRW)", "width": 110, "excelFormat": "krw" },
    { "field": "orderedAt", "headerName": "Order Time", "width": 140, "excelFormat": "datetime" }
  ]
}
`

---

## 🐳 Running the 2-Node Cluster Demo

We provide a complete Docker Compose demonstration environment consisting of:
- **Nginx** (Load Balancer on port 80)
- **Node 1** (Spring Boot on port 8081)
- **Node 2** (Spring Boot on port 8082)
- **MariaDB** (Relational DB on port 3306)
- **MinIO** (S3-compatible Object Storage on port 9000 / Console 9001)

`ash
docker-compose up --build -d
`

Test the cluster:
`ash
curl http://localhost/api/excel/config
`

---

## 🛡️ Quality Gate & Verification

`ash
# Run tests, JaCoCo coverage, SpotBugs, and Spotless formatting check
./gradlew check

# Automatically reformat codebase to Google Java Format
./gradlew spotlessApply
`

---

## 📄 License
This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.