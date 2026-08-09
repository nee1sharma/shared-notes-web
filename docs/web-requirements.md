# SharedNoteBook Web Companion Requirements

**Companion specifications:** [Android requirements](requirements.md), [Web design](web-design.md)
**Document status:** Product baseline for the web companion
**Last updated:** 2026-08-09
**Platform:** Standards-based desktop and tablet web browsers

**Implementation repository:** this repository

## 1. Product definition

The SharedNoteBook web companion is hosted by the stateful Spring Boot application on the designated admin laptop. It lets a household member use the family's shared notebook from a browser without installing a separate client and without requiring an internet service.

The backend stores the complete shared household dataset in PostgreSQL, runs the discovery/registry service, commits browser edits, and reconciles them with registered Android peers. Android devices remain capable of editing and peer-synchronizing while the laptop backend is stopped.

The browser is not an independent note-holding peer and does not persist notebook content. The laptop backend is a durable full replica and control-plane authority, but it must not become a prerequisite for Android note use.

The web companion is also the household's exclusive administrative surface. Android devices may store, forward, validate, and apply signed administrative state, but the Android application must not display admin dashboards or initiate admin-only operations.

### 1.1 Delivery priorities

- **Primary:** Open the notebook on the admin laptop, view and edit shared notes, save durably to PostgreSQL, and show honest database and Android-propagation state.
- **Secondary:** Persistent web-device acceptance, member authorization, configurable approval, and admin connected-device management.

Secondary priority affects delivery order, not the production security boundary. Early primary-workflow prototypes may use synthetic notes, but a production build must not expose real household content before the acceptance and authorization requirements are complete.

## 2. Terminology

- **Web companion:** The browser-based SharedNoteBook interface.
- **Web device:** A browser profile on a laptop, desktop, or tablet that has a stable web-device identity.
- **Accepted web device:** A web device authorized to access the household under its current web-access policy.
- **New web device:** A browser profile that does not present a valid accepted web-device identity.
- **Admin laptop:** The root-admin machine running Spring Boot, PostgreSQL, discovery, and the web companion.
- **Web backend:** The stateful Spring Boot service that hosts the UI, authenticates sessions, stores the shared replica, runs discovery, and reconciles Android peers.
- **Web session:** One authenticated connection between an accepted web device and the laptop backend.
- **Connected device:** A device with a currently authenticated, non-expired peer or web session.
- **Remembered acceptance:** The rule that an accepted web device may reconnect without approval until it is blocked, revoked, expired by policy, or loses its browser credential.
- **Browser credential:** The device-bound identity material retained by the browser profile. It identifies the web device but does not contain note content.

## 3. Product principles

1. Core web access must work without an internet connection.
2. A normal browser visit must be sufficient; installing a PWA, extension, helper, or desktop application is not required.
3. A web device is accepted once and does not require approval for every later session.
4. Admins control whether new web devices require approval before first access.
5. Admins can see accepted, connected, blocked, and revoked web devices.
6. The browser does not become a full peer and does not persist notebook content in version 1.
7. Laptop edits are committed to PostgreSQL before Android propagation is reported.
8. A user must always be able to distinguish PostgreSQL commit state from household synchronization state.
9. Private Android notes remain unavailable to the web companion in version 1.
10. The experience should behave consistently across supported browsers; browser-specific details are an implementation concern, not a user decision.
11. Every privileged admin page and operation is available only in the web companion.
12. A member with the admin role receives no additional administrative UI or action in the Android application.

### 3.1 Engineering constraints

- The backend shall use Spring Boot 4.1 and support Java 21 or newer. The existing project currently targets Java 25.
- PostgreSQL is required, with versioned Flyway migrations.
- The frontend shall use React with TypeScript and a Vite-based build.
- Frontend and backend source shall live in this single repository.
- Spring Boot shall expose versioned authenticated JSON endpoints and serve the compiled React production assets.
- React and Java dependencies shall be pinned through their respective lock/build files; upgrades require compatibility verification rather than an unbounded `latest` dependency.
- Unit tests are not required for the web application. Manual acceptance verification is still required for security boundaries, permissions, device revocation, and save/synchronization behavior.
- The frontend shall be built and packaged from the same repository and served through the selected Java backend deployment model unless a later architecture decision explicitly changes this.

