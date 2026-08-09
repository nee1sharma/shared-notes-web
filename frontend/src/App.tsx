import {
  Activity,
  ArrowLeft,
  BookOpen,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleAlert,
  Clock3,
  Database,
  FileClock,
  Laptop,
  LoaderCircle,
  LogOut,
  Menu,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Save,
  Search,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Smartphone,
  Users,
  Wifi,
  WifiOff,
  X,
  type LucideIcon,
} from 'lucide-react'
import {
  createContext,
  type FormEvent,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import {
  Navigate,
  NavLink,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom'
import {
  ApiError,
  createNote,
  getAdminOverview,
  getNote,
  getSession,
  listNotes,
  saveNote,
  synchronize,
  type NoteQuery,
} from './api'
import type { AdminOverview, Device, NoteDetail, NoteSummary, Session } from './types'

type SessionContextValue = {
  session: Session
  refreshSession: () => Promise<void>
}

const SessionContext = createContext<SessionContextValue | null>(null)

function useSession() {
  const context = useContext(SessionContext)
  if (!context) throw new Error('Session context is unavailable')
  return context
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)
  const [unavailable, setUnavailable] = useState(false)

  const refreshSession = useCallback(async () => {
    const next = await getSession()
    setSession(next)
    setUnavailable(false)
  }, [])

  const connect = useCallback(async () => {
    setLoading(true)
    try {
      await refreshSession()
    } catch {
      setUnavailable(true)
    } finally {
      setLoading(false)
    }
  }, [refreshSession])

  useEffect(() => {
    void connect()
  }, [connect])

  if (loading) return <ApplicationLoading />
  if (unavailable || !session) return <BackendUnavailable onRetry={connect} />

  return (
    <SessionContext.Provider value={{ session, refreshSession }}>
      <Routes>
        <Route element={<ApplicationShell />}>
          <Route index element={<Navigate to="/notes" replace />} />
          <Route path="notes" element={<NotesWorkspace />} />
          <Route path="notes/:noteId" element={<NotesWorkspace />} />
          <Route path="connection" element={<ConnectionPage />} />
          <Route path="admin" element={<AdminPage />} />
          <Route path="*" element={<Navigate to="/notes" replace />} />
        </Route>
      </Routes>
    </SessionContext.Provider>
  )
}

function ApplicationLoading() {
  return (
    <main className="launch-screen" aria-busy="true">
      <BrandMark large />
      <div className="launch-copy">
        <p className="eyebrow">Opening the family notebook</p>
        <h1>SharedNoteBook</h1>
        <div className="loading-line"><span /></div>
      </div>
    </main>
  )
}

function BackendUnavailable({ onRetry }: { onRetry: () => Promise<void> }) {
  const [retrying, setRetrying] = useState(false)

  const retry = async () => {
    setRetrying(true)
    await onRetry()
    setRetrying(false)
  }

  return (
    <main className="unavailable-page">
      <section className="unavailable-card">
        <BrandMark large />
        <p className="eyebrow">Host device not ready</p>
        <h1>Start SharedNoteBook on this laptop</h1>
        <p className="unavailable-lede">
          The notebook opens here only while the local SharedNoteBook service and PostgreSQL are running.
          Mobile notes continue to work independently.
        </p>
        <ol className="startup-steps">
          <li><span>1</span><div><strong>Start PostgreSQL</strong><p>Confirm the shared_notebook database is available.</p></div></li>
          <li><span>2</span><div><strong>Start SharedNoteBook</strong><p>Supply the registered member, host-device, database, and security configuration.</p></div></li>
          <li><span>3</span><div><strong>Connect Android</strong><p>Only authenticated Android apps will appear as connected household devices.</p></div></li>
        </ol>
        <button className="primary-button large" onClick={() => void retry()} disabled={retrying}>
          <RefreshCw size={18} className={retrying ? 'spin' : ''} />
          {retrying ? 'Checking again…' : 'Try again'}
        </button>
        <details className="technical-details">
          <summary>Technical details</summary>
          <p>Expected local backend: http://localhost:8080</p>
        </details>
      </section>
      <p className="privacy-note"><ShieldCheck size={16} /> No notebook content is shown until a local session is ready.</p>
    </main>
  )
}

function BrandMark({ large = false }: { large?: boolean }) {
  return (
    <div className={`brand-mark${large ? ' large' : ''}`} aria-hidden="true">
      <span className="brand-mark-line" />
      <BookOpen size={large ? 31 : 23} strokeWidth={1.8} />
    </div>
  )
}

function ApplicationShell() {
  const { session } = useSession()
  const location = useLocation()
  const [mobileMenu, setMobileMenu] = useState(false)

  useEffect(() => setMobileMenu(false), [location.pathname])

  return (
    <div className="application-shell">
      <aside className={`navigation-rail${mobileMenu ? ' mobile-open' : ''}`}>
        <div className="rail-brand">
          <BrandMark />
          <div><strong>SharedNoteBook</strong><span>Family notebook</span></div>
          <button className="icon-button close-menu" onClick={() => setMobileMenu(false)} aria-label="Close navigation"><X size={20} /></button>
        </div>
        <nav className="primary-navigation" aria-label="Primary navigation">
          <RailLink to="/notes" icon={BookOpen} label="Notes" />
          <RailLink to="/connection" icon={Wifi} label="Connection" badge={session.pendingChanges || undefined} />
          <div className="navigation-separator" />
          <p className="navigation-label">Household</p>
          <RailLink to="/admin" icon={ShieldCheck} label="Admin" />
        </nav>
        <div className="rail-connection">
          <span className="connection-symbol"><Wifi size={15} /></span>
          <div><strong>Connected</strong><span>{session.reachablePeers} peers reachable</span></div>
          <ChevronRight size={16} />
        </div>
        <button className="member-card" type="button">
          <span className="avatar"><Laptop size={17} /></span>
          <span className="member-copy"><strong>{session.device.name}</strong><span>{deviceKind(session.device)} · Root admin</span></span>
          <MoreHorizontal size={18} />
        </button>
      </aside>

      <header className="mobile-app-bar">
        <button className="icon-button" onClick={() => setMobileMenu(true)} aria-label="Open navigation"><Menu size={22} /></button>
        <div className="mobile-brand"><BrandMark /><strong>SharedNoteBook</strong></div>
        <span className="avatar small"><Laptop size={15} /></span>
      </header>

      {mobileMenu && <button className="menu-scrim" onClick={() => setMobileMenu(false)} aria-label="Close navigation overlay" />}

      <div className="application-main">
        <ConnectionStrip />
        <Outlet />
      </div>

      <nav className="mobile-bottom-nav" aria-label="Mobile navigation">
        <RailLink to="/notes" icon={BookOpen} label="Notes" />
        <RailLink to="/connection" icon={Wifi} label="Connection" badge={session.pendingChanges || undefined} />
        <RailLink to="/admin" icon={ShieldCheck} label="Admin" />
      </nav>
    </div>
  )
}

function RailLink({ to, icon: Icon, label, badge }: { to: string; icon: LucideIcon; label: string; badge?: number }) {
  return (
    <NavLink to={to} className={({ isActive }) => `rail-link${isActive ? ' active' : ''}`}>
      <Icon size={19} strokeWidth={1.9} />
      <span>{label}</span>
      {badge ? <em>{badge}</em> : null}
    </NavLink>
  )
}

function ConnectionStrip() {
  const { session } = useSession()
  const pending = session.pendingChanges > 0
  return (
    <div className="connection-strip" role="status" aria-live="polite">
      <span className="connection-strip-item"><CheckCircle2 size={15} /> Connected to {session.device.name}</span>
      <span className="status-divider" />
      <span className={`connection-strip-item${pending ? ' pending' : ''}`}>
        {pending ? <Clock3 size={15} /> : <Check size={15} />}
        {!session.notesAvailable ? 'No Android notebook connected' : pending ? `Saved to ${session.device.name} · ${session.pendingChanges} mobile ${session.pendingChanges === 1 ? 'change' : 'changes'} pending` : `All edits saved to ${session.device.name}`}
      </span>
      <span className="connection-strip-peers"><Smartphone size={14} /> {session.reachablePeers} household peers reachable</span>
    </div>
  )
}

function NotesWorkspace() {
  const { noteId } = useParams()
  const navigate = useNavigate()
  const { session, refreshSession } = useSession()
  const [notes, setNotes] = useState<NoteSummary[]>([])
  const [query, setQuery] = useState('')
  const [searchField, setSearchField] = useState('TITLE')
  const [sort, setSort] = useState('MODIFIED_DESC')
  const [conflict, setConflict] = useState('')
  const [modifiedFrom, setModifiedFrom] = useState('')
  const [modifiedTo, setModifiedTo] = useState('')
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [totalMatches, setTotalMatches] = useState(0)
  const [loading, setLoading] = useState(true)
  const [moreLoading, setMoreLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [filterOpen, setFilterOpen] = useState(false)
  const [listVersion, setListVersion] = useState(0)

  const filters: NoteQuery = useMemo(() => ({
    query,
    searchField,
    sort,
    conflict,
    modifiedFrom,
    modifiedTo,
  }), [query, searchField, sort, conflict, modifiedFrom, modifiedTo])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      setListError('')
      void listNotes(filters, controller.signal)
        .then(page => {
          setNotes(page.items)
          setNextCursor(page.nextCursor)
          setTotalMatches(page.totalMatches)
        })
        .catch(error => {
          if (error instanceof DOMException && error.name === 'AbortError') return
          setListError(error instanceof Error ? error.message : 'Shared notes could not be loaded.')
        })
        .finally(() => setLoading(false))
    }, 220)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [filters, listVersion])

  const activeId = noteId ?? notes[0]?.id

  const loadMore = async () => {
    if (!nextCursor) return
    setMoreLoading(true)
    try {
      const page = await listNotes({ ...filters, cursor: nextCursor })
      setNotes(current => [...current, ...page.items.filter(next => !current.some(item => item.id === next.id))])
      setNextCursor(page.nextCursor)
    } catch (error) {
      setListError(error instanceof Error ? error.message : 'The next page could not be loaded.')
    } finally {
      setMoreLoading(false)
    }
  }

  const startNote = async () => {
    if (!session.notesAvailable) {
      setListError('Connect a registered Android app before creating a shared note from this laptop.')
      return
    }
    try {
      const result = await createNote('Untitled note', '')
      setListVersion(version => version + 1)
      await refreshSession()
      navigate(`/notes/${result.note.id}`)
    } catch (error) {
      setListError(error instanceof Error ? error.message : 'A new note could not be created.')
    }
  }

  const refreshList = () => {
    setListVersion(version => version + 1)
    void refreshSession()
  }

  const activeFilterCount = [conflict, modifiedFrom, modifiedTo].filter(Boolean).length

  return (
    <main className={`notes-workspace${noteId ? ' has-selected-route' : ''}`}>
      <section className="notes-list-pane" aria-label="Shared notes">
        <div className="notes-list-heading">
          <div>
            <p className="eyebrow">Family notebook</p>
            <h1>Shared notes</h1>
            <p>{loading ? 'Loading…' : `${totalMatches} ${totalMatches === 1 ? 'note' : 'notes'} available to everyone`}</p>
          </div>
          <button className="primary-button compact" onClick={() => void startNote()} disabled={!session.notesAvailable} title={!session.notesAvailable ? 'Connect a registered Android app first' : undefined}><Plus size={18} /> New note</button>
        </div>

        <div className="search-row">
          <label className="search-field">
            <Search size={18} />
            <span className="visually-hidden">Search shared notes by {labelForSearchField(searchField)}</span>
            <input value={query} onChange={event => setQuery(event.target.value)} placeholder={`Search by ${labelForSearchField(searchField).toLowerCase()}…`} />
            {query && <button type="button" onClick={() => setQuery('')} aria-label="Clear search"><X size={16} /></button>}
          </label>
          <div className="filter-wrap">
            <button className={`secondary-icon-button${filterOpen ? ' active' : ''}`} onClick={() => setFilterOpen(open => !open)} aria-expanded={filterOpen} aria-label="Search filters">
              <SlidersHorizontal size={18} />
              {activeFilterCount > 0 && <span>{activeFilterCount}</span>}
            </button>
            {filterOpen && (
              <div className="filter-popover">
                <div className="popover-heading"><strong>Filter shared notes</strong><button className="icon-button" onClick={() => setFilterOpen(false)} aria-label="Close filters"><X size={17} /></button></div>
                <label>Search field<select value={searchField} onChange={event => setSearchField(event.target.value)}><option value="TITLE">Title</option><option value="CREATED_BY">Created by</option><option value="LAST_EDITED_BY">Last edited by</option></select></label>
                <label>Conflict state<select value={conflict} onChange={event => setConflict(event.target.value)}><option value="">All notes</option><option value="true">Has conflict</option><option value="false">No conflict</option></select></label>
                <div className="date-filter-grid">
                  <label>Modified from<input type="date" value={modifiedFrom} onChange={event => setModifiedFrom(event.target.value)} /></label>
                  <label>Modified to<input type="date" value={modifiedTo} onChange={event => setModifiedTo(event.target.value)} /></label>
                </div>
                <button className="text-button" onClick={() => { setConflict(''); setModifiedFrom(''); setModifiedTo('') }}>Clear filters</button>
              </div>
            )}
          </div>
        </div>

        <div className="list-tools">
          <span>{query || activeFilterCount ? 'Matching notes' : 'Recently updated'}</span>
          <label>Sort <select value={sort} onChange={event => setSort(event.target.value)}><option value="MODIFIED_DESC">Recently modified</option><option value="CREATED_DESC">Recently created</option><option value="TITLE_ASC">Title A–Z</option></select></label>
        </div>

        <div className="note-list" aria-busy={loading}>
          {loading && notes.length === 0 ? <NoteListSkeleton /> : null}
          {!loading && notes.length === 0 ? (
            <div className="empty-list"><Search size={25} /><strong>{session.notesAvailable ? 'No shared notes match' : 'No Android notebook connected'}</strong><p>{session.notesAvailable ? 'Try a different search or clear the filters.' : 'Authenticated Android apps and their real registered names will appear after discovery and reconciliation.'}</p></div>
          ) : null}
          {notes.map(note => (
            <button key={note.id} className={`note-row${activeId === note.id ? ' selected' : ''}`} onClick={() => navigate(`/notes/${note.id}`)}>
              <span className="note-row-top"><strong>{note.title}</strong><time>{relativeTime(note.modifiedAt)}</time></span>
              <span className="note-preview">{note.preview || 'Empty note'}</span>
              <span className="note-row-bottom">
                <span>Edited from {note.originDeviceName} · {deviceKind(note)}</span>
                {note.conflict ? <em className="conflict-label"><CircleAlert size={13} /> Conflict</em> : note.propagationStatus === 'PENDING' ? <em className="pending-label"><Clock3 size={13} /> Sync pending</em> : <em className="saved-label"><Check size={13} /> Saved</em>}
              </span>
            </button>
          ))}
          {nextCursor && <button className="show-more-button" onClick={() => void loadMore()} disabled={moreLoading}>{moreLoading ? <LoaderCircle size={17} className="spin" /> : <Plus size={17} />}{moreLoading ? 'Loading…' : 'Show more'}</button>}
          {listError && <div className="inline-error" role="alert"><CircleAlert size={16} /><span>{listError}</span><button onClick={() => setListVersion(version => version + 1)}>Retry</button></div>}
        </div>
      </section>

      <section className="editor-pane" aria-label="Note editor">
        {activeId ? <NoteEditor key={activeId} noteId={activeId} onChanged={refreshList} /> : <NoNoteSelected onCreate={startNote} enabled={session.notesAvailable} />}
      </section>
    </main>
  )
}

