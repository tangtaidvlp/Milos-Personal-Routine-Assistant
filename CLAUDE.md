# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build & run (uses H2 in-memory DB by default with "fast" profile)
./gradlew bootRun

# Run with local PostgreSQL (requires docker-compose up first)
./gradlew bootRun -Pprofile=local

# Build executable JAR → build/libs/
./gradlew build -x test        # skip tests for speed
./gradlew build                 # with tests

# Release task: bootJar → release/routinemanager-<version>.jar
./gradlew release

# Run the release JAR (with optional profile)
./gradlew runRelease -Pprofile=local

# Tests
./gradlew test
./gradlew test --tests "com.tom.payment.routinemanager.RoutineServiceIntegrationTest"
./gradlew test --tests "com.tom.payment.routinemanager.apptest.OnboardingRoutineTest"

# Docker (local dev DB)
docker compose up -d database   # PostgreSQL on port 5469
```

## Architecture

**Spring Boot 4.1 + Java 25 + Gradle** backend for the Routine Manager app. REST API with JWT auth, PostgreSQL persistence, and Gemini AI chat integration.

### Profile strategy

| Profile | DB | Auth | Purpose |
|---------|----|------|---------|
| `fast` (default) | H2 in-memory | Disabled (`allowAll=true`) | Quick local dev, no DB setup |
| `local` | PostgreSQL (localhost:5469) | Disabled (`allowAll=true`) | Full local stack |
| `sit` | PostgreSQL (localhost:5469) | Enabled (JWT filter) | Staging/integration testing |
| `prod` | External PostgreSQL | Enabled (JWT filter) | Production |

### Domain model

- **User** — `UUID` id, email, username, `onboardCompleted` flag. Has `@OneToMany` default routines. On registration, `UserService.createUserAndDefaultRoutine()` creates the user + a "Default Routine" (type DAILY).
- **DefaultRoutine** — template with `RoutineTypeEnum` (DAILY/WEEKEND). Owned by a User. Contains `RoutineTaskTemplate` tasks. Used as the blueprint for cloning into daily routines.
- **DailyRoutine** — a specific date's routine for a user. Auto-created from the default routine template. Contains `DailyTask` instances.
- **DailyTask** / **RoutineTaskTemplate** — both have `startTime` (LocalTime), `durationMinutes`, name, description. DailyTask adds `completed` boolean. Both compute `endTime` as a read-only JSON property.
- **RefreshToken** — persisted JWT refresh token with expiry and revocation tracking.

### Key service behaviors

- **`getOrCreateDailyRoutine()`** — for *today only*, auto-clones from the default routine if missing. For non-today dates, returns a transient (unsaved) empty routine — no silent writes.
- **`ensureDailyRoutine()`** — explicit intent-driven create for any date. Always persists and clones from default.
- **Task overlap handling** — `TaskTimeFunctions.shiftOverlappingTasks()` / `standardiseTasks()` push conflicting tasks later in time when a new task is inserted.
- **Weekend vs weekday** — the midnight cron job (`DailyTaskSpawningJob`) checks `DayOfWeek` and clones from the weekend default routine if available, otherwise falls back to the weekday one.

### Auth flow

- `POST /api/v1/auth/login` — returns access token in JSON body + refresh token as httpOnly cookie (path-scoped to `/api/v1/auth/refresh`).
- `POST /api/v1/auth/refresh` — reads refresh token from cookie, returns new access token.
- JWT filter (`JwtAuthenticationFilter`) is only active on `prod` and `sit` profiles. `local`/`fast` profiles skip auth entirely (all requests permitted).
- JWT signing key is generated in-memory at startup (`Jwts.SIG.HS256.key().build()`) — **not persisted across restarts**, so all tokens are invalidated on restart.

### AI integration

- `AiChatService` interface with two implementations switched by `spring.ai.active` property:
  - **`GeminiAiChatService`** (`spring.ai.active=true`, default) — Spring AI with Gemini 2.0 Flash. Uses `@Tool`-annotated methods in `DailyRoutineAiTools` and `DefaultRoutineAiTools` for CRUD operations. Conversation memory: in-memory window of last 20 messages per conversation.
  - **`MockAiChatService`** (`spring.ai.active=false`) — returns echo responses, no Spring AI beans required.
- `AiRoutineFunctions` is a **legacy** function-calling approach using `java.util.function.Function` beans — it coexists but `GeminiAiChatService` uses the `@Tool` approach.
- Gemini API key is configured via `GEMINI_API_KEY` environment variable.
- Past-date daily routines are frozen for the AI: no time schedule changes allowed, only content/completion edits.

### API endpoints summary

| Prefix | Controller | Purpose |
|--------|-----------|---------|
| `/api/v1/auth/**` | `AuthenticationController` | Login, register, refresh, logout |
| `/api/v1/default-routine/**` | `DefaultRoutineController` | CRUD for default routine templates & tasks |
| `/api/routine-manager/daily-routine/**` | `DailyRoutineController` | CRUD for daily routines & tasks |
| `/api/ai/chat/**` | `AIController` | AI chat for default & daily routines |
| `/api/users/**` | `UserController` | Onboarding completion |
| `/api/support/cron/**` | `CronSupportController` | Manual cron trigger (local/fast/test only) |

### Package layout

```
com.tom.payment.routinemanager
├── api/          # @RestController classes
├── config/       # SecurityConfig, JwtAuthenticationFilter, GeminiProperties, AiRoutineFunctions, CustomObjectMapper
├── dto/          # Request/response DTOs
├── funcs/        # Utility classes (JsonFunc, TaskTimeFunctions)
├── model/        # JPA @Entity classes
├── repository/   # Spring Data JPA repositories
├── service/      # Business logic
│   ├── aitools/  # @Tool-annotated classes for Gemini function calling
│   └── cronjobs/ # @Scheduled jobs
```

## Deployment

- **GitHub Actions** (`.github/workflows/gradle-publish.yml`): builds with Gradle, builds Docker image, pushes to AWS ECR (`milos` repo), deploys to EC2 via SSH.
- **Docker**: `Dockerfile` uses `eclipse-temurin:25` base. `docker-compose.yml` runs PostgreSQL 17 (`trixie` tag) on port 5469.
- Tests are skipped in CI (`-x test`).

## Conventions

- Lombok used extensively (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`, `@Data`).
- Constructor injection preferred (via `@RequiredArgsConstructor` or explicit constructors).
- `@JsonManagedReference` / `@JsonBackReference` for bidirectional JPA relationships to prevent infinite recursion.
- `@Transactional` on all service write methods.
- UUID primary keys with `GenerationType.UUID`.
- `application.yml` contains shared config; profile-specific files override datasource settings.
- The `application-fast.yml` (default profile) uses H2 in-memory DB — no PostgreSQL needed for basic development.
