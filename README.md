# 🚀 ha-excel-job-engine

> **Redis-Free High-Availability Distributed Job & Large-Scale Excel Export Engine for Spring Boot**  
> 추가 인프라(Redis/Zookeeper) 없이 **DB CAS(Compare-And-Swap)와 Heartbeat 복구**를 통해 다중 서버(HA) 환경에서 중복 없는 분산 Job을 실행하고, **SXSSF 스트리밍 및 청크 ZIP 분할**로 OOM 없이 수십만 건의 대용량 엑셀을 안전하게 생성·다운로드하는 고가용성 엑셀 엔진입니다.

---

## 📌 1. 프로젝트 개요 & 오픈소스 비전 (Open Source Vision)

### 💡 왜 이 엔진이 필요한가? (Problem Statement)
* **대용량 엑셀 다운로드 시 메모리 폭발(OOM)**: 수만~수십만 건의 데이터를 일반 POI(`XSSFWorkbook`)로 메모리에 올릴 경우 Heap 메모리 고갈로 서버가 다운됩니다.
* **로드밸런서(LB) 다중 서버 환경의 동시성 및 파일 공유 문제**: 여러 서버가 동일한 비동기 다운로드 Job을 중복 선점하거나, 1번 서버가 생성한 엑셀 파일을 사용자가 2번 서버로 다운로드 요청했을 때 파일을 찾지 못하는 문제가 발생합니다.
* **과도한 인프라 도입 비용**: 분산 락(Distributed Lock) 하나를 위해 Redis나 Redisson 클러스터를 도입하고 운영하는 것은 소규모/중규모 시스템에 오버엔지니어링(비용·운영 부담)이 될 수 있습니다.

### 🎯 오픈소스 비전 & 제공 형태
* **무인프라(No-Redis) 경량 HA 분산 처리**:
  * `UPDATE ha_excel_job SET status='RUNNING', server_id=? WHERE job_id=? AND status='PENDING'` 단건 영향 행 수(CAS)를 통해 원자적 선점 보장.
  * 서버 비정상 다운 시 살아있는 노드가 **Heartbeat 기반 Orphan Job 자동 인수/복구**.
* **Zero-OOM 3단계 스트리밍 파이프라인**:
  1. `소량 (< 10k)`: 브라우저 즉시 생성 또는 메모리 처리.
  2. `중용량 (10k ~ 100k)`: MyBatis Cursor 스트리밍 + Apache POI `SXSSFWorkbook` 임시 파일 플러시 단일 xlsx.
  3. `초대용량 (> 100k)`: 50k 단위 청크 스트리밍 + 멀티 파일 ZIP 압축 다운로드.
* **플러그형 멀티 클라우드 & 공유 스토리지 지원**:
  * 설정(`application.yml`) 한 줄로 **Local, NAS, AWS S3, NCP(네이버클라우드), Azure Blob, GCP Cloud Storage**를 자유롭게 교체 가능.
* **누구나 쓸 수 있는 Starter & Docker Compose 데모**:
  * Spring Boot Starter 형태로 제공하며, 2개 노드 + DB 로드밸런싱 환경을 `docker-compose up` 한 번으로 직접 시연하고 테스트할 수 있는 Standalone 패키지 제공.

---

## ☁️ 2. 플러그형 스토리지 아키텍처 (Multi-Storage Architecture)

다중 서버(HA) 환경에서 모든 노드가 생성된 엑셀 파일을 원활히 서빙할 수 있도록 **Strategy Pattern 기반의 `StorageProvider` 인터페이스**를 제공합니다.

```
ha-excel-job-engine
  └── StorageProvider (Core Interface)
        ├── LocalDiskStorageProvider       (로컬 디스크 / 단일 노드 테스트용)
        ├── NasStorageProvider             (공유 파일시스템 / 온프레미스 다중 서버용)
        ├── AwsS3StorageProvider           (AWS S3 & S3 호환 MinIO)
        ├── NcpObjectStorageProvider       (네이버클라우드 플랫폼 S3 호환 스토리지)
        ├── AzureBlobStorageProvider       (Microsoft Azure Blob Storage)
        └── GcpCloudStorageProvider        (Google Cloud Storage)
```