function labelForSearchField(field: string) {
  if (field === 'CREATED_BY') return 'Created by'
  if (field === 'LAST_EDITED_BY') return 'Last edited by'
  return 'Title'
}

function NoteListSkeleton() {
  return <>{[0, 1, 2, 3].map(item => <div className="note-row-skeleton" key={item}><span /><span /><span /></div>)}</>
}

function NoNoteSelected({ onCreate, enabled }: { onCreate: () => Promise<void>; enabled: boolean }) {
  return (
    <div className="no-note-selected">
      <div className="paper-stack"><BookOpen size={32} /></div>
      <h2>Open a shared note</h2>
      <p>{enabled ? 'Select a note from the list or begin a new page for the household.' : 'Connect a registered Android app to load the household notebook.'}</p>
      <button className="primary-button" onClick={() => void onCreate()} disabled={!enabled}><Plus size={18} /> New shared note</button>
    </div>
  )
}

type EditorState = 'clean' | 'editing' | 'saving' | 'saved' | 'error' | 'conflict'

function NoteEditor({ noteId, onChanged }: { noteId: string; onChanged: () => void }) {
  const navigate = useNavigate()
  const [note, setNote] = useState<NoteDetail | null>(null)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [state, setState] = useState<EditorState>('clean')
  const [message, setMessage] = useState('')
  const [historyOpen, setHistoryOpen] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setMessage('')
    void getNote(noteId, controller.signal)
      .then(next => {
        setNote(next)
        setTitle(next.title)
        setBody(next.body)
        setState('clean')
      })
      .catch(error => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setMessage(error instanceof Error ? error.message : 'This note could not be opened.')
        setState('error')
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [noteId])

  const dirty = note ? title !== note.title || body !== note.body : false

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty || state === 'saving') return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, state])

  useEffect(() => {
    if (dirty && state !== 'saving') setState('editing')
  }, [dirty, state])

  const submit = async (event?: FormEvent) => {
    event?.preventDefault()
    if (!note || !title.trim() || !dirty || state === 'saving') return
    setState('saving')
    setMessage('')
    try {
      const result = await saveNote(note, title, body)
      setNote(result.note)
      setTitle(result.note.title)
      setBody(result.note.body)
      setState('saved')
      setMessage(result.message)
      onChanged()
    } catch (error) {
      if (error instanceof ApiError && error.code === 'conflict') {
        setState('conflict')
        setMessage('Another device saved a newer revision. Your draft is still here in this tab.')
      } else {
        setState('error')
        setMessage(error instanceof Error ? error.message : 'Save failed. Your draft is still in this tab.')
      }
    }
  }

  if (loading) return <EditorSkeleton />
  if (!note) return <div className="editor-error"><CircleAlert size={24} /><h2>Note unavailable</h2><p>{message}</p></div>

  return (
    <form className="note-editor" onSubmit={event => void submit(event)}>
      <div className="editor-toolbar">
        <button type="button" className="back-to-notes" onClick={() => navigate('/notes')}><ArrowLeft size={18} /> Shared notes</button>
        <div className="editor-breadcrumb"><span>Shared notes</span><ChevronRight size={14} /><strong>{note.title}</strong></div>
        <div className="editor-actions">
          <button type="button" className={`secondary-button${historyOpen ? ' active' : ''}`} onClick={() => setHistoryOpen(open => !open)}><FileClock size={17} /> History</button>
          <button type="button" className="icon-button bordered" aria-label="More note actions"><MoreHorizontal size={19} /></button>
          <button type="submit" className="primary-button" disabled={!dirty || !title.trim() || state === 'saving'}>{state === 'saving' ? <LoaderCircle size={17} className="spin" /> : <Save size={17} />}{state === 'saving' ? 'Saving…' : 'Save'}</button>
        </div>
      </div>

      <div className={`editor-content${historyOpen ? ' with-history' : ''}`}>
        <article className="editor-paper">
          {note.conflict && (
            <div className="conflict-callout" role="alert">
              <CircleAlert size={18} />
              <div><strong>This note has two versions</strong><p>Both candidates are preserved. Conflict resolution arrives in the synchronization slice.</p></div>
              <button type="button">Review</button>
            </div>
          )}
          <input className="title-input" aria-label="Note title" value={title} onChange={event => setTitle(event.target.value)} maxLength={180} />
          <div className="editor-meta">
            <span>{note.revision}</span><i />
            <span>Edited from {note.originDeviceName} · {deviceKind(note)}</span><i />
            <span>{fullDate(note.modifiedAt)}</span>
          </div>
          <textarea className="body-input" aria-label="Note body" value={body} onChange={event => setBody(event.target.value)} placeholder="Write something for the household…" spellCheck />
        </article>

        {historyOpen && (
          <aside className="history-panel" aria-label="Revision history">
            <div className="history-heading"><div><p className="eyebrow">Retained on {note.originDeviceName}</p><h2>Revision history</h2></div><button type="button" className="icon-button" onClick={() => setHistoryOpen(false)} aria-label="Close revision history"><X size={19} /></button></div>
            <p className="history-note">History is limited by household retention and what has reconciled to PostgreSQL.</p>
            <ol className="revision-list">
              {note.revisions.map((revision, index) => (
                <li key={revision.id}>
                  <span className="revision-marker">{index === 0 ? <Check size={14} /> : null}</span>
                  <div><div className="revision-title"><strong>{revision.revision}</strong><em>{revision.label}</em></div><p>{revision.origin} · {deviceKind({ type: revision.originDeviceType, platform: revision.originDevicePlatform })}</p><time>{fullDate(revision.createdAt)}</time><button type="button">Preview revision</button></div>
                </li>
              ))}
            </ol>
          </aside>
        )}
      </div>

      <div className={`editor-status ${state}`} role="status" aria-live="polite">
        <div className="status-primary">
          {state === 'saving' ? <LoaderCircle size={17} className="spin" /> : state === 'error' || state === 'conflict' ? <CircleAlert size={17} /> : dirty ? <Clock3 size={17} /> : <CheckCircle2 size={17} />}
          <span><strong>{statusTitle(state, dirty, note.originDeviceName)}</strong><small>{message || statusMessage(state, note)}</small></span>
        </div>
        <div className="propagation-state">
          {note.propagationStatus === 'PENDING' ? <Clock3 size={16} /> : <Smartphone size={16} />}
          <span><strong>{note.propagationStatus === 'PENDING' ? 'Mobile propagation pending' : 'Synchronized with reachable peers'}</strong><small>Offline devices may still have undiscovered work.</small></span>
        </div>
      </div>
    </form>
  )
}

