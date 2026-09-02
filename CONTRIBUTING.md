# Contributing to HA Excel Job Engine

Thank you for your interest in contributing to **HA Excel Job Engine**!  
We welcome all bug reports, feature requests, documentation improvements, and pull requests.

---

## 🛠️ Development Setup

### Prerequisites
- JDK 17 or JDK 21+
- Git

### Build & Check
Run the comprehensive verification task before submitting any pull request:
`ash
./gradlew check
`
This task automatically executes:
1. Unit and integration tests (	est)
2. JaCoCo code coverage verification (jacocoTestCoverageVerification)
3. Spotless code formatting check (spotlessCheck)
4. SpotBugs static code analysis (spotbugsMain, spotbugsTest)

To automatically fix formatting issues according to Google Java Format:
`ash
./gradlew spotlessApply
`

---

## 🔀 Git Workflow & Branching Strategy

1. **Fork** the repository and create a topic branch from main:
   `ash
   git checkout -b feature/awesome-feature
   `
2. Commit your changes following [Conventional Commits](https://www.conventionalcommits.org/):
   - eat: add Google Cloud Storage signed URL support
   - ix: prevent potential NPE during path normalization
   - docs: update quickstart guide
   - 	est: add concurrency test for orphan scanner
3. Push your branch and open a Pull Request against main.

---

## 🧪 Testing Guidelines

- All new features and bug fixes **must** include tests.
- Verify CAS atomicity and concurrency safety for any queue/worker modifications.
- Ensure ./gradlew check passes cleanly without warnings.

---

## 📜 License
By contributing to HA Excel Job Engine, you agree that your contributions will be licensed under its [Apache License 2.0](LICENSE).