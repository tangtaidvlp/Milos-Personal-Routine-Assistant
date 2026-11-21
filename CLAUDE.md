# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run with the default profile (`local` → PostgreSQL; start the DB first)
docker compose up -d database   # PostgreSQL on port 5469
./gradlew bootRun

# Run without a DB (H2 in-memory, `fast` profile)
SPRING_PROFILES_ACTIVE=fast ./gradlew bootRun
# -Pprofile is NOT read by bootRun; it only applies to runRelease

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
```

## Architecture

**Spring Boot 4.1 + Java 25 + Gradle** backend for the Routine Manager app. REST API with JWT auth, PostgreSQL persistence, and Gemini AI chat integration.

### Profile strategy

| Profile | DB | Auth | Purpose |
|---------|----|------|---------|
| `fast` | H2 in-memory | Disabled (`allowAll=true`) | Quick local dev, no DB setup |
| `local` (default, set in `application.yml`) | PostgreSQL (localhost:5469) | Disabled (`allowAll=true`) | Full local stack |
| `sit` | PostgreSQL (localhost:5469) | Enabled (JWT filter) | Staging/integration testing |
| `prod` | External PostgreSQL | Enabled (JWT filter) | Production |

### Domain model

- **User** — `UUID` id, email, username, `onboardCompleted` flag. Has `@OneToMany` default routines. On registration, `UserService.createUserAndDefaultRoutine()` creates the user + two empty default routines: "Weekday Routine" (DAILY) and "Weekend Routine" (WEEKEND).
- **DefaultRoutine** — template with `RoutineTypeEnum` (DAILY = weekday, WEEKEND). Owned by a User; **at most one per type per user** — always look up with `findByUserAndRoutineType` (there is deliberately no `findByUser`). Contains `RoutineTaskTemplate` tasks. Used as the blueprint for cloning into daily routines.
- **DailyRoutine** — a specific date's routine for a user. Auto-created from the default routine template. Contains `DailyTask` instances.
- **DailyTask** / **RoutineTaskTemplate** — both have `startTime` (LocalTime), `durationMinutes`, name, description, `color`. DailyTask adds `completed` boolean. Both compute `endTime` as a read-only JSON property.
- **Task colors (F005, `specs/F005-i-want-color-to-be-persisted/`)** — `color` is a palette id (e.g. `green`, follows the UI theme) or an exact `#RRGGBB`. The palette lives in `application.yml` (`app.task-palette.colors`, `TaskPaletteProperties`, validated at startup; list order = round-robin order; never rename/remove an id). `TaskColorService` assigns colors on every create path (`addDefaultTasks`, `addDailyTasks`, full-replace of a default routine; AI tools and legacy `AiRoutineFunctions` go through these): no color → `palette[colorCursor]` and the routine's `colorCursor` advances; the next id sent explicitly also advances; any other valid color is kept without advancing; same-named tasks in one request share a color. Updates change the color only when one is sent (null keeps it). Invalid colors → 400. Routines expose a read-only `nextColor` (filled by the service; `colorCursor` is `@JsonIgnore`). Cloning a default routine into a day copies each task's color and the cursor. Tasks from before F005 have `color = null`; there is deliberately no backfill or special handling.
- **RefreshToken** — persisted JWT refresh token with expiry and revocation tracking.

### Key service behaviors

- **`getOrCreateDailyRoutine()`** — for *today only*, auto-clones from the default routine if missing. For non-today dates, returns a transient (unsaved) empty routine — no silent writes.
- **`ensureDailyRoutine()`** — explicit intent-driven create for any date. Always persists and clones from default.
- **Task overlap handling** — `TaskTimeFunctions.shiftOverlappingTasks()` / `standardiseTasks()` push conflicting tasks later in time when a new task is inserted.
- **Weekend vs weekday** — `DefaultRoutineService.resolveTemplateFor(user, date)` picks the template for every new day (`getOrCreateDailyRoutine`, `ensureDailyRoutine`, the midnight `DailyTaskSpawningJob`): Saturday/Sunday use the WEEKEND routine if it has at least one task, otherwise the DAILY one.
- **`GET /api/v1/default-routine/users/{userId}?type=DAILY|WEEKEND`** (default DAILY) — `type=WEEKEND` creates an empty weekend routine if the user has none (idempotent; for users from before weekend routines). Invalid `type` → 400.

### Auth flow

- `POST /api/v1/auth/login` — returns access token in JSON body + refresh token as httpOnly cookie (path-scoped to `/api/v1/auth/refresh`).
- `POST /api/v1/auth/refresh` — reads refresh token from cookie, returns new access token.
- JWT filter (`JwtAuthenticationFilter`) is only active on `prod` and `sit` profiles. `local`/`fast` profiles skip auth entirely (all requests permitted).
- JWT signing key is generated in-memory at startup (`Jwts.SIG.HS256.key().build()`) — **not persisted across restarts**, so all tokens are invalidated on restart.