## 4. Roles and permissions

### 4.1 Member using the web companion

An authorized member can:

- View, search, create, and edit shared notes.
- View shared-note revisions retained in PostgreSQL.
- Restore an available revision as a new revision.
- Resolve a shared-note conflict.
- View the current database-commit and Android-propagation state.
- Rename their current web device, subject to household policy.
- End their current web session.

A member using the web companion cannot:

- View, create, edit, copy, convert, or delete private notes.
- Delete or move a shared note to private unless the member is an admin.
- Accept, block, or revoke another device unless the member is an admin.
- Treat an offline Android peer as proof that household data is lost.

### 4.2 Admin using the web companion

An authenticated admin has all member permissions and can additionally:

- View accepted and currently connected Android and web devices.
- Inspect a web device's member, display name, acceptance time, accepting authority, last-seen time, current connection, and status.
- Accept a pending web device when approval is required.
- Block, unblock, or revoke a web device.
- Configure the new-web-device acceptance policy.
- Configure web-session and web-triggered synchronization policies.
- Move shared notes to admin-managed trash, restore or permanently purge them, and initiate coordinated shared-to-private delivery to an eligible owned Android device.
- Register and remove their own passkeys, subject to preserving at least one usable root-admin passkey.

High-risk admin actions remain subject to reauthentication rules defined by the household security policy.

## 5. Functional requirements

### 5.1 Local web availability

- **WEB-FR-001:** The laptop backend shall start and stop the web companion together with the Spring Boot control plane.
- **WEB-FR-002:** The root-admin UI shall bind to loopback by default and shall not require an internet connection.
- **WEB-FR-003:** The backend shall provide the root-admin browser address and, when trusted LAN web access is enabled, a QR code or short local association code.
- **WEB-FR-004:** A direct-address fallback shall be available when a friendly local hostname cannot be resolved.
- **WEB-FR-005:** A web session shall require the laptop backend and PostgreSQL; it shall not require an Android gateway to be online.
- **WEB-FR-006:** The backend shall visibly indicate web sessions, PostgreSQL health, discovery state, and Android reconciliation state.
- **WEB-FR-007:** Disabling web access shall stop new connections and terminate active web sessions after protecting already committed edits.
- **WEB-FR-008:** The web companion shall explain that Android devices may be offline while the PostgreSQL copy remains usable, and that propagation resumes when peers reconnect.
- **WEB-FR-009:** The web companion shall not require installation as a PWA, browser extension, helper application, or native desktop application.

### 5.2 Web-device identity and acceptance

- **WEB-FR-020:** A new browser profile shall create a globally unique web-device identity and browser credential before household data is disclosed.
- **WEB-FR-021:** Initial enrollment shall request a member identity and an editable web-device display name.
- **WEB-FR-022:** A web-device display name may be suggested from non-sensitive browser and operating-system information.
- **WEB-FR-023:** The household shall have a configurable new-web-device policy with at least `Approval required` and `Allow on home LAN` modes. `Approval required` shall be the default.
- **WEB-FR-024:** `Approval required` shall prevent a new web device from receiving shared-note data until an admin accepts it.
- **WEB-FR-025:** `Allow on home LAN` may automatically accept a new web device only while the laptop backend is offering enrollment on the stored home-LAN profile over trusted HTTPS.
- **WEB-FR-026:** An automatically accepted web device shall be visible to admins and shall produce the same acceptance notification and audit visibility as an admin-accepted device.
- **WEB-FR-027:** Once accepted, a web device shall reconnect without repeated approval while its credential remains valid and its status remains accepted.
- **WEB-FR-028:** Clearing browser site data, using a different browser profile, or losing the browser credential shall make that profile appear as a new web device.
- **WEB-FR-029:** Acceptance shall associate the web device with exactly one household member.
- **WEB-FR-030:** Typing another member's display name shall not grant that member's identity or admin role.
- **WEB-FR-031:** Existing-member association shall require proof from an accepted device belonging to that member or an authenticated admin action.
- **WEB-FR-032:** A rejected, blocked, revoked, or cryptographically invalid web device shall not receive household note data.
- **WEB-FR-033:** The active web companion shall show when a new web device is accepted automatically or requests approval. Closed-browser notifications are not required.
- **WEB-FR-034:** Admins shall be able to disable all new web-device enrollment without disconnecting already accepted web devices unless separately configured.

