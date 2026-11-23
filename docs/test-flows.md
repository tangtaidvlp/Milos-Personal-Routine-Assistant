# Backend test flows

This map shows which test covers each user flow, business rule and external connection. The rules for writing tests are in `CLAUDE.md` → "Testing strategy" and constitution Principle II.

- **Last verified:** 2026-11-24, branch `feat/E001-personal-target` (F013). 178 tests on Postgres (`-PtestDb=postgres`), all passing; on H2 the 2 Postgres-only tests are skipped. Earlier: 2026-11-23, `feat/F008-backend-testing-structure`, 136 tests: 76 component, 53 unit, 7 integration. One Spring context per run. About 24 s on H2 and 26 s on Postgres (full `./gradlew test`).
- **Keep it current:** update this file when you add, remove or change a test class.

## 1. How a component test runs

```mermaid
flowchart TD
    A["One Spring context per run<br/>(profiles test + test-h2 | test-postgres)"] --> B["newUser(): POST /api/v1/auth/register + /login<br/>→ TestUser with a real access token"]
    B --> C["as(user, request): MockMvc call with Bearer token<br/>through the real security filter chain + JWT filter"]
    C --> D["Assert HTTP status + JSON body"]
    D --> E["On writes: assert stored rows via a repository"]
    E --> F["@AfterEach: clock reset; AI spies reset"]
    G["Controlled: MutableClock (Wed 2026-11-18 09:00),<br/>mock AI (spied), @Async run inline"] -.-> C
```

| Run | Command | Database |
|---|---|---|
| Local (default) | `./gradlew test` | H2 in memory; `@RequiresPostgres` tests are skipped |
| Like CI | `./gradlew test -PtestDb=postgres` | Testcontainers `postgres:17` (Docker needed) |
| One class | `./gradlew test --tests '*SignInComponentTest'` | either |

Every refusal check uses `error(HttpStatus.X)`, which also checks the `ApiError` body shape (`status`, `error`, `message`, `timestamp`). `TestStructureRulesTest` fails on a bare status check.

## 2. Main user flows → component tests

| Flow | Test class | Tests | Success path | Refusals covered |
|---|---|---|---|---|
| Sign up, sign in, keep the session, sign out | `SignInComponentTest` | 12 | register 201; login 200 with token and HttpOnly refresh cookie; refresh 200; logout clears the cookie | wrong email/password → 401 (same body); duplicate email → 409; refresh with no, unknown, revoked, expired or orphan token → 401 |
| Onboarding: empty weekday and weekend routines | `OnboardingComponentTest` | 5 | registration creates both; a legacy user gets a weekend routine once; `type` defaults to DAILY | `type=FOO` → 400; no token → 401 |
| Edit the weekday template | `DefaultRoutineComponentTest` | 12 | add tasks (end times computed); one overlap shift; rename; delete; replace routine; overnight task stays one task; copy Weekday to Weekend replaces the Weekend tasks (empty Weekday empties it) | unknown colour → 400 with nothing stored; duration outside 1–1440 → 400; another user's copy → 403; no token → 401 |
| A day from the template, and daily tasks | `DailyRoutineComponentTest` | 15 | today copied and stored; other days read without being created; Saturday from the weekend template; add / complete / rename / delete; overnight task stays one task, also when copied from the template; Reset day replaces the day with the template (weekend fallback, missing future day created, reflection kept) | Buddy can't add to or reschedule a past day; reset of a past day → 400 with the day unchanged; another user's reset → 403; duration outside 1–1440 → 400; no token → 401 |
| Task colours | `TaskColorComponentTest` | 8 | auto colour + `nextColor`; chosen colour stored; update without colour keeps it; delete doesn't rewind; new day copies colours and cursor; midnight spawn copies colours; palette order; Buddy sets palette names | unknown colour → 400 on create and update; Buddy refuses hex and unknown names |
| Account settings and profile | `UserSettingsComponentTest` | 12 | defaults; PATCH stored and marked customized; partial PATCH; profile without secrets; corrupt or unknown stored rows ignored; Buddy tone (F014) defaults to `BALANCED` and is stored upper-case | invalid PATCH → 400 with nothing written; unknown user 404; another user 403; no token 401 |
| Chat with Buddy (history) | `ChatHistoryComponentTest` | 11 | exchanges stored in order; one conversation per routine and per date; context passed to the AI; paging back with no gaps | blank message or missing date → 400; unknown user or another user's routine → 404; AI failure → 5xx with nothing stored; no token → 401 |
| Chat compaction | `ChatCompactionComponentTest` | 2 | at 20 messages the context is summarised; visible history unchanged; summary never returned | summariser failure: chat keeps working and compaction retries |
| Personal goals (F013) | `PersonalGoalComponentTest` | 7 | create (trimmed, stored), list oldest first with `limit` 3, update both texts, delete | 4th goal → 409; blank or 151-char goal → 400 with nothing stored; another user's goal id → 404; another user's path → 403; unknown user 404; no token 401 |
| Buddy proposes a goal change (F013) | `GoalProposalComponentTest` | 6 | `proposeGoal` records ADD or UPDATE (current goal from the DB, blank help keeps it) and writes nothing; the chat reply carries `goalProposal`, history stores only the text; no proposal → `goalProposal: null` | 4th add, unknown/foreign/malformed goal id, invalid text, a second proposal in one reply → refused with a reason, nothing recorded |
| Buddy's prompt gets the user's goals, tone and time of day (E001) | `BuddyContextComponentTest` | 3 | the new user gets Balanced and "no goals"; the tone saved by PATCH and a goal saved by POST appear in the next prompt; feedback has no proposal rule; the window follows the local time; only the Today chat gets the 3-sentence rule | — |
| Buddy calls carry the user's local time (F015) | `LocalTimeComponentTest` | 4 | `localTime` "HH:mm" in the daily and reflection chat bodies and on `/feedback?localTime=` reaches the AI layer | missing or malformed time → ignored (null), the chat still works |
| Daily reflection | `ReflectionComponentTest` | 14 | plan copied once; reading creates nothing; reality and plan are independent; Buddy's note and staleness; separate reflection chat | future date → 400; validation → 400; edits before creation → 404; another user's task → 404; unknown user 404; AI note failure → 503 keeping the old note; no token → 401 |

