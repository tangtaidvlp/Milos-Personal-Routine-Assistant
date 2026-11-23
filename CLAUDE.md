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

# Tests (see "Testing strategy")
./gradlew test                                       # H2
./gradlew test -PtestDb=postgres                     # Testcontainers Postgres, like CI
./gradlew test --tests '*SignInComponentTest'        # one class
```

## Architecture

**Spring Boot 4.1 + Java 25 + Gradle** backend for the Routine Manager app. REST API with JWT auth, PostgreSQL persistence, and Gemini AI chat integration.

### Profile strategy

| Profile | DB | Auth | Purpose |
|---------|----|------|---------|
| `fast` | H2 in-memory | Disabled (`allowAll=true`) | Quick local dev, no DB setup |
| `local` (default, set in `application.yml`) | PostgreSQL (localhost:5469) | Disabled (`allowAll=true`) | Full local stack |
| `test` | H2 or Testcontainers Postgres in the test suite (`src/test/resources/application-test*.yml`); the EC2 backend also runs this profile | Enabled (JWT filter) | Automated tests; deployed backend |
| `sit` | PostgreSQL (localhost:5469) | Enabled (JWT filter) | Staging/integration testing |
| `prod` | External PostgreSQL | Enabled (JWT filter) | Production |

### Domain model

- **User** — `UUID` id, email, username, `onboardCompleted` flag. Has `@OneToMany` default routines. On registration, `UserService.createUserAndDefaultRoutine()` creates the user + two empty default routines: "Weekday Routine" (DAILY) and "Weekend Routine" (WEEKEND).
- **DefaultRoutine** — template with `RoutineTypeEnum` (DAILY = weekday, WEEKEND). Owned by a User; **at most one per type per user** — always look up with `findByUserAndRoutineType` (there is deliberately no `findByUser`). Contains `RoutineTaskTemplate` tasks. Used as the blueprint for cloning into daily routines.
- **DailyRoutine** — a specific date's routine for a user. Auto-created from the default routine template. Contains `DailyTask` instances.
- **DailyTask** / **RoutineTaskTemplate** — both have `startTime` (LocalTime), `durationMinutes`, name, description, `color`. DailyTask adds `completed` boolean. Both compute `endTime` as a read-only JSON property.
- **Task colors (F005, `specs/.done.F005-i-want-color-to-be-persisted/`)** — `color` is a palette id (e.g. `green`, follows the UI theme) or an exact `#RRGGBB`. The palette lives in `application.yml` (`app.task-palette.colors`, `TaskPaletteProperties`, validated at startup; list order = round-robin order; never rename/remove an id). `TaskColorService` assigns colors on every create path (`addDefaultTasks`, `addDailyTasks`, full-replace of a default routine; AI tools and legacy `AiRoutineFunctions` go through these): no color → `palette[colorCursor]` and the routine's `colorCursor` advances; the next id sent explicitly also advances; any other valid color is kept without advancing; same-named tasks in one request share a color. Updates change the color only when one is sent (null keeps it). Invalid colors → 400. Routines expose a read-only `nextColor` (filled by the service; `colorCursor` is `@JsonIgnore`). Cloning a default routine into a day copies each task's color and the cursor. Tasks from before F005 have `color = null`; there is deliberately no backfill or special handling.
- **DailyReflection / ReflectionTask (F007, `specs/.done.F007-reflection/`)** — what really happened on a day, next to the plan (`DailyRoutine`). Tables `daily_reflection` (unique user + date, `color_cursor`, `feedback_text`/`feedback_at`, `content_updated_at`) and `reflection_task` (`planned_task_id` = plain UUID link to the `daily_task` it was copied from, no FK; null = unplanned). `service/reflection/ReflectionService` creates it on demand (copies the plan **once**, never re-syncs) and **never writes the plan** — not even `ensureDailyRoutine` for a past day without one. Today and past dates only (future → 400). Reflection tasks are never shifted for overlaps. Every task id is looked up inside the (user, date) reflection (404 otherwise). `ReflectionComparisonService` groups tasks as AS_PLANNED / CHANGED (same link, other time or duration) / SKIPPED / UNPLANNED (no link, a dangling link, or a 2nd link to one plan task) and is returned in every `ReflectionView`. Buddy's note: `ReflectionFeedbackService` (503 on AI failure, old note kept); `stale` = `feedback_at < content_updated_at`.
- **RefreshToken** — persisted JWT refresh token with expiry and revocation tracking.
- **UserSetting (F006, `specs/.done.F006-user-profile-configuration-store/`)** — per-user UI preferences as key/value rows in `user_setting` (PK `user_id, setting_key`; no row = default). `UserSettingsRegistry` lists the known keys with default + validation: `theme` (LIGHT/DARK/SYSTEM, default SYSTEM, case-insensitive input) and `boardZoom` (0.5–3.0 in 0.25 steps, JSON number only, default 1.0). **Add a preference = one `SettingDefinition` in `builtIns()` + the frontend `settingsSchema.ts` entry; no DDL.** `UserSettingsService`: reads merge defaults (unknown keys and invalid stored values are ignored), PATCH validates every key before writing (400, nothing written), upserts per key, last write wins.

