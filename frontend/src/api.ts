import type {
  AdminOverview,
  ApiFailureBody,
  NoteDetail,
  NotePage,
  SaveResult,
  Session,
  SyncResult,
} from './types'

let csrfToken = ''

export class ApiError extends Error {
  status: number
  code?: string
  current?: NoteDetail

  constructor(status: number, body: ApiFailureBody) {
    super(body.message ?? 'NetBook could not complete the request.')
    this.status = status
    this.code = body.code
    this.current = body.current
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body) headers.set('Content-Type', 'application/json')
  if (init?.method && !['GET', 'HEAD'].includes(init.method)) {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
    cache: 'no-store',
  })

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiFailureBody
    throw new ApiError(response.status, body)
  }
  return (await response.json()) as T
}

export async function getSession(): Promise<Session> {
  const session = await request<Session>('/api/v1/session')
  csrfToken = session.csrfToken
  return session
}

export type NoteQuery = {
  query: string
  searchField: string
  sort: string
  conflict: string
  modifiedFrom: string
  modifiedTo: string
  cursor?: string | null
}

export function listNotes(query: NoteQuery, signal?: AbortSignal): Promise<NotePage> {
  const params = new URLSearchParams({
    limit: '20',
    searchField: query.searchField,
    sort: query.sort,
  })
  if (query.query.trim()) params.set('query', query.query.trim())
  if (query.conflict) params.set('conflict', query.conflict)
  if (query.modifiedFrom) params.set('modifiedFrom', new Date(`${query.modifiedFrom}T00:00:00`).toISOString())
  if (query.modifiedTo) params.set('modifiedTo', new Date(`${query.modifiedTo}T23:59:59`).toISOString())
  if (query.cursor) params.set('cursor', query.cursor)
  return request<NotePage>(`/api/v1/shared-notes?${params}`, { signal })
}

export function getNote(noteId: string, signal?: AbortSignal): Promise<NoteDetail> {
  return request<NoteDetail>(`/api/v1/shared-notes/${noteId}`, { signal })
}

export function createNote(title: string, body: string): Promise<SaveResult> {
  return request<SaveResult>('/api/v1/shared-notes', {
    method: 'POST',
    body: JSON.stringify({ title, body, idempotencyKey: crypto.randomUUID() }),
  })
}

export function saveNote(note: NoteDetail, title: string, body: string): Promise<SaveResult> {
  return request<SaveResult>(`/api/v1/shared-notes/${note.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      title,
      body,
      parentRevisionId: note.revisionId,
      idempotencyKey: crypto.randomUUID(),
    }),
  })
}

export function synchronize(): Promise<SyncResult> {
  return request<SyncResult>('/api/v1/synchronization', { method: 'POST' })
}

export function getAdminOverview(): Promise<AdminOverview> {
  return request<AdminOverview>('/api/v1/admin/overview')
}