### 5.3 Repeat access and sessions

- **WEB-FR-040:** An accepted web device shall authenticate using its retained browser credential when starting a later session.
- **WEB-FR-041:** Repeat access shall not require an admin to approve the device again.
- **WEB-FR-042:** The household may require a member unlock step or session reauthentication without converting it into repeated device approval.
- **WEB-FR-043:** A web session shall expire after the configured idle period.
- **WEB-FR-044:** The browser and backend shall both provide an explicit `Disconnect` action.
- **WEB-FR-045:** A disconnected web device shall remain accepted unless it is blocked, revoked, explicitly forgotten, or expired by policy.
- **WEB-FR-046:** Concurrent sessions from the same web-device identity shall be shown separately or rejected according to household policy.
- **WEB-FR-047:** A browser private/incognito profile shall be treated as a new web device and may lose acceptance when that profile closes.

### 5.4 Shared notes

- **WEB-FR-060:** An authorized web member shall be able to list, search, sort, open, create, and edit shared notes.
- **WEB-FR-061:** The web companion shall not expose private-note identifiers, titles, previews, content, or activity.
- **WEB-FR-062:** The shared-note list shall show title, preview, last modification, last editor when known, conflict state, PostgreSQL commit state, and Android-propagation state.
- **WEB-FR-063:** The editor shall support at least a title and plain-text body.
- **WEB-FR-064:** A web save shall include the parent revision from which the edit was made.
- **WEB-FR-065:** A successful web save shall first commit a new immutable revision transactionally in PostgreSQL.
- **WEB-FR-066:** The browser shall show `Saved to <device name>` only after PostgreSQL acknowledges the durable commit.
- **WEB-FR-067:** The browser shall show `Synchronized` only according to the same cautious peer-to-peer semantics used by the Android application.
- **WEB-FR-068:** An unchanged save shall not create a duplicate revision.
- **WEB-FR-069:** Only an authenticated admin shall move a shared note to trash, restore it, permanently purge it, or initiate coordinated private delivery to an owned Android device.
- **WEB-FR-070:** Unsaved browser edits shall be retained in page memory during a recoverable connection interruption.
- **WEB-FR-071:** The browser shall warn before closing or navigating away while edits have not been acknowledged by PostgreSQL.
- **WEB-FR-072:** Version 1 shall not persist note bodies, titles, previews, or revision content in browser storage after the web session ends.
- **WEB-FR-073:** The shared-note home shall initially return and render at most 20 matching notes.
- **WEB-FR-074:** If another page exists, the UI shall show `Show more` and append the next 20 matching notes.
- **WEB-FR-075:** Search shall support explicit filters for note title, creator, last editor, modified-date range, and conflict state.
- **WEB-FR-076:** Changing search text, filters, or sort order shall reset pagination to the first 20 results.
- **WEB-FR-077:** The backend shall return an opaque next-page cursor rather than expose database offsets or internal sort keys.
- **WEB-FR-078:** Search results shall include all authorized current shared notes that match, not only the 20 rows already loaded in the browser.
- **WEB-FR-079:** Version 1 shall not persist a plaintext title or body search index; filtering may decrypt authorized shared-note summaries transiently in backend memory.

### 5.5 Revisions and conflicts

