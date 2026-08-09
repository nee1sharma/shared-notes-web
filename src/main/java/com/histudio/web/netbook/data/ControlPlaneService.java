package com.histudio.web.netbook.data;

import static com.histudio.web.netbook.api.ApiModels.DeviceView;
import static com.histudio.web.netbook.api.ApiModels.MobileDeviceView;
import static com.histudio.web.netbook.api.ApiModels.MobileHeartbeatRequest;
import static com.histudio.web.netbook.api.ApiModels.MobileNote;
import static com.histudio.web.netbook.api.ApiModels.MobileRegistrationRequest;
import static com.histudio.web.netbook.api.ApiModels.MobileRegistrationResponse;
import static com.histudio.web.netbook.api.ApiModels.MobileSyncRequest;
import static com.histudio.web.netbook.api.ApiModels.MobileSyncResponse;
import static com.histudio.web.netbook.api.ApiModels.NoteCommand;
import static com.histudio.web.netbook.api.ApiModels.NoteDetail;
import static com.histudio.web.netbook.api.ApiModels.NotePage;
import static com.histudio.web.netbook.api.ApiModels.NoteSummary;
import static com.histudio.web.netbook.api.ApiModels.RevisionView;
import static com.histudio.web.netbook.api.ApiModels.SaveResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The durable, local control plane shared by the browser and registered Android devices.
 * Browser-facing data is converted to view models here so encrypted database values never
 * reach the HTTP layer by accident.
 */
@Service
public final class ControlPlaneService {

	private static final int MAX_PAGE_SIZE = 20;
	private static final Duration ONLINE_WINDOW = Duration.ofMinutes(20);

	private final JdbcTemplate jdbc;
	private final AtRestCrypto crypto;
	private final ObjectMapper objectMapper;
	private final String configuredNodeId;
	private final String configuredMemberName;
	private final SecureRandom secureRandom = new SecureRandom();

	private UUID householdId;
	private UUID rootMemberId;

	public ControlPlaneService(
			JdbcTemplate jdbc,
			AtRestCrypto crypto,
			ObjectMapper objectMapper,
			@Value("${netbook.node.id:}") String configuredNodeId,
			@Value("${netbook.node.member-name:}") String configuredMemberName) {
		this.jdbc = jdbc;
		this.crypto = crypto;
		this.objectMapper = objectMapper;
		this.configuredNodeId = configuredNodeId;
		this.configuredMemberName = configuredMemberName;
	}

	@PostConstruct
	@Transactional
	void bootstrapHousehold() {
		List<UUID> households = jdbc.query(
				"SELECT id FROM household ORDER BY created_at ASC LIMIT 1",
				(resultSet, rowNum) -> resultSet.getObject("id", UUID.class));
		if (households.isEmpty()) {
			householdId = UUID.randomUUID();
			jdbc.update(
					"INSERT INTO household (id, encrypted_display_name) VALUES (?, ?)",
					householdId,
					crypto.encryptCombined("NetBook household"));
		} else {
			householdId = households.getFirst();
		}

		String rootName = normalName(configuredMemberName, System.getProperty("user.name", "Local member"));
		String stableNodeId = configuredNodeId == null || configuredNodeId.isBlank()
				? rootName + "@" + System.getProperty("user.name", "local")
				: configuredNodeId.strip();
		rootMemberId = UUID.nameUUIDFromBytes(("netbook-root:" + stableNodeId).getBytes(StandardCharsets.UTF_8));
		Integer existing = jdbc.queryForObject(
				"SELECT COUNT(*) FROM household_member WHERE id = ?", Integer.class, rootMemberId);
		if (existing == null || existing == 0) {
			jdbc.update("""
					INSERT INTO household_member
					(id, household_id, encrypted_display_name, display_name_hash, role, status)
					VALUES (?, ?, ?, ?, 'ROOT_ADMIN', 'ACTIVE')
					""",
					rootMemberId,
					householdId,
					crypto.encryptCombined(rootName),
					crypto.digestHex(normalize(rootName)));
		}
	}

