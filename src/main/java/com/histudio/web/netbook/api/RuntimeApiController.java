package com.histudio.web.netbook.api;

import static com.histudio.web.netbook.api.ApiModels.AdminOverview;
import static com.histudio.web.netbook.api.ApiModels.ApiError;
import static com.histudio.web.netbook.api.ApiModels.DeviceView;
import static com.histudio.web.netbook.api.ApiModels.MemberView;
import static com.histudio.web.netbook.api.ApiModels.MobileDeviceView;
import static com.histudio.web.netbook.api.ApiModels.MobileHeartbeatRequest;
import static com.histudio.web.netbook.api.ApiModels.MobileRegistrationRequest;
import static com.histudio.web.netbook.api.ApiModels.MobileRegistrationResponse;
import static com.histudio.web.netbook.api.ApiModels.MobileSyncRequest;
import static com.histudio.web.netbook.api.ApiModels.MobileSyncResponse;
import static com.histudio.web.netbook.api.ApiModels.NoteCommand;
import static com.histudio.web.netbook.api.ApiModels.NoteDetail;
import static com.histudio.web.netbook.api.ApiModels.NotePage;
import static com.histudio.web.netbook.api.ApiModels.SaveResult;
import static com.histudio.web.netbook.api.ApiModels.SessionView;
import static com.histudio.web.netbook.api.ApiModels.SyncResult;

