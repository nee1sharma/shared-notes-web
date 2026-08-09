package com.histudio.web.sharednotebook.prototype;

import static com.histudio.web.sharednotebook.prototype.PrototypeModels.AdminOverview;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.CreateNoteRequest;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.DeviceView;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.NoteDetail;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.NotePage;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.NoteSummary;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.RevisionView;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SaveNoteRequest;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SaveResult;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SyncResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "shared-notebook.prototype-mode", havingValue = "true")
public class PrototypeNotebookService {

	private static final int MAX_PAGE_SIZE = 20;
	private static final String CURRENT_MEMBER = "Household owner";
	private static final String CURRENT_DEVICE = "hitstudio";
	private static final String LAPTOP = "LAPTOP";
	private static final String MOBILE = "MOBILE";
	private static final String WEB = "WEB";
	private static final String ANDROID = "ANDROID";
	private static final String IPHONE = "IPHONE";

	private final Map<UUID, MutableNote> notes = new LinkedHashMap<>();
	private final Map<String, CursorState> cursors = new ConcurrentHashMap<>();
	private final Map<UUID, SaveResult> idempotentResults = new ConcurrentHashMap<>();
	private Instant lastSynchronizedAt = Instant.now().minus(Duration.ofMinutes(4));

	public PrototypeNotebookService() {
		seedNotes();
	}

	public synchronized NotePage list(
			String query,
			String searchField,
			Boolean conflict,
			Instant modifiedFrom,
			Instant modifiedTo,
			String sort,
			int requestedLimit,
			String cursor) {
		String normalizedQuery = normalize(query);
		String normalizedField = normalizeEnum(searchField, "TITLE");
		String normalizedSort = normalizeEnum(sort, "MODIFIED_DESC");
		String signature = String.join("|",
			normalizedQuery,
			normalizedField,
			String.valueOf(conflict),
			String.valueOf(modifiedFrom),
			String.valueOf(modifiedTo),
			normalizedSort);

		int start = 0;
		if (cursor != null && !cursor.isBlank()) {
			CursorState state = cursors.remove(cursor);
			if (state == null || !state.signature().equals(signature)) {
				throw new InvalidCursorException();
			}
			start = state.start();
		}

		List<MutableNote> matches = notes.values().stream()
			.filter(note -> matchesQuery(note, normalizedQuery, normalizedField))
			.filter(note -> conflict == null || note.conflict == conflict)
			.filter(note -> modifiedFrom == null || !note.modifiedAt.isBefore(modifiedFrom))
			.filter(note -> modifiedTo == null || !note.modifiedAt.isAfter(modifiedTo))
			.sorted(noteComparator(normalizedSort))
			.toList();

		int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
		int end = Math.min(start + limit, matches.size());
		List<NoteSummary> items = start >= matches.size()
			? List.of()
			: matches.subList(start, end).stream().map(this::toSummary).toList();

		String nextCursor = null;
		if (end < matches.size()) {
			nextCursor = UUID.randomUUID().toString();
			cursors.put(nextCursor, new CursorState(end, signature));
		}
		return new NotePage(items, nextCursor, matches.size());
	}

	public synchronized NoteDetail get(UUID noteId) {
		return toDetail(requireNote(noteId));
	}

	public synchronized SaveResult create(CreateNoteRequest request) {
		SaveResult prior = idempotentResults.get(request.idempotencyKey());
		if (prior != null) {
			return prior;
		}

		Instant now = Instant.now();
		UUID noteId = UUID.randomUUID();
		UUID revisionId = UUID.randomUUID();
		MutableNote note = new MutableNote(
			noteId,
			request.title().strip(),
			request.body(),
			CURRENT_MEMBER,
			CURRENT_MEMBER,
			CURRENT_DEVICE,
			LAPTOP,
			WEB,
			now,
			now,
			revisionId,
			1,
			false,
			"PENDING");
		note.revisions.add(new Revision(
			revisionId, 1, note.title, note.body, CURRENT_MEMBER, CURRENT_DEVICE, LAPTOP, WEB, now, "Current"));
		notes.put(noteId, note);

		SaveResult result = new SaveResult("committed", toDetail(note), "Saved to hitstudio. Mobile propagation is pending.");
		idempotentResults.put(request.idempotencyKey(), result);
		return result;
	}