### Key service behaviors

- **`getOrCreateDailyRoutine()`** — for *today only*, auto-clones from the default routine if missing. For non-today dates, returns a transient (unsaved) empty routine — no silent writes.
- **`ensureDailyRoutine()`** — explicit intent-driven create for any date. Always persists and clones from default.
- **Task overlap handling** — `TaskTimeFunctions.shiftOverlappingTasks()` / `standardiseTasks()` push conflicting tasks later in time when a new task is inserted.
- **Weekend vs weekday** — `DefaultRoutineService.resolveTemplateFor(user, date)` picks the template for every new day (`getOrCreateDailyRoutine`, `ensureDailyRoutine`, the midnight `DailyTaskSpawningJob`): Saturday/Sunday use the WEEKEND routine if it has at least one task, otherwise the DAILY one.
- **`GET /api/v1/default-routine/users/{userId}?type=DAILY|WEEKEND`** (default DAILY) — `type=WEEKEND` creates an empty weekend routine if the user has none (idempotent; for users from before weekend routines). Invalid `type` → 400.

### Auth flow

- `POST /api/v1/auth/login` — returns access token in JSON body + refresh token as httpOnly cookie (path-scoped to `/api/v1/auth/refresh`).
- `POST /api/v1/auth/refresh` — reads refresh token from cookie, returns new access token.
- JWT filter (`JwtAuthenticationFilter`) is active on every profile except `local`/`fast`, which skip auth entirely (all requests permitted). So it runs in `test`, `sit` and `prod`. A malformed or forged bearer token leaves the request unauthenticated (401), never a 500. A request without a valid token gets 401 with the `ApiError` body (`message` "Please sign in to continue.").
- Sign-in refusals are client errors with a short message (F008): wrong email or password → 401 `Invalid email or password.` (same body for both), duplicate registration → 409, unknown/revoked/expired refresh token → 401. Refresh-token expiry is checked with the injected `Clock`.
- JWT signing key is generated in-memory at startup (`Jwts.SIG.HS256.key().build()`) — **not persisted across restarts**, so all tokens are invalidated on restart.

### AI integration

- `AiChatService` interface with two implementations switched by `spring.ai.active` property:
  - **`GeminiAiChatService`** (`spring.ai.active=true`, default) — Spring AI with Gemini (model set in `application.yml`, currently `gemini-3.6-flash`). Uses `@Tool`-annotated methods in `DailyRoutineAiTools` and `DefaultRoutineAiTools` for CRUD operations. Stateless: no chat-memory advisor; Buddy's context is passed in as `ChatContext`.
  - **`MockAiChatService`** (`spring.ai.active=false`) — returns echo responses, no Spring AI beans required.
- `AiRoutineFunctions` is a **legacy** function-calling approach using `java.util.function.Function` beans — it coexists but `GeminiAiChatService` uses the `@Tool` approach.
- Gemini API key is configured via `GEMINI_API_KEY` environment variable.
- **Chat history (F004, `specs/.done.F004-…`)** — `service/chat/ChatConversationService` stores each exchange in `chat_conversation` / `chat_message`: one conversation per (user, default routine), which includes onboarding chats, and one per (user, date). An exchange is stored only after the AI replies, so a failed call stores nothing. Buddy's context = `summary_text` + messages after `summarized_through_seq`, rebuilt from the DB on every message, so it survives restarts.
- **Compaction** — `ChatCompactionService` runs `@Async` after commit. Once `app.chat.compaction-threshold` (default 20) messages are past the cursor, `ChatSummarizer` folds them into the summary: `GeminiChatSummarizer` uses its own tool-less client, `MockChatSummarizer` is used when AI is off. Visible messages never change. A failure is logged and retried after the next exchange.
- History: `GET /api/ai/chat/default-routine/{userId}/{routineId}/messages` and `GET /api/ai/chat/daily-routine/{userId}/{date}/messages`. Both page with `?before=<seq>&limit=` (max `app.chat.history-page-size`, 20) and return `{messages (oldest→newest), hasMore, nextBefore}`. `CurrentUserGuard` returns 404 for an unknown user and 403 when the JWT user ≠ `{userId}`. The routine must belong to the user, otherwise 404.
- `application-fast.yml` sets `H2Dialect`: the global PostgreSQL dialect emits `FOR NO KEY UPDATE` for row locks, which H2 rejects.
- **Reflection AI (F007)** — `ReflectionAiService` (`GeminiReflectionAiService` / `MockReflectionAiService`, same `spring.ai.active` switch) is separate from `AiChatService`: its chat client's only tools are `ReflectionAiTools`, so the reflection chat can never edit the plan (`GeminiAiChatService` registers plan tools as *default* tools, which per-call options can't remove). Feedback uses a tool-less client with tone rules (positive first, ≤ 2 gentle suggestions, no harsh words). Reflection chat history is its own `DAILY_REFLECTION` conversation per (user, date).
- Past-date daily routines are frozen for the AI: no time schedule changes allowed, only content/completion/color edits.
- Buddy's task tools take an optional `color` that must be a palette id (case-insensitive); hex values and unknown names are refused with a reply listing the palette. The id list in the tool descriptions is `TaskColorService.PALETTE_IDS_HINT` — keep it in sync with `application.yml`.