Today is Wednesday 2026-11-18 in every component test (`clock`). Move it with `clock.setToday(...)` or `clock.advance(...)`.

## 3. Core business rules → unit tests

| Rule | Code | Test | Tests |
|---|---|---|---|
| Colour round-robin, preselection, same-name sharing, validation | `service/TaskColorService` | `TaskColorServiceTest` | 8 |
| Overlap shifting on a 24h cycle (template and daily) | `funcs/TaskTimeFunctions` | `TaskTimeFunctionsTest` | 13 |
| Weekday vs weekend template | `funcs/RoutineTemplateRules` | `RoutineTemplateRulesTest` | 9 |
| Past / future day | `funcs/DayRules` | `DayRulesTest` | 4 |
| History page size; compaction due | `config/ChatHistoryProperties` | `ChatHistoryPropertiesTest` | 4 |
| Plan-vs-reality grouping | `service/reflection/ReflectionComparisonService` | `ReflectionComparisonServiceTest` | 6 |
| Settings values, defaults, adding a preference (incl. `buddyTone`, F014) | `service/UserSettingsRegistry` | `UserSettingsRegistryTest` | 15 |
| Tone block in Buddy's prompt: style line per tone, rules every tone keeps (F014) | `funcs/BuddyTonePrompt`, `funcs/BuddyTone` | `BuddyTonePromptTest` | 8 |
| Goal text: required, trimmed, 150 / 500 characters | `funcs/GoalRules` | `GoalRulesTest` | 5 |
| Part of the day (05/12/17 boundaries) and strict `HH:mm` parsing (F015) | `funcs/TimeOfDay` | `TimeOfDayTest` | 18 |
| "Right now" block: window style, keep the tone, length rule per kind of chat (F015) | `funcs/TimeOfDayPrompt`, `funcs/ChatKind` | `TimeOfDayPromptTest` | 3 |
| Goals block in Buddy's prompt: JSON data, rules, no-goals text, proposal rule | `funcs/PersonalGoalPrompt` | `PersonalGoalPromptTest` | 4 |
| Palette contrast (WCAG AA, both themes) | `application.yml` palette | `TaskPaletteContrastTest` | 1 |
| Test-suite shape (packages, names, one context; refusals checked with `error(..)`) | test classes | `TestStructureRulesTest` | 2 |

## 4. External connections → integration tests

| Connection | Test | Runs on |
|---|---|---|
| Spring Security + JWT filter: valid token accepted; missing, non-Bearer, garbage, tampered or foreign-signed token → 401 with the `ApiError` body | `JwtSecurityIntegrationTest` (6) | H2 and Postgres |
| PostgreSQL row lock on a conversation: concurrent chat messages get unique, gap-free sequence numbers | `ChatSequenceLockingIntegrationTest` (1) | Postgres only (`@RequiresPostgres`) |
| PostgreSQL row lock on the user for the 3-goal limit: concurrent goal saves never make a 4th goal (F013) | `GoalLimitLockingIntegrationTest` (1) | Postgres only (`@RequiresPostgres`) |

## 5. Endpoint coverage

✅ = covered through HTTP with a real token

| Endpoint | Covered by |
|---|---|
| `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout` | SignIn ✅ |
| `GET /api/v1/default-routine/users/{userId}` | Onboarding, DefaultRoutine, TaskColor ✅ |
| `POST /api/v1/default-routine/{routineId}/tasks`, `PUT /tasks`, `DELETE /tasks`, `PUT /{id}` | DefaultRoutine, TaskColor ✅ |
| `POST /api/v1/default-routine/users/{userId}` | — (not used by the frontend) |
| `GET` / `POST /api/routine-manager/daily-routine/{userId}` | DailyRoutine, TaskColor, Reflection ✅ |
| `POST /api/routine-manager/daily-routine/{routineId}/tasks`, `PUT /tasks`, `DELETE /tasks` | DailyRoutine, TaskColor ✅ |
| `/api/routine-manager/daily-reflection/{userId}/{date}` (all) | Reflection ✅ |
| `/api/ai/chat/**` (send + history, all three kinds) | ChatHistory, ChatCompaction, Reflection ✅ |
| `GET` / `PATCH /api/v1/users/{userId}/settings`, `GET …/profile` | UserSettings ✅ |
| `GET` / `POST /api/v1/users/{userId}/goals`, `PUT` / `DELETE …/goals/{goalId}` | PersonalGoal, GoalProposal ✅ |
| `GET /api/v1/task-palette` | TaskColor ✅ |
| `POST /api/support/cron/spawn-daily-task` | TaskColor ✅ |
| `GET` / `POST /api/users/{userId}/onboarding/complete` | — |

## 6. Known gaps (filed as bugs, not covered on purpose)

- The default/daily routine endpoints don't check that the routine belongs to the caller. Tests don't assert either way (`AI-project-docs/bugs/`).
- Buddy's update/delete tools read lazy task lists without a transaction. Tests call them inside one (`AI-project-docs/bugs/`).
- The `prod` profile (open-in-view off) can't serialise routine entities. Tests use the `test` profile, which the EC2 backend runs (`AI-project-docs/bugs/`).
- No AI-provider integration test yet (needs a local stand-in for the Gemini API).