	public synchronized SaveResult save(UUID noteId, SaveNoteRequest request) {
		SaveResult prior = idempotentResults.get(request.idempotencyKey());
		if (prior != null) {
			return prior;
		}

		MutableNote note = requireNote(noteId);
		if (!note.revisionId.equals(request.parentRevisionId())) {
			throw new StaleRevisionException(toDetail(note));
		}

		String title = request.title().strip();
		String body = request.body();
		if (Objects.equals(note.title, title) && Objects.equals(note.body, body)) {
			SaveResult result = new SaveResult("unchanged", toDetail(note), "No changes to save.");
			idempotentResults.put(request.idempotencyKey(), result);
			return result;
		}

		Instant now = Instant.now();
		note.title = title;
		note.body = body;
		note.lastEditedBy = CURRENT_MEMBER;
		note.originDeviceName = CURRENT_DEVICE;
		note.originDeviceType = LAPTOP;
		note.originDevicePlatform = WEB;
		note.modifiedAt = now;
		note.revisionNumber += 1;
		note.revisionId = UUID.randomUUID();
		note.propagationStatus = "PENDING";
		for (int i = 0; i < note.revisions.size(); i++) {
			Revision previous = note.revisions.get(i);
			note.revisions.set(i, previous.withLabel("Retained"));
		}
		note.revisions.add(0, new Revision(
			note.revisionId,
			note.revisionNumber,
			note.title,
			note.body,
			note.lastEditedBy,
			CURRENT_DEVICE,
			LAPTOP,
			WEB,
			now,
			"Current"));
		if (note.revisions.size() > 5) {
			note.revisions.remove(note.revisions.size() - 1);
		}

		SaveResult result = new SaveResult("committed", toDetail(note), "Saved to hitstudio. Mobile propagation is pending.");
		idempotentResults.put(request.idempotencyKey(), result);
		return result;
	}

	public synchronized SyncResult synchronize() {
		int pending = 0;
		for (MutableNote note : notes.values()) {
			if ("PENDING".equals(note.propagationStatus)) {
				pending += 1;
				note.propagationStatus = "SYNCED";
			}
		}
		lastSynchronizedAt = Instant.now();
		return new SyncResult(
			"completed",
			2,
			0,
			lastSynchronizedAt,
			pending == 0
				? "No pending changes known to hitstudio."
				: "Reachable household peers accepted " + pending + (pending == 1 ? " change." : " changes."));
	}

	public synchronized int pendingChanges() {
		return (int) notes.values().stream().filter(note -> "PENDING".equals(note.propagationStatus)).count();
	}

	public synchronized Instant lastSynchronizedAt() {
		return lastSynchronizedAt;
	}

	public synchronized AdminOverview adminOverview() {
		int conflicts = (int) notes.values().stream().filter(note -> note.conflict).count();
		return new AdminOverview(
			1,
			2,
			1,
			2,
			1,
			0,
			conflicts,
			0,
			List.of(
				new DeviceView(seedId("device-hitstudio"), "hitstudio", "LAP-7A2F", LAPTOP, WEB, "CONNECTED", "Now"),
				new DeviceView(seedId("device-pixel-9"), "pixel-9", "MOB-A91C", MOBILE, ANDROID, "CONNECTED", "Now"),
				new DeviceView(seedId("device-iphone-15"), "iphone-15", "MOB-C42E", MOBILE, IPHONE, "CONNECTED", "2m ago"),
				new DeviceView(seedId("device-guest-browser"), "guest-browser", "LAP-F103", LAPTOP, WEB, "PENDING", "Requested 12m ago")));
	}

	private MutableNote requireNote(UUID noteId) {
		MutableNote note = notes.get(noteId);
		if (note == null) {
			throw new NoteNotFoundException(noteId);
		}
		return note;
	}

	private boolean matchesQuery(MutableNote note, String query, String field) {
		if (query.isBlank()) {
			return true;
		}
		return switch (field) {
			case "CREATED_BY" -> normalize(note.createdBy).contains(query);
			case "LAST_EDITED_BY" -> normalize(note.lastEditedBy).contains(query);
			default -> normalize(note.title).contains(query);
		};
	}

	private Comparator<MutableNote> noteComparator(String sort) {
		return switch (sort) {
			case "TITLE_ASC" -> Comparator.comparing(note -> note.title.toLowerCase(Locale.ROOT));
			case "CREATED_DESC" -> Comparator.comparing((MutableNote note) -> note.createdAt).reversed();
			default -> Comparator.comparing((MutableNote note) -> note.modifiedAt).reversed();
		};
	}

