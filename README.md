# SharedNoteBook Web Companion

SharedNoteBook is a local-first household notebook that runs on the designated admin laptop. The web companion lets household members work with shared notes in a browser while Spring Boot owns authentication, administration, durable PostgreSQL commits, and synchronization with registered Android devices.

The browser is a temporary interface, not an independent notebook peer. Private Android notes are never exposed on the web.

> [!IMPORTANT]
> The current codebase implements the first project slice with synthetic household data. It is suitable for interface and integration development only. Do not use the `prototype` profile with real household content.

## Current implementation

The first slice includes:

- A responsive React and TypeScript application for laptop, tablet, and narrow-window layouts.
- Shared-note listing, searching, filtering, sorting, creation, editing, and revision previews.
- Separate PostgreSQL-save and Android-propagation status surfaces.
- Connection and synchronization status pages.
- An initial household administration overview.
- A synthetic in-memory Spring API for development.
- CSRF protection, loopback enforcement, Content Security Policy, anti-framing headers, and no-cache API responses.
- A PostgreSQL Flyway baseline using encrypted payload columns rather than plaintext note indexes.
- A Maven lifecycle that builds the frontend and packages it into the Spring Boot application.

Not yet implemented:

- Root-admin passkey bootstrap and WebAuthn verification.
- Remembered web-device enrollment and approval.
- PostgreSQL-backed note services and encrypted content handling.
- Authenticated Android discovery, heartbeat presence, and reconciliation.
- Full conflict resolution, trash, private-delivery jobs, activity history, and policy controls.

See [web-design.md](docs/web-design.md) and [web-requirements.md](docs/web-requirements.md) for the complete product definition.

## Technology

| Area | Technology |
|---|---|
| Backend | Spring Boot 4.1, Java 25 |
| Frontend | React 19, TypeScript, Vite |
| Database | PostgreSQL with Flyway migrations |
| Security foundation | Spring Security, CSRF, loopback-only listener, strict browser headers |
| Build | Maven Wrapper with pinned Node.js and pnpm tooling |

## Repository structure

```text
shared-notebook/
├── frontend/                       React, TypeScript, and Vite application
├── src/main/java/                  Spring Boot configuration and APIs
├── src/main/resources/
│   ├── application.yaml            Production defaults and safe bounds
│   ├── application-prototype.yaml  Synthetic development profile
│   └── db/migration/               Flyway PostgreSQL migrations
├── docs/                            Product specifications and smoke checklist
├── pom.xml                          Backend and integrated frontend build
└── mvnw                             Maven Wrapper
```

## Prerequisites

- Java 25.
- Internet access for the first dependency download.
- PostgreSQL for the default production-oriented profile.
- Node.js and pnpm are optional for a normal Maven build because Maven downloads the pinned frontend toolchain. Install Node.js 24 and pnpm 11 locally when using Vite hot reload.

## Quick start with prototype data

Build the complete application without running tests:

```bash
./mvnw clean package -DskipTests
```

Start the packaged application with the synthetic prototype profile:

```bash
java -jar target/shared-notebook-0.1.0.jar \
  --spring.profiles.active=prototype
```

Open [http://localhost:8080](http://localhost:8080).

The interface displays a `Prototype data` label while this profile is active. All notes and state are held in memory and reset when the application stops.

## Frontend development with hot reload

Start Spring Boot in one terminal:

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=prototype \
  -Dskip.frontend=true
```

Start Vite in another terminal:

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Open the localhost URL printed by Vite. Development API calls under `/api` are proxied to Spring Boot on port `8080`.

## Production-oriented configuration

The default profile binds the web application to `127.0.0.1:8080`, enables PostgreSQL and Flyway, and keeps non-local web access disabled. It expects these values to be supplied externally:

```bash
export SNB_DB_URL='jdbc:postgresql://localhost:5432/shared_notebook'
export SNB_DB_USER='shared_notebook'
export SNB_DB_PASSWORD='replace-with-an-external-secret'
export SNB_NODE_ID='replace-with-the-admin-laptop-node-id'
export SNB_MASTER_KEY_REF='replace-with-an-os-keystore-reference'
export SNB_SIGNING_KEY_REF='replace-with-an-os-keystore-reference'
export SNB_HOME_LAN_PROFILE_REF='replace-with-the-trusted-network-profile-reference'
```

Do not commit real secret values or key material. The default profile currently provides the configuration and database foundation; production authentication and persistent note services are still pending later implementation slices.

## Available prototype APIs

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/session` | Current synthetic member, web device, CSRF token, and connection state |
| `GET` | `/api/v1/shared-notes` | Searchable shared-note summaries with opaque cursor support |
| `GET` | `/api/v1/shared-notes/{noteId}` | Note content and retained revisions |
| `POST` | `/api/v1/shared-notes` | Create a synthetic shared note |
| `PUT` | `/api/v1/shared-notes/{noteId}` | Save a note using parent revision and idempotency identifiers |
| `POST` | `/api/v1/synchronization` | Simulate reconciliation with reachable Android peers |
| `GET` | `/api/v1/admin/overview` | Synthetic household administration totals |

Mutating API requests require the CSRF token returned by the session endpoint. The React client handles this automatically.

## Security boundaries

- The web listener binds to loopback by default.
- The loopback filter rejects non-local requests even if the listener is accidentally rebound.
- LAN web access remains disabled until trusted HTTPS can be established without warning bypasses.
- No third-party scripts, fonts, analytics, advertisements, or remote household-session resources are loaded.
- Note content is not written to browser local storage, session storage, or IndexedDB.
- Password authentication is intentionally unavailable; the planned admin authentication mechanism is WebAuthn passkeys.
- Private-note content and metadata are outside the web companion's scope.

## Validation

The integrated production build is:

```bash
./mvnw clean package -DskipTests
```

The packaged application is created at:

```text
target/shared-notebook-0.1.0.jar
```

Unit tests are not required by the current product decision. Follow the [manual smoke checklist](docs/manual-smoke-checklist.md) when validating a slice.

## GitHub builds and releases

Every push and pull request runs `.github/workflows/build.yml`. The workflow scans tracked files for common credential formats, builds the complete application, creates a SHA-256 checksum, and uploads the JAR as a GitHub Actions artifact for 14 days.

GitHub Releases are created from semantic version tags. For example:

```bash
git tag -a v0.1.0 -m "SharedNoteBook v0.1.0"
git push origin v0.1.0
```

The release workflow builds from the tagged source and publishes these assets:

```text
shared-notebook-v0.1.0.jar
shared-notebook-v0.1.0.jar.sha256
```

Before pushing, run the repository secret check locally:

```bash
./scripts/check-for-secrets.sh
```

Keep database passwords, signing keys, master keys, certificate material, access tokens, and environment-specific configuration outside Git. Store CI-only values in GitHub Actions secrets. Enable GitHub secret scanning and push protection in the repository settings when available.

## Implementation roadmap

The planned slices are defined in detail in the product design:

1. Full-stack foundation with synthetic notes — current slice.
2. Root-admin passkey bootstrap and secure-origin verification.
3. Authenticated Android discovery and reconciliation.
4. Web-device identity, remembered acceptance, and global device management.
5. PostgreSQL-backed note listing, filtering, editing, and immutable revision saves.
6. Android propagation, outage recovery, and conflict resolution.
7. Revision/activity history, trash, purge, and private delivery.
8. Policy controls, delegated admins, device revocation, accessibility, and browser-matrix verification.

## Project documents

- [Product and application design](docs/web-design.md)
- [Web companion requirements](docs/web-requirements.md)
- [Manual smoke checklist](docs/manual-smoke-checklist.md)
