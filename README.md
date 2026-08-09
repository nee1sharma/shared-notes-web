# NetBook Web Companion

NetBook is a local-first household notebook for people on the same network, hosted on the designated laptop. Household members use the browser for shared notes, while administration is available only in the web companion. Android continues to own private notes and can synchronize shared notes directly with registered peers when the laptop is unavailable.

This repository contains the Java 21 backend and React frontend. It contains no sample household, fake mobile-device records, or synthetic note profile. Device and member names shown by the web application must come from configured host identity or authenticated NetBook Android registrations.

## Current implementation

- PostgreSQL/Flyway-backed household, device, presence, note, revision, and sync state.
- Browser shared-note list, create, edit, revision history, optimistic conflict detection, and connection/admin views.
- mDNS/DNS-SD advertisement of the Android control-plane endpoint on `_netbook._tcp`.
- Android open-home-LAN registration, bearer-token authenticated heartbeats, registry-backed device presentation, and upload/download shared-note synchronization.
- AES-256-GCM encryption for shared-note payloads and display names at rest. The required key is supplied outside the repository.
- Integrated Maven frontend build.

Private Android notes never leave the device. Shared-note propagation is performed when a registered Android app saves a shared note, starts, or runs its periodic heartbeat. The current conflict marker preserves the latest revision and flags a divergent parent revision; a conflict-resolution editor remains future work.

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
netbook/
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

With no identity variables, the application derives the current operating-system username and laptop hostname. Override them when the registered household names differ. The service will not start without PostgreSQL and `NETBOOK_MASTER_KEY`. Generate the key once and retain it in your operating-system secret store or deployment secret manager:

```bash
export NETBOOK_DB_URL='jdbc:postgresql://localhost:5432/netbook'
export NETBOOK_DB_USER='netbook'
export NETBOOK_DB_PASSWORD='replace-with-an-external-secret'
export NETBOOK_MASTER_KEY="$(openssl rand -base64 32)"
export NETBOOK_NODE_ID='stable-id-for-this-laptop-service'
export NETBOOK_MEMBER_NAME='Your registered member name'
export NETBOOK_DEVICE_NAME='Your laptop name'
export NETBOOK_SIGNING_KEY_REF='replace-with-an-os-keystore-reference'
export NETBOOK_HOME_LAN_PROFILE_REF='replace-with-the-trusted-network-profile-reference'
```

Do not commit database passwords, signing keys, master keys, certificates, tokens, or household identity secrets.

## Build and run

Build the complete application without unit tests:

```bash
./mvnw clean package -DskipTests
```

Run it directly:

```bash
java -jar target/netbook-0.1.0.jar
```

Open [http://localhost:8080](http://localhost:8080) on the host laptop.

At startup Flyway creates or upgrades the schema and the service creates the local root-admin household identity. Open the browser only on the host laptop at [http://localhost:8080](http://localhost:8080), then use **Find or Join Household** in Android to discover and register with the advertised service.

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
- Application name, such as `NetBook Android`.
- Android hardware manufacturer/model.
- Platform and device type.
- Stable short device identifier.
- Accepted, connected, offline, blocked, revoked, or pending status.
- Connection start and last authenticated heartbeat.

A missing heartbeat produces `Offline` with a last-seen time. It must not be shown as connected.

## Security boundaries

- The browser/admin interface remains loopback-only. Requests from another LAN device are rejected by the loopback filter.
- The authenticated Android API shares the process but is the only API path permitted from the LAN.
- Android transport is currently HTTP on the trusted home LAN, with a per-installation bearer token after registration. This is an implementation limitation: deploy a TLS-terminating mobile endpoint before using an untrusted network.
- No third-party scripts, fonts, analytics, advertisements, or remote household-session resources are loaded.
- Note content is not written to browser local storage, session storage, or IndexedDB.
- Private-note content and metadata are outside the web companion's scope.
- Android heartbeats, device lists, and synchronization require the registration-issued bearer token. Open home-LAN registration is intentionally unauthenticated and should be disabled or replaced with an approval/key-exchange flow for higher-risk networks.

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