	private NoteSummary toSummary(MutableNote note) {
		return new NoteSummary(
			note.id,
			note.title,
			preview(note.body),
			note.createdBy,
			note.lastEditedBy,
			note.originDeviceName,
			note.originDeviceType,
			note.originDevicePlatform,
			note.modifiedAt,
			"R" + note.revisionNumber,
			"SAVED",
			note.propagationStatus,
			note.conflict);
	}

	private NoteDetail toDetail(MutableNote note) {
		List<RevisionView> revisions = note.revisions.stream()
			.map(revision -> new RevisionView(
				revision.id,
				"R" + revision.number,
				revision.title,
				revision.body,
				revision.author,
				revision.origin,
				revision.originDeviceType,
				revision.originDevicePlatform,
				revision.createdAt,
				revision.label))
			.toList();
		return new NoteDetail(
			note.id,
			note.title,
			note.body,
			note.createdBy,
			note.lastEditedBy,
			note.originDeviceName,
			note.originDeviceType,
			note.originDevicePlatform,
			note.modifiedAt,
			note.revisionId,
			"R" + note.revisionNumber,
			"SAVED",
			note.propagationStatus,
			note.conflict,
			revisions);
	}

	private String preview(String body) {
		String collapsed = body.replaceAll("\\s+", " ").strip();
		return collapsed.length() <= 116 ? collapsed : collapsed.substring(0, 113) + "…";
	}

	private String normalize(String value) {
		return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
	}

	private String normalizeEnum(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip().toUpperCase(Locale.ROOT);
	}

