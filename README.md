# Memento

A personal bookmark manager with a dual-auth API — browser users log in with a session, AI agents authenticate with a JWT token.

---

## Table of Contents
- [Tech Stack](#tech-stack)
- [High Level Design (HLD)](#high-level-design)
- [Low Level Design (LLD)](#low-level-design)
- [Project Structure](#project-structure)
- [API Reference](#api-reference)
- [Docker Files Explained](#docker-files-explained)
- [Local Development](#local-development)
- [Production Deployment](#production-deployment)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 4.x |
| Language | Java 25 |
| Security | Spring Security 7 (session + JWT) |
| Database | PostgreSQL 17 (Alpine) |
| Migrations | Flyway |
| ORM | Spring Data JPA (Hibernate) |
| Cache | Caffeine (in-memory) |
| Rate Limiting | Bucket4j (token bucket, per IP) |
| Templates | Thymeleaf |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Containerisation | Docker + Docker Compose |
| CI/CD | GitHub Actions → ghcr.io → VPS |

---

## High Level Design

### System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Clients                          │
│                                                         │
│   Browser User                       AI Agent           │
│  (session auth)                    (JWT Bearer)         │
└────────────┬────────────────────────────┬───────────────┘
             │                            │
             ▼                            ▼
┌─────────────────────────────────────────────────────────┐
│                     VPS (Oracle Free Tier)              │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │               Docker Compose                    │   │
│  │                                                  │   │
│  │  ┌─────────────────────┐  ┌──────────────────┐  │   │
│  │  │   Spring Boot App   │  │   PostgreSQL 17   │  │   │
│  │  │   (512 MB / 0.8c)   │◄─┤   (200 MB / 0.5c)│  │   │
│  │  │                     │  │    Alpine image   │  │   │
│  │  │  Rate Limiter       │  └──────────────────┘  │   │
│  │  │  Spring Security    │                         │   │
│  │  │  JWT Filter         │  ┌──────────────────┐  │   │
│  │  │  Caffeine Cache     │  │  Named Volumes   │  │   │
│  │  │  Flyway Migrations  │  │  postgres_data   │  │   │
│  │  │                     │  │  logs            │  │   │
│  │  └─────────────────────┘  └──────────────────┘  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│   Swap: 1 GB  |  swappiness=10  |  vfs_cache_pressure=50│
└─────────────────────────────────────────────────────────┘
```

### CI/CD Pipeline

```
Developer
   │
   │  git push origin main
   ▼
GitHub Actions Runner
   │
   ├─ docker build (multi-stage Dockerfile)
   │     Stage 1: eclipse-temurin:25-jdk-alpine  →  ./gradlew bootJar
   │     Stage 2: eclipse-temurin:25-jre-alpine  →  copy JAR
   │
   ├─ docker push → ghcr.io/<username>/memento:latest
   │
   ├─ scp docker-compose.yml → VPS /opt/memento/
   │
   └─ ssh VPS
         docker compose pull app
         docker compose up -d app      ← only app restarts, db untouched
         docker image prune -f
```

### Authentication Flow

```
Browser User                              AI Agent
     │                                       │
     │  GET /                                │  GET /api/bookmarks
     │                                       │  Authorization: Bearer <jwt>
     ▼                                       ▼
[RateLimitFilter] ── 429 Too Many Requests ──────────────────►
     │
     ▼
[Spring Security FilterChainProxy]
     │
     ├── Path: /api/**  →  [JWT Filter Chain]
     │                          │
     │                     JwtAuthFilter
     │                     validates Bearer token
     │                     sets SecurityContext
     │                          │
     │                     Controller / Service
     │
     └── Path: everything else  →  [Session Filter Chain]
                                        │
                                   formLogin checks session
                                        │
                              ┌─────────┴──────────┐
                              │                    │
                         Authenticated          Not authenticated
                              │                    │
                         Controller            redirect /login
                                               LoginController
                                               (Thymeleaf form)
                                                    │
                                              POST /login
                                              Spring Security
                                              validates creds
                                                    │
                                              redirect /auth/token
                                              JWT displayed in browser
```

### User Isolation

Every bookmark is scoped to the authenticated user. `userId` is extracted from the JWT or session — the client never sends it.

```
User A's JWT                    User B's JWT
     │                               │
     ▼                               ▼
currentUserId() = 1          currentUserId() = 2
     │                               │
     ▼                               ▼
SELECT * FROM bookmarks        SELECT * FROM bookmarks
WHERE user_id = 1              WHERE user_id = 2
```

---

## Low Level Design

### Request Filter Pipeline

Every request passes through this pipeline in order:

```
Incoming Request
      │
      ▼
① RateLimitFilter          (@Component — runs on ALL paths)
  Token bucket per IP
  429 if bucket empty
      │
      ▼
② FilterChainProxy         (Spring Security's entry point)
      │
      ├─ /api/**  →  ③a JwtAuthFilter
      │                  Reads Authorization header
      │                  Validates JWT signature + expiry
      │                  Sets UsernamePasswordAuthenticationToken
      │                  in SecurityContextHolder
      │
      └─ /**      →  ③b SessionFilter
                         Reads JSESSIONID cookie
                         Restores authentication from session
                         Unauthenticated → redirect /login
      │
      ▼
④ DispatcherServlet        (Spring MVC)
  Routes to Controller
      │
      ▼
⑤ Controller → Service → Repository → PostgreSQL
```

### Security Filter Chains

Two `SecurityFilterChain` beans are defined with explicit ordering:

```
@Order(1) jwtFilterChain
  securityMatcher: /api/**
  csrf: disabled
  sessionCreationPolicy: NEVER        ← uses existing session, never creates one
  authEntryPoint: redirect /login     ← browser users hitting API get login page
  addFilterBefore: JwtAuthFilter

@Order(2) sessionFilterChain
  securityMatcher: /** (catch-all)
  csrf: disabled
  formLogin:
    loginPage: /login                 ← our LoginController, not Spring's generated page
    defaultSuccessUrl: /auth/token
  logout:
    GET /logout → invalidate session → redirect /login?logout
```

### Caching Strategy

```
@Cacheable                              @CacheEvict(allEntries=true)
─────────                              ────────────────────────────
getAllBookMarks()                       createBookMark()
  key: "all:{userId}"                  updateBookMark()
                                       deleteBookMark()
getBookMark(title)
  key: "{title}:{userId}"

getBookMarksByTag(tag)
  key: "tag:{tag}:{userId}"
```

Cache is scoped per user via `currentUserId()`. Any write clears the entire cache for all users (simple and safe for small scale). Caffeine config: max 500 entries, expire after 10 minutes.

### Database Schema

```sql
users
┌─────────┬──────────────┬─────────────┬──────────────────────────────────┐
│ id      │ username     │ password    │ role                             │
│ BIGINT  │ VARCHAR(50)  │ VARCHAR(255)│ VARCHAR(20)                      │
│ PK      │ UNIQUE       │ BCrypt hash │ CHECK IN ('ADMIN','OPERATOR',    │
│ AUTO    │              │             │           'GUEST')               │
└─────────┴──────────────┴─────────────┴──────────────────────────────────┘

bookmarks
┌──────────────┬────────────────┬─────────────┬──────────┐
│ title        │ description    │ tag         │ user_id  │
│ VARCHAR(255) │ VARCHAR(2000)  │ VARCHAR(100)│ BIGINT   │
│              │                │ nullable    │ FK→users │
│ PRIMARY KEY (title, user_id) ◄─ composite  │          │
└──────────────┴────────────────┴─────────────┴──────────┘
```

`title` is `@Id` in JPA for simplicity (avoids the `@EmbeddedId` boilerplate). All queries are scoped with `findByTitleAndUserId` so the composite uniqueness is always enforced.

### JWT Structure

```
Header:  { alg: HS256 }
Payload: {
  sub:    "username",
  userId: 1,
  role:   "ADMIN",
  iat:    <issued-at>,
  exp:    <issued-at + 24h>
}
Signature: HMAC-SHA256(header + payload, JWT_SECRET)
```

The signing key is derived once at startup via `@PostConstruct` and cached — not re-derived on every request.

### Role Model

```
ADMIN    → full access (currently no endpoint restrictions by role,
OPERATOR   designed for future @PreAuthorize use)
GUEST
```

Roles stored as strings in DB (`EnumType.STRING`). Spring Security authorities are prefixed with `ROLE_` in `getAuthorities()` only — the enum stays clean.

### Package Structure

```
com.memento
├── MementoApplication.java          @SpringBootApplication, @EnableCaching

├── config/
│   ├── AppUserDetails.java          UserDetails wrapper around User entity
│   ├── JwtUtil.java                 JWT generate / parse / validate
│   └── SecurityConfig.java          Two SecurityFilterChain beans + FilterRegistrationBean

├── controller/
│   ├── AuthController.java          GET /auth/token — shows JWT to browser user
│   ├── BookMarkController.java      GET/POST/PUT/DELETE /api/bookmarks
│   ├── LoginController.java         GET /login — Thymeleaf login form
│   └── RootController.java          GET / → redirect /auth/token

├── dto/
│   └── request/
│       └── UpdateBookMarkRequest.java   PUT body (description + tag)

├── exception/
│   ├── BookMarkExistException.java
│   ├── BookMarkNotFoundException.java
│   └── GlobalExceptionHandler.java  @RestControllerAdvice — maps exceptions to HTTP responses

├── filter/
│   ├── JwtAuthFilter.java           Reads Bearer token, sets SecurityContext
│   └── RateLimitFilter.java         Per-IP token bucket, returns 429 when empty

├── model/
│   ├── BookMark.java                @Entity — title is @Id, userId is @JsonIgnore
│   ├── Role.java                    Enum: ADMIN, OPERATOR, GUEST
│   └── User.java                    @Entity — id, username, BCrypt password, role

├── repo/
│   ├── BookMarkRepo.java            Spring Data derived queries (scoped by userId)
│   └── UserRepo.java                findByUsername for auth

└── service/
    ├── BookMarkService.java         Business logic + @Cacheable/@CacheEvict
    └── CustomUserDetailsService.java  UserDetailsService for Spring Security login
```

---

## API Reference

Interactive docs available at `http://localhost:8080/swagger-ui/index.html` when the app is running.

### Authentication

All `/api/**` endpoints require either:
- **Browser**: active session (login at `/login`)
- **AI Agent**: `Authorization: Bearer <token>` header

Get a token at `/auth/token` after logging in via the browser.

### Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/` | Redirect to token page | — |
| `GET` | `/login` | Login form | Public |
| `POST` | `/login` | Submit credentials | Public |
| `GET` | `/logout` | Invalidate session | Session |
| `GET` | `/auth/token` | View JWT token | Session |
| `GET` | `/api/bookmarks` | List all bookmarks | JWT / Session |
| `GET` | `/api/bookmarks?tag=x` | Filter by tag | JWT / Session |
| `GET` | `/api/bookmarks/{title}` | Get one bookmark | JWT / Session |
| `POST` | `/api/bookmarks` | Create bookmark | JWT / Session |
| `PUT` | `/api/bookmarks/{title}` | Update bookmark | JWT / Session |
| `DELETE` | `/api/bookmarks/{title}` | Delete bookmark | JWT / Session |
| `GET` | `/actuator/health` | Health check | Public |
| `GET` | `/swagger-ui/index.html` | API docs UI | Public |

### Request / Response Examples

**Create bookmark**
```http
POST /api/bookmarks
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Spring Docs",
  "description": "Official Spring Framework documentation",
  "tag": "dev"
}
```

**Update bookmark**
```http
PUT /api/bookmarks/Spring%20Docs
Authorization: Bearer <token>
Content-Type: application/json

{
  "description": "Updated description",
  "tag": "reference"
}
```

---

## Docker Files Explained

There are two Docker-related files in this repo. They serve different purposes at different stages.

### `Dockerfile` — build recipe (used by GitHub Actions only)

Defines how to compile the source code and package it into a runnable Docker image. It uses a two-stage build:
- **Stage 1** (`jdk`): runs `./gradlew bootJar` to produce the fat JAR
- **Stage 2** (`jre`): copies only the JAR into a minimal JRE image — no compiler, no source code, smaller final image

You never run this manually. GitHub Actions runs `docker build` using this file on every push to `main`, then pushes the resulting image to `ghcr.io`.

> **Previous approach vs current:** An earlier version built the image inside the VPS during deployment — meaning the VPS needed Gradle, Java, and source code. The current approach builds once on GitHub's servers and ships a pre-built image to the VPS. The VPS only needs Docker.

### `docker-compose.yml` — runtime config (used on VPS only)

Defines what to run and how. It does not build anything — it pulls the pre-built image from `ghcr.io` and starts two containers:

| Container | What it runs |
|-----------|-------------|
| `db` | `postgres:17-alpine` — the database |
| `app` | Your Spring Boot app image from `ghcr.io` |

It also wires up env vars, memory limits, volumes, and the healthcheck dependency between containers. This is the only file you interact with on the VPS.

### `.dockerignore` — build context filter

When `docker build` runs, Docker sends the project folder to the Docker daemon. `.dockerignore` tells it what to skip — similar to `.gitignore`. Without it, Docker would bundle `.git/`, `build/`, and `.env` into the image context, making builds slower and potentially leaking secrets.

### How the three files work together

```
Local machine / GitHub Actions          VPS
──────────────────────────────          ──────────────────────────────
Dockerfile                              docker-compose.yml
  + source code                           reads APP_IMAGE from .env
  + .dockerignore (filters noise)         pulls ghcr.io/you/memento:latest
        │                                 starts db + app containers
        ▼
  docker build → image
        │
        ▼
  docker push → ghcr.io ──────────────► docker compose pull app
                                         docker compose up -d app
```

---

## Local Development

Prerequisites: Java 25, Docker (for PostgreSQL)

```bash
# Start PostgreSQL only
docker run -d \
  --name memento-db \
  -e POSTGRES_DB=memento \
  -e POSTGRES_USER=memento \
  -e POSTGRES_PASSWORD=memento123 \
  -p 5432:5432 \
  postgres:17-alpine

# Run the app
./gradlew bootRun
```

App: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui/index.html`

**Seed a user** (run once in psql):
```sql
-- BCrypt hash of "password" (cost 10) — change this in production
INSERT INTO users (username, password, role)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');
```

---

## Production Deployment

### First-time VPS setup

Run these commands once on a fresh VPS. After this, GitHub Actions handles all future deployments automatically.

#### 1. Install Docker

```bash
# Downloads and runs the official Docker install script
curl -fsSL https://get.docker.com | sh

# Allow your user to run Docker without sudo (log out and back in after this)
sudo usermod -aG docker $USER
```

#### 2. Configure swap

Without swap, the Linux OOM (Out-of-Memory) killer will terminate processes when RAM is full — usually the JVM, since it's the largest consumer. A 1 GB swap file on disk acts as a safety buffer.

```bash
# Allocate a 1 GB file on disk and format it as swap
sudo fallocate -l 1G /swapfile
sudo chmod 600 /swapfile        # restrict access — swap must not be world-readable
sudo mkswap /swapfile
sudo swapon /swapfile           # activate immediately

# Make swap survive reboots
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

```bash
# vm.swappiness=10 — kernel prefers RAM, only spills to swap when truly needed
# (default is 60, which is too aggressive for a server)
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf

# vm.vfs_cache_pressure=50 — keep filesystem cache in RAM longer (better read speed)
# (default is 100, which reclaims cache too eagerly)
echo 'vm.vfs_cache_pressure=50' | sudo tee -a /etc/sysctl.conf

# Apply settings immediately without reboot
sudo sysctl -p
```

#### 3. Create app directory and .env

```bash
# Create the directory where the app and its config will live
sudo mkdir -p /opt/memento

# Generate a secure JWT secret (copy the output — you'll need it below)
openssl rand -base64 32
```

Create `/opt/memento/.env` with the following content, filling in real values:

```bash
# PostgreSQL credentials — used by both the db container and the app
SPRING_DATASOURCE_USERNAME=memento
SPRING_DATASOURCE_PASSWORD=<strong-password>

# Base64-encoded signing secret for JWT tokens — paste the openssl output above
JWT_SECRET=<output of: openssl rand -base64 32>

# Docker image built and pushed by GitHub Actions on every push to main
# Replace with your actual GitHub username and repo name (must be lowercase)
APP_IMAGE=ghcr.io/<your-github-username>/memento:latest
```

```bash
# Restrict .env to root only — it contains secrets
sudo chmod 600 /opt/memento/.env
```

### GitHub Secrets

Add these in your repo → **Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|-------|
| `VPS_HOST` | VPS IP address |
| `VPS_USER` | SSH username (`ubuntu`, `opc`, `root`, etc.) |
| `VPS_SSH_KEY` | Your private SSH key — run `cat ~/.ssh/id_rsa` on your local machine |

### Automated deploys

After the first-time setup, every `git push origin main` automatically:
1. Builds the Docker image on GitHub's servers (no JDK needed on VPS)
2. Pushes it to `ghcr.io` (GitHub's free built-in Docker registry)
3. SSHes to the VPS and restarts **only the app container**
4. The `db` container and `postgres_data` volume are never touched — data is safe

### Resource allocation (1 GB AMD VPS)

| Component | Memory cap | CPU cap | Notes |
|-----------|-----------|---------|-------|
| PostgreSQL | 200 MB | 0.5 core | Runtime tuned: `shared_buffers=32MB`, `max_connections=20` |
| Spring Boot | 512 MB | 0.8 core | JVM tuned: `-Xmx350m -Xss256k` via `JAVA_TOOL_OPTIONS` |
| OS headroom | ~200 MB | — | Reserved for kernel + system processes |
| Swap buffer | 1 GB (disk) | — | Safety net against OOM kills |
