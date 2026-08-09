package com.histudio.web.sharednotebook.api;

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

	public record NotePage(List<Object> items, String nextCursor, int totalMatches) {}

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