## Testing strategy

Three kinds of test, set by package and name (constitution Principle II; spec `specs/.done.F008-technical-enhancement-update-backend-testing-structure/`). `unit/TestStructureRulesTest` enforces the rules below.

| Kind | Where / name | What it covers | Rules |
|---|---|---|---|
| **Component** (default) | `component/<Flow>ComponentTest`, extends `support/BaseComponentTest` | One main user flow through the public API: success path plus main refusals (400/401/403/404/409). Check status, JSON body and, on writes, stored rows | Real sign-in: `newUser()` registers and logs in through `/api/v1/auth/*` and `as(user, request)` sends the real bearer token. Don't call services instead of API routes (the one exception: Buddy's tool methods, which only the AI calls). Don't repeat a rule's edge cases here |
| **Unit** | `unit/<Rule>Test` | One core business rule and its edge cases (colours, overlap shift, template choice, day rules, chat paging/compaction, settings validation, plan-vs-reality) | Plain JUnit 5, no Spring, no database, never mock storage. Pull a rule out of storage code first (`funcs/`, property records) |
| **Integration** (max 10) | `integration/<Connection>IntegrationTest`, extends `BaseComponentTest` | One external connection: PostgreSQL-only behaviour (`@RequiresPostgres`), the JWT/security setup | Skipped (not passed) on H2 |

- **One Spring context per run.** All Spring test config lives in `BaseComponentTest` (profiles, MockMvc with the security filter chain, the AI spies `aiChatService`/`chatSummarizer`/`reflectionAi`). Subclasses add no `@SpringBootTest`, `@TestPropertySource`, `@MockitoBean`/`@MockitoSpyBean` or `@DirtiesContext`.
- **Controlled time and AI.** `clock` (`support/MutableClock`) is the `Clock` bean, fixed at Wed 2026-11-18 09:00 and reset after each test. Production code reads dates with `LocalDate.now(clock)`. The test profile uses the mock AI (`spring.ai.active=false`) and needs no `GEMINI_API_KEY`. `@Async` chat compaction runs inline in tests, so never poll or sleep for it.
- **Refusals.** Check every 4xx/5xx with `andExpect(error(HttpStatus.X))` from `BaseComponentTest`: it checks the status and the whole `ApiError` shape. A bare `status().isNotFound()` fails `TestStructureRulesTest`. Check the message text separately where the flow promises one.
- **Data.** Every test creates its own users; nothing is rolled back. Use repositories only to check stored state or to seed states the API can't create.
- **Database.** H2 by default (`application-test-h2.yml` in `src/test/resources`); `-PtestDb=postgres` uses a Testcontainers `postgres:17` (Docker required, no fallback to H2). CI always uses Postgres.
- **Bug fixes** start with a failing test: a component test for a flow bug, a unit test for a rule bug.
- **While working**, run the class you're changing; run the full suite before closing a task.
- **Flow map:** `docs/test-flows.md` lists which test covers each flow, rule and connection. Update it when you add, remove or change a test class.

```bash
./gradlew test                                              # everything, H2 (no Docker)
./gradlew test -PtestDb=postgres                            # everything, Testcontainers Postgres (what CI runs)
./gradlew test --tests '*DailyRoutineComponentTest'         # one class
./gradlew test --tests 'com.tom.payment.routinemanager.unit.*'   # one kind
```

