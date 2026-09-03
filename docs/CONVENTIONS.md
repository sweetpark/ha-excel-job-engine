# Coding & Git Conventions

## 1. Java Code Style & Formatting
- **Code Formatter**: Google Java Format (1.18.1) via Spotless.
- Automatically format code before committing:
  ```bash
  ./gradlew spotlessApply
  ```
- **Lombok Policy**: We avoid Lombok in the core engine to minimize reflection overhead and ensure clean standalone library compatibility across all JVM toolchains.
- **Logging**: Standard SLF4J LoggerFactory.getLogger(ClassName.class).

## 2. Static Analysis Quality Gates
- **SpotBugs**: Runs on check. No high/medium severity bugs permitted.
- **JaCoCo**: Verification enforced during ./gradlew check.

## 3. Commit Messages (Conventional Commits)
Format: `<type>(<scope>): <subject>`

### Types
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code (formatting, white-space)
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `chore`: Changes to the build process or auxiliary tools and libraries

## 4. Dependency Scoping Policy (`api` vs `implementation`)

This module is a Spring Boot Starter *library*, so every `api` dependency becomes a forced
transitive dependency for every consumer, and every `implementation` dependency stays private to
this module. The rule we apply: a dependency is `api` **only if** a type from it appears directly
in a public method signature that a consumer must compile against (a public utility class they can
call, or an SPI they implement) - not merely because we use it internally.

| Dependency | Scope | Why |
|---|---|---|
| `org.apache.poi:poi(-ooxml)` | `api` | `ExcelWriterUtils` (public) takes/returns POI types (`Sheet`, `Workbook`, `Cell`, `CellStyle`) directly. |
| `org.mybatis.spring.boot:mybatis-spring-boot-starter` | `api` | The public `ExcelStreamable` SPI's `streamRows(Map, SqlSession): Cursor<...>` signature carries MyBatis types. |
| `org.jxls:jxls`, `jxls-poi` | `implementation` | Used only inside `JxlsTemplateEngine`. The public `TemplateExcelEngine` SPI is pure `InputStream`/`OutputStream` - no jxls type ever crosses the boundary. |
| `spring-boot-starter-web`, `spring-boot-starter-jdbc` | `implementation` | Nothing in this library's public API exposes a Spring MVC or `DataSource` type (the one servlet type consumers see, `HttpServletRequest` in `ExcelSecurityProvider`, comes from the servlet API itself). Any consumer wiring up `ExcelController` is already a Spring Boot web app with its own `spring-boot-starter-web`. |
| AWS SDK / Azure SDK / GCP SDK (S3, Blob, GCS) | `compileOnly` | Optional storage backends - see [README > Optional Storage Dependencies](../README.md#-optional-storage-dependencies). Only the consumer who picks that `storage-type` needs the SDK. |

Before moving anything from `api` to `implementation`, grep the public classes/interfaces for the
dependency's package and confirm nothing leaks - moving a genuinely-leaked dependency down breaks
every consumer's build with a compile error, not a runtime surprise.
