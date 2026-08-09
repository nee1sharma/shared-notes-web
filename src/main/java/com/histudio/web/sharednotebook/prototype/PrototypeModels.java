package com.histudio.web.sharednotebook.prototype;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PrototypeModels {

	private PrototypeModels() {
	}

	public record MemberView(UUID id, String name, String role, String initials) {}

	public record DeviceView(
			UUID id,
			String name,
			String shortId,
			String type,
			String platform,
			String status,
			String lastSeen) {}

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
			boolean prototype) {}

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
			String body,
			String createdBy,
			String lastEditedBy,
			String originDeviceName,
			String originDeviceType,
			String originDevicePlatform,
			Instant modifiedAt,
			UUID revisionId,
			String revision,
			String saveStatus,
			String propagationStatus,
			boolean conflict,
			List<RevisionView> revisions) {}

	public record NotePage(List<NoteSummary> items, String nextCursor, int totalMatches) {}

	public record CreateNoteRequest(
			@NotBlank @Size(max = 180) String title,
			@NotNull @Size(max = 100_000) String body,
			@NotNull UUID idempotencyKey) {}

	public record SaveNoteRequest(
			@NotBlank @Size(max = 180) String title,
			@NotNull @Size(max = 100_000) String body,
			@NotNull UUID parentRevisionId,
			@NotNull UUID idempotencyKey) {}

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

	public record ApiError(String code, String message, Instant timestamp) {}
}