### ⚙️ `application.yml` 스토리지 설정 예시
```yaml
ha-excel:
  storage:
    type: aws-s3 # [local | nas | aws-s3 | ncp | azure-blob | gcp-gcs]
    
    # 1. 로컬 디스크 (기본값)
    local:
      base-dir: /tmp/ha-excel-exports
      
    # 2. NAS (NFS/공유 디렉터리 마운트)
    nas:
      mount-path: /mnt/shared-nas/excel
      
    # 3. AWS S3
    aws-s3:
      bucket: my-company-excel
      region: ap-northeast-2
      
    # 4. NCP (네이버클라우드 Object Storage - S3 호환)
    ncp:
      bucket: ncp-excel-bucket
      endpoint: https://kr.object.ncloud.storage.com
      region: kr-standard
      
    # 5. Azure Blob Storage
    azure-blob:
      container-name: excel-exports
      connection-string: ${AZURE_STORAGE_CONNECTION_STRING}
      
    # 6. GCP Cloud Storage (GCS)
    gcp-gcs:
      bucket: gcp-excel-bucket
      project-id: my-gcp-project
```

---

## 🔍 3. 원본 소스 및 이관 대상 (Source Reference)

| 구분 | 내용 |
| :--- | :--- |
| **원본 저장소** | `wiezonSRC/REFECTOR_SOLPAY_SERVER` (Branch: `feature/feat-editRunTx_wypark_260708` 등 최신) |
| **추출 대상 경로** | `src/main/java/com/wiezon/poompaytest/common/excel/` |
| **핵심 컴포넌트** | • `ExcelJobManager`, `ExcelWorkerService`, `ExcelJobQueue`<br/>• `ExcelGeneratorService`, `ExcelZipGeneratorService`, `ExcelWriterUtils`<br/>• `template/` (`JxlsTemplateEngine`, `TemplateExcelEngine`)<br/>• `domain/` (`ExcelJob`, `ExcelJobStatus`, `ExcelColumnDef`, `ExcelRequest`)<br/>• `config/` (`ExcelProperties`, `ExcelJobQueueConfig`, `ExcelThreadPoolConfig`) |

---

## 🛠 4. 이관 및 리팩토링 기준 (Refactoring & Sanitization Rules)

새로운 세션에서 SOLPAY 거대 모듈로부터 엑셀 엔진을 독립 추출할 때 **반드시 준수해야 하는 기준**입니다.

### ① 결제/PG 비즈니스 로직 완전 분리 (Sanitization & Decoupling)
* **특정 비즈니스 도메인 제거**:
  * 사내 PG/정산/가맹점 전용 DTO 및 로직을 모두 걷어내고, **Generic Row Mapper(`ExcelStreamable<T>`)** 인터페이스 기반으로 데이터 소스를 주입받도록 추상화.
* **스토리지 추상화 (`StorageProvider`)**:
  * 사내 전용 StorageService 의존성을 제거하고, 상기 6종 스토리지 구현체를 플러그형으로 지원.
* **DB DDL 표준화**:
  * 사내 테이블명(`TBEX_EXCEL_JOB`) 대신 `ha_excel_job` 표준 DDL 스크립트(`schema-mysql.sql`, `schema-h2.sql`, `schema-postgresql.sql`)를 프로젝트 내에 제공.

### ② 패키지 및 프로젝트 네이밍 표준화
* **Target Base Package**: `io.github.sweetpark.haexcel`
  * Core Engine: `io.github.sweetpark.haexcel.core`
  * Generator: `io.github.sweetpark.haexcel.generator`
  * Storage Providers: `io.github.sweetpark.haexcel.storage.{local,nas,s3,ncp,azure,gcp}`
  * AutoConfiguration: `io.github.sweetpark.haexcel.autoconfigure`