	private UUID seedId(String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private void seedNotes() {
		Instant now = Instant.now();
		seed("sunday-market", "Sunday market list",
			"Vegetables\n• Tomatoes and spinach\n• Green chillies\n• Coriander\n\nPantry\n• Toor dal\n• Filter coffee\n• Jaggery\n\nRemember the flowers near the east gate.",
			"Household owner", "Household member", "pixel-9", MOBILE, ANDROID,
			now.minus(Duration.ofMinutes(18)), 17, false, "PENDING");
		seed("school-week", "School week",
			"Monday: science model materials\nWednesday: library book return\nFriday: blue house T-shirt and water bottle",
			"Household member", "Household member", "iphone-15", MOBILE, IPHONE,
			now.minus(Duration.ofHours(2)), 9, false, "SYNCED");
		seed("garden", "Balcony garden notes",
			"Move the basil away from the strongest afternoon sun. The jasmine needs a deeper pot before the next rain.",
			"Household member", "Household owner", CURRENT_DEVICE, LAPTOP, WEB,
			now.minus(Duration.ofHours(6)), 12, false, "SYNCED");
		seed("monsoon", "Monsoon prep",
			"Check balcony drain, replace the study window seal, and keep two charged torches in the hall cupboard.",
			"Household owner", "Household owner", CURRENT_DEVICE, LAPTOP, WEB,
			now.minus(Duration.ofDays(1)), 6, false, "SYNCED");
		seed("repairs", "Household repairs",
			"Kitchen tap washer\nGuest room fan regulator\nLoose hinge on the shoe cabinet",
			"Household owner", "Household member", "pixel-9", MOBILE, ANDROID,
			now.minus(Duration.ofDays(2)), 14, true, "PENDING");
		seed("diwali", "Diwali guest plan",
			"Confirm train times with Chithappa. Prepare the guest room on Thursday and order extra floor cushions.",
			"Household member", "Household member", "iphone-15", MOBILE, IPHONE,
			now.minus(Duration.ofDays(3)), 8, false, "SYNCED");
		seed("recipes", "Grandma's recipes to learn",
			"Lemon rasam, ragi dosa batter, coconut burfi, and the quick mango pickle that keeps for one week.",
			"Household member", "Household member", "pixel-9", MOBILE, ANDROID,
			now.minus(Duration.ofDays(5)), 11, false, "SYNCED");
		seed("errands", "Weekend errands",
			"Pick up framed photo, renew library cards, service the blue bicycle, and visit the tailor after 4 pm.",
			"Household owner", "Household owner", CURRENT_DEVICE, LAPTOP, WEB,
			now.minus(Duration.ofDays(6)), 5, false, "SYNCED");
	}

	private void seed(
			String key,
			String title,
			String body,
			String createdBy,
			String lastEditedBy,
			String originDeviceName,
			String originDeviceType,
			String originDevicePlatform,
			Instant modifiedAt,
			int revisionNumber,
			boolean conflict,
			String propagationStatus) {
		UUID noteId = seedId("note-" + key);
		UUID revisionId = seedId("revision-" + key + "-" + revisionNumber);
		MutableNote note = new MutableNote(
			noteId,
			title,
			body,
			createdBy,
			lastEditedBy,
			originDeviceName,
			originDeviceType,
			originDevicePlatform,
			modifiedAt.minus(Duration.ofDays(Math.max(1, revisionNumber / 2))),
			modifiedAt,
			revisionId,
			revisionNumber,
			conflict,
			propagationStatus);
		note.revisions.add(new Revision(
			revisionId,
			revisionNumber,
			title,
			body,
			lastEditedBy,
			originDeviceName,
			originDeviceType,
			originDevicePlatform,
			modifiedAt,
			conflict ? "Conflict candidate" : "Current"));
		if (revisionNumber > 1) {
			boolean currentRevisionFromLaptop = LAPTOP.equals(originDeviceType);
			note.revisions.add(new Revision(
				seedId("revision-" + key + "-" + (revisionNumber - 1)),
				revisionNumber - 1,
				title,
				body + "\n",
				createdBy,
				currentRevisionFromLaptop ? "pixel-9" : CURRENT_DEVICE,
				currentRevisionFromLaptop ? MOBILE : LAPTOP,
				currentRevisionFromLaptop ? ANDROID : WEB,
				modifiedAt.minus(Duration.ofDays(1)),
				"Retained"));
		}
		notes.put(noteId, note);
	}

	private record CursorState(int start, String signature) {}

	private record Revision(
			UUID id,
			int number,
			String title,
			String body,
			String author,
			String origin,
			String originDeviceType,
			String originDevicePlatform,
			Instant createdAt,
			String label) {
		Revision withLabel(String nextLabel) {
			return new Revision(
				id, number, title, body, author, origin, originDeviceType, originDevicePlatform, createdAt, nextLabel);
		}
	}

	private static final class MutableNote {
		private final UUID id;
		private String title;
		private String body;
		private final String createdBy;
		private String lastEditedBy;
		private String originDeviceName;
		private String originDeviceType;
		private String originDevicePlatform;
		private final Instant createdAt;
		private Instant modifiedAt;
		private UUID revisionId;
		private int revisionNumber;
		private final boolean conflict;
		private String propagationStatus;
		private final List<Revision> revisions = new ArrayList<>();

		private MutableNote(
				UUID id,
				String title,
				String body,
				String createdBy,
				String lastEditedBy,
				String originDeviceName,
				String originDeviceType,
				String originDevicePlatform,
				Instant createdAt,
				Instant modifiedAt,
				UUID revisionId,
				int revisionNumber,
				boolean conflict,
				String propagationStatus) {
			this.id = id;
			this.title = title;
			this.body = body;
			this.createdBy = createdBy;
			this.lastEditedBy = lastEditedBy;
			this.originDeviceName = originDeviceName;
			this.originDeviceType = originDeviceType;
			this.originDevicePlatform = originDevicePlatform;
			this.createdAt = createdAt;
			this.modifiedAt = modifiedAt;
			this.revisionId = revisionId;
			this.revisionNumber = revisionNumber;
			this.conflict = conflict;
			this.propagationStatus = propagationStatus;
		}
	}

	public static final class NoteNotFoundException extends RuntimeException {
		private final UUID noteId;

		public NoteNotFoundException(UUID noteId) {
			super("Shared note was not found.");
			this.noteId = noteId;
		}

		public UUID noteId() {
			return noteId;
		}
	}

	public static final class StaleRevisionException extends RuntimeException {
		private final NoteDetail current;

		public StaleRevisionException(NoteDetail current) {
			super("This note changed on another device.");
			this.current = current;
		}

		public NoteDetail current() {
			return current;
		}
	}

	public static final class InvalidCursorException extends RuntimeException {
		public InvalidCursorException() {
			super("The note-list cursor is no longer valid. Start again from the first page.");
		}
	}
}