With Colima instead of Docker Desktop, Testcontainers needs `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.


### API endpoints summary

| Prefix | Controller | Purpose |
|--------|-----------|---------|
| `/api/v1/auth/**` | `AuthenticationController` | Login, register, refresh, logout |
| `/api/v1/default-routine/**` | `DefaultRoutineController` | CRUD for default routine templates & tasks |
| `/api/routine-manager/daily-routine/**` | `DailyRoutineController` | CRUD for daily routines & tasks |
| `/api/routine-manager/daily-reflection/{userId}/{date}` | `DailyReflectionController` | F007 reflection: GET (read only), POST (create-or-get), `/tasks` POST/PUT/DELETE, `/feedback` POST; all return `ReflectionView` |
| `/api/ai/chat/**` | `AIController` | AI chat for default & daily routines, and `daily-reflection` (F007) |
| `/api/users/**` | `UserController` | Onboarding completion |
| `/api/v1/users/{userId}/settings`, `/profile` | `UserSettingsController` | UI settings GET/PATCH; profile view (username, email, plan = FREE). `CurrentUserGuard`: 404 unknown user, 403 other user |
| `/api/v1/task-palette` | `TaskPaletteController` | Task color palette (GET), round-robin order |
| `/api/support/cron/**` | `CronSupportController` | Manual cron trigger (local/fast/test only) |

Packages live under `com.tom.payment.routinemanager` (`api`, `config`, `dto`, `funcs`, `model`, `repository`, `service/aitools`, `service/cronjobs`).

## Deployment

- **GitHub Actions** (`.github/workflows/gradle-publish.yml`): builds with Gradle, builds Docker image, pushes to AWS ECR (`milos` repo), deploys to EC2 via SSH.
- **Docker**: `Dockerfile` uses `eclipse-temurin:25` base. `docker-compose.yml` runs PostgreSQL 17 (`trixie` tag) on port 5469.
- CI runs `./gradlew clean build -PtestDb=postgres`: every test on Testcontainers Postgres. A failing test stops the job before the image is built, pushed or deployed, and the test report is uploaded as an artifact.
- **Schema**: until production phase 1, Hibernate `ddl-auto` builds the schema (`update` by default, `create-drop` in tests); Flyway comes with the first stable schema at phase 1 (constitution Principle III). Prod is `ddl-auto: validate`. New tables or columns need a DDL script under `db/` applied to the prod DB *before* the new image starts. F004: `db/F004-chat-history.sql`. F005: `db/F005-task-color.sql`. F006: `db/F006-user-settings.sql`. F007: `db/F007-daily-reflection.sql`.

## Conventions

- Lombok used extensively (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`, `@Data`).
- Constructor injection preferred (via `@RequiredArgsConstructor` or explicit constructors).
- `@JsonManagedReference` / `@JsonBackReference` for bidirectional JPA relationships to prevent infinite recursion.
- `@Transactional` on all service write methods.
- UUID primary keys with `GenerationType.UUID`.
- `application.yml` contains shared config; profile-specific files override datasource settings.
- `application-fast.yml` uses H2 in-memory DB — no PostgreSQL needed. It is not the default; select it with `SPRING_PROFILES_ACTIVE=fast`.

## Rules for new code (from the constitution)

Existing code doesn't follow all of these yet; new and substantially changed code must.

- **DTOs:** request/response bodies are Java records, never JPA entities. Validate input with Bean Validation (`@Valid`, `@NotNull`, …).
- **Errors:** every error returns the one `ApiError` body (`status`, `error`, `message`, `timestamp`), via `GlobalExceptionHandler` or, for the security 401, the entry point in `SecurityConfig`. No second format (no RFC 9457 Problem Details). Never put stack traces, SQL or class names in the body.
- **Ownership:** look up every user-owned entity by (user, id); 404 when it isn't the user's.
- **Tests:** follow "Testing strategy" above: a component test for every new flow or flow fix, unit tests for new rules. Inject `java.time.Clock` instead of calling `LocalDate.now()`.
- **Schema:** changes are additive and backward compatible (expand → migrate → contract). Don't rename/drop a column, enum value, palette id or setting key in one release. Until production phase 1, `ddl-auto` builds the schema and `db/<feature>.sql` scripts (idempotent) cover the prod database; Flyway is adopted at phase 1 with the first stable schema, and then tests build the schema from the migrations.
- **Security:** secrets come only from env vars. Never log tokens, prompts or chat content at INFO or above. The JWT key must come from a secret, not be generated at startup.
- **AI tools:** register only the tools a chat context needs; take the user id from the security context, never from model output; validate tool arguments like user input; destructive bulk actions need user confirmation in the UI.
- **Logging:** SLF4J only (no `System.out`); log an error once, where it's handled.