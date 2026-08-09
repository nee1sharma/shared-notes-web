package com.histudio.web.sharednotebook.prototype;

import static com.histudio.web.sharednotebook.prototype.PrototypeModels.AdminOverview;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.ApiError;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.CreateNoteRequest;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.DeviceView;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.MemberView;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.NoteDetail;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.NotePage;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SaveNoteRequest;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SaveResult;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SessionView;
import static com.histudio.web.sharednotebook.prototype.PrototypeModels.SyncResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
@ConditionalOnProperty(name = "shared-notebook.prototype-mode", havingValue = "true")
public class PrototypeApiController {

	private static final UUID MEMBER_ID = UUID.nameUUIDFromBytes("prototype-household-owner".getBytes());
	private static final UUID DEVICE_ID = UUID.nameUUIDFromBytes("prototype-hitstudio".getBytes());

	private final PrototypeNotebookService notebook;

	public PrototypeApiController(PrototypeNotebookService notebook) {
		this.notebook = notebook;
	}

	@GetMapping("/session")
	SessionView session(CsrfToken csrfToken) {
		int pending = notebook.pendingChanges();
		return new SessionView(
			new MemberView(MEMBER_ID, "Household owner", "ROOT_ADMIN", "HO"),
			new DeviceView(DEVICE_ID, "hitstudio", "LAP-7A2F", "LAPTOP", "WEB", "CONNECTED", "Now"),
			"hitstudio",
			"CONNECTED",
			"READY",
			pending == 0 ? "SYNCED" : "PENDING",
			2,
			pending,
			notebook.lastSynchronizedAt(),
			"AFTER_EACH_SAVE",
			csrfToken.getToken(),
			true);
	}

	@GetMapping("/shared-notes")
	NotePage notes(
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "TITLE") String searchField,
			@RequestParam(required = false) Boolean conflict,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant modifiedFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant modifiedTo,
			@RequestParam(defaultValue = "MODIFIED_DESC") String sort,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(required = false) String cursor) {
		return notebook.list(query, searchField, conflict, modifiedFrom, modifiedTo, sort, limit, cursor);
	}

	@GetMapping("/shared-notes/{noteId}")
	NoteDetail note(@PathVariable UUID noteId) {
		return notebook.get(noteId);
	}

	@PostMapping("/shared-notes")
	ResponseEntity<SaveResult> create(@Valid @RequestBody CreateNoteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(notebook.create(request));
	}

	@PutMapping("/shared-notes/{noteId}")
	SaveResult save(@PathVariable UUID noteId, @Valid @RequestBody SaveNoteRequest request) {
		return notebook.save(noteId, request);
	}

	@PostMapping("/synchronization")
	SyncResult synchronize() {
		return notebook.synchronize();
	}

	@GetMapping("/admin/overview")
	AdminOverview adminOverview() {
		return notebook.adminOverview();
	}

	@ExceptionHandler(PrototypeNotebookService.NoteNotFoundException.class)
	ResponseEntity<ApiError> noteNotFound(PrototypeNotebookService.NoteNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiError("note_not_found", exception.getMessage(), Instant.now()));
	}

	@ExceptionHandler(PrototypeNotebookService.InvalidCursorException.class)
	ResponseEntity<ApiError> invalidCursor(PrototypeNotebookService.InvalidCursorException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiError("invalid_cursor", exception.getMessage(), Instant.now()));
	}

	@ExceptionHandler(PrototypeNotebookService.StaleRevisionException.class)
	ResponseEntity<Map<String, Object>> staleRevision(PrototypeNotebookService.StaleRevisionException exception) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", "conflict");
		body.put("message", exception.getMessage());
		body.put("current", exception.current());
		body.put("timestamp", Instant.now());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getField() + " " + error.getDefaultMessage())
			.orElse("The request was not valid.");
		return ResponseEntity.badRequest().body(new ApiError("invalid_request", message, Instant.now()));
	}
}