function statusTitle(state: EditorState, dirty: boolean, deviceName: string) {
  if (state === 'saving') return `Saving to ${deviceName}`
  if (state === 'conflict') return 'Conflict'
  if (state === 'error') return 'Save failed'
  if (dirty || state === 'editing') return 'Editing · not saved yet'
  return `Saved to ${deviceName}`
}

function statusMessage(state: EditorState, note: NoteDetail) {
  if (state === 'saving') return 'Waiting for PostgreSQL acknowledgement…'
  if (state === 'editing') return 'Draft kept in this tab until you save.'
  return `${note.revision} committed durably on ${note.originDeviceName}.`
}

function EditorSkeleton() {
  return <div className="editor-skeleton"><div className="editor-toolbar-skeleton" /><div className="paper-skeleton"><span /><span /><span /><span /><span /></div></div>
}

function ConnectionPage() {
  const { session, refreshSession } = useSession()
  const [syncing, setSyncing] = useState(false)
  const [message, setMessage] = useState('')

  const syncNow = async () => {
    setSyncing(true)
    setMessage('')
    try {
      const result = await synchronize()
      setMessage(result.message)
      await refreshSession()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Synchronization could not be started.')
    } finally {
      setSyncing(false)
    }
  }

  return (
    <main className="page-shell connection-page">
      <PageHeading eyebrow="This browser" title="Connection & synchronization" description={`Saves to ${session.device.name} and mobile propagation are tracked separately, so you always know where a change reached.`} action={<button className="primary-button" onClick={() => void syncNow()} disabled={syncing}>{syncing ? <LoaderCircle className="spin" size={17} /> : <RefreshCw size={17} />}{syncing ? 'Synchronizing…' : 'Sync now'}</button>} />
      {message && <div className="success-banner" role="status"><CheckCircle2 size={18} />{message}</div>}
      <section className="status-layers" aria-label="Connection layers">
        <StatusLayer icon={Laptop} tone="good" eyebrow="Browser session" title={`Connected to ${session.device.name}`} detail={`${deviceKind(session.device)} · ${session.device.shortId}`} />
        <StatusLayer icon={Database} tone={session.databaseStatus === 'READY' ? 'good' : 'pending'} eyebrow="PostgreSQL commit" title={session.databaseStatus === 'READY' ? 'All acknowledged edits saved' : 'Persistence not connected'} detail={session.databaseStatus === 'READY' ? `${session.device.name} is ready for durable commits.` : 'Shared notes remain unavailable until PostgreSQL and Android reconciliation are connected.'} />
        <StatusLayer icon={Smartphone} tone={session.pendingChanges ? 'pending' : 'good'} eyebrow="Mobile propagation" title={session.pendingChanges ? `${session.pendingChanges} ${session.pendingChanges === 1 ? 'change' : 'changes'} waiting` : 'No pending changes known'} detail={`${session.reachablePeers} household peers globally connected`} />
      </section>

      <section className="connection-grid">
        <div className="surface-card connection-detail-card">
          <div className="card-heading"><div><p className="eyebrow">Current session</p><h2>{session.device.name}</h2></div><span className="online-chip"><Wifi size={14} /> Connected</span></div>
          <dl className="detail-list"><div><dt>Device</dt><dd>{session.device.shortId}</dd></div><div><dt>Type</dt><dd>{deviceKind(session.device)}</dd></div><div><dt>Backend</dt><dd>{session.backendName}</dd></div><div><dt>Idle timeout</dt><dd>30 minutes</dd></div><div><dt>Network scope</dt><dd>Loopback only</dd></div></dl>
          <button className="secondary-button danger-soft"><LogOut size={16} /> Disconnect this session</button>
        </div>
        <div className="surface-card sync-detail-card">
          <div className="card-heading"><div><p className="eyebrow">Household sync</p><h2>After each save</h2></div><span className="subtle-chip">Default</span></div>
          <div className="sync-stat"><span className="sync-stat-icon"><RefreshCw size={20} /></span><div><strong>{relativeTime(session.lastSynchronizedAt)}</strong><span>Last successful reconciliation</span></div></div>
          <div className="sync-stat"><span className="sync-stat-icon"><Smartphone size={20} /></span><div><strong>{session.reachablePeers} reachable peers</strong><span>Latest-known global presence</span></div></div>
          <p className="caution-copy"><CircleAlert size={16} /> Offline devices may hold work {session.device.name} has not discovered yet.</p>
        </div>
      </section>
    </main>
  )
}