- **WEB-FR-080:** The web companion shall display shared-note revisions retained in PostgreSQL and eligible for the member.
- **WEB-FR-081:** Restoring a revision shall create a new current revision rather than rewrite history.
- **WEB-FR-082:** A save based on an outdated parent revision shall not silently overwrite PostgreSQL's current revision candidates.
- **WEB-FR-083:** Concurrent candidates shall be preserved and surfaced as a conflict.
- **WEB-FR-084:** An authorized member shall be able to choose either version or combine content manually.
- **WEB-FR-085:** Conflict resolution shall create a new revision naming all resolved parents.

### 5.6 Web-triggered synchronization

- **WEB-FR-100:** Saving from the browser and propagating to household peers shall be treated as distinct states.
- **WEB-FR-101:** The default web-triggered synchronization mode shall attempt Android reconciliation after each successful PostgreSQL commit.
- **WEB-FR-102:** Admins shall be able to configure `After each save`, `Periodic while connected`, or `Manual only` synchronization.
- **WEB-FR-103:** Periodic mode shall expose and validate its configured interval.
- **WEB-FR-104:** Manual mode shall provide a clear `Sync now` action without disabling durable PostgreSQL saves.
- **WEB-FR-105:** A policy change shall not cause acknowledged PostgreSQL saves to be lost.
- **WEB-FR-106:** The web companion shall show PostgreSQL health, globally connected Android devices, pending propagation, conflicts, failures, and last successful reconciliation.
- **WEB-FR-107:** The UI shall not claim that every household device is current when offline peers may have undiscovered work.

### 5.7 Connected-device administration

- **WEB-FR-120:** Admin device management shall include both Android devices and accepted web devices.
- **WEB-FR-121:** Each web-device record shall show member, display name, stable short identifier, type `Web`, acceptance method, acceptance time, accepting admin when applicable, last seen, and accepted/connected/blocked/revoked state.
- **WEB-FR-122:** A connected web-device record shall show the admin laptop backend and session start time.
- **WEB-FR-123:** The admin overview shall show the current number of connected Android peers and connected web devices separately.
- **WEB-FR-124:** Blocking a web device shall terminate its active sessions and prevent reconnection until unblocked.
- **WEB-FR-125:** Revoking a web device shall terminate active sessions, invalidate its credential, and require new enrollment before later access.
- **WEB-FR-126:** Revocation shall not claim to erase information a user already saw, copied, printed, or manually saved.
- **WEB-FR-127:** Admins shall be able to terminate one active web session without revoking the accepted web device.
- **WEB-FR-128:** Device status shall be eventually consistent and shall include a latest-known or last-seen time.

### 5.8 Browser compatibility and accessibility

- **WEB-FR-140:** The core workflow shall support current stable versions of Chrome, Edge, Firefox, and Safari on desktop-class devices.
- **WEB-FR-141:** Users shall not be required to choose a browser-specific product mode.
- **WEB-FR-142:** When friendly-hostname resolution or local-network permission behavior differs, the web companion shall offer understandable fallback instructions.
- **WEB-FR-143:** Note creation, editing, saving, reconnecting, and conflict handling shall remain usable with keyboard-only navigation.
- **WEB-FR-144:** Status shall be conveyed through text and semantics, not color alone.
- **WEB-FR-145:** Layout shall remain functional at common laptop, tablet, and desktop viewport sizes.

### 5.9 Admin-configurable household settings

The web admin interface shall expose a deliberately limited set of household-wide configuration. Security invariants are not configurable.

#### Required for version 1