### ③ 멀티 노드 데모 & 통합 테스트 환경 구성
* `docker-compose.yml`을 통해:
  * Nginx (LB) 1대 + Spring Boot 인스턴스 2대 + MariaDB 1대 + 로컬 MinIO/NAS 마운트
  * Node 1이 다운되었을 때 Node 2가 작업을 인수하고 사용자가 어느 노드로 접근하든 정상 다운로드되는 시나리오 검증.

---

## 🗺 5. 단계별 로드맵 (Roadmap to Public Release)

```mermaid
graph LR
    P1["Phase 1<br/>레포 초기화 & 설계"] --> P2["Phase 2<br/>모듈 추출 & 멀티 스토리지"]
    P2 --> P3["Phase 3<br/>HA 검증 & Docker 데모"]
    P3 --> P4["Phase 4<br/>Public 오픈소스 전환"]
    style P1 fill:#238636,stroke:#fff,stroke-width:2px,color:#fff
    style P2 fill:#1f6feb,stroke:#fff,stroke-width:2px,color:#fff
    style P3 fill:#8957e5,stroke:#fff,stroke-width:2px,color:#fff
    style P4 fill:#d29922,stroke:#fff,stroke-width:2px,color:#fff
```

### 📌 Phase 1: Private 레포 생성 및 청사진 수립 (✅ 현재 단계)
- [x] 오픈소스 지향 저장소(`ha-excel-job-engine`) 생성 (Private)
- [x] 모듈 분리 설계, CAS HA 메커니즘, **플러그형 6종 스토리지 지원 설계**가 담긴 README 작성

### 📌 Phase 2: 소스코드 추출 및 도메인 중립화 (Next Session)
- [ ] `wiezonSRC/REFECTOR_SOLPAY_SERVER`의 `common/excel`을 Standalone Gradle 프로젝트로 추출
- [ ] 패키지명 변경 (`io.github.sweetpark.haexcel`)
- [ ] 사내 비즈니스 로직 제거 및 Generic 스트리밍 인터페이스로 재설계
- [ ] **`StorageProvider` 인터페이스 및 6종(Local, NAS, S3, NCP, Azure, GCP) 구현체 작성**
- [ ] 표준 `ha_excel_job` DDL 스크립트 작성 (MySQL, H2, PostgreSQL)

### 📌 Phase 3: 동시성 & 대용량 OOM 검증 및 데모 구축
- [ ] 다중 스레드 동시 Job 선점 CAS 원자성 단위 테스트
- [ ] 100만 건 더미 데이터 기반 메모리 프로파일링(SXSSF 스트리밍 OOM 방지 검증)
- [ ] 다중 노드 장애 복구(Heartbeat 고아 작업 인수) 시연용 `docker-compose` 환경 구성

### 📌 Phase 4: Public 오픈소스 전환 준비
- [ ] 오픈소스 라이선스 확정 (MIT 또는 Apache License 2.0)
- [ ] `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` 추가
- [ ] 아키텍처 시퀀스 다이어그램(CAS 선점, Heartbeat 복구, 청크 스트리밍) 최종화
- [ ] **저장소 Public 전환 (공개 오픈소스화)**

---

## 📋 다음 세션 작업자를 위한 체크리스트 (Action Items for Next Session)
1. `REFECTOR_SOLPAY_SERVER` 저장소의 `src/main/java/.../common/excel` 복사
2. `ha-excel-job-engine` 프로젝트 구조(Gradle 멀티모듈 or 스타터)로 재구성
3. `StorageProvider` 전략 패턴으로 6종 스토리지(Local, NAS, S3, NCP, Azure, GCP) 연동 모듈 구현
4. 10만 건 스트리밍 생성 단위 테스트 작성 및 메모리 사용량 측정