function StatusLayer({ icon: Icon, tone, eyebrow, title, detail }: { icon: LucideIcon; tone: 'good' | 'pending'; eyebrow: string; title: string; detail: string }) {
  return <article className={`status-layer ${tone}`}><span className="layer-icon"><Icon size={22} /></span><div><p>{eyebrow}</p><strong>{title}</strong><span>{detail}</span></div>{tone === 'good' ? <CheckCircle2 size={18} className="layer-check" /> : <Clock3 size={18} className="layer-check" />}</article>
}

function AdminPage() {
  const { session } = useSession()
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    void getAdminOverview().then(setOverview).catch(next => setError(next instanceof Error ? next.message : 'Admin overview could not be loaded.'))
  }, [])

  return (
    <main className="page-shell admin-page">
      <PageHeading eyebrow="Household administration" title="Admin overview" description="Manage access, devices, shared-note health, and household policy from the designated host device." action={<button className="secondary-button"><Settings2 size={17} /> Household settings</button>} />
      <div className="eventual-banner"><Activity size={17} /><span><strong>Latest-known household state</strong> Device presence updates while {session.backendName} receives authenticated heartbeats.</span></div>
      {error && <div className="inline-error"><CircleAlert size={16} />{error}</div>}
      <section className="metric-grid">
        <MetricCard label="Laptop devices" value={overview?.registeredLaptopDevices} helper={`${overview?.connectedLaptopDevices ?? '—'} connected now`} icon={Laptop} />
        <MetricCard label="Mobile devices" value={overview?.registeredMobileDevices} helper={`${overview?.connectedMobileDevices ?? '—'} connected now`} icon={Smartphone} />
        <MetricCard label="Pending approvals" value={overview?.pendingApprovals} helper="Needs an admin decision" icon={Clock3} tone="attention" />
        <MetricCard label="Unresolved conflicts" value={overview?.unresolvedConflicts} helper="Both versions preserved" icon={CircleAlert} tone="attention" />
      </section>

      <section className="admin-grid">
        <div className="surface-card devices-card">
          <div className="card-heading"><div><p className="eyebrow">Connected now</p><h2>Household devices</h2></div><button className="text-button">View all <ChevronRight size={15} /></button></div>
          {overview?.devices.map(device => <DeviceRow key={device.id} device={device} />)}
          {overview && overview.devices.length === 0 ? <div className="empty-list"><WifiOff size={23} /><strong>No household devices registered</strong><p>Connected Android apps will appear with their registered member, device, app, model, and status.</p></div> : null}
        </div>

        <div className="surface-card activity-card">
          <div className="card-heading"><div><p className="eyebrow">Android applications</p><h2>Connection identity</h2></div></div>
          <div className="empty-list"><Smartphone size={25} /><strong>{overview?.connectedMobileDevices ? `${overview.connectedMobileDevices} Android ${overview.connectedMobileDevices === 1 ? 'app' : 'apps'} connected` : 'Waiting for Android'}</strong><p>This page lists only authenticated registrations and live heartbeats from the SharedNoteBook Android application.</p></div>
        </div>
      </section>
    </main>
  )
}