- **WEB-FR-150:** Admins shall be able to enable or disable new Android-device enrollment without disconnecting already registered devices.
- **WEB-FR-151:** Admins shall be able to choose Android enrollment mode: `Open on home LAN` or `Admin approval required`.
- **WEB-FR-152:** Admins shall be able to enable or disable new web-device enrollment without disconnecting already accepted web devices.
- **WEB-FR-153:** Admins shall be able to choose web-device acceptance mode: `Admin approval required` or `Automatically accept on home LAN`.
- **WEB-FR-154:** Admins shall be able to configure the web-session idle timeout within safe minimum and maximum bounds.
- **WEB-FR-155:** Admins shall be able to configure whether one web device may have one session or multiple concurrent sessions.
- **WEB-FR-156:** Admins shall be able to select the web-triggered sync mode: `After each save`, `Periodic while connected`, or `Manual only`.
- **WEB-FR-157:** Admins shall be able to configure the periodic synchronization interval when periodic mode is selected.
- **WEB-FR-158:** Admins shall be able to configure the number of retained note revisions; the default is 5.
- **WEB-FR-159:** Admins shall be able to configure admin activity retention in days; the default is 100.
- **WEB-FR-160:** Admins shall be able to grant or remove the admin role while the household always retains at least one admin.
- **WEB-FR-161:** Admins shall be able to configure the admin reauthentication interval, while destructive and key-management actions always require fresh reauthentication.
- **WEB-FR-162:** Admins shall be able to choose whether non-local web-device access is disabled or allowed through a trusted HTTPS listener on the home LAN. The safe default is disabled.
- **WEB-FR-163:** All configuration keys, defaults, and safe bounds shall be documented in `application.yaml`.
- **WEB-FR-164:** Admin changes shall persist in PostgreSQL as runtime overrides; YAML shall remain the startup/default definition.
- **WEB-FR-165:** Activity retention shall default to 100 days, note revisions to 5, trash retention to 30 days, and session idle timeout to 30 minutes.
- **WEB-FR-166:** Discovery heartbeat shall default to 15 seconds and offline detection to 45 seconds.
- **WEB-FR-167:** Secrets shall be environment/external-secret placeholders in YAML and shall never have committed literal defaults.
- **WEB-FR-168:** The admin configuration page shall show effective value and source without revealing secrets.
- **WEB-FR-169:** Invalid values outside documented bounds shall be rejected rather than silently coerced.
- **WEB-FR-172:** Admins shall configure shared-trash retention; the default is 30 days.

#### Recommended after version 1

- **WEB-FR-170:** Admins may configure an optional acceptance expiry for web devices that have not been seen for a specified period.
- **WEB-FR-171:** Admins may configure notification categories for pending enrollment, newly accepted devices, revocation, repeated sync failure, and conflicts.

#### Fixed and not configurable

- Private-note content and metadata never appear in web administration or household audit activity.
- Network traffic and stored note content remain encrypted.
- Conflicting revisions are preserved and never silently overwritten.
- A revoked device cannot receive future household updates.
- At least one admin must remain.
- Members cannot gain admin rights by typing an admin's name.
- Android never exposes admin pages or initiates privileged admin actions.
- Audit integrity fields, required security events, and destructive-action confirmation cannot be disabled.

## 6. Web activity-event catalog

The following events extend the Android activity catalog. They contain no note bodies or private-note metadata.

| Event type | Created when |
|---|---|
| `WEB_DEVICE_APPROVAL_REQUESTED` | A new web device enters the admin-approval queue. |
| `WEB_DEVICE_ACCEPTED` | An admin or home-LAN policy accepts a web device. |
| `WEB_DEVICE_RENAMED` | An accepted web device's display name changes. |
| `WEB_DEVICE_BLOCKED` | An admin blocks an accepted web device. |
| `WEB_DEVICE_UNBLOCKED` | An admin unblocks a web device. |
| `WEB_DEVICE_REVOKED` | An admin permanently invalidates a web-device credential. |
| `WEB_SESSION_STARTED` | An accepted web device establishes an authenticated laptop-backend session. |
| `WEB_SESSION_ENDED` | A web session disconnects, expires, is terminated, or fails. |
| `WEB_ACCESS_POLICY_CHANGED` | An admin changes enrollment, timeout, or synchronization policy. |
| `ADMIN_PASSKEY_REGISTERED` | An admin registers a passkey, including root-admin bootstrap. |
| `ADMIN_PASSKEY_REMOVED` | An admin removes or revokes a passkey. |
| `ADMIN_PASSKEY_REAUTHENTICATED` | A fresh passkey assertion authorizes a sensitive operation. |

