# SharedNoteBook Web Companion

SharedNoteBook is a local-first household notebook hosted on the designated laptop. Household members use the browser for shared notes, while administration is available only in the web companion. Android continues to own private notes and can synchronize shared notes directly with registered peers when the laptop is unavailable.

This repository contains the Java 21 backend and React frontend. It contains no sample household, fake mobile-device records, or synthetic note profile. Device and member names shown by the web application must come from configured host identity or authenticated SharedNoteBook Android registrations.

## Current implementation

- Spring Boot application with loopback-only web access, CSRF protection, strict browser headers, and PostgreSQL/Flyway configuration.
- React and TypeScript application for shared notes, connection state, and web-only administration.
- Runtime session status using the configured member and host-device identity.
- Honest empty states until a registered Android app connects and reconciles household data.
- Device presentation prepared for registered member name, editable device name, app name, hardware model, platform, stable identifier, connection state, and last-seen time.
- Integrated Maven frontend build.

Android discovery, authenticated registration/heartbeat, PostgreSQL note reconciliation, passkey bootstrap, and persistent admin operations remain implementation work. Until Android reconciliation is complete, the UI shows no Android devices or shared notes instead of invented values.

## Technology

| Area | Technology |
|---|---|
| Backend | Spring Boot 4.1, Java 21 |
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
│   ├── application.yaml            Runtime configuration and safe bounds
│   └── db/migration/               Flyway PostgreSQL migrations
├── docs/                            Product specifications and acceptance checklist
├── pom.xml                          Backend and integrated frontend build
└── mvnw                             Maven Wrapper
```

## Required configuration

With no identity variables, the application derives the current operating-system username and laptop hostname. Override them when the registered household names differ. Database and cryptographic variables are reserved for the persistence and Android-reconciliation implementation:

```bash
export SNB_DB_URL='jdbc:postgresql://localhost:5432/shared_notebook'
export SNB_DB_USER='shared_notebook'
export SNB_DB_PASSWORD='replace-with-an-external-secret'
export SNB_NODE_ID='stable-id-for-this-laptop-service'
export SNB_MEMBER_NAME='Your registered member name'
export SNB_DEVICE_NAME='Your laptop name'
export SNB_MASTER_KEY_REF='replace-with-an-os-keystore-reference'
export SNB_SIGNING_KEY_REF='replace-with-an-os-keystore-reference'
export SNB_HOME_LAN_PROFILE_REF='replace-with-the-trusted-network-profile-reference'
```

Do not commit database passwords, signing keys, master keys, certificates, tokens, or household identity secrets.

## Build and run

Build the complete application without unit tests:

```bash
./mvnw clean package -DskipTests
```

Run it directly:

```bash
java -jar target/shared-notebook-0.1.0.jar
```

Open [http://localhost:8080](http://localhost:8080) on the host laptop.

The current runtime starts without PostgreSQL because persistent note and Android reconciliation services are not connected yet. It reports that state honestly and disables note creation. Database auto-configuration will be enabled when those real services replace the current unavailable state.

For frontend hot reload, run the backend normally, then:

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

## Device-name contract

The administration UI must never generate friendly-looking Android devices. Each displayed row is built from authenticated registry and presence data:

- Registered member name.
- User-editable device name.
- Application name, such as `SharedNoteBook Android`.
- Android hardware manufacturer/model.
- Platform and device type.
- Stable short device identifier.
- Accepted, connected, offline, blocked, revoked, or pending status.
- Connection start and last authenticated heartbeat.

A missing heartbeat produces `Offline` with a last-seen time. It must not be shown as connected.

## Security boundaries

- The browser listener binds to loopback by default.
- LAN browser access remains disabled until trusted HTTPS is configured.
- No third-party scripts, fonts, analytics, advertisements, or remote household-session resources are loaded.
- Note content is not written to browser local storage, session storage, or IndexedDB.
- Private-note content and metadata are outside the web companion's scope.
- Android registrations and heartbeats must be authenticated before their names or presence are displayed.

## Validation

Unit tests are not required by the current product decision. Follow the [manual acceptance checklist](docs/manual-acceptance-checklist.md) and run:

```bash
./mvnw clean package -DskipTests
./scripts/check-for-secrets.sh
```

## Project documents

- [Product and application design](docs/web-design.md)
- [Web companion requirements](docs/web-requirements.md)
- [Manual acceptance checklist](docs/manual-acceptance-checklist.md)
