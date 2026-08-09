export type Member = {
  id: string
  name: string
  role: 'MEMBER' | 'ADMIN' | 'ROOT_ADMIN'
  initials: string
}

export type WebDevice = {
  id: string
  name: string
  shortId: string
  status: string
}

export type Session = {
  member: Member
  webDevice: WebDevice
  backendName: string
  browserSession: string
  databaseStatus: string
  propagationStatus: string
  reachablePeers: number
  pendingChanges: number
  lastSynchronizedAt: string
  syncMode: string
  csrfToken: string
  prototype: boolean
}

export type NoteSummary = {
  id: string
  title: string
  preview: string
  createdBy: string
  lastEditedBy: string
  modifiedAt: string
  revision: string
  saveStatus: 'SAVED'
  propagationStatus: 'PENDING' | 'SYNCED' | 'FAILED'
  conflict: boolean
}

export type Revision = {
  id: string
  revision: string
  title: string
  body: string
  author: string
  origin: string
  createdAt: string
  label: string
}

export type NoteDetail = NoteSummary & {
  body: string
  revisionId: string
  revisions: Revision[]
}

export type NotePage = {
  items: NoteSummary[]
  nextCursor: string | null
  totalMatches: number
}

export type SaveResult = {
  outcome: 'committed' | 'unchanged'
  note: NoteDetail
  message: string
}

export type SyncResult = {
  outcome: string
  reachablePeers: number
  pendingChanges: number
  completedAt: string
  message: string
}

export type AdminOverview = {
  registeredAndroidDevices: number
  acceptedWebDevices: number
  connectedAndroidPeers: number
  connectedWebSessions: number
  pendingApprovals: number
  blockedOrRevoked: number
  unresolvedConflicts: number
  recentSyncFailures: number
}

export type ApiFailureBody = {
  code?: string
  message?: string
  current?: NoteDetail
}