	public UUID householdId() {
		return householdId;
	}

	public UUID rootMemberId() {
		return rootMemberId;
	}

	public int connectedMobileCount() {
		Instant onlineAfter = Instant.now().minus(ONLINE_WINDOW);
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM web_device
				WHERE household_id = ? AND device_type = 'MOBILE' AND status = 'ACCEPTED'
				  AND last_seen_at >= ?
				""", Integer.class, householdId, Timestamp.from(onlineAfter));
		return count == null ? 0 : count;
	}

	public int registeredMobileCount() {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM web_device WHERE household_id = ? AND device_type = 'MOBILE'",
				Integer.class,
				householdId);
		return count == null ? 0 : count;
	}

	public List<DeviceView> mobileDevices() {
		return deviceRecords().stream().map(this::toDeviceView).toList();
	}

	public List<MobileDeviceView> mobileDeviceViews() {
		return deviceRecords().stream()
				.map(record -> new MobileDeviceView(
					record.id(),
					decryptName(record.memberName()),
					decryptName(record.deviceName()),
					deviceStatus(record),
					record.lastSeenAt() == null ? 0L : record.lastSeenAt().toEpochMilli()))
				.toList();
	}

	@Transactional
	public MobileRegistrationResponse registerMobile(MobileRegistrationRequest request) {
		if (request == null) throw new IllegalArgumentException("Registration data is required.");
		String installationId = required(request.installationId(), "installationId", 160);
		String memberName = required(request.memberName(), "memberName", 120);
		String deviceName = required(request.deviceName(), "deviceName", 120);
		String token = newAccessToken();
		String tokenHash = crypto.digestHex(token);
		Instant now = Instant.now();
		UUID memberId = findOrCreateMember(memberName);

		List<UUID> existing = jdbc.query(
				"SELECT id FROM web_device WHERE external_identity = ?", 
				(resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
				installationId);
		if (!existing.isEmpty()) {
			UUID deviceId = existing.getFirst();
			jdbc.update("""
					UPDATE web_device
					SET member_id = ?, encrypted_display_name = ?, device_token_hash = ?, app_name = ?,
					    model_name = ?, device_type = 'MOBILE', platform = ?, status = 'ACCEPTED',
					    last_seen_at = ?, last_connected_at = ?, updated_at = now()
					WHERE id = ?
					""",
					memberId,
					crypto.encryptCombined(deviceName),
					tokenHash,
					valueOr(request.appName(), "NetBook Android", 96),
					valueOr(request.modelName(), "Android device", 160),
					valueOr(request.platform(), "ANDROID", 24).toUpperCase(Locale.ROOT),
					Timestamp.from(now),
					Timestamp.from(now),
					deviceId);
			return new MobileRegistrationResponse(deviceId, householdId, "REGISTERED", token);
		}

		UUID deviceId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO web_device
				(id, household_id, member_id, encrypted_display_name, public_identity, acceptance_method,
				 accepted_at, status, external_identity, device_token_hash, app_name, model_name,
				 device_type, platform, last_seen_at, last_connected_at)
				VALUES (?, ?, ?, ?, ?, 'HOME_LAN_AUTOMATIC', ?, 'ACCEPTED', ?, ?, ?, ?, 'MOBILE', ?, ?, ?)
				""",
				deviceId,
				householdId,
				memberId,
				crypto.encryptCombined(deviceName),
				request.publicKey() == null ? installationId.getBytes(StandardCharsets.UTF_8) : request.publicKey().getBytes(StandardCharsets.UTF_8),
				Timestamp.from(now),
				installationId,
				tokenHash,
				valueOr(request.appName(), "NetBook Android", 96),
				valueOr(request.modelName(), "Android device", 160),
				valueOr(request.platform(), "ANDROID", 24).toUpperCase(Locale.ROOT),
				Timestamp.from(now),
				Timestamp.from(now));
		return new MobileRegistrationResponse(deviceId, householdId, "REGISTERED", token);
	}

	@Transactional
	public void heartbeat(String authorization, MobileHeartbeatRequest request) {
		UUID deviceId = authenticateDevice(authorization);
		jdbc.update("UPDATE web_device SET last_seen_at = ?, updated_at = now() WHERE id = ?",
				Timestamp.from(Instant.now()), deviceId);
	}

	@Transactional
	public MobileSyncResponse synchronizeMobile(String authorization, MobileSyncRequest request) {
		UUID deviceId = authenticateDevice(authorization);
		UUID memberId = deviceMember(deviceId);
		if (request != null && request.notes() != null) {
			if (request.notes().size() > 200) throw new IllegalArgumentException("A sync batch may contain at most 200 notes.");
			for (MobileNote note : request.notes()) acceptMobileNote(note, memberId, deviceId);
		}
		Instant since = request == null || request.lastSynchronizedAt() <= 0
				? Instant.EPOCH
				: Instant.ofEpochMilli(request.lastSynchronizedAt());
		Instant synchronizedAt = Instant.now();
		List<MobileNote> changed = currentNotes().stream()
				.filter(note -> !note.updatedAt().isBefore(since))
				.map(this::toMobileNote)
				.toList();
		jdbc.update("UPDATE web_device SET last_seen_at = ?, updated_at = now() WHERE id = ?",
				Timestamp.from(synchronizedAt), deviceId);
		return new MobileSyncResponse(changed, synchronizedAt.toEpochMilli());
	}

	public List<MobileDeviceView> mobileDevices(String authorization) {
		authenticateDevice(authorization);
		return mobileDeviceViews();
	}

	public NotePage listNotes(String query, String sort, int requestedLimit, String cursor) {
		List<NoteSummary> all = currentNotes().stream()
				.filter(note -> !note.deleted())
				.map(this::toSummary)
				.filter(note -> query == null || query.isBlank()
						|| note.title().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))
						|| note.preview().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT)))
				.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
		if ("TITLE_ASC".equals(sort)) {
			all.sort(Comparator.comparing(NoteSummary::title, String.CASE_INSENSITIVE_ORDER));
		} else if ("CREATED_DESC".equals(sort)) {
			all.sort(Comparator.comparing(NoteSummary::modifiedAt).reversed());
		} else {
			all.sort(Comparator.comparing(NoteSummary::modifiedAt).reversed());
		}
		int offset = parseCursor(cursor);
		int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
		int from = Math.min(offset, all.size());
		int to = Math.min(from + limit, all.size());
		String next = to < all.size() ? encodeCursor(to) : null;
		return new NotePage(all.subList(from, to), next, all.size());
	}

	public NoteDetail getNote(UUID noteId) {
		return currentNote(noteId).map(this::toDetail)
				.orElseThrow(() -> new MissingNoteException(noteId));
	}

	@Transactional
	public SaveResult createWebNote(NoteCommand command, UUID originDeviceId) {
		String title = valueOr(command == null ? null : command.title(), "Untitled note", 500);
		String body = valueOr(command == null ? null : command.body(), "", 50_000);
		CurrentNote saved = writeNote(
				UUID.randomUUID(), title, body, "SHARED", rootMemberId, originDeviceId,
				UUID.randomUUID(), null, Instant.now(), false);
		return new SaveResult("committed", toDetail(saved), "Saved to this laptop. Mobile propagation is queued for the next sync.");
	}

	@Transactional
	public SaveResult saveWebNote(UUID noteId, NoteCommand command, UUID originDeviceId) {
		CurrentNote existing = currentNote(noteId).orElseThrow(() -> new MissingNoteException(noteId));
		if (command == null || command.parentRevisionId() == null || !command.parentRevisionId().equals(existing.revisionId())) {
			throw new NoteConflictException(toDetail(existing));
		}
		String title = valueOr(command.title(), "Untitled note", 500);
		String body = valueOr(command.body(), "", 50_000);
		CurrentNote saved = writeNote(noteId, title, body, "SHARED", rootMemberId, originDeviceId,
				UUID.randomUUID(), existing.revisionId(), Instant.now(), false);
		String outcome = saved.revisionId().equals(existing.revisionId()) ? "unchanged" : "committed";
		return new SaveResult(outcome, toDetail(saved), "Saved to this laptop. Mobile propagation is queued for the next sync.");
	}

	private void acceptMobileNote(MobileNote incoming, UUID defaultMemberId, UUID deviceId) {
		if (incoming == null || incoming.id() == null || incoming.revisionId() == null) return;
		if (!"SHARED".equalsIgnoreCase(incoming.visibility())) return;
		String title = valueOr(incoming.title(), "Untitled note", 500);
		String body = valueOr(incoming.body(), "", 50_000);
		Instant changedAt = incoming.updatedAt() > 0 ? Instant.ofEpochMilli(incoming.updatedAt()) : Instant.now();
		CurrentNote existing = currentNote(incoming.id()).orElse(null);
		if (existing != null && existing.revisionId().equals(incoming.revisionId())) return;
		if (existing != null && existing.updatedAt().isAfter(changedAt)) return;
		UUID author = parseUuidOr(incoming.creatorId(), defaultMemberId);
		if (!memberExists(author)) author = defaultMemberId;
		writeNote(incoming.id(), title, body, "SHARED", author, deviceId, incoming.revisionId(),
				incoming.parentRevisionId(), changedAt, incoming.deleted());
	}

	private CurrentNote writeNote(
			UUID noteId,
			String title,
			String body,
			String visibility,
			UUID authorId,
			UUID originDeviceId,
			UUID revisionId,
			UUID parentRevisionId,
			Instant changedAt,
			boolean deleted) {
		CurrentNote existing = currentNote(noteId).orElse(null);
		String serialized = serializePayload(new StoredPayload(title, body, visibility));
		if (existing != null && serializePayload(existing.payload()).equals(serialized) && existing.deleted() == deleted) {
			return existing;
		}
		Instant now = Instant.now();
		if (existing == null) {
			jdbc.update("""
					INSERT INTO note (id, household_id, conflict_state, trashed_at, created_at, updated_at)
					VALUES (?, ?, false, ?, ?, ?)
					""",
					noteId, householdId, deleted ? Timestamp.from(now) : null, Timestamp.from(now), Timestamp.from(now));
		}
		AtRestCrypto.Sealed sealed = crypto.encrypt(serialized);
		try {
			jdbc.update("""
					INSERT INTO note_revision
					(id, note_id, author_member_id, origin_device_id, encrypted_payload, payload_nonce,
					 payload_key_version, content_digest, created_at)
					VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
					""",
					revisionId, noteId, authorId, originDeviceId, sealed.ciphertext(), sealed.nonce(),
					crypto.digest(serialized), Timestamp.from(now));
		} catch (org.springframework.dao.DuplicateKeyException duplicate) {
			return currentNote(noteId).orElseThrow(() -> duplicate);
		}
		if (parentRevisionId != null && revisionExists(parentRevisionId)) {
			jdbc.update("INSERT INTO note_revision_parent (revision_id, parent_revision_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
					revisionId, parentRevisionId);
		}
		boolean conflict = existing != null && parentRevisionId != null && !parentRevisionId.equals(existing.revisionId());
		jdbc.update("""
				UPDATE note SET current_revision_id = ?, conflict_state = ?, trashed_at = ?, updated_at = ? WHERE id = ?
				""",
				revisionId, conflict, deleted ? Timestamp.from(now) : null, Timestamp.from(now), noteId);
		return currentNote(noteId).orElseThrow();
	}

	private List<CurrentNote> currentNotes() {
		return jdbc.query("""
				SELECT n.id AS note_id, n.conflict_state, n.trashed_at, n.created_at AS note_created_at,
				       n.updated_at AS note_updated_at, r.id AS revision_id, r.author_member_id,
				       r.origin_device_id, r.encrypted_payload, r.payload_nonce, r.created_at AS revision_created_at,
				       m.encrypted_display_name AS author_name, d.encrypted_display_name AS origin_name,
				       d.device_type AS origin_type, d.platform AS origin_platform
				FROM note n
				JOIN note_revision r ON r.id = n.current_revision_id
				JOIN household_member m ON m.id = r.author_member_id
				LEFT JOIN web_device d ON d.id = r.origin_device_id
				WHERE n.household_id = ?
				""", this::mapCurrentNote, householdId);
	}

	private java.util.Optional<CurrentNote> currentNote(UUID noteId) {
		List<CurrentNote> notes = jdbc.query("""
				SELECT n.id AS note_id, n.conflict_state, n.trashed_at, n.created_at AS note_created_at,
				       n.updated_at AS note_updated_at, r.id AS revision_id, r.author_member_id,
				       r.origin_device_id, r.encrypted_payload, r.payload_nonce, r.created_at AS revision_created_at,
				       m.encrypted_display_name AS author_name, d.encrypted_display_name AS origin_name,
				       d.device_type AS origin_type, d.platform AS origin_platform
				FROM note n
				JOIN note_revision r ON r.id = n.current_revision_id
				JOIN household_member m ON m.id = r.author_member_id
				LEFT JOIN web_device d ON d.id = r.origin_device_id
				WHERE n.household_id = ? AND n.id = ?
				""", this::mapCurrentNote, householdId, noteId);
		return notes.stream().findFirst();
	}

	private CurrentNote mapCurrentNote(ResultSet resultSet, int rowNum) throws SQLException {
		return new CurrentNote(
				resultSet.getObject("note_id", UUID.class),
				resultSet.getObject("revision_id", UUID.class),
				resultSet.getObject("author_member_id", UUID.class),
				resultSet.getObject("origin_device_id", UUID.class),
				readPayload(resultSet.getBytes("encrypted_payload"), resultSet.getBytes("payload_nonce")),
				decryptName(resultSet.getBytes("author_name")),
				resultSet.getBytes("origin_name") == null ? "NetBook Web" : decryptName(resultSet.getBytes("origin_name")),
				resultSet.getString("origin_type") == null ? "LAPTOP" : resultSet.getString("origin_type"),
				resultSet.getString("origin_platform") == null ? "WEB" : resultSet.getString("origin_platform"),
				readInstant(resultSet, "note_created_at"),
				readInstant(resultSet, "note_updated_at"),
				resultSet.getBoolean("conflict_state"),
				resultSet.getTimestamp("trashed_at") != null);
	}

	private NoteSummary toSummary(CurrentNote note) {
		return new NoteSummary(
				note.noteId(), note.payload().title(), preview(note.payload().body()), note.authorName(), note.authorName(),
				note.originDeviceName(), note.originDeviceType(), note.originDevicePlatform(), note.updatedAt(),
				note.revisionId().toString(), "SAVED", connectedMobileCount() == 0 ? "PENDING" : "SYNCED", note.conflict());
	}

	private NoteDetail toDetail(CurrentNote note) {
		return new NoteDetail(
				note.noteId(), note.payload().title(), note.authorName(), note.authorName(), note.originDeviceName(),
				note.originDeviceType(), note.originDevicePlatform(), note.updatedAt(), note.revisionId().toString(),
				"SAVED", connectedMobileCount() == 0 ? "PENDING" : "SYNCED", note.conflict(), note.payload().body(),
				note.revisionId(), revisions(note.noteId()));
	}

	private List<RevisionView> revisions(UUID noteId) {
		return jdbc.query("""
				SELECT r.id, r.encrypted_payload, r.payload_nonce, r.created_at, m.encrypted_display_name AS author_name,
				       d.encrypted_display_name AS origin_name, d.device_type AS origin_type, d.platform AS origin_platform
				FROM note_revision r
				JOIN household_member m ON m.id = r.author_member_id
				LEFT JOIN web_device d ON d.id = r.origin_device_id
				WHERE r.note_id = ? ORDER BY r.created_at DESC
				""", (resultSet, rowNum) -> {
			StoredPayload payload = readPayload(resultSet.getBytes("encrypted_payload"), resultSet.getBytes("payload_nonce"));
			Instant createdAt = readInstant(resultSet, "created_at");
			return new RevisionView(
					resultSet.getObject("id", UUID.class), "Revision " + (rowNum + 1), payload.title(), payload.body(),
					decryptName(resultSet.getBytes("author_name")),
					resultSet.getBytes("origin_name") == null ? "NetBook Web" : decryptName(resultSet.getBytes("origin_name")),
					resultSet.getString("origin_type") == null ? "LAPTOP" : resultSet.getString("origin_type"),
					resultSet.getString("origin_platform") == null ? "WEB" : resultSet.getString("origin_platform"),
					createdAt, "Saved " + createdAt);
		}, noteId);
	}

	private MobileNote toMobileNote(CurrentNote note) {
		return new MobileNote(note.noteId(), "SHARED", note.payload().title(), note.payload().body(),
				note.authorId().toString(), note.revisionId(), null, note.createdAt().toEpochMilli(),
				note.updatedAt().toEpochMilli(), note.deleted());
	}

	private List<DeviceRecord> deviceRecords() {
		return jdbc.query("""
				SELECT d.id, m.encrypted_display_name AS member_name, d.encrypted_display_name AS device_name,
				       d.app_name, d.model_name, d.device_type, d.platform, d.status, d.created_at,
				       d.last_seen_at
				FROM web_device d JOIN household_member m ON m.id = d.member_id
				WHERE d.household_id = ? AND d.device_type = 'MOBILE'
				ORDER BY d.last_seen_at DESC NULLS LAST, d.created_at ASC
				""", (resultSet, rowNum) -> new DeviceRecord(
					resultSet.getObject("id", UUID.class), resultSet.getBytes("member_name"), resultSet.getBytes("device_name"),
					valueOr(resultSet.getString("app_name"), "NetBook Android", 96),
					valueOr(resultSet.getString("model_name"), "Android device", 160),
					valueOr(resultSet.getString("device_type"), "MOBILE", 24),
					valueOr(resultSet.getString("platform"), "ANDROID", 24), resultSet.getString("status"),
					readInstant(resultSet, "created_at"), readInstantNullable(resultSet, "last_seen_at")), householdId);
	}

	private DeviceView toDeviceView(DeviceRecord record) {
		return new DeviceView(record.id(), decryptName(record.memberName()), decryptName(record.deviceName()),
				record.id().toString().substring(0, 8).toUpperCase(Locale.ROOT), record.appName(), record.modelName(),
				record.deviceType(), record.platform(), deviceStatus(record), record.createdAt(), record.lastSeenAt());
	}

	private String deviceStatus(DeviceRecord record) {
		if (!"ACCEPTED".equals(record.status())) return record.status();
		return record.lastSeenAt() != null && !record.lastSeenAt().isBefore(Instant.now().minus(ONLINE_WINDOW))
				? "CONNECTED" : "OFFLINE";
	}

	private UUID findOrCreateMember(String memberName) {
		String hash = crypto.digestHex(normalize(memberName));
		List<UUID> ids = jdbc.query(
				"SELECT id FROM household_member WHERE household_id = ? AND display_name_hash = ? LIMIT 1",
				(resultSet, rowNum) -> resultSet.getObject("id", UUID.class), householdId, hash);
		if (!ids.isEmpty()) return ids.getFirst();
		UUID memberId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO household_member
				(id, household_id, encrypted_display_name, display_name_hash, role, status)
				VALUES (?, ?, ?, ?, 'MEMBER', 'ACTIVE')
				""", memberId, householdId, crypto.encryptCombined(memberName), hash);
		return memberId;
	}

	private UUID authenticateDevice(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new UnauthorizedMobileException();
		}
		String hash = crypto.digestHex(authorization.substring("Bearer ".length()).strip());
		List<UUID> ids = jdbc.query("""
				SELECT id FROM web_device
				WHERE household_id = ? AND device_token_hash = ? AND status = 'ACCEPTED'
				""", (resultSet, rowNum) -> resultSet.getObject("id", UUID.class), householdId, hash);
		if (ids.isEmpty()) throw new UnauthorizedMobileException();
		return ids.getFirst();
	}

	private UUID deviceMember(UUID deviceId) {
		return jdbc.queryForObject("SELECT member_id FROM web_device WHERE id = ?", UUID.class, deviceId);
	}

	private boolean memberExists(UUID memberId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE id = ?", Integer.class, memberId);
		return count != null && count > 0;
	}

	private boolean revisionExists(UUID revisionId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM note_revision WHERE id = ?", Integer.class, revisionId);
		return count != null && count > 0;
	}

	private String serializePayload(StoredPayload payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to serialize a note payload.", exception);
		}
	}

	private StoredPayload readPayload(byte[] ciphertext, byte[] nonce) {
		try {
			return objectMapper.readValue(crypto.decrypt(ciphertext, nonce), StoredPayload.class);
		} catch (Exception exception) {
			throw new IllegalStateException("A stored note payload is malformed.", exception);
		}
	}

	private String decryptName(byte[] encryptedName) {
		return crypto.decryptCombined(encryptedName);
	}

	private String newAccessToken() {
		byte[] token = new byte[32];
		secureRandom.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	private static String normalName(String configured, String fallback) {
		return configured == null || configured.isBlank() ? fallback.strip() : configured.strip();
	}

	private static String normalize(String value) {
		return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private static String required(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
		return valueOr(value, "", maxLength);
	}

	private static String valueOr(String value, String fallback, int maxLength) {
		String result = value == null || value.isBlank() ? fallback : value.strip();
		if (result.length() > maxLength) throw new IllegalArgumentException("Value exceeds " + maxLength + " characters.");
		return result;
	}

	private static String preview(String body) {
		String compact = body.replaceAll("\\s+", " ").strip();
		return compact.length() <= 160 ? compact : compact.substring(0, 157) + "…";
	}

	private static Instant readInstant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? Instant.EPOCH : value.toInstant();
	}

	private static Instant readInstantNullable(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static UUID parseUuidOr(String value, UUID fallback) {
		try {
			return value == null ? fallback : UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}

	private static int parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) return 0;
		try {
			return Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
		} catch (Exception ignored) {
			return 0;
		}
	}

	private static String encodeCursor(int offset) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
	}

	private record StoredPayload(String title, String body, String visibility) {}

	private record CurrentNote(
			UUID noteId,
			UUID revisionId,
			UUID authorId,
			UUID originDeviceId,
			StoredPayload payload,
			String authorName,
			String originDeviceName,
			String originDeviceType,
			String originDevicePlatform,
			Instant createdAt,
			Instant updatedAt,
			boolean conflict,
			boolean deleted) {}

	private record DeviceRecord(
			UUID id,
			byte[] memberName,
			byte[] deviceName,
			String appName,
			String modelName,
			String deviceType,
			String platform,
			String status,
			Instant createdAt,
			Instant lastSeenAt) {}

	public static final class MissingNoteException extends RuntimeException {
		public MissingNoteException(UUID noteId) { super("The requested shared note does not exist: " + noteId); }
	}

	public static final class NoteConflictException extends RuntimeException {
		private final NoteDetail current;
		public NoteConflictException(NoteDetail current) {
			super("This note changed on another device. Reload it before saving.");
			this.current = current;
		}
		public NoteDetail current() { return current; }
	}

	public static final class UnauthorizedMobileException extends RuntimeException {
		public UnauthorizedMobileException() { super("A valid registered-device token is required."); }
	}
}
