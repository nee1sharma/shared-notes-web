# Web companion — slice 1 manual smoke checklist

This checklist covers the first implementation slice defined in `web-design.md`. It intentionally uses synthetic household notes through the `prototype` Spring profile. Do not use this profile with real household content.

## Start the prototype

1. Build the application with `./mvnw clean package -DskipTests`.
2. Start it with `./mvnw spring-boot:run -Dspring-boot.run.profiles=prototype -Dskip.frontend=true`.
3. Open `http://localhost:8080` in a current desktop browser.
4. Confirm the page identifies its data as `Prototype data`.

For frontend hot reload, start the Spring prototype as above, then run `pnpm dev` from `frontend/` and open the printed localhost address. Vite proxies `/api` to Spring Boot.

## Application shell and access boundary

- Confirm the site opens only through the loopback listener and shows the SharedNoteBook shell.
- Confirm Notes, Connection, and Admin navigation is keyboard reachable and has a visible focus indicator.
- Stop Spring Boot, reload the frontend, and confirm the startup/help screen appears without a household name or note preview.
- Confirm API responses have `Cache-Control: no-store` and the page has CSP, frame, referrer, and permissions headers.
- Confirm no third-party scripts, fonts, analytics, or remote images are requested.

## Shared notes

- Confirm the initial list contains at most 20 rows and is labeled `Shared notes`; no Private filter exists.
- Search by title, creator, and last editor. Confirm changing a search field resets the result list.
- Filter by conflict state and modified-date range, then clear the filters.
- Change sort order between recently modified, recently created, and title.
- Select a note and confirm its title, body, revision, editor, commit state, and Android propagation state appear.
- Create a note, edit its title and body, and save it.
- Confirm `Saved to laptop` appears only after the API responds and propagation remains a separate status.
- Edit a saved note and try to leave or close the tab before saving; confirm the browser warns.
- Confirm note text is not written to localStorage, sessionStorage, or IndexedDB.

## Connection and admin previews

- Open Connection and confirm browser session, PostgreSQL commit, and Android propagation appear as three distinct layers.
- Use `Sync now`; confirm pending synthetic changes become synchronized with reachable peers.
- Confirm the wording is `No pending changes known to the laptop`, not a claim that every offline device is current.
- Open Admin and confirm Android devices and web sessions have separate counts.
- Confirm pending approval, conflicts, connected devices, and latest-known activity are represented.
- Confirm the page explains that passkeys, remembered acceptance, durable PostgreSQL notes, and Android reconciliation are later protected slices.

## Responsive and accessibility pass

- At a tablet width, confirm the layout remains readable without horizontal page scrolling.
- At a phone/narrow-window width, confirm the note list and editor become separate route views with an explicit back action.
- Complete search, note selection, edit, save, sync, and navigation with a keyboard only.
- Confirm status meaning is conveyed with text and iconography, not color alone.
- Enable reduced motion in the operating system and confirm loaders/transitions do not depend on motion.

## Production-profile readiness

- Do not start the default profile until PostgreSQL and the required `SNB_*` secret references are supplied.
- Confirm Flyway discovers `V1__initial_control_plane.sql` on a disposable PostgreSQL database.
- Confirm the production listener remains bound to `127.0.0.1` and LAN web access remains disabled.