Shared-note actions performed through the web companion continue to create the existing shared-note activity types. Their origin metadata identifies the acting web device and laptop backend.

## 7. Security and privacy requirements

- **WEB-SEC-001:** No shared-note data shall be disclosed before web-device acceptance and session authentication complete.
- **WEB-SEC-002:** The root-admin UI shall be loopback-only by default. Any LAN web traffic carrying household data shall require trusted HTTPS, integrity, replay resistance, and backend authentication.
- **WEB-SEC-003:** The browser credential shall be scoped to one browser profile and household.
- **WEB-SEC-004:** Browser storage shall contain only the minimum identity and preference data needed for remembered acceptance; note content shall not be persistently cached in version 1.
- **WEB-SEC-005:** Web pages shall not load third-party scripts, fonts, analytics, advertisements, or remote resources during a household session.
- **WEB-SEC-006:** Session authorization shall be bound to the accepted web-device identity rather than to an IP address or typed display name.
- **WEB-SEC-007:** Acceptance codes, session secrets, cryptographic material, optional email addresses, and note content shall not appear in URLs, browser history, logs, diagnostics, or referrer headers.
- **WEB-SEC-008:** Blocking or revocation shall be enforced before further note requests or saves are accepted.
- **WEB-SEC-009:** Admin-only pages and actions shall verify the current member's synchronized admin role.
- **WEB-SEC-010:** The backend shall rate-limit enrollment, authentication, reconnect, discovery, and heartbeat attempts.
- **WEB-SEC-011:** A release shall not rely on unauthenticated HTTP delivery of security-sensitive JavaScript. The secure-origin approach must be validated on all supported browsers before real household content is enabled.
- **WEB-SEC-012:** Losing or clearing browser credentials shall not expose household keys; it shall only require the web device to enroll again.
- **WEB-SEC-013:** Admin authentication shall use WebAuthn passkeys verified by Spring Security; version 1 shall provide no password authentication or password fallback.
- **WEB-SEC-014:** Passkey registration shall require an already authenticated root-admin bootstrap or a fresh assertion from an existing admin passkey.
- **WEB-SEC-015:** Passkey assertions shall require user verification and shall validate relying-party ID, allowed origin, challenge, signature counter behavior, credential status, and replay protection.
- **WEB-SEC-016:** Destructive, role-changing, passkey-management, and key-management actions shall require a fresh passkey assertion when the configured reauthentication age is exceeded.
- **WEB-SEC-017:** PostgreSQL shall store only the public WebAuthn credential material and credential metadata needed for verification; authenticator private keys never enter the application.

## 8. Core data requirements

The web companion adds these conceptual records:

- `Device`: device ID, household ID, display name, type (`LAPTOP` or `MOBILE`), platform (`WEB`, `ANDROID`, or `IPHONE`), public identity, acceptance method, accepted by, acceptance time, last seen, status, and credential generation.
- `WebSession`: session ID, web-device ID, backend node ID, start time, last activity, expiry time, end reason, and authenticated protocol version.
- `WebAccessPolicy`: web access enabled, new-device approval mode, enrollment enabled, idle timeout, concurrent-session rule, synchronization mode, periodic interval, version, and changing admin.
- `AdminPasskeyCredential`: admin member ID, WebAuthn credential ID, public key, signature-counter state, transports, friendly name, creation/last-used time, and active/revoked status.

PostgreSQL persistently stores `Note`, `NoteRevision`, `ActivityEvent`, `SyncReceipt`, device-policy, trash, tombstone, discovery-presence, and role records. A browser profile does not own a separate note repository.

## 9. Non-functional requirements

