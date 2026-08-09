export type Member = {
  id: string
  name: string
  role: 'MEMBER' | 'ADMIN' | 'ROOT_ADMIN'
  initials: string
}

export type DeviceType = 'LAPTOP' | 'MOBILE'

export type DevicePlatform = 'WEB' | 'ANDROID' | 'IPHONE'

export type Device = {
  id: string
  name: string
  shortId: string
  type: DeviceType
  platform: DevicePlatform
  status: string
  lastSeen: string
}

export type Session = {
  member: Member
  device: Device
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
  originDeviceName: string
  originDeviceType: DeviceType
  originDevicePlatform: DevicePlatform
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
  originDeviceType: DeviceType
  originDevicePlatform: DevicePlatform
  createdAt: string
  label: string
}

export type NoteDetail = Omit<NoteSummary, 'preview'> & {
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
  registeredLaptopDevices: number
  registeredMobileDevices: number
  connectedLaptopDevices: number
  connectedMobileDevices: number
  pendingApprovals: number
  blockedOrRevoked: number
  unresolvedConflicts: number
  recentSyncFailures: number
  devices: Device[]
}

export type ApiFailureBody = {
  code?: string
  message?: string
  current?: NoteDetail
}
