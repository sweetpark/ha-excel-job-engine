# 🧩 Interfaces & Extension Guide (SPI Reference)

`ha-excel-job-engine` is designed with pluggable extension points (SPIs) allowing developers to customize data retrieval, streaming, storage, security, and templating without modifying the core engine.

---

## 📑 Core Extension Points

| Interface | Package | Purpose | Default Implementation |
|---|---|---|---|
| [`ExcelDataProvider`](#1-exceldataprovider) | `io.github.sweetpark.haexcel.core` | Generic data provider for in-memory datasets | Discovered via `ExcelDataRegistry` |
| [`ExcelStreamable`](#2-excelstreamable) | `io.github.sweetpark.haexcel.core` | MyBatis `Cursor` streaming row-by-row | Optional interface for `ExcelDataProvider` |
| [`StorageProvider`](#3-storageprovider) | `io.github.sweetpark.haexcel.storage` | Strategy for saving and retrieving generated files | `LocalDiskStorageProvider` (or NAS/S3/NCP/Azure/GCP) |
| [`ExcelSecurityProvider`](#4-excelsecurityprovider) | `io.github.sweetpark.haexcel.controller` | User identification & IDOR job ownership verification | `DefaultExcelSecurityProvider` |
| [`TemplateExcelEngine`](#5-templateexcelengine) | `io.github.sweetpark.haexcel.template` | Engine for template-based (.xlsx) report filling | `JxlsTemplateEngine` |

---

## 1. `ExcelDataProvider`

### Purpose
Provides tabular dataset rows as a `List<Map<String, Object>>` for a given `bizNm` (query identifier).

### Interface Definition
```java
package io.github.sweetpark.haexcel.core;

import java.util.List;
import java.util.Map;

public interface ExcelDataProvider {

    /**
     * Unique identifier matching the {bizNm} URL parameter in POST /api/excel/{bizNm}.
     */
    String getName();

    /**
     * Fetches dataset rows based on request parameters.
     */
    List<Map<String, Object>> fetchData(Map<String, Object> params);

    /**
     * Whether this provider supports Cursor streaming (default checks instanceof ExcelStreamable).
     */
    default boolean isStreamable() {
        return this instanceof ExcelStreamable;
    }
}
```

### Example Implementation
```java
@Component
public class UserExportDataProvider implements ExcelDataProvider {

    @Autowired
    private UserRepository userRepository;

    @Override
    public String getName() {
        return "userList"; // Handles POST /api/excel/userList
    }

    @Override
    public List<Map<String, Object>> fetchData(Map<String, Object> params) {
        String dept = (String) params.get("department");
        return userRepository.findByDepartment(dept)
                .stream()
                .map(user -> Map.of(
                        "userId", user.getId(),
                        "userName", user.getName(),
                        "email", user.getEmail(),
                        "joinedAt", user.getJoinedDate()
                ))
                .toList();
    }
}
```
> **Auto-Discovery**: `ExcelDataRegistry` automatically discovers and registers all Spring beans implementing `ExcelDataProvider` on startup.

---

## 2. `ExcelStreamable`

### Purpose
To export massive datasets (e.g. 500,000 ~ 2,000,000 rows) without exhausting the JVM heap, `ExcelStreamable` streams rows directly from the database through a MyBatis `Cursor<T>`.

### Interface Definition
```java
package io.github.sweetpark.haexcel.core;

import java.util.Map;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;

public interface ExcelStreamable {

    /**
     * Returns an active Cursor for streaming rows.
     * The lifecycle of the SqlSession is managed by ExcelGeneratorService.
     */
    Cursor<Map<String, Object>> streamRows(Map<String, Object> params, SqlSession sqlSession);
}
```

### Example Implementation with MyBatis Cursor
```java
@Component
public class MassiveOrderDataProvider implements ExcelDataProvider, ExcelStreamable {

    @Override
    public String getName() {
        return "massiveOrders";
    }

    @Override
    public List<Map<String, Object>> fetchData(Map<String, Object> params) {
        // Fallback for non-streaming environments
        return Collections.emptyList();
    }

    @Override
    public Cursor<Map<String, Object>> streamRows(Map<String, Object> params, SqlSession sqlSession) {
        // Calls MyBatis mapper statement returning Cursor<Map<String, Object>>
        return sqlSession.selectCursor("com.example.mapper.OrderMapper.streamAllOrders", params);
    }
}
```

#### MyBatis Mapper XML Definition
```xml
<select id="streamAllOrders" resultType="java.util.LinkedHashMap" fetchSize="1000">
    SELECT order_id     AS orderId,
           user_name    AS userName,
           total_amount AS amount,
           created_at   AS orderedAt
      FROM orders
     WHERE status = #{status}
</select>
```
> **Memory Advantage**: Rows are streamed in batches (`fetchSize="1000"`), piped directly into Apache POI `SXSSFWorkbook` (with a sliding row window of 100 in memory), and flushed to disk, keeping JVM heap usage virtually constant regardless of dataset size.

---

## 3. `StorageProvider`

### Purpose
Decouples file storage from local node disks so that any cluster node can serve the download link (`GET /api/excel/{jobId}/file`).

### Interface Definition
```java
package io.github.sweetpark.haexcel.storage;

import java.io.IOException;
import java.nio.file.Path;

public interface StorageProvider {

    StorageType getType();

    String storeFile(Path source, String key, String contentType) throws IOException;

    StorageResource getResource(String key) throws IOException;

    void delete(String key) throws IOException;
}
```

### Implementing a Custom Storage Provider
If your enterprise uses a private storage solution (e.g., Ceph, WebDAV, SFTP, or MinIO with custom encryption), simply implement `StorageProvider` and declare it as a `@Bean`:

```java
@Component
@Primary
public class CustomMinioStorageProvider implements StorageProvider {

    private final io.minio.MinioClient minioClient;
    private final String bucket = "my-secure-excel-bucket";

    public CustomMinioStorageProvider(io.minio.MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }

    @Override
    public String storeFile(Path source, String key, String contentType) throws IOException {
        try {
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .filename(source.toString())
                            .contentType(contentType)
                            .build()
            );
            return key;
        } catch (Exception e) {
            throw new IOException("Failed to upload to MinIO", e);
        }
    }

    @Override
    public StorageResource getResource(String key) throws IOException {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build()
            );
            return new StorageResource(
                    new InputStreamResource(stream),
                    key,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    -1
            );
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(key).build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to delete from MinIO", e);
        }
    }
}
```
> Because `ExcelAutoConfiguration` uses `@ConditionalOnMissingBean(StorageProvider.class)`, registering your own bean overrides the default provider automatically.

---

## 4. `ExcelSecurityProvider`

### Purpose
Extracts the current user identifier and ensures users can only check or download their own export jobs (IDOR protection).

### Interface Definition
```java
package io.github.sweetpark.haexcel.controller;

import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import jakarta.servlet.http.HttpServletRequest;

public interface ExcelSecurityProvider {

    String extractUserId(HttpServletRequest request);

    default boolean isOwner(ExcelJob job, String requestUserId) {
        if (requestUserId == null || requestUserId.isBlank()) {
            return true;
        }
        return requestUserId.equals(job.getWorker());
    }
}
```

### Spring Security Integration Example
```java
@Component
public class SpringSecurityExcelProvider implements ExcelSecurityProvider {

    @Override
    public String extractUserId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    @Override
    public boolean isOwner(ExcelJob job, String requestUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Allow ADMIN role to access all files
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        return requestUserId.equals(job.getWorker());
    }
}
```

---

## 5. `TemplateExcelEngine`

### Purpose
Applies business data models into pre-styled `.xlsx` template files (e.g. accounting balance sheets, tax receipts).

### Interface Definition
```java
package io.github.sweetpark.haexcel.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public interface TemplateExcelEngine {

    void fill(InputStream templateInput, Map<String, Object> context, OutputStream output) throws IOException;
}
```
> **Default Engine (`JxlsTemplateEngine`)**: Uses Jxls 3.x with a built-in rich-text XML flattening preprocessor to prevent Apache POI `XmlValueDisconnectedException` on merged cells.

---

## ⚙️ Configuration Properties Reference (`application.yml`)

```yaml
ha-excel:
  # Client direct export threshold (row count)
  client-threshold: 10000

  # Worker thread pool size for normal (single xlsx) queue
  worker-count: 4

  # Worker thread pool size for large (chunked zip) queue
  large-worker-count: 2

  # Server node identifier (defaults to hostname if omitted)
  server-id: ""

  # Idempotency window in minutes to prevent duplicate submissions
  idempotency-window-minutes: 30

  # Hours before a PENDING job is treated as orphaned and reclaimed
  orphan-threshold-hours: 2

  # Completed file retention TTL in minutes (auto-evicted after expiry)
  job-ttl-minutes: 60

  # Row threshold above which jobs are chunked into a ZIP archive
  zip-threshold: 100000

  # Maximum rows per workbook part in ZIP mode
  chunk-size: 50000

  # Storage provider type: LOCAL | NAS | S3 | NCP | AZURE | GCP
  storage-type: LOCAL

  # Storage specific settings
  local-storage-path: "/tmp/ha-excel-storage"
  nas-storage-path: "/mnt/shared-excel"
  s3-bucket: "my-excel-bucket"
  s3-region: "ap-northeast-2"
  s3-endpoint: "" # Set for MinIO
  ncp-bucket: "ncp-bucket"
  ncp-endpoint: "https://kr.object.ncloudstorage.com"
  azure-container: "excel-container"
  azure-connection-string: ""
  gcp-bucket: "gcp-bucket"
  gcp-project-id: "my-gcp-project"

  # Optional REST controller enablement
  controller:
    enabled: true
```
