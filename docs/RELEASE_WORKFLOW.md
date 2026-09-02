# 🚀 자동 릴리즈 및 JitPack 배포 워크플로우 (Automated Release Workflow)

`ha-excel-job-engine`은 `main` 브랜치에 커밋/머지될 때 커밋 메시지(Conventional Commits)를 분석하여 **시맨틱 버전(SemVer) 계산 ➔ 품질 게이트 검증(./gradlew check) ➔ Git Tag 생성 ➔ GitHub Release 발행 ➔ JitPack 빌드 웜업(Warm-up)**까지 무인 자동화로 처리합니다.

---

## 📑 전체 파이프라인 개요

```
[개발자 PR 생성] ──► [CI 품질 게이트 통과] ──► [main 브랜치에 Squash & Merge]
                                                       │
  ┌────────────────────────────────────────────────────┘
  ▼
[GitHub Actions: release.yml 자동 실행]
  ├─ 1. 이전 태그 이후의 커밋 메시지(feat, fix 등) 분석
  ├─ 2. 다음 시맨틱 버전 계산 (예: v1.0.0 ➔ v1.1.0)
  ├─ 3. 전체 품질 게이트 재검증 (./gradlew check)
  ├─ 4. Git Tag 자동 생성 및 Push (v1.1.0)
  ├─ 5. GitHub Release 자동 생성 (Changelog 및 JitPack 의존성 가이드 자동 첨부)
  └─ 6. JitPack API 호출 ➔ 백그라운드 사전 빌드(Warm-up) 완료
```

---

## 🏷️ 커밋 메시지 컨벤션 및 버전 승격 규칙

저장소는 [Conventional Commits](https://www.conventionalcommits.org/) 표준을 기반으로 버전을 자동으로 판별합니다:

| 커밋 접두사 (Type) | 의미 | 승격되는 버전 (SemVer) | 예시 |
| :--- | :--- | :---: | :--- |
| **`fix:`**, **`perf:`** | 버그 수정 및 성능 개선 | **PATCH** (`+0.0.1`) | `fix: prevent potential NPE during path normalization (#15)` |
| **`feat:`** | 새로운 기능 추가 | **MINOR** (`+0.1.0`) | `feat: support Google Cloud Storage signed URL (#16)` |
| **`BREAKING CHANGE:`** 또는 **`feat!:`** | 기존 API 파괴적 변경 | **MAJOR** (`+1.0.0`) | `feat!: change default storage properties hierarchy (#20)` |

---

## 📦 JitPack 의존성 추가 가이드

릴리즈가 완료되면 외부 사용자는 즉시 다음과 같이 프로젝트에 추가할 수 있습니다:

### Gradle (Groovy)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sweetpark:ha-excel-job-engine:v1.0.0'
}
```

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.sweetpark:ha-excel-job-engine:v1.0.0")
}
```

### Maven (`pom.xml`)
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.sweetpark</groupId>
        <artifactId>ha-excel-job-engine</artifactId>
        <version>v1.0.0</version>
    </dependency>
</dependencies>
```

---

## 📖 Javadoc 생성 및 열람

프로젝트 소스 코드의 Javadoc 문서는 Gradle 명령어로 즉시 생성할 수 있습니다:

```bash
./gradlew javadoc
```
생성된 문서는 `build/docs/javadoc/index.html`에서 브라우저로 확인할 수 있습니다.  
또한 `withJavadocJar()` 및 `withSourcesJar()`가 활성화되어 있어 JitPack 배포본을 내려받는 사용자의 IDE(IntelliJ IDEA 등)에서 클래스 및 메서드에 마우스를 올리면 **Javadoc 툴팁과 원본 소스 코드가 자동으로 연동**됩니다.
