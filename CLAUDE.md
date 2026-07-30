# CLAUDE.md

# Project Overview
This is a Spring Boot application using Java.

## Project Structure
- `src/main/java/` - Application source code
- `src/main/resources/` - Configuration files
- `src/test/java/` - Test source code
- `pom.xml` or `build.gradle` - Build configuration

## Architecture
- `controller/` - REST endpoints
- `service/` - Business logic
- `repository/` - Data access layer
- `model/` or `entity/` - Domain objects
- `dto/` - Data transfer objects
- `config/` - Configuration classes

## Conventions
- Use constructor injection over field injection
- Follow layered architecture (Controller → Service → Repository)
- Use DTOs for API request/response, not entities
- Handle exceptions with @ControllerAdvice
- Use Bean Validation annotations for input validation

## Testing
- Unit test services with Mockito
- Integration test with @SpringBootTest
- Use @WebMvcTest for controller tests
- Use Testcontainers for database tests

## Commands
- `./mvnw spring-boot:run` - Start application
- `./mvnw test` - Run tests
- `./mvnw package` - Build JAR
- `./gradlew bootRun` - Start (Gradle)
- `./gradlew test` - Run tests (Gradle)



# Token-efficiency rules
# CLAUDE.md - Spring Boot Optimization

## 🛑 Scope & Boundaries
To conserve context and prevent token bloat, **NEVER** read, search, or modify files in the following directories or matching these patterns:
* `target/` (Maven build output)
* `build/`, `.gradle/`, and `gradle/` (Gradle build output and wrapper)
* `.idea/`, `.vscode/`, `.settings/`, `.eclipse/` (IDE configurations)
* `logs/` or any `*.log` files
* Any compiled `*.class`, `*.jar`, `*.war`, or `*.zip` files
* `src/main/resources/static/` or `src/main/resources/public/` (unless specifically asked to edit front-end assets, avoid reading minified JS/CSS or images).

## ⏸️ Execution Rules
* **Plan First:** Before modifying multiple Java classes, changing Spring Data repositories, altering Bean configurations, or adding new dependencies to `pom.xml` / `build.gradle`, you MUST use `/plan` to output a brief strategy and wait for explicit approval.
* **Stop on Confusion:** If a task is ambiguous, or a Maven/Gradle build command fails 3 times in a row, stop and ask the user for clarification instead of guessing or brute-forcing a fix.
* **No Blind Refactoring:** Do not rewrite large swaths of business logic in `@Service` classes unless specifically requested.

## 🔍 Search Constraints
* When searching for context, do not read entire large files unless absolutely necessary to understand the logic.
* Use localized search tools like `rg` (ripgrep) or `grep` to find specific Spring annotations (e.g., `@RestController`, `@Service`, `@Entity`, `@Bean`, `@Configuration`) or method signatures first to keep the context window clean.
* Rely on Java structure: Check interfaces before reading implementation classes if only the contract is needed.

## ✅ Testing & Verification
* **Narrow Testing:** Do not run the entire Spring test context to verify a small change. The Spring context takes time to load and will output excessive logs, rapidly burning tokens. Run only the specific, relevant test class or method.
* **Maven Commands:** 
  * Class: `mvn test -Dtest=ClassNameTest` 
  * Method: `mvn test -Dtest=ClassNameTest#methodName`
* **Gradle Command:** 
  * Class: `gradle test --tests "com.example.package.ClassNameTest"`
* **Quiet Logging:** When running build or test commands, prefer quiet or less verbose logging flags (e.g., `mvn clean test -q`) to prevent massive terminal output from flooding the AI context window.
- **Never re-read a file just to confirm an edit worked.** Edit/Write tools already error on failure — the harness tracks file state. Only re-read a file if you need to see content you don't already have (e.g. context beyond what you last read).
- **Don't read whole files when only a section is needed.** Use `offset`/`limit` on Read to pull just the relevant lines once you know where they are (e.g. from a grep match or line number reference).
- **Before scanning across many files** (broad grep/find across the repo, reading multiple files to explore, spawning an Explore/general-purpose agent for wide research), ask for confirmation first — unless the permission mode is set to fully automatic, in which case proceed without asking.