- **WEB-NFR-001:** After the local laptop backend starts, core use shall not depend on DNS outside the LAN, cloud APIs, internet signaling, analytics, or a remote data service.
- **WEB-NFR-002:** Ordinary note-list and editor interactions should feel immediate on a healthy LAN.
- **WEB-NFR-003:** A temporary network interruption shall not silently discard an in-memory edit.
- **WEB-NFR-004:** Repeated requests and reconnects shall be idempotent and shall not duplicate revisions or activity events.
- **WEB-NFR-005:** The backend shall apply bounded session, request, query, and memory limits.
- **WEB-NFR-006:** User-facing errors shall distinguish backend stopped, PostgreSQL unavailable, authorization required, device blocked, session expired, save failed, propagation pending, conflict, and browser/network restrictions.
- **WEB-NFR-007:** Timestamps shall be stored unambiguously and displayed in the browser's locale.
- **WEB-NFR-008:** Core behavior shall be verified against a maintained browser-support matrix rather than assumed from one browser engine.

## 10. Known constraints and trust boundaries

- The laptop backend and PostgreSQL must be running for every web/admin session. Android notes continue independently when they are stopped.
- Remembered acceptance depends on browser-profile storage. Clearing site data, changing profiles, or privacy-mode cleanup may remove the credential.
- A browser profile is not equivalent to hardware-backed Android Keystore identity. A compromised browser profile or operating-system account may expose its accepted credential.
- Friendly `.local` names, local-network permissions, certificate handling, and private-address access vary between browsers and operating systems.
- Version 1 resolves its trusted-origin requirement by serving the root-admin UI only on the laptop's loopback `localhost` origin. Non-local browser access stays disabled because raw LAN HTTP is unsafe and untrusted self-signed HTTPS is not an acceptable substitute.
- A web device is `connected` only while an authenticated session is live. `Accepted` means it may reconnect without new approval.
- The discovery service's global presence is current only while the laptop backend is running and devices are sending authenticated heartbeats.
- Revoking a web device prevents future access but cannot erase information already observed or copied by its user.
- Version 1 has no password fallback or automated passkey/root-key recovery. Losing every usable root-admin passkey or the laptop key material may permanently remove administrative access.

## 11. Version 1 pages

1. Backend unavailable / startup and connection instructions.
2. New web-device enrollment or pending approval.
3. Passkey setup, admin authentication, or session unlock when required by policy.
4. Shared-notes home.
5. Shared-note editor.
6. Revision history.
7. Conflict resolution.
8. Connection and synchronization status.
9. Admin overview.
10. Admin connected-device management.
11. Admin web-access policy.
12. Admin activity history with web filters.
13. Admin shared-note trash, restore/purge, and private-delivery jobs.

## 12. Explicitly out of scope for web version 1

- Browser installation as a PWA or extension.
- Web/admin use while the laptop backend or PostgreSQL is stopped.
- Persistent browser storage of private or shared note content.
- Private-note access or creation.
- Cloud storage, cloud accounts, internet signaling, or remote relay synchronization.
- Browser-to-browser synchronization outside the PostgreSQL control plane.
- Real-time character-by-character collaboration.
- Attachments, drawing, audio, video, and rich-text editing.
- Guaranteed support for obsolete browsers or embedded web views.
- Password-based admin authentication or passkey password fallback.
- Automated passkey, root-key, or PostgreSQL disaster recovery.

## 13. Release acceptance summary

The web companion is acceptable when the admin laptop starts Spring Boot and PostgreSQL, serves the compiled React application, and authenticates the root admin with a WebAuthn passkey without a password fallback. An authenticated browser must create and update shared notes without internet access, initially list 20 matching notes, append 20 through `Show more`, apply the defined filters across all authorized shared notes, and clearly distinguish PostgreSQL commit from Android propagation. The backend must reconcile peer changes after downtime, show global presence from discovery heartbeats, and preserve conflict semantics.

Admins must manage registered devices, roles, configuration, activity retention, trash, restore/purge, and coordinated shared-to-private delivery. The browser must not persist note content or expose private-note information. The loopback admin origin must be verified across supported browsers; any future LAN listener requires trusted HTTPS before real content is enabled.
