# Web companion manual acceptance checklist

This checklist validates the real runtime configuration. It must not use invented household members, sample notes, or fabricated Android devices.

## Startup

1. Supply PostgreSQL, member, host-device, and external-secret configuration described in the README.
2. Build with `./mvnw clean package -DskipTests`.
3. Start the packaged application without an alternate sample-data profile.
4. Open `http://localhost:8080` on the host laptop.
5. Confirm the displayed member and laptop names match `NETBOOK_MEMBER_NAME` and `NETBOOK_DEVICE_NAME`.

## Access and privacy

- Confirm non-loopback browser requests are rejected, except for `/api/v1/mobile/**`.
- Confirm an Android registration, heartbeat, sync, or device-list request without its bearer token returns `401`.
- Confirm API responses use `Cache-Control: no-store`.
- Confirm CSP, frame, referrer, and permissions headers are present.
- Confirm no third-party script, font, analytics, advertisement, or remote image is requested.
- Confirm note content is absent from localStorage, sessionStorage, IndexedDB, URLs, logs, and browser history.

## Device accuracy

- With no authenticated Android heartbeat, confirm the admin page shows zero connected mobile devices.
- Confirm the page never displays sample phone names or invented connection activity.
- Register an Android device and confirm its row shows the submitted member, device name, `NetBook Android`, model, stable short ID, and current status.
- Stop Android heartbeats and confirm the device becomes `Offline` after the configured 20-minute threshold instead of remaining `Connected`.
- Reconnect the same Android identity and confirm the existing row updates rather than creating a duplicate.
- Block and revoke a device and confirm its status and ability to reconnect change correctly.

## Shared notes

- Create a shared note in the browser and confirm it is committed to PostgreSQL before the success message is returned.
- Register Android device A, create a shared note on it, and confirm it appears in the browser after the one-time sync completes.
- Register Android device B and confirm it receives notes created by the browser and Android device A after synchronization.
- Confirm private Android notes never appear in the browser or on Android device B.
- Make divergent edits while devices are offline and confirm the browser marks the resulting current note as conflicted. The current release does not yet provide a conflict-resolution editor.

## Administration

- Confirm admin pages and privileged actions exist only in the web application.
- Confirm Android has no admin route, menu, screen, or privileged action.
- Confirm connected counts include only live authenticated sessions.
- Confirm offline devices show their last authenticated heartbeat.

## Responsive and keyboard pass

- Validate desktop, tablet, and narrow-window layouts without horizontal page scrolling.
- Complete navigation and every enabled operation using a keyboard.
- Confirm status meaning is communicated with text and iconography, not color alone.
