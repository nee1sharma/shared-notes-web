package com.histudio.web.netbook.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiModels {

	private ApiModels() {
	}

	public record MemberView(UUID id, String name, String role, String initials) {}

	public record DeviceView(
			UUID id,
			String memberName,
			String name,
			String shortId,
			String appName,
			String modelName,
			String type,
			String platform,
			String status,
			Instant connectedAt,
			Instant lastSeenAt) {}

	public record SessionView(
			MemberView member,
			DeviceView device,
			String backendName,
			String browserSession,
			String databaseStatus,
			String propagationStatus,
			int reachablePeers,
			int pendingChanges,
			Instant lastSynchronizedAt,
			String syncMode,
			String csrfToken,
			boolean notesAvailable) {}

	public record NoteSummary(
			UUID id,
			String title,
			String preview,
			String createdBy,
			String lastEditedBy,
			String originDeviceName,
			String originDeviceType,
			String originDevicePlatform,
			Instant modifiedAt,
			String revision,
			String saveStatus,
			String propagationStatus,
			boolean conflict) {}

	public record RevisionView(
			UUID id,
			String revision,
			String title,
			String body,
			String author,
			String origin,
			String originDeviceType,
			String originDevicePlatform,
			Instant createdAt,
			String label) {}

	public record NoteDetail(
			UUID id,
			String title,
			String createdBy,
			String lastEditedBy,
			String originDeviceName,
			String originDeviceType,
			String originDevicePlatform,
			Instant modifiedAt,
			String revision,
			String saveStatus,
			String propagationStatus,
			boolean conflict,
			String body,
			UUID revisionId,
			List<RevisionView> revisions) {}

	public record NotePage(List<NoteSummary> items, String nextCursor, int totalMatches) {}

	public record NoteCommand(String title, String body, UUID parentRevisionId, UUID idempotencyKey) {}

	public record SaveResult(String outcome, NoteDetail note, String message) {}

	public record SyncResult(
			String outcome,
			int reachablePeers,
			int pendingChanges,
			Instant completedAt,
			String message) {}

	public record AdminOverview(
			int registeredLaptopDevices,
			int registeredMobileDevices,
			int connectedLaptopDevices,
			int connectedMobileDevices,
			int pendingApprovals,
			int blockedOrRevoked,
			int unresolvedConflicts,
			int recentSyncFailures,
			List<DeviceView> devices) {}

	public record ApiError(String code, String message, Instant timestamp, NoteDetail current) {}

	public record MobileRegistrationRequest(
			String installationId,
			String memberName,
			String deviceName,
			String email,
			String publicKey,
			String appName,
			String modelName,
			String platform) {}

	public record MobileRegistrationResponse(
			UUID deviceId,
			UUID householdId,
			String status,
			String accessToken) {}

	public record MobileHeartbeatRequest(long timestamp) {}

	public record MobileNote(
			UUID id,
			String visibility,
			String title,
			String body,
			String creatorId,
			UUID revisionId,
			UUID parentRevisionId,
			long createdAt,
			long updatedAt,
			boolean deleted) {}

	public record MobileSyncRequest(List<MobileNote> notes, long lastSynchronizedAt) {}

	public record MobileSyncResponse(List<MobileNote> notes, long synchronizedAt) {}

	public record MobileDeviceView(
			UUID id,
			String memberName,
			String deviceName,
			String status,
			long lastSeenAt) {}
}
