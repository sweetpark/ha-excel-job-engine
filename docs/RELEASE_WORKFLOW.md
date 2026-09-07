요청하신 내용을 자연스럽고 전문적인 오픈소스 기술 문서 스타일의 영어로 번역했습니다.

---

# 🚀 Automated Release & JitPack Deployment Workflow

Whenever commits are merged or pushed to the `main` branch, `ha-excel-job-engine` automatically analyzes commit messages ([Conventional Commits](https://www.conventionalcommits.org/)) to handle the entire lifecycle unattended: **SemVer Calculation ➔ Quality Gate Verification (`./gradlew check`) ➔ Git Tag Creation ➔ GitHub Release Publication ➔ JitPack Build Warm-up**.

---

## 📑 Pipeline Overview

```
[Developer Creates PR] ──► [CI Quality Gate Passes] ──► [Squash & Merge to main]
                                                               │
  ┌────────────────────────────────────────────────────────────┘
  ▼
[GitHub Actions: release.yml Triggered]
  ├─ 1. Analyze commit messages (feat, fix, etc.) since the last tag
  ├─ 2. Calculate the next Semantic Version (e.g., v1.0.0 ➔ v1.1.0)
  ├─ 3. Re-verify the full Quality Gate (./gradlew check)
  ├─ 4. Automatically create and push Git Tag (v1.1.0)
  ├─ 5. Publish GitHub Release (with auto-generated changelog & JitPack guide)
  └─ 6. Trigger JitPack API ➔ Complete background pre-build (Warm-up)
```

---

## 🏷️ Commit Message Conventions & Version Bump Rules

This repository determines version increments automatically based on the [Conventional Commits](https://www.conventionalcommits.org/) specification:

| Commit Prefix (Type) | Description | Version Bump (SemVer) | Example |
| :--- | :--- | :---: | :--- |
| **`fix:`**, **`perf:`** | Bug fixes and performance improvements | **PATCH** (`+0.0.1`) | `fix: prevent potential NPE during path normalization (#15)` |
| **`feat:`** | New features | **MINOR** (`+0.1.0`) | `feat: support Google Cloud Storage signed URL (#16)` |
| **`BREAKING CHANGE:`** or **`feat!:`** | Breaking API changes | **MAJOR** (`+1.0.0`) | `feat!: change default storage properties hierarchy (#20)` |

---

## 📦 JitPack Dependency Setup Guide

Once a release is published, external consumers can immediately add the library to their projects:

### Gradle (Groovy)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sweetpark:ha-excel-job-engine:v1.1.2'
}
```

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.sweetpark:ha-excel-job-engine:v1.1.2")
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
        <version>v1.1.2</version>
    </dependency>
</dependencies>
```

---

## 📖 Javadoc Generation & Viewing

Javadoc documentation for the project's source code can be generated locally using Gradle:

```bash
./gradlew javadoc
```

The generated HTML documentation can be viewed in your browser at `build/docs/javadoc/index.html`.  
Additionally, since `withJavadocJar()` and `withSourcesJar()` are enabled in the build configuration, modern IDEs (such as IntelliJ IDEA) will **automatically link Javadoc tooltips and original source code** when hovering over classes and methods in downstream projects.
