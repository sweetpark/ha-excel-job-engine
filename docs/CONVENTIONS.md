# Coding & Git Conventions

## 1. Java Code Style & Formatting
- **Code Formatter**: Google Java Format (1.18.1) via Spotless.
- Automatically format code before committing:
  `ash
  ./gradlew spotlessApply
  `
- **Lombok Policy**: We avoid Lombok in the core engine to minimize reflection overhead and ensure clean standalone library compatibility across all JVM toolchains.
- **Logging**: Standard SLF4J LoggerFactory.getLogger(ClassName.class).

## 2. Static Analysis Quality Gates
- **SpotBugs**: Runs on check. No high/medium severity bugs permitted.
- **JaCoCo**: Verification enforced during ./gradlew check.

## 3. Commit Messages (Conventional Commits)
Format: <type>(<scope>): <subject>

### Types
- eat: A new feature
- ix: A bug fix
- docs: Documentation only changes
- style: Changes that do not affect the meaning of the code (formatting, white-space)
- efactor: A code change that neither fixes a bug nor adds a feature
- perf: A code change that improves performance
- 	est: Adding missing tests or correcting existing tests
- chore: Changes to the build process or auxiliary tools and libraries