package com.histudio.web.sharednotebook.api;

import static com.histudio.web.sharednotebook.api.ApiModels.AdminOverview;
import static com.histudio.web.sharednotebook.api.ApiModels.ApiError;
import static com.histudio.web.sharednotebook.api.ApiModels.DeviceView;
import static com.histudio.web.sharednotebook.api.ApiModels.MemberView;
import static com.histudio.web.sharednotebook.api.ApiModels.NotePage;
import static com.histudio.web.sharednotebook.api.ApiModels.SessionView;
import static com.histudio.web.sharednotebook.api.ApiModels.SyncResult;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RuntimeApiController {

	private final UUID memberId;
	private final UUID deviceId;
	private final String memberName;
	private final String deviceName;
	private final String appName;
	private final String deviceType;
	private final String platform;
	private final Instant startedAt = Instant.now();

	public RuntimeApiController(
			@Value("${shared-notebook.node.id}") String nodeId,
			@Value("${shared-notebook.node.member-name}") String memberName,
			@Value("${shared-notebook.node.name}") String deviceName,
			@Value("${shared-notebook.node.app-name:SharedNoteBook Web}") String appName,
			@Value("${shared-notebook.node.type:LAPTOP}") String deviceType,
			@Value("${shared-notebook.node.platform:WEB}") String platform) {
		String detectedHostName = hostName();
		this.memberName = valueOr(memberName, System.getProperty("user.name", "Local member"));
		this.deviceName = valueOr(deviceName, detectedHostName);
		String effectiveNodeId = valueOr(nodeId, this.memberName + "@" + detectedHostName);
		this.deviceId = stableId("device:" + effectiveNodeId);
		this.memberId = stableId("member:" + this.memberName);
		this.appName = appName.strip();
		this.deviceType = deviceType.strip().toUpperCase(Locale.ROOT);
		this.platform = platform.strip().toUpperCase(Locale.ROOT);
	}

	@GetMapping("/session")
	SessionView session(CsrfToken csrfToken) {
		return new SessionView(
			new MemberView(memberId, memberName, "ROOT_ADMIN", initials(memberName)),
			hostDevice(),
			deviceName,
			"CONNECTED",
			"NOT_CONNECTED",
			"WAITING_FOR_ANDROID",
			0,
			0,
			startedAt,
			"AFTER_EACH_SAVE",
			csrfToken.getToken(),
			false);
	}

	@GetMapping("/shared-notes")
	NotePage notes(
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "20") int limit) {
		return new NotePage(List.of(), null, 0);
	}

	@GetMapping("/shared-notes/{noteId}")
	ResponseEntity<ApiError> note(@PathVariable UUID noteId) {
		return unavailable("android_not_connected", "This note is unavailable until a registered Android app connects and reconciles real household data.");
	}

	@PostMapping("/shared-notes")
	ResponseEntity<ApiError> create(@RequestBody Map<String, Object> ignored) {
		return unavailable("android_not_connected", "Connect a registered Android app before creating a shared note from the web companion.");
	}

	@PutMapping("/shared-notes/{noteId}")
	ResponseEntity<ApiError> save(@PathVariable UUID noteId, @RequestBody Map<String, Object> ignored) {
		return unavailable("android_not_connected", "Connect a registered Android app before saving a shared note from the web companion.");
	}

	@PostMapping("/synchronization")
	SyncResult synchronize() {
		return new SyncResult(
			"no_android_devices",
			0,
			0,
			Instant.now(),
			"No registered Android apps are connected. Nothing was synchronized.");
	}

	@GetMapping("/admin/overview")
	AdminOverview adminOverview() {
		return new AdminOverview(1, 0, 1, 0, 0, 0, 0, 0, List.of(hostDevice()));
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

	private ResponseEntity<ApiError> unavailable(String code, String message) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(new ApiError(code, message, Instant.now()));
	}

	private UUID stableId(String value) {
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private String shortId(UUID id) {
		return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
	}

	private String initials(String name) {
		String[] words = name.strip().split("\\s+");
		if (words.length == 0 || words[0].isBlank()) {
			return "?";
		}
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