import com.histudio.web.netbook.data.ControlPlaneService;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RuntimeApiController {

	private final ControlPlaneService controlPlane;
	private final UUID memberId;
	private final UUID deviceId;
	private final String memberName;
	private final String deviceName;
	private final String appName;
	private final String deviceType;
	private final String platform;
	private final Instant startedAt = Instant.now();

	public RuntimeApiController(
			ControlPlaneService controlPlane,
			@Value("${netbook.node.id}") String nodeId,
			@Value("${netbook.node.member-name}") String memberName,
			@Value("${netbook.node.name}") String deviceName,
			@Value("${netbook.node.app-name:NetBook Web}") String appName,
			@Value("${netbook.node.type:LAPTOP}") String deviceType,
			@Value("${netbook.node.platform:WEB}") String platform) {
		this.controlPlane = controlPlane;
		String detectedHostName = hostName();
		this.memberName = valueOr(memberName, System.getProperty("user.name", "Local member"));
		this.deviceName = valueOr(deviceName, detectedHostName);
		String effectiveNodeId = valueOr(nodeId, this.memberName + "@" + detectedHostName);
		this.deviceId = stableId("device:" + effectiveNodeId);
		this.memberId = controlPlane.rootMemberId();
		this.appName = appName.strip();
		this.deviceType = deviceType.strip().toUpperCase(Locale.ROOT);
		this.platform = platform.strip().toUpperCase(Locale.ROOT);
	}

	@GetMapping("/session")
	SessionView session(CsrfToken csrfToken) {
		int connectedMobileDevices = controlPlane.connectedMobileCount();
		return new SessionView(
				new MemberView(memberId, memberName, "ROOT_ADMIN", initials(memberName)),
				hostDevice(),
				deviceName,
				"CONNECTED",
				"READY",
				connectedMobileDevices > 0 ? "SYNCHRONIZING" : "WAITING_FOR_ANDROID",
				connectedMobileDevices,
				0,
				startedAt,
				"AFTER_EACH_SAVE",
				csrfToken.getToken(),
				true);
	}

	@GetMapping("/shared-notes")
	NotePage notes(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int limit) {
		return controlPlane.listNotes(query, sort, limit, cursor);
	}

	@GetMapping("/shared-notes/{noteId}")
	NoteDetail note(@PathVariable UUID noteId) {
		return controlPlane.getNote(noteId);
	}

	@PostMapping("/shared-notes")
	SaveResult create(@RequestBody(required = false) NoteCommand command) {
		return controlPlane.createWebNote(command, null);
	}

	@PutMapping("/shared-notes/{noteId}")
	SaveResult save(@PathVariable UUID noteId, @RequestBody NoteCommand command) {
		return controlPlane.saveWebNote(noteId, command, null);
	}

	@PostMapping("/synchronization")
	SyncResult synchronize() {
		int reachablePeers = controlPlane.connectedMobileCount();
		return new SyncResult(
				reachablePeers > 0 ? "scheduled" : "no_reachable_devices",
				reachablePeers,
				0,
				Instant.now(),
				reachablePeers > 0
						? "Registered Android devices will pick up the latest shared revisions during their next synchronization."
						: "No Android device has sent a recent authenticated heartbeat.");
	}

	@GetMapping("/admin/overview")
	AdminOverview adminOverview() {
		List<DeviceView> devices = new ArrayList<>();
		devices.add(hostDevice());
		devices.addAll(controlPlane.mobileDevices());
		int registeredMobile = controlPlane.registeredMobileCount();
		int connectedMobile = controlPlane.connectedMobileCount();
		return new AdminOverview(1, registeredMobile, 1, connectedMobile, 0, 0, 0, 0, devices);
	}

	@PostMapping("/mobile/registration")
	MobileRegistrationResponse registerMobile(@RequestBody MobileRegistrationRequest request) {
		return controlPlane.registerMobile(request);
	}

	@PostMapping("/mobile/presence/heartbeat")
	ResponseEntity<Void> mobileHeartbeat(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody(required = false) MobileHeartbeatRequest request) {
		controlPlane.heartbeat(authorization, request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/mobile/sync")
	MobileSyncResponse mobileSynchronize(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody(required = false) MobileSyncRequest request) {
		return controlPlane.synchronizeMobile(authorization, request);
	}

	@GetMapping("/mobile/devices")
	List<MobileDeviceView> mobileDevices(
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		return controlPlane.mobileDevices(authorization);
	}

	@ExceptionHandler(ControlPlaneService.MissingNoteException.class)
	ResponseEntity<ApiError> missingNote(ControlPlaneService.MissingNoteException exception) {
		return error(HttpStatus.NOT_FOUND, "note_not_found", exception.getMessage(), null);
	}

	@ExceptionHandler(ControlPlaneService.NoteConflictException.class)
	ResponseEntity<ApiError> noteConflict(ControlPlaneService.NoteConflictException exception) {
		return error(HttpStatus.CONFLICT, "revision_conflict", exception.getMessage(), exception.current());
	}

	@ExceptionHandler(ControlPlaneService.UnauthorizedMobileException.class)
	ResponseEntity<ApiError> mobileUnauthorized(ControlPlaneService.UnauthorizedMobileException exception) {
		return error(HttpStatus.UNAUTHORIZED, "mobile_authentication_required", exception.getMessage(), null);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiError> invalidRequest(IllegalArgumentException exception) {
		return error(HttpStatus.BAD_REQUEST, "invalid_request", exception.getMessage(), null);
	}

	private DeviceView hostDevice() {
		return new DeviceView(
				deviceId,
				memberName,
				deviceName,
				shortId(deviceId),
				appName,
				hostModel(),
				deviceType,
				platform,
				"CONNECTED",
				startedAt,
				Instant.now());
	}

	private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, NoteDetail current) {
		return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now(), current));
	}

	private UUID stableId(String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private String shortId(UUID id) {
		return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
	}

	private String initials(String name) {
		String[] words = name.strip().split("\\s+");
		if (words.length == 0 || words[0].isBlank()) return "?";
		String first = words[0].substring(0, 1);
		String last = words.length > 1 ? words[words.length - 1].substring(0, 1) : "";
		return (first + last).toUpperCase(Locale.ROOT);
	}

	private String hostModel() {
		return System.getProperty("os.name") + " · " + hostName();
	}

	private String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (Exception ignored) {
			return "Local laptop";
		}
	}

	private String valueOr(String configured, String fallback) {
		return configured == null || configured.isBlank() ? fallback : configured.strip();
	}
}