function MetricCard({ label, value, helper, icon: Icon, tone }: { label: string; value?: number; helper: string; icon: LucideIcon; tone?: 'attention' }) {
  return <article className={`metric-card${tone ? ` ${tone}` : ''}`}><span><Icon size={19} /></span><div><p>{label}</p><strong>{value ?? '—'}</strong><small>{helper}</small></div></article>
}

function DeviceRow({ device }: { device: Device }) {
  const pending = device.status === 'PENDING'
  const Icon = device.type === 'MOBILE' ? Smartphone : Laptop
  const statusLabel = device.status === 'CONNECTED' ? 'Connected' : device.status === 'OFFLINE' ? `Last seen ${relativeTime(device.lastSeenAt)}` : device.status.charAt(0) + device.status.slice(1).toLowerCase()
  return (
    <div className={`device-row${pending ? ' pending-device' : ''}`}>
      <span className={`device-icon${pending ? ' pending' : device.type === 'MOBILE' ? ' mobile' : ''}`}><Icon size={19} /></span>
      <div><strong>{device.name}</strong><span>{device.memberName} · {device.appName} · {device.modelName} · {device.shortId}</span></div>
      {pending
        ? <button className="secondary-button compact">Review</button>
        : <em className={device.status === 'CONNECTED' ? 'online-chip' : 'subtle-chip'}>{device.status === 'CONNECTED' ? <Wifi size={13} /> : <WifiOff size={13} />}{statusLabel}</em>}
    </div>
  )
}

function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <header className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{action && <div>{action}</div>}</header>
}

function relativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000))
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(new Date(value))
}

function fullDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function deviceKind(device: Pick<Device, 'type' | 'platform'> | Pick<NoteSummary, 'originDeviceType' | 'originDevicePlatform'>) {
  const type = 'type' in device ? device.type : device.originDeviceType
  const platform = 'platform' in device ? device.platform : device.originDevicePlatform
  if (type === 'MOBILE') return platform === 'IPHONE' ? 'Mobile · iPhone' : 'Mobile · Android'
  return platform === 'WEB' ? 'Laptop · Web' : 'Laptop'
}