### AI integration

- `AiChatService` interface with two implementations switched by `spring.ai.active` property:
  - **`GeminiAiChatService`** (`spring.ai.active=true`, default) — Spring AI with Gemini (model set in `application.yml`, currently `gemini-3.6-flash`). Uses `@Tool`-annotated methods in `DailyRoutineAiTools` and `DefaultRoutineAiTools` for CRUD operations. Stateless: no chat-memory advisor; Buddy's context is passed in as `ChatContext`.
  - **`MockAiChatService`** (`spring.ai.active=false`) — returns echo responses, no Spring AI beans required.
- `AiRoutineFunctions` is a **legacy** function-calling approach using `java.util.function.Function` beans — it coexists but `GeminiAiChatService` uses the `@Tool` approach.
- Gemini API key is configured via `GEMINI_API_KEY` environment variable.
- **Chat history (F004, `specs/F004-…`)** — `service/chat/ChatConversationService` stores each exchange in `chat_conversation` / `chat_message`: one conversation per (user, default routine), which includes onboarding chats, and one per (user, date). An exchange is stored only after the AI replies, so a failed call stores nothing. Buddy's context = `summary_text` + messages after `summarized_through_seq`, rebuilt from the DB on every message, so it survives restarts.
- **Compaction** — `ChatCompactionService` runs `@Async` after commit. Once `app.chat.compaction-threshold` (default 20) messages are past the cursor, `ChatSummarizer` folds them into the summary: `GeminiChatSummarizer` uses its own tool-less client, `MockChatSummarizer` is used when AI is off. Visible messages never change. A failure is logged and retried after the next exchange.
- History: `GET /api/ai/chat/default-routine/{userId}/{routineId}/messages` and `GET /api/ai/chat/daily-routine/{userId}/{date}/messages`. Both page with `?before=<seq>&limit=` (max `app.chat.history-page-size`, 20) and return `{messages (oldest→newest), hasMore, nextBefore}`. `CurrentUserGuard` returns 404 for an unknown user and 403 when the JWT user ≠ `{userId}`. The routine must belong to the user, otherwise 404.
- `application-fast.yml` sets `H2Dialect`: the global PostgreSQL dialect emits `FOR NO KEY UPDATE` for row locks, which H2 rejects.
- Past-date daily routines are frozen for the AI: no time schedule changes allowed, only content/completion/color edits.
- Buddy's task tools take an optional `color` that must be a palette id (case-insensitive); hex values and unknown names are refused with a reply listing the palette. The id list in the tool descriptions is `TaskColorService.PALETTE_IDS_HINT` — keep it in sync with `application.yml`.

### API endpoints summary

| Prefix | Controller | Purpose |
|--------|-----------|---------|
| `/api/v1/auth/**` | `AuthenticationController` | Login, register, refresh, logout |
| `/api/v1/default-routine/**` | `DefaultRoutineController` | CRUD for default routine templates & tasks |
| `/api/routine-manager/daily-routine/**` | `DailyRoutineController` | CRUD for daily routines & tasks |
| `/api/ai/chat/**` | `AIController` | AI chat for default & daily routines |
| `/api/users/**` | `UserController` | Onboarding completion |
| `/api/v1/task-palette` | `TaskPaletteController` | Task color palette (GET), round-robin order |
| `/api/support/cron/**` | `CronSupportController` | Manual cron trigger (local/fast/test only) |

Packages live under `com.tom.payment.routinemanager` (`api`, `config`, `dto`, `funcs`, `model`, `repository`, `service/aitools`, `service/cronjobs`).

## Deployment

- **GitHub Actions** (`.github/workflows/gradle-publish.yml`): builds with Gradle, builds Docker image, pushes to AWS ECR (`milos` repo), deploys to EC2 via SSH.
- **Docker**: `Dockerfile` uses `eclipse-temurin:25` base. `docker-compose.yml` runs PostgreSQL 17 (`trixie` tag) on port 5469.
- Tests are skipped in CI (`-x test`).
- **Schema**: prod is `ddl-auto: validate` with no Flyway. New tables or columns need a DDL script under `db/` applied to the prod DB *before* the new image starts. F004: `db/F004-chat-history.sql`. F005: `db/F005-task-color.sql`.

## Conventions

- Lombok used extensively (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`, `@Data`).
- Constructor injection preferred (via `@RequiredArgsConstructor` or explicit constructors).
- `@JsonManagedReference` / `@JsonBackReference` for bidirectional JPA relationships to prevent infinite recursion.
- `@Transactional` on all service write methods.
- UUID primary keys with `GenerationType.UUID`.
- `application.yml` contains shared config; profile-specific files override datasource settings.
- `application-fast.yml` uses H2 in-memory DB — no PostgreSQL needed. It is not the default; select it with `SPRING_PROFILES_ACTIVE=fast`.
