# AWS Deployment Architecture — Routine Manager

**Goal:** cheapest viable production hosting for the Spring Boot API on EC2, with the
React SPA served free from GitHub Pages.

**Target cost: ~$17/month on-demand, ~$12/month with a 1-year Savings Plan.**

> Prices are approximate, `us-east-1`, as of early 2026. Verify against the
> [AWS Pricing Calculator](https://calculator.aws) before committing.

---

## 1. The shape

The frontend and the backend are hosted separately, but under **one registrable
domain**. GitHub Pages serves the React build from `app.example.com`; one EC2
instance runs the API at `api.example.com` behind Caddy. No load balancer, no RDS,
no NAT gateway, no S3 website bucket, no CloudFront.

```
                         Browser
                            │
            ┌───────────────┴───────────────┐
            │                               │
     HTTPS  │  static assets         HTTPS  │  XHR / fetch
            ▼                               │  (cross-origin,
  ┌─────────────────────┐                   │   SAME-SITE,
  │  app.example.com    │                   │   credentialed)
  │  CNAME → GitHub     │                   ▼
  │  Pages (React SPA)  │    ┌──────────────────────────────┐
  │  free, CDN, TLS     │    │   EC2 t4g.small (Graviton)   │
  └─────────────────────┘    │   api.example.com            │
                             │                              │
                             │  ┌────────────────────────┐  │
                             │  │ Caddy  :80 :443        │  │
                             │  │  auto TLS (Let'sEncrypt)│ │
                             │  │  reverse_proxy app:8080│  │
                             │  └────────────┬───────────┘  │
                             │               ▼              │
                             │  ┌────────────────────────┐  │
                             │  │ Spring Boot  :8080     │  │
                             │  │  (internal only)       │  │
                             │  │  emits CORS headers    │  │
                             │  └────────────┬───────────┘  │
                             │               ▼              │
                             │  ┌────────────────────────┐  │
                             │  │ Postgres :5432         │  │
                             │  │  volume: pgdata        │  │
                             │  └────────────────────────┘  │
                             └──────────────┬───────────────┘
                                            │ nightly pg_dump
                                            ▼
                                    S3 (backups, ~$0.10/mo)

                             ── outbound ──> Google Gemini API
```

**The decision this rests on: both hosts share the registrable domain
`example.com`.** They are different *origins* (different hostnames), so CORS
applies and must be configured. But they are the same *site*, so the refresh
cookie stays first-party and `SameSite=Strict` keeps working — in every browser,
Safari included.

Serving the SPA from the raw `<user>.github.io` hostname instead would make it
cross-*site*, because `github.io` is on the Public Suffix List. The refresh cookie
would become a third-party cookie, which Safari blocks outright. The CNAME is what
avoids that, and it costs nothing beyond the domain the API needs anyway.

**Both hosts need TLS.** Pages provisions its own certificate once the DNS record
resolves; Caddy gets `api.example.com` from Let's Encrypt on first boot. A raw EC2
IP cannot serve HTTPS, and an HTTPS page cannot call an HTTP API.

---

## 2. What we deliberately avoid, and why

These are the four things that silently turn a $12 project into a $90 one.

| Tempting AWS service | Cost/month | Why we skip it |
|---|---|---|
| Application Load Balancer | ~$16–20 | More than the server. Caddy terminates TLS for free. |
| RDS `db.t4g.micro` | ~$15 + storage | Postgres in a container on the same box is free. |
| NAT Gateway | ~$32 + data | Only needed for private subnets. We use one public subnet. |
| CloudFront + S3 for the SPA | ~$1–5 | GitHub Pages already hosts and CDN-serves the SPA, free. |

Combined, those four would cost ~$65/month for a project whose actual compute
need is one small box.

**When to revisit:** move Postgres to RDS the moment the data becomes something
you'd be upset to lose and you're not confident in your backups (§10). Add an ALB
when you need more than one app instance. Neither is true on day one.

---

## 3. Cost breakdown

| Item | Spec | Monthly |
|---|---:|---:|
| EC2 `t4g.small` on-demand | 2 vCPU, 2 GB, ARM | $12.26 |
| EBS `gp3` root volume | 20 GB | $1.60 |
| Public IPv4 address | 1 address | $3.65 |
| Data transfer out | first 100 GB free | $0.00 |
| S3 backups | ~1 GB, Standard-IA | $0.10 |
| DNS — Cloudflare free tier | — | $0.00 |
| **Total (on-demand)** | | **~$17.61** |
| **Total (1-yr Savings Plan, no upfront)** | | **~$13.00** |

**Notes on the line items:**

- **Public IPv4 is no longer free.** Since February 2024 AWS charges $0.005/hour
  for *every* public IPv4 address, attached or not. It is unavoidable unless you
  go IPv6-only, which is not practical for a public web app.
- **Route 53** would add $0.50/month per hosted zone. Cloudflare's free tier does
  the same job at zero, and gives you a free proxy/CDN in front. Use Cloudflare
  unless you need Route 53's AWS integrations.
- **Graviton (`t4g`) is ~20% cheaper than `t3`** for the same specs. Your
  `eclipse-temurin:25` base image publishes `arm64` variants, so this is free
  money — see the build caveat in §9.
- **A domain is now mandatory, not optional.** The SPA is served over HTTPS by
  Pages, so the API must also be HTTPS on a real hostname (§1). Registration runs
  ~$10–15/year — about $1/month, outside the AWS bill and not in the table above.
- **Region.** `us-east-1` is the cheapest. `ap-southeast-1` (Singapore) runs
  ~15–20% higher but will cut a couple hundred ms of latency if your users are in
  Asia. Pick based on where people actually are, not on the price alone.

### Cheaper alternative: Lightsail

Being honest about this since the ask was "cheapest": **AWS Lightsail's $10/month
plan** (2 GB RAM, 2 vCPU, 60 GB SSD, 3 TB transfer) bundles the instance, storage,
and the public IP into one flat price — roughly $7/month less than the equivalent
EC2 build above, with simpler networking.

Choose **EC2** if you want IAM roles, security groups, SSM, Savings Plans, and a
clean path to scaling. Choose **Lightsail** if you want the lowest number and
don't need those. The container setup in this document is identical either way.

---

## 4. Instance sizing

`t4g.small` (2 GB) is the recommended floor. `t4g.micro` (1 GB) will save $6/month
and *can* work, but Java 25 + Postgres + Caddy in 1 GB leaves no headroom and you
will meet the OOM killer.

**Memory budget for 2 GB:**

| Process | Allocation |
|---|---|
| Spring Boot | `-Xmx768m -Xms256m` |
| Postgres | ~256 MB (`shared_buffers=128MB`) |
| Caddy | ~30 MB |
| OS + Docker daemon | ~350 MB |
| Headroom | ~350 MB |

**Always add swap** on a small instance — it converts a hard crash into mere
slowness:

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**On burst credits:** `t4g` is a burstable instance. A Spring Boot cold start will
eat CPU credits, but steady-state traffic for an app this size stays well under
the baseline. Keep `unlimited` mode **off** to avoid surprise charges, and watch
the `CPUCreditBalance` CloudWatch metric for the first week.

---

## 5. Network and security

**One VPC, one public subnet.** Private subnets require a NAT gateway to reach the
internet ($32/month), which buys nothing here — the instance needs outbound access
for Gemini and package updates.

**Security group — inbound:**

| Port | Source | Purpose |
|---|---|---|
| 443 | `0.0.0.0/0` | HTTPS |
| 80 | `0.0.0.0/0` | HTTP→HTTPS redirect + ACME challenge |
| 22 | **closed** | use SSM Session Manager instead |

Leave port 22 closed entirely and attach an IAM role with
`AmazonSSMManagedInstanceCore`. Then `aws ssm start-session --target i-xxxx` gives
you a shell with no open SSH port, no key management, and audit logging. SSM is
free.

**Critically: do not expose 8080 or 5432.** Both stay on the Docker bridge
network, reachable only from Caddy and the app respectively. The current
`docker-compose.yml` publishes Postgres on `5469:5432` — that is for local
development and must not carry over to production.

---

## 6. Configuration files

### `Caddyfile`

Caddy is now an API gateway only — it no longer serves static files, because
GitHub Pages does.

```
api.example.com {
    encode gzip zstd

    reverse_proxy app:8080

    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "strict-origin-when-cross-origin"
    }
}
```

Two deliberate changes from a same-origin setup:

- **No `X-Frame-Options`/static handler.** This vhost only ever returns JSON.
- **Do not add CORS headers in Caddy.** Spring Security already emits them (§7a).
  Two layers both writing `Access-Control-Allow-Origin` produces a duplicated
  header, which browsers reject outright — and the failure looks exactly like a
  misconfigured allowlist, so it wastes an afternoon. Let Caddy proxy `OPTIONS`
  through untouched.

Caddy obtains and renews a Let's Encrypt certificate automatically on first boot.
Point the `api.example.com` A record at the Elastic IP *before* starting it, or the
ACME challenge fails.

### `docker-compose.prod.yml`

```yaml
services:
  caddy:
    image: caddy:2-alpine
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data           # certificates — must persist
      - caddy_config:/config
    depends_on:
      - app

  app:
    image: routinemanager:latest
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://database:5432/postgres
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
      APP_CORS_ALLOWED_ORIGINS: https://<user>.github.io
      JAVA_TOOL_OPTIONS: "-Xmx768m -Xms256m -XX:+UseSerialGC"
    depends_on:
      database:
        condition: service_healthy
    # no ports: — reachable only via Caddy

  database:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: postgres
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    # no ports: — reachable only from app

volumes:
  pgdata:
  caddy_data:
  caddy_config:
```

`-XX:+UseSerialGC` is deliberate: on a 2-vCPU box the serial collector uses
noticeably less memory than G1 and the pause times are irrelevant at this scale.

### `src/main/resources/application-prod.yml`

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  h2:
    console:
      enabled: false

server:
  forward-headers-strategy: framework

logging:
  level:
    root: WARN
    com.tom.payment.routinemanager: INFO
```

### Secrets

Keep them in `/opt/app/.env`, `chmod 600`, owned by root, and **never committed**:

```bash
DB_PASSWORD=<generate with: openssl rand -base64 32>
GEMINI_API_KEY=<...>
JWT_SECRET=<generate with: openssl rand -base64 64>
```

Upgrade path when you want rotation and audit: **SSM Parameter Store** standard
parameters are free (Secrets Manager is $0.40/secret/month — skip it).

---

## 7. Required application changes

Hosting the SPA on GitHub Pages and the API on AWS means **the two are on
different origins**, and the browser enforces that distinction in three separate
mechanisms: CORS, cookie `SameSite`, and mixed content. All three must be right
or auth silently fails. Read §7d before anything else here.

### a. Add a `prod` profile — **this one is a data-loss bug**

`application.yml` has `spring.profiles.active: fast`, and the `fast` profile is
`jdbc:h2:mem:testdb` — an **in-memory** database. Deployed as-is, the app would
appear to work and silently discard every user on each restart. The
`SPRING_PROFILES_ACTIVE: prod` env var in the compose file overrides it.
`src/main/resources/application-prod.yml` has since been created (it did not exist)
and is what that selects — `ddl-auto: validate`, `open-in-view: false`, H2 console
off, `forward-headers-strategy: framework`, quiet logging.

### b. Change `ddl-auto` from `update` to `validate`

`update` lets Hibernate mutate your production schema on startup based on entity
changes. It never drops columns, silently diverges from what you expect, and has
no rollback. Use `validate` in prod and manage schema with Flyway (a Gradle
dependency and a `db/migration` folder — worth doing before the first real user).

### c. Frontend build config for Pages

- **Set the base path to `/`.** `vite.config.ts` has `base: '/milos.github.io/'`,
  which is correct only for the raw Pages URL. Served from `app.example.com` the
  site lives at the domain root, and that prefix breaks every asset URL:
  ```ts
  base: '/',
  ```
- **Commit a `CNAME` file** containing `app.example.com`, and set the same value
  under *Settings → Pages → Custom domain*. Tick **Enforce HTTPS** once the DNS
  record resolves.
- **`API_BASE_URL` must become absolute.** Its `'/api/v1'` default resolves
  against `github.io` and will 404 against the Pages CDN. Set
  `VITE_API_BASE_URL=https://api.example.com/api/v1` as a repository variable and
  inject it at build time in the Pages workflow.
- **Every authenticated `fetch` needs `credentials: 'include'`.** Cross-origin
  requests omit cookies by default, so the refresh call will arrive with no cookie
  and no error message worth reading.
- **Add a SPA 404 fallback.** Pages has no `try_files`; a deep-link refresh on a
  client-side route returns the 404 page. Commit a `404.html` that is a copy of
  `index.html`.

### d. CORS and the refresh cookie — what was implemented

An earlier revision of this document assumed Caddy would serve both the SPA and the
API from one domain and concluded the CORS bean was dead code. With the SPA on
GitHub Pages that is wrong: the two are different origins, so **the CORS bean is
load-bearing**. What changed in the code:

**1. Allowed origins moved to configuration.** `SecurityConfig` previously
hardcoded `http://localhost:5173` and `http://47.130.132.148:4173` as the
non-dev fallback — neither is the frontend. Origins now come from
`app.cors.allowed-origins`, set per profile and overridable at deploy time with
`APP_CORS_ALLOWED_ORIGINS`, no rebuild:

```yaml
app:
  cors:
    allowed-origins: https://app.example.com
```

An origin is `scheme://host[:port]` — no path, no trailing slash. It is matched
byte-for-byte against the `Origin` header.

**2. The wildcard branch was deleted.** `local`/`fast`/`sit` previously got
`setAllowedOriginPatterns("*")` together with `allowCredentials(true)`, which
reflects any requesting origin back and lets any site on the internet make
credentialed calls. Every profile now lists explicit origins.

**3. Preflight caching added** — `setMaxAge(3600)`. Every request carrying an
`Authorization` header is non-simple, so without it each one pays an extra
round-trip to the instance's region.

**4. The refresh cookie was left exactly as it was.** `SameSite=Strict`, `Secure`,
`HttpOnly`, `Path=/api/v1/auth/refresh` are all correct on a same-site deployment:
Strict and Lax differ only on top-level link navigations, which the SPA never uses
for `/auth/refresh`. Strict also keeps that endpoint closed to CSRF while
`csrf()` is disabled. This is the payoff of the CNAME in §1 — had the SPA stayed on
`<user>.github.io`, this cookie would have needed `SameSite=None` and Safari would
have blocked it regardless.

**5. Unauthenticated requests now return 401, not 403.** Spring's default entry
point returns 403 for a denied anonymous request, which the SPA cannot distinguish
from "logged in but not allowed". An `HttpStatusEntryPoint(UNAUTHORIZED)` was added
so the SPA can tell the two apart and trigger its refresh flow.

**6. A `@ControllerAdvice` was added.** A missing refresh cookie returned
`RuntimeException` → 500; it is now a 401. The handler extends
`ResponseEntityExceptionHandler` so framework exceptions keep their real status —
a bare catch-all turns a 404 into a 500, and an error response that loses its CORS
headers reaches the browser as `TypeError: Failed to fetch`, hiding the real fault.

### e. Authentication was not running in production at all

Found while implementing the above, and more serious than any of it.
`JwtAuthenticationFilter` was annotated `@Profile("prod && sit")` — a boolean AND,
so it loaded only when both profiles were active at once. Under
`SPRING_PROFILES_ACTIVE=prod` it never loaded, and nothing read the `Bearer` token.
Independently, `SecurityConfig` authorized via
`authentication.get().isAuthenticated()`, which returns `true` for
`AnonymousAuthenticationToken`. Both halves failed open: every endpoint was public.

Fixed by removing `@Component`/`@Profile` from the filter and registering it
explicitly with `addFilterBefore(...)`, and by replacing the `.access(...)` lambda
with `.authenticated()`. Verified with a token-less `curl` returning 401.

> **Separate issue, flagged for follow-up:** `anyRequest().access(...)` in
> `SecurityConfig` tests `authentication.get().isAuthenticated()`, which returns
> `true` for `AnonymousAuthenticationToken`. And `JwtAuthenticationFilter` is
> registered via `@Component` rather than `addFilterBefore(...)`, which places it
> after the security chain in filter order. Together these suggest protected
> endpoints may not be enforcing auth. Verify with a `curl` carrying no token
> before going live — this is more urgent than anything else in this document.

---

## 8. Instance setup

```bash
# Amazon Linux 2023, t4g.small, 20GB gp3
sudo dnf update -y
sudo dnf install -y docker git postgresql17
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# docker compose v2 (ARM build)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL \
  https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

Autostart on reboot — `restart: unless-stopped` handles the containers, but the
stack needs to come up after an instance stop/start:

```ini
# /etc/systemd/system/routinemanager.service
[Unit]
Description=Routine Manager
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/app
EnvironmentFile=/opt/app/.env
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml up -d
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down

[Install]
WantedBy=multi-user.target
```

---

## 9. Deployment

Two independent pipelines now, which is the main practical upside of the split —
a frontend change ships in ~40 seconds with no container restart and no downtime.

**Frontend → GitHub Pages.** `actions/deploy-pages` on push to `main`. Inject the
API URL at build time, since Vite bakes env vars into the bundle:

```yaml
- run: npm ci && npm run build
  env:
    VITE_API_BASE_URL: https://api.example.com/api/v1
```

Enable Pages under *Settings → Pages → Source: GitHub Actions*. If you took the
custom-domain route (§7d.5), add the domain there too and tick **Enforce HTTPS** —
Pages provisions the certificate, but only once the DNS record resolves.

**Backend → EC2.** Cheapest workable flow, GitHub Actions builds and the box pulls:

1. **Build the JAR** in CI (`./gradlew bootJar`).
2. **Build the image for `linux/arm64`.** Your Dockerfile's
   `eclipse-temurin:25` has arm64 variants, but a runner on x86 produces an x86
   image that will not run on Graviton. Use
   `docker buildx build --platform linux/arm64`, or simply build on the instance
   itself.
3. **Push to ECR.** The private registry's free tier covers 500 MB/month, which
   fits one image if you prune old tags. Docker Hub public is the free
   alternative — only if the image contains nothing sensitive.
4. **Deploy over SSM**, no SSH key in CI:
   ```bash
   aws ssm send-command --instance-ids i-xxxx \
     --document-name AWS-RunShellScript \
     --parameters 'commands=["cd /opt/app && docker compose pull && docker compose up -d"]'
   ```
5. **Nothing to copy for the SPA.** Pages holds it. The only coupling between the
   two pipelines is the CORS allowlist and the `VITE_API_BASE_URL` value — if
   either hostname changes, both sides need updating in the same breath.

Also pin the JAR name. The Dockerfile hardcodes
`routinemanager-0.0.3-SNAPSHOT.jar`, so every version bump silently breaks the
build. Use a wildcard or an `ARG`:

```dockerfile
COPY build/libs/*.jar /opt/app/app.jar
```

**Expect ~30s of API downtime** per backend deploy while the container restarts.
The SPA stays up throughout — it is on Pages — so users see failed requests rather
than a dead site, which is worth handling with a retry and a banner. That is the
honest trade for having no load balancer. Zero-downtime deploys need a second
instance and an ALB — roughly triple the cost.

---

## 10. Backups

Self-hosted Postgres means backups are entirely your responsibility. This is the
real cost of skipping RDS, and it's a nightly cron job:

```bash
#!/bin/bash
# /opt/app/backup.sh  — 0 3 * * *
set -euo pipefail
STAMP=$(date +%Y%m%d-%H%M)
docker compose -f /opt/app/docker-compose.prod.yml exec -T database \
  pg_dump -U postgres postgres | gzip > /tmp/db-$STAMP.sql.gz
aws s3 cp /tmp/db-$STAMP.sql.gz s3://your-backups/db/
rm /tmp/db-$STAMP.sql.gz
```

- Set an **S3 lifecycle rule**: Standard-IA after 30 days, delete after 90.
- Enable **EBS snapshots** (weekly, ~$0.05/GB-month) as a second line of defence.
- **Test a restore before you need one.** An untested backup is not a backup.

---

## 11. Monitoring

Free tier covers what a project this size needs:

- **CloudWatch basic metrics** (5-minute CPU, network, disk) — free.
- **One billing alarm** at your threshold — free, and the most valuable alarm you
  will set.
- **Spring Actuator** `/actuator/health` — expose it *only* on the internal
  network, never through Caddy.
- Skip the CloudWatch agent for custom memory metrics; it pushes you past the free
  tier for little benefit at this scale.

---

## 12. Scaling path

When one box is no longer enough, in the order the pressure usually arrives:

1. **Vertical first.** `t4g.small` → `medium` → `large`. A single resize buys a
   long time and costs one reboot.
2. **Postgres → RDS.** The first thing to split out: managed backups,
   point-in-time recovery, and it takes the memory pressure off the app box.
3. **ALB + 2 app instances.** Zero-downtime deploys and real availability.
4. **SPA off Pages** — only if you outgrow it. Pages has a soft 100 GB/month
   bandwidth limit, a 1 GB site cap, and no control over cache headers or
   redirects. CloudFront + S3 is the successor, and if you serve the API through
   the *same* CloudFront distribution on a shared domain, the cross-site cookie
   problem in §7d disappears entirely.

Steps 1 and 2 will carry you further than you expect. Resist 3 and 4 until
something actually hurts.

---

## Checklist before going live

**Done in code** (verified locally against the `sit` profile with an H2 override):

- [x] Auth actually enforced — token-less `curl` returns 401, valid token returns 200
- [x] `JwtAuthenticationFilter` loads (was `@Profile("prod && sit")`, never active)
- [x] CORS origins config-driven; wildcard-with-credentials branch removed
- [x] Preflight cached (`Access-Control-Max-Age: 3600`)
- [x] Unauthenticated → 401 rather than 403
- [x] Missing refresh cookie → 401 rather than 500; framework 404s stay 404
- [x] `application-prod.yml` created with `ddl-auto: validate`

**Still to do at deploy time:**

- [ ] DNS: `app.example.com` CNAME → `<user>.github.io`; `api.example.com` A → Elastic IP
- [ ] Pages custom domain set, `CNAME` file committed, **Enforce HTTPS** ticked
- [ ] `APP_CORS_ALLOWED_ORIGINS=https://app.example.com` set on the instance
- [ ] `SPRING_PROFILES_ACTIVE=prod`; confirm it is not using H2
- [ ] Schema managed by migrations (`validate` fails startup on drift, by design)
- [ ] Ports 8080 and 5432 not published to the host
- [ ] Port 22 closed; SSM Session Manager working
- [ ] `vite.config.ts` → `base: '/'`; `VITE_API_BASE_URL` absolute `https://…`
- [ ] Authenticated `fetch` calls send `credentials: 'include'`
- [ ] `404.html` committed so deep-link refresh works on Pages
- [ ] Secrets in `/opt/app/.env`, `chmod 600`, not in git
- [ ] TLS issued on both hosts; no mixed-content warning
- [ ] **Refresh-token expiry fixed** — see the note below; the 7-day cookie is
      currently useless without it
- [ ] Log in, hard-refresh, confirm you stay logged in (Chrome *and* Safari — on
      this same-site setup both should pass)
- [ ] Nightly backup running **and a restore tested**
- [ ] Billing alarm set

---

## Known defect: refresh tokens expire immediately

`RefreshToken.expiryDate` is typed `java.time.LocalTime` — a time of day with no
date — and `AuthenticationService.saveRefreshToken` sets it to
`LocalTime.now().plusSeconds(refreshTokenExpiration)`.

With a 7-day expiry that arithmetic is a no-op: 604800 seconds is exactly 7 × 86400,
and `LocalTime` wraps at 24 hours, so `plusSeconds(604800)` returns **the same time
of day it started from**. The token is therefore already expired the moment any
time passes, and `refresh()` throws `"Refresh token has expired"` on the next call.

Confirmed locally: a refresh issued and replayed within the same second returns 200;
the same cookie two minutes later returns an error.

The fix is a type change — `LocalTime` → `Instant` on the entity and both call
sites — which also changes the column type, so it needs a migration. Not applied
yet; it was outside the approved scope of the security work above.
