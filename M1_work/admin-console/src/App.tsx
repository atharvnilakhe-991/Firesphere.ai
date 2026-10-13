import { useEffect, useMemo, useRef, useState } from 'react';
import type { ApiError, SessionUser, AiAnalysisResponse, AiFindingDto, EvidenceResponse } from '../../packages/api-client/src/types';
import { createApiClient, normalizeApiError, parseContractMap, DEFAULT_CONTRACT_PATHS, type Operation } from '../../packages/api-client/src/client';
import { createDemoAdmin, isDemoToken, loadDemoRecords, loadDemoAiAnalyses, loadDemoEvidence, updateDemoFindingReview } from './dev/mockServices';
import { filterAndSortRows, pageRows } from './tableModel';

type PageKey = 'overview' | 'applications' | 'inspections' | 'incidents' | 'officers' | 'users' | 'organizations' | 'assignments' | 'vehicles' | 'inventory' | 'training' | 'reports' | 'certificates' | 'gis' | 'notifications' | 'settings';
type NavItem = { key: PageKey; label: string; icon: string; operation?: Operation; };

const nav: NavItem[] = [
  { key: 'overview', label: 'Overview', icon: 'grid', operation: 'admin.dashboard' },
  { key: 'applications', label: 'NOC applications', icon: 'file', operation: 'admin.applications' },
  { key: 'inspections', label: 'Inspections', icon: 'check', operation: 'admin.inspections' },
  { key: 'incidents', label: 'Incidents', icon: 'flame', operation: 'admin.incidents' },
  { key: 'officers', label: 'Officer workload', icon: 'users', operation: 'admin.officers' },
  { key: 'users', label: 'Users', icon: 'users', operation: 'admin.users' },
  { key: 'organizations', label: 'Organizations', icon: 'grid', operation: 'admin.organizations' },
  { key: 'assignments', label: 'Assignments', icon: 'check', operation: 'admin.assignments' },
  { key: 'vehicles', label: 'Vehicles', icon: 'flame', operation: 'admin.vehicles' },
  { key: 'inventory', label: 'Inventory', icon: 'file', operation: 'admin.inventory' },
  { key: 'training', label: 'Training', icon: 'check', operation: 'admin.training' },
  { key: 'reports', label: 'Reports', icon: 'file', operation: 'admin.reports' },
  { key: 'certificates', label: 'Certificates', icon: 'file', operation: 'admin.certificates' },
  { key: 'gis', label: 'GIS & resources', icon: 'map', operation: 'gis.layers' },
  { key: 'notifications', label: 'Notifications', icon: 'bell', operation: 'admin.notifications' },
  { key: 'settings', label: 'Configuration', icon: 'settings' },
];

const api = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL,
  contractPaths: {
    ...DEFAULT_CONTRACT_PATHS,
    ...parseContractMap(import.meta.env.VITE_API_CONTRACTS),
  },
});

function Icon({ name, size = 19 }: { name: string; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true as const };
  const paths: Record<string, React.ReactNode> = {
    grid: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" /></>,
    file: <><path d="M13.5 3.5h-7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-9z" /><path d="M13 3.5v6h6M8.5 14h7M8.5 17.5h7" /></>,
    check: <><path d="M20 11v1a8 8 0 1 1-4.7-7.3" /><path d="m8.5 11.5 3 3 8-9" /></>,
    flame: <><path d="M12 21c4.4 0 7-2.8 7-6.7 0-2.5-1.5-4.9-4.1-7.3.2 2.1-.5 3.1-1.4 3.8C13 7.1 11.1 4.8 8.5 3.5c.4 3.1-.4 5.1-2 7.1C5.2 12.2 5 13.4 5 14.6 5 18.5 7.7 21 12 21Z" /><path d="M12 21c-1.8 0-3-1.2-3-2.9 0-1.4.9-2.5 2.4-3.9.1 1 .7 1.7 1.5 2.2.6-.6.9-1.4.8-2.4 1.1 1 1.7 2.3 1.7 3.8 0 2-1.3 3.2-3.4 3.2Z" /></>,
    users: <><path d="M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20" /><circle cx="10" cy="7.5" r="3.5" /><path d="M16 4.4a3.5 3.5 0 0 1 0 6.8M20 20v-1.5a3.5 3.5 0 0 0-2.5-3.3" /></>,
    map: <><path d="m3.5 6 6-2.5 5 2.5 6-2.5v14l-6 2.5-5-2.5-6 2.5z" /><path d="M9.5 3.5v14M14.5 6v14" /></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></>,
    settings: <><circle cx="12" cy="12" r="3" /><path d="m19.4 15 .1.1 1.1.9-1.5 2.6-1.4-.5-.2.1-1.2.7-.3 1.5h-3l-.3-1.5-1.4-.8-1.4.5-1.5-2.6 1.1-.9v-1.6l-1.1-.9 1.5-2.6 1.4.5 1.4-.8.3-1.5h3l.3 1.5 1.4.8 1.4-.5 1.5 2.6-1.1.9z" /></>,
    search: <><circle cx="10.8" cy="10.8" r="6.5" /><path d="m16 16 4.5 4.5" /></>,
    menu: <><path d="M4 6h16M4 12h16M4 18h16" /></>,
    arrow: <><path d="M5 12h14M13 6l6 6-6 6" /></>,
    alert: <><path d="m10.3 4.8-7 12.1A2 2 0 0 0 5 20h14a2 2 0 0 0 1.7-3.1l-7-12.1a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4m0 3h.01" /></>,
  };
  return <svg {...common}>{paths[name] || paths.grid}</svg>;
}

function App() {
  const [page, setPage] = useState<PageKey>('overview');
  const [menuOpen, setMenuOpen] = useState(false);
  const [user, setUser] = useState<SessionUser | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const demo = import.meta.env.DEV && isDemoToken(token);

  const client = useMemo(() => createApiClient({
    baseUrl: import.meta.env.VITE_API_BASE_URL,
    contractPaths: {
      ...DEFAULT_CONTRACT_PATHS,
      ...parseContractMap(import.meta.env.VITE_API_CONTRACTS),
    },
    getAccessToken: () => token,
    onUnauthorized: () => { setToken(null); setUser(null); setNotice('Your session has expired. Sign in again to continue.'); },
  }), [token]);

  useEffect(() => {
    setNotice('Organization authentication contract is not connected. Development preview is available locally.');
    setAuthChecked(true);
  }, []);

  async function signIn(email: string, password: string) {
    setNotice('');
    void email; void password;
    setNotice('Organization sign-in is unavailable until the approved auth adapter is supplied. Credentials were not sent.');
  }

  function signDemo() {
    if (!import.meta.env.DEV) return;
    const session = createDemoAdmin();
    setToken(session.token); setUser(session.user); setPage('overview'); setNotice('Development preview: local fixtures only.');
  }

  async function signOut() {
    setToken(null); setUser(null); setPage('overview');
  }

  const heading = nav.find((item) => item.key === page)?.label || 'Overview';
  if (!authChecked) return <div className="boot"><div className="brand-mark"><span /></div><span>Loading FireSphere…</span></div>;
  if (!user) return <Login notice={notice} onSubmit={signIn} onDemoSubmit={import.meta.env.DEV ? signDemo : undefined} />;
  if (user.role !== 'ADMIN') return <div className="boot"><div className="state-panel"><div className="state-icon state-amber"><Icon name="alert" /></div><h2>Administrative access required</h2><p>Sign in with an account assigned administrative access. The API remains responsible for enforcing permissions.</p><button className="secondary-button" onClick={signOut}>Sign out</button></div></div>;

  return <div className="app-shell">
    <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
      <div className="brand-lockup"><div className="brand-mark"><span /></div><div><strong>FireSphere</strong><small>PUBLIC SAFETY PLATFORM</small></div><button className="icon-button mobile-close" aria-label="Close navigation" onClick={() => setMenuOpen(false)}>×</button></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav aria-label="Main navigation">{nav.map((item) => <button key={item.key} aria-current={page === item.key ? 'page' : undefined} className={`nav-link ${page === item.key ? 'active' : ''}`} onClick={() => { setPage(item.key); setMenuOpen(false); }}><Icon name={item.icon} /><span>{item.label}</span>{item.key === 'applications' && <span className="nav-dot" />}</button>)}</nav>
      <div className="sidebar-bottom"><div className="support-card"><div className="support-icon"><Icon name="alert" size={17} /></div><strong>Need assistance?</strong><p>Contact your platform administrator for access and configuration help.</p></div><div className="side-user"><div className="avatar">{initials(user.displayName)}</div><div className="side-user-meta"><strong>{user.displayName}</strong><small>{user.role.replace('_', ' ')}</small></div><button className="icon-button side-signout" aria-label="Sign out" onClick={signOut}>↗</button></div></div>
    </aside>
    {menuOpen && <button className="scrim" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
    <main className="main-area">
      <header className="topbar"><div className="topbar-left"><button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setMenuOpen(true)}><Icon name="menu" /></button><div className="breadcrumb">Workspace <span>/</span> <strong>{heading}</strong></div></div><div className="topbar-actions"><label className="search-field"><Icon name="search" size={17} /><input aria-label="Search unavailable until search API contract is configured" placeholder="Search unavailable" value={search} onChange={(event) => setSearch(event.target.value)} disabled title="Server search requires an approved API contract" /></label><span className="top-divider" /><button className="icon-button notification-action" aria-label="Notifications" onClick={() => setPage('notifications')}><Icon name="bell" /><i /></button><div className="avatar top-avatar">{initials(user.displayName)}</div></div></header>
      <div className="page-content">
        {demo && <div className="notice-banner demo-banner"><div className="notice-symbol"><Icon name="alert" size={20} /></div><div><strong>Development preview</strong><p>All records and updates are local fixtures. Nothing is sent to a FireSphere service.</p></div></div>}
        {page === 'overview' ? <Overview user={user} onNavigate={setPage} onRefresh={() => setNotice(demo ? 'Development view refreshed.' : 'Overview refreshed.')} demo={demo} /> : page === 'settings' ? <Settings /> : <FeaturePage item={nav.find((entry) => entry.key === page)!} client={client} demo={demo} onNotice={setNotice} />}
      </div>
    </main>
    {notice && <div className="toast" role="status"><Icon name="alert" size={18} /><span>{notice}</span><button onClick={() => setNotice('')} aria-label="Dismiss">×</button></div>}
  </div>;
}

function Login({ notice, onSubmit, onDemoSubmit }: { notice: string; onSubmit: (email: string, password: string) => void; onDemoSubmit?: () => void }) {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [busy, setBusy] = useState(false);
  async function submit(event: React.FormEvent) { event.preventDefault(); setBusy(true); await onSubmit(email, password); setBusy(false); }
  const integrationMissing = notice.includes('contract') || notice.includes('API endpoint');
  return <main className="login-page"><div className="login-art"><div className="login-art-grid" /><div className="login-brand"><div className="brand-mark"><span /></div><strong>FireSphere</strong><small>FIRE ADMINISTRATION PLATFORM</small></div><div className="login-message"><span className="eyebrow">COORDINATION • COMPLIANCE • RESPONSE</span><h1>Clarity for the work that protects communities.</h1><p>A secure workspace for fire administration teams to coordinate operations and oversee service delivery.</p><div className="login-art-footer"><span className="live-dot" /> PUBLIC SAFETY OPERATIONS</div></div></div><section className="login-panel"><form className="login-form" onSubmit={submit}><div className="login-heading"><span className="eyebrow">ADMINISTRATIVE CONSOLE</span><h2>Welcome back</h2><p>Sign in with your organization account.</p></div>{notice && <div className={`inline-alert ${integrationMissing ? 'info' : ''}`} role="alert"><Icon name="alert" size={18} /><span>{integrationMissing ? 'Authentication service is not connected. Configure the approved API contract to sign in.' : notice}</span></div>}<label>Email address<input type="email" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@organization.gov" required /></label><label>Password<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" required /></label><div className="form-row"><label className="remember"><input type="checkbox" /> Keep me signed in</label><button type="button" className="text-button" onClick={() => alert('Contact your organization administrator to reset your password.')}>Forgot password?</button></div><button className="primary-button full-button" disabled={busy}>{busy ? 'Signing in…' : 'Sign in securely'}<Icon name="arrow" size={17} /></button>{onDemoSubmit && <button type="button" className="demo-login" onClick={onDemoSubmit}>Open admin development preview</button>}<div className="login-secure"><span>♧</span> Secure sign-in · Access managed by your organization</div></form><footer className="login-footer">FireSphere Administration <span>•</span> Help & support</footer></section></main>;
}

function Overview({ user, onNavigate, onRefresh, demo }: { user: SessionUser; onNavigate: (page: PageKey) => void; onRefresh: () => void; demo: boolean }) {
  const configResult = useLoad('app.config', api, demo);
  const demoRows = useLoad('admin.dashboard', api, demo);
  return <>
    <div className="page-heading"><div><div className="eyebrow">{new Intl.DateTimeFormat('en', { weekday: 'long', month: 'long', day: '2-digit', year: 'numeric' }).format(new Date()).toUpperCase()}</div><h1>Good morning, {user.displayName.split(' ')[0]}</h1><p>Here’s your operational overview for today.</p></div><button className="secondary-button" onClick={() => { onRefresh(); void configResult.reload(); }}><span className="refresh-symbol">↻</span> Refresh</button></div>
    {configResult.error && configResult.error.code !== 'API_CONTRACT_UNAVAILABLE' && configResult.error.code !== 'API_NOT_CONFIGURED' && <ErrorBanner message={configResult.error.message} onRetry={configResult.reload} />}
    <div className="notice-banner"><div className="notice-symbol"><Icon name="alert" size={20} /></div><div><strong>{demo ? 'Development fixture dataset' : 'Connect an approved FireSphere service'}</strong><p>{demo ? 'The following values are sample data for frontend review and are not operational totals.' : 'Live totals, assignments and service activity will appear here when the API contract is configured.'}</p></div><button onClick={() => onNavigate('settings')}>View setup <Icon name="arrow" size={16} /></button></div>
    <div className="section-title-row"><div><h2>Operational snapshot</h2><p>{demo ? 'Local preview record counts.' : 'Live data will populate after service connection.'}</p></div><span className="data-state"><i /> {demo ? 'DEMO DATA' : 'Awaiting connection'}</span></div>
    <div className="metric-grid">{[['Open NOC applications', demo && Array.isArray(demoRows.data) ? String(demoRows.data.length) : '—', 'file', '#eff5ff', '#4873c8'], ['Inspections today', '—', 'check', '#eef8f4', '#39846e'], ['Active incidents', '—', 'flame', '#fff2ef', '#c76652'], ['Officers on duty', '—', 'users', '#f4f0ff', '#7964bb']].map(([label, value, icon, tint, color]) => <div className="metric-card" key={label}><div className="metric-top"><span>{label}</span><span className="metric-icon" style={{ background: tint, color }}><Icon name={icon} /></span></div><strong>{value}</strong><small>{demo ? 'Local preview' : 'API data unavailable'}</small></div>)}</div>
    <div className="content-grid"><section className="panel"><div className="panel-heading"><div><h2>Work queue</h2><p>Priority work across your organization</p></div><button className="quiet-button" onClick={() => onNavigate('applications')}>Open queue <Icon name="arrow" size={15} /></button></div><EmptyState icon="file" title="Nothing to display yet" body="Applications and assignments will appear when the service is connected." /></section><section className="panel activity-panel"><div className="panel-heading"><div><h2>Service activity</h2><p>Latest events across the platform</p></div><span className="muted-chip">LIVE</span></div><EmptyState icon="bell" title="No activity available" body="Notifications and recent events are loaded from the approved API." /></section></div>
    <div className="lower-grid"><section className="panel quick-panel"><div className="panel-heading"><div><h2>Common tasks</h2><p>Jump into a workspace</p></div></div><div className="quick-links"><button onClick={() => onNavigate('applications')}><span className="quick-icon blue"><Icon name="file" /></span><span><strong>Review applications</strong><small>NOC application monitoring</small></span><Icon name="arrow" size={17} /></button><button onClick={() => onNavigate('inspections')}><span className="quick-icon green"><Icon name="check" /></span><span><strong>Inspection activity</strong><small>Schedules and completion</small></span><Icon name="arrow" size={17} /></button><button onClick={() => onNavigate('gis')}><span className="quick-icon amber"><Icon name="map" /></span><span><strong>Open GIS layers</strong><small>Stations and response assets</small></span><Icon name="arrow" size={17} /></button></div></section><section className="panel readiness-panel"><div className="panel-heading"><div><h2>Platform readiness</h2><p>Client connection status</p></div></div><div className="readiness-row"><span><i className="state-dot neutral" /> API contract</span><strong>Not configured</strong></div><div className="readiness-row"><span><i className="state-dot neutral" /> Live data</span><strong>Unavailable</strong></div><div className="readiness-row"><span><i className="state-dot neutral" /> Workspace role</span><strong>{user.role}</strong></div><button className="text-button setup-link" onClick={() => onNavigate('settings')}>Review integration settings <Icon name="arrow" size={15} /></button></section></div>
  </>;
}

function FeaturePage({ item, client, demo, onNotice }: { item: NavItem; client: ReturnType<typeof createApiClient>; demo: boolean; onNotice: (message: string) => void }) {
  const result = useLoad(item.operation, client, demo);
  const [query, setQuery] = useState(''); const [status, setStatus] = useState('All'); const [sortKey, setSortKey] = useState('id'); const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc'); const [page, setPage] = useState(0); const [selected, setSelected] = useState<Record<string, unknown> | null>(null);
  const [localRows, setLocalRows] = useState<Record<string, unknown>[]>([]); const [draftFields, setDraftFields] = useState<Record<string, string>>({});
  const drawerRef = useRef<HTMLElement | null>(null); const returnFocusRef = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!selected) return;
    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    drawerRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setSelected(null); return }
      if (event.key !== 'Tab' || !drawerRef.current) return;
      const nodes = Array.from(drawerRef.current.querySelectorAll<HTMLElement>('a[href],button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex]:not([tabindex="-1"])'));
      if (!nodes.length) { event.preventDefault(); drawerRef.current.focus(); return }
      const first = nodes[0], last = nodes[nodes.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === drawerRef.current || !drawerRef.current.contains(document.activeElement))) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && (document.activeElement === last || !drawerRef.current.contains(document.activeElement))) { event.preventDefault(); first.focus() }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => { document.removeEventListener('keydown', handleKeyDown); returnFocusRef.current?.focus() };
  }, [selected]);
  const titles: Record<PageKey, string> = { overview: '', applications: 'NOC applications', inspections: 'Inspections', incidents: 'Incidents', officers: 'Officer workload', users: 'Users', organizations: 'Organizations', assignments: 'Assignments', vehicles: 'Vehicles', inventory: 'Inventory', training: 'Training', reports: 'Reports', certificates: 'Certificates', gis: 'GIS & response resources', notifications: 'Notifications', settings: '' };
  const descriptions: Record<PageKey, string> = { overview: '', applications: 'Review applications and presentation states.', inspections: 'Review schedules, assignments, and locally visible status.', incidents: 'Incident presentation module; no dispatch service is connected.', officers: 'Review officer workload presentations.', users: 'User administration presentation.', organizations: 'Organization administration presentation.', assignments: 'Assignment distribution presentation.', vehicles: 'Fleet presentation; no live vehicles are loaded.', inventory: 'Inventory presentation; no live stock data is loaded.', training: 'Training records presentation.', reports: 'Report review presentation.', certificates: 'Certificate list presentation; no official certificate is generated.', gis: 'Map and resource integration presentation.', notifications: 'Notification inbox presentation.', settings: '' };
  const records = Array.isArray(result.data) ? result.data.filter((row): row is Record<string, unknown> => Boolean(row) && typeof row === 'object') as Record<string, unknown>[] : [];
  const localIds = new Set(localRows.map((row) => String(row.id ?? '')));
  const rows = [...localRows, ...records.filter((row) => !localIds.has(String(row.id ?? '')))];
  const filtered = filterAndSortRows(rows, { query, status, sortKey, direction: sortDirection });
  const pageSize = 10; const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); const visibleRows = pageRows(filtered, Math.min(page, totalPages - 1), pageSize);
  const statuses = ['All', ...new Set(rows.map((row) => String(row.status ?? '')).filter(Boolean))];
  function selectRecord(row: Record<string, unknown>) { setSelected(row); setDraftFields(Object.fromEntries(Object.entries(row).filter(([, value]) => ['string', 'number', 'boolean'].includes(typeof value)).map(([key, value]) => [key, String(value ?? '')]))) }
  function saveLocalRecord() { if (!demo) return; const next = { ...selected, ...draftFields } as Record<string, unknown>; if (!Object.entries(next).some(([key, value]) => key !== 'id' && String(value).trim())) { onNotice('Add at least one field before saving this local draft.'); return } setLocalRows((existing) => [next, ...existing.filter((row) => row.id !== next.id)]); setSelected(null); onNotice('Saved to this browser session only. No service request was made.') }
  return <><div className="page-heading"><div><div className="eyebrow">OPERATIONS / {item.label.toUpperCase()}</div><h1>{titles[item.key]}</h1><p>{descriptions[item.key]}</p></div>{item.key === 'notifications' && <button className="secondary-button" disabled={!demo} onClick={() => onNotice('Development notice marked read in this page session.')}>Mark all as read</button>}</div><div className="content-toolbar"><label className="module-search"><Icon name="search" size={16} /><input aria-label={`Search ${item.label}`} placeholder={`Search ${item.label.toLowerCase()}`} value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} /></label><label className="module-filter">Status<select aria-label="Filter by status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>{statuses.map((value) => <option key={value}>{value}</option>)}</select></label>{demo && <button className="secondary-button" onClick={() => selectRecord({ id: `local-new-${Date.now()}`, name: '', status: 'DRAFT' })}>New development record</button>}<label className="module-filter">Sort<select aria-label="Sort records" value={sortKey + '|' + sortDirection} onChange={(event) => { const [key, direction] = event.target.value.split('|'); setSortKey(key); setSortDirection(direction as 'asc' | 'desc') }}><option value="id|asc">Reference · ascending</option><option value="id|desc">Reference · descending</option><option value="name|asc">Name · ascending</option><option value="name|desc">Name · descending</option></select></label><div className="toolbar-info"><span className="data-state"><i /> {demo ? 'LOCAL FIXTURES' : 'API contract view'}</span></div></div>{result.loading ? <div className="panel loading-panel"><span className="spinner" /> Loading service data…</div> : result.error ? <ContractState operation={item.operation} error={result.error} onRetry={result.reload} /> : <div className="panel data-panel"><div className="panel-heading"><div><h2>{item.label}</h2><p>{demo ? 'Deterministic local fixtures for UI review.' : 'Rows are returned by the configured approved API contract.'}</p></div></div><DataTable label={item.label} data={visibleRows} onSelect={selectRecord} />{!filtered.length && <EmptyState icon="search" title="No matching records" body="Adjust the search term or status filter." />}</div>}{filtered.length > pageSize && <div className="pagination"><button className="secondary-button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>Previous</button><span>Page {Math.min(page, totalPages - 1) + 1} of {totalPages}</span><button className="secondary-button" disabled={page >= totalPages - 1} onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))}>Next</button></div>}{selected && (item.key === 'inspections' ? <OfficerInspectionReviewDrawer inspection={selected} client={client} demo={demo} onClose={() => setSelected(null)} onNotice={onNotice} /> : <div className="detail-backdrop" role="presentation" onClick={() => setSelected(null)}><section className="detail-drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" tabIndex={-1} ref={drawerRef} onClick={(event) => event.stopPropagation()}><div className="drawer-heading"><div><span className="eyebrow">{demo ? 'LOCAL DEVELOPMENT RECORD' : 'RECORD DETAIL'}</span><h2 id="drawer-title">{String(draftFields.reference ?? draftFields.name ?? draftFields.title ?? selected.id ?? item.label)}</h2></div><button className="icon-button" aria-label="Close details" onClick={() => setSelected(null)}>×</button></div>{Object.keys(draftFields).map((key) => <label className="drawer-label" key={key}>{key.replace(/([A-Z])/g, ' $1').replaceAll('_', ' ')}<input value={draftFields[key]} disabled={!demo} onChange={(event) => setDraftFields((fields) => ({ ...fields, [key]: event.target.value }))} /></label>)}{demo && <div className="drawer-actions"><button className="secondary-button" onClick={() => setSelected(null)}>Cancel</button><button className="primary-button" onClick={saveLocalRecord}>Save local preview</button></div>}</section></div>)}</>;
}

function DataTable({ label, data, onSelect }: { label: string; data: unknown; onSelect?: (row: Record<string, unknown>) => void }) {
  if (!Array.isArray(data) || !data.length) return <EmptyState icon="file" title="No records found" body="There are no matching records in the service response." />;
  const rows = data as Record<string, unknown>[];
  const cols = Object.keys(rows[0]).slice(0, 5);
  return <div className="table-scroll" role="region" aria-label={`${label} records table`} tabIndex={0}><table><thead><tr>{cols.map((key) => <th scope="col" key={key}>{key.replace(/([A-Z])/g, ' $1').replaceAll('_', ' ')}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}>{cols.map((col, columnIndex) => <td key={col}>{columnIndex === 0 && onSelect ? <button type="button" className="row-action" aria-label={`Open details for ${formatCell(row[col])}`} onClick={() => onSelect(row)}>{formatCell(row[col])}</button> : formatCell(row[col])}</td>)}</tr>)}</tbody></table></div>;
} function formatCell(value: unknown) { if (value === null || value === undefined || typeof value === 'object') return '—'; return String(value); }

function Settings() {
  const apiOrigin = import.meta.env.VITE_API_BASE_URL || '';
  const paths = parseContractMap(import.meta.env.VITE_API_CONTRACTS);
  const configured = Object.keys(paths).length;
  const validUrl = !apiOrigin || (apiOrigin.startsWith('https://') || import.meta.env.DEV && apiOrigin.startsWith('http://localhost'));
  return <><div className="page-heading"><div><div className="eyebrow">WORKSPACE / SETUP</div><h1>Integration settings</h1><p>Connection details for your authorized FireSphere service.</p></div></div><div className="settings-grid"><section className="panel settings-panel"><div className="panel-heading"><div><h2>API connection</h2><p>Runtime connection status for this console</p></div><span className={`status-pill ${apiOrigin && configured ? 'status-neutral' : 'status-warn'}`}>{apiOrigin && configured ? 'Configured' : 'Needs setup'}</span></div><div className="settings-field"><span>API origin</span><code>{apiOrigin || 'Not configured'}</code></div><div className="settings-field"><span>Contract operations</span><code>{configured} mapped</code></div><div className="settings-field"><span>Required API prefix</span><code>/api/v1</code></div><div className="setup-note"><Icon name="alert" /><p>Set <code>VITE_API_BASE_URL</code> and <code>VITE_API_CONTRACTS</code> from the approved backend specification. Contract mappings accept operation IDs and relative paths; no payloads or endpoint names are assumed by this client.</p></div>{!validUrl && <ErrorBanner message="Production API endpoints must use HTTPS." />}</section><section className="panel settings-panel"><div className="panel-heading"><div><h2>Session & access</h2><p>Access is checked by the backend on every request.</p></div></div><div className="settings-field"><span>Role display</span><code>Server-provided session role</code></div><div className="settings-field"><span>Organization scope</span><code>Server-authoritative</code></div><div className="setup-note info-note"><p>Role-aware UI controls are display behavior only. Authorization, tenant isolation, and resource access must be enforced by the API.</p></div></section><section className="panel settings-panel"><div className="panel-heading"><div><h2>Product readiness</h2><p>Service capabilities unavailable without approved contracts.</p></div></div><div className="readiness-row"><span>NOC workflows</span><strong>Awaiting API spec</strong></div><div className="readiness-row"><span>Offline inspection sync</span><strong>Mobile client setup</strong></div><div className="readiness-row"><span>GIS layers</span><strong>Awaiting API spec</strong></div><div className="readiness-row"><span>Notifications</span><strong>Awaiting API spec</strong></div></section></div></>;
}

function useLoad<T = unknown>(operation?: Operation, client: ReturnType<typeof createApiClient> = api, demo = false) {
  const [data, setData] = useState<T | null>(null); const [error, setError] = useState<ApiError | null>(null); const [loading, setLoading] = useState(Boolean(operation)); const [count, setCount] = useState(0);
  const reload = () => setCount((value) => value + 1);
  useEffect(() => { if (!operation) return; let live = true; setLoading(true); setError(null); if (demo) { setData(loadDemoRecords(operation) as T); setLoading(false); return () => { live = false } } client.request<T>(operation).then((result) => { if (live) setData(result); }).catch((caught: unknown) => { if (live) { const normalized = caught instanceof Error && 'code' in caught ? caught as ApiError : normalizeApiError(caught); setError(normalized); } }).finally(() => { if (live) setLoading(false); }); return () => { live = false; }; }, [operation, client, count, demo]);
  return { data, error, loading, reload };
}
function ContractState({ operation, error, onRetry }: { operation?: Operation; error: ApiError; onRetry: () => void }) {
  const contractMissing = error.code === 'API_CONTRACT_UNAVAILABLE' || error.code === 'API_NOT_CONFIGURED';
  return <div className="panel state-panel"><div className={`state-icon ${contractMissing ? 'state-amber' : 'state-red'}`}><Icon name={contractMissing ? 'settings' : 'alert'} size={24} /></div><span className="eyebrow">{contractMissing ? 'SERVICE NOT CONNECTED' : 'UNABLE TO LOAD'}</span><h2>{contractMissing ? 'This view needs an approved API connection' : 'We couldn’t load this view'}</h2><p>{error.message}</p>{operation && <code className="operation-code">Operation: {operation}</code>}{!contractMissing && <button className="secondary-button" onClick={onRetry}>Retry <span className="refresh-symbol">↻</span></button>}</div>;
}
function ErrorBanner({ message, onRetry }: { message: string; onRetry?: () => void }) { return <div className="error-banner"><Icon name="alert" size={18} /><span>{message}</span>{onRetry && <button onClick={onRetry}>Retry</button>}</div>; }
function EmptyState({ icon, title, body }: { icon: string; title: string; body: string }) { return <div className="empty-state"><div className="empty-icon"><Icon name={icon} size={21} /></div><strong>{title}</strong><p>{body}</p></div>; }
function initials(name: string) { return name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase(); }

function parseBoundingBox(jsonStr?: string): { top: number; left: number; width: number; height: number } | null {
  if (!jsonStr) return null;
  try {
    const parsed = JSON.parse(jsonStr) as Record<string, unknown> | number[];
    let ymin = 0, xmin = 0, ymax = 0, xmax = 0;
    if (Array.isArray(parsed) && parsed.length >= 4) {
      [ymin, xmin, ymax, xmax] = parsed as number[];
    } else if (typeof parsed === 'object' && parsed !== null) {
      const rec = parsed as Record<string, number>;
      ymin = Number(rec.yMin ?? rec.ymin ?? rec.top ?? 0);
      xmin = Number(rec.xMin ?? rec.xmin ?? rec.left ?? 0);
      ymax = Number(rec.yMax ?? rec.ymax ?? rec.bottom ?? (ymin + (rec.height ?? 0)));
      xmax = Number(rec.xMax ?? rec.xmax ?? rec.right ?? (xmin + (rec.width ?? 0)));
    }
    const top = Math.max(0, Math.min(100, ymin * 100));
    const left = Math.max(0, Math.min(100, xmin * 100));
    const width = Math.max(0, Math.min(100 - left, (xmax - xmin) * 100));
    const height = Math.max(0, Math.min(100 - top, (ymax - ymin) * 100));
    return { top, left, width, height };
  } catch {
    return null;
  }
}

function getEvidenceImageUrl(evidence: EvidenceResponse | { dataUrl?: string; fileName?: string; id?: string }, demo: boolean): string {
  void demo;
  if ('dataUrl' in evidence && evidence.dataUrl) return evidence.dataUrl;
  const fileName = evidence.fileName || 'Inspection Photo';
  const id = evidence.id || 'EVID-01';
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400" viewBox="0 0 600 400">
    <rect width="600" height="400" fill="#1b2832"/>
    <rect x="20" y="20" width="560" height="360" rx="8" fill="#243440" stroke="#374b59" stroke-width="2"/>
    <circle cx="300" cy="180" r="45" fill="#314656"/>
    <path d="M220 280 L280 200 L340 260 L380 220 L440 280 Z" fill="#3c5568"/>
    <text x="300" y="325" fill="#8ca0ae" font-family="system-ui, sans-serif" font-size="14" font-weight="600" text-anchor="middle">${fileName}</text>
    <text x="300" y="348" fill="#5c7280" font-family="system-ui, sans-serif" font-size="11" text-anchor="middle">Evidence ID: ${id}</text>
  </svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function OfficerInspectionReviewDrawer({
  inspection,
  client,
  demo,
  onClose,
  onNotice,
}: {
  inspection: Record<string, unknown>;
  client: ReturnType<typeof createApiClient>;
  demo: boolean;
  onClose: () => void;
  onNotice: (message: string) => void;
}) {
  const drawerRef = useRef<HTMLElement | null>(null);
  const inspectionId = String(inspection.id ?? '');
  const applicationId = String(inspection.applicationId ?? inspection.reference ?? inspection.id ?? '');
  const [analyses, setAnalyses] = useState<AiAnalysisResponse[]>([]);
  const [evidenceList, setEvidenceList] = useState<EvidenceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<string | null>(null);
  const [activeFindingId, setActiveFindingId] = useState<string | null>(null);
  const [reviewStates, setReviewStates] = useState<Record<string, {
    status: 'CONFIRMED' | 'DISMISSED' | 'MODIFIED';
    notes: string;
    hazardVerified: boolean;
    submitting: boolean;
    feedback: string | null;
  }>>({});

  useEffect(() => {
    let active = true;
    setLoading(true);
    if (demo) {
      const demoAnalyses = loadDemoAiAnalyses(applicationId);
      const demoEv = loadDemoEvidence(inspectionId);
      setAnalyses(demoAnalyses as unknown as AiAnalysisResponse[]);
      setEvidenceList(demoEv as unknown as EvidenceResponse[]);
      if (demoEv.length > 0) setSelectedEvidenceId(demoEv[0].id);
      setLoading(false);
    } else {
      Promise.allSettled([
        client.request<AiAnalysisResponse[]>('officer.ai.analyses', {}, { applicationId }),
        client.request<EvidenceResponse[]>('officer.evidence.list', {}, { inspectionId }),
      ]).then(([analysesRes, evidenceRes]) => {
        if (!active) return;
        if (analysesRes.status === 'fulfilled') {
          const data = analysesRes.value;
          setAnalyses(Array.isArray(data) ? data : data ? [data] : []);
        } else {
          setAnalyses([]);
        }
        if (evidenceRes.status === 'fulfilled') {
          const list = Array.isArray(evidenceRes.value) ? evidenceRes.value : [];
          setEvidenceList(list);
          if (list.length > 0) setSelectedEvidenceId(list[0].id);
        } else {
          setEvidenceList([]);
        }
        setLoading(false);
      });
    }
    return () => { active = false; };
  }, [inspectionId, applicationId, demo, client]);

  useEffect(() => {
    drawerRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { onClose(); return; }
      if (event.key !== 'Tab' || !drawerRef.current) return;
      const nodes = Array.from(drawerRef.current.querySelectorAll<HTMLElement>(
        'a[href],button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex]:not([tabindex="-1"])'
      ));
      if (!nodes.length) { event.preventDefault(); drawerRef.current.focus(); return; }
      const first = nodes[0], last = nodes[nodes.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === drawerRef.current || !drawerRef.current.contains(document.activeElement))) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !drawerRef.current.contains(document.activeElement))) {
        event.preventDefault(); first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const activeRun = analyses[0];
  const findings: AiFindingDto[] = activeRun?.findings ?? [];
  const currentEvidence = evidenceList.find((e) => e.id === selectedEvidenceId) || evidenceList[0];

  const handleReviewSubmit = async (findingId: string) => {
    const currentState = reviewStates[findingId] || {
      status: 'CONFIRMED',
      notes: '',
      hazardVerified: false,
      submitting: false,
      feedback: null,
    };
    if (!activeRun) return;

    setReviewStates((prev) => ({
      ...prev,
      [findingId]: { ...currentState, submitting: true, feedback: null },
    }));

    try {
      if (demo) {
        updateDemoFindingReview(
          applicationId,
          activeRun.runId,
          findingId,
          currentState.status,
          currentState.notes,
          currentState.hazardVerified
        );
        setAnalyses((prevAnalyses) =>
          prevAnalyses.map((run) =>
            run.runId === activeRun.runId
              ? {
                  ...run,
                  findings: run.findings.map((f) =>
                    f.id === findingId
                      ? {
                          ...f,
                          reviewStatus: currentState.status,
                          officerNotes: currentState.notes,
                          hazardVerified: currentState.hazardVerified,
                        }
                      : f
                  ),
                }
              : run
          )
        );
        setReviewStates((prev) => ({
          ...prev,
          [findingId]: {
            ...currentState,
            submitting: false,
            feedback: 'Officer verification recorded (Demo).',
          },
        }));
        onNotice('Officer finding review saved to development session.');
      } else {
        await client.request<unknown>(
          'officer.finding.review',
          {
            method: 'POST',
            body: JSON.stringify({
              reviewStatus: currentState.status,
              officerNotes: currentState.notes,
              hazardVerified: currentState.hazardVerified,
            }),
          },
          {
            applicationId,
            runId: activeRun.runId,
            findingId,
          }
        );
        setAnalyses((prevAnalyses) =>
          prevAnalyses.map((run) =>
            run.runId === activeRun.runId
              ? {
                  ...run,
                  findings: run.findings.map((f) =>
                    f.id === findingId
                      ? {
                          ...f,
                          reviewStatus: currentState.status,
                          officerNotes: currentState.notes,
                          hazardVerified: currentState.hazardVerified,
                        }
                      : f
                  ),
                }
              : run
          )
        );
        setReviewStates((prev) => ({
          ...prev,
          [findingId]: {
            ...currentState,
            submitting: false,
            feedback: 'Officer verification recorded in backend.',
          },
        }));
        onNotice('Officer finding review recorded successfully in M2.');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to record officer review.';
      setReviewStates((prev) => ({
        ...prev,
        [findingId]: {
          ...currentState,
          submitting: false,
          feedback: `Error: ${msg}`,
        },
      }));
    }
  };

  return (
    <div className="detail-backdrop" role="presentation" onClick={onClose}>
      <section
        className="officer-review-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="officer-drawer-title"
        tabIndex={-1}
        ref={drawerRef}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="drawer-heading">
          <div>
            <span className="eyebrow">FIELD INSPECTION · OFFICER REVIEW &amp; AI ADVISORY</span>
            <h2 id="officer-drawer-title">
              {String(inspection.reference ?? inspection.id ?? 'Inspection Review')}
            </h2>
          </div>
          <button className="icon-button" aria-label="Close review drawer" onClick={onClose}>
            ×
          </button>
        </div>

        <div className="review-drawer-content">
          {/* Statutory Advisory Warning Banner */}
          <div className="statutory-alert-box" role="alert">
            <div style={{ flex: '0 0 auto', color: '#d96e4d' }}>
              <Icon name="alert" size={22} />
            </div>
            <div>
              <strong>STATUTORY ADVISORY NOTICE — OFFICER VERIFICATION MANDATORY</strong>
              <p>
                AI observations are advisory visual inputs for authorized officer verification. Automated findings do NOT constitute statutory Fire NOC approval or compliance certification. The authorized officer retains sole statutory decision-making authority.
              </p>
            </div>
          </div>

          {/* Model Governance Strip */}
          <div className="model-governance-strip">
            <span className="gov-pill cv-status">
              CV Detection: <strong>{activeRun?.cvStatus || 'NOT_APPROVED_GATED'}</strong>
            </span>
            <span className="gov-pill vlm-status">
              VLM Scene Analysis: <strong>{activeRun?.vlmStatus || 'UNAVAILABLE'}</strong>
            </span>
            <span className="gov-pill calibration">
              Calibration: <strong>{activeRun?.calibrationStatus || 'NOT_CALIBRATED'}</strong>
            </span>
            <span className="gov-pill reconciliation">
              Reconciliation: <strong>{activeRun?.reconciliationStatus || 'SINGLE_PROVIDER'}</strong>
            </span>
            <span className="gov-pill" style={{ background: '#f0f3f5', color: '#566874' }}>
              Model: <strong>{activeRun?.modelName || 'baseline-v0.8.0'}</strong>
            </span>
          </div>

          {/* Inspection Metadata Summary */}
          <div className="inspection-meta-summary">
            <div className="meta-box">
              <span>Inspection Ref</span>
              <strong>{String(inspection.reference ?? inspection.id ?? '—')}</strong>
            </div>
            <div className="meta-box">
              <span>Application ID</span>
              <strong>{applicationId || '—'}</strong>
            </div>
            <div className="meta-box">
              <span>Status</span>
              <strong>{String(inspection.status ?? '—')}</strong>
            </div>
            <div className="meta-box">
              <span>Assigned Officer</span>
              <strong>{String(inspection.assignee ?? inspection.officer ?? 'Authorized Officer')}</strong>
            </div>
          </div>

          {/* Evidence Photos Viewer */}
          <div>
            <div className="section-subtitle">
              <span>Original Inspection Evidence Photos ({evidenceList.length})</span>
              <small style={{ fontSize: '11px', color: '#7a8c99', fontWeight: 500 }}>
                {evidenceList.length ? 'Select photo to view spatial observations' : 'No photos attached'}
              </small>
            </div>

            {evidenceList.length === 0 ? (
              <div className="evidence-viewer-card" style={{ padding: '24px', textAlign: 'center', color: '#7a8c99', fontSize: '12px' }}>
                No photographic evidence has been uploaded for this inspection.
              </div>
            ) : (
              <div className="evidence-viewer-card">
                {evidenceList.length > 1 && (
                  <div className="evidence-thumbnails-row">
                    {evidenceList.map((ev, idx) => (
                      <button
                        key={ev.id}
                        type="button"
                        className={`evidence-thumb-btn ${ev.id === currentEvidence?.id ? 'selected' : ''}`}
                        onClick={() => setSelectedEvidenceId(ev.id)}
                      >
                        <Icon name="file" size={14} />
                        <span>Photo #{idx + 1}: {ev.fileName}</span>
                      </button>
                    ))}
                  </div>
                )}

                {currentEvidence && (
                  <>
                    <div className="evidence-stage">
                      <img
                        src={getEvidenceImageUrl(currentEvidence, demo)}
                        alt={currentEvidence.fileName || 'Evidence'}
                        className="evidence-img-main"
                      />

                      {/* Overlaid spatial bounding boxes */}
                      <div className="spatial-overlay-layer">
                        {findings
                          .filter((f) => !f.evidenceId || f.evidenceId === currentEvidence.id)
                          .map((f) => {
                            const box = parseBoundingBox(f.boundingBoxJson);
                            if (!box) return null;
                            const isSelected = f.id === activeFindingId;
                            return (
                              <div
                                key={f.id}
                                className="spatial-bbox-box"
                                style={{
                                  top: `${box.top}%`,
                                  left: `${box.left}%`,
                                  width: `${box.width}%`,
                                  height: `${box.height}%`,
                                  borderColor: isSelected ? '#ff2a1a' : '#ef754c',
                                  background: isSelected ? 'rgba(255, 42, 26, 0.25)' : 'rgba(239, 117, 76, 0.15)',
                                }}
                              >
                                <span className="spatial-bbox-tag">
                                  {f.findingType || f.finding} ({Math.round(f.confidence * 100)}%)
                                </span>
                              </div>
                            );
                          })}
                      </div>
                    </div>

                    <div className="evidence-details-footer">
                      <span><strong>File:</strong> {currentEvidence.fileName}</span>
                      <span><strong>Size:</strong> {(currentEvidence.fileSizeBytes / 1024).toFixed(1)} KB</span>
                      <span><strong>SHA256:</strong> {currentEvidence.sha256Hash?.slice(0, 16)}…</span>
                      {currentEvidence.gpsLatitude && (
                        <span><strong>GPS:</strong> {currentEvidence.gpsLatitude.toFixed(4)}, {currentEvidence.gpsLongitude?.toFixed(4)}</span>
                      )}
                      {currentEvidence.captureTimestamp && (
                        <span><strong>Captured:</strong> {new Date(currentEvidence.captureTimestamp).toLocaleString()}</span>
                      )}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>

          {/* Consolidated AI Advisory Findings */}
          <div>
            <div className="section-subtitle">
              <span>Consolidated AI Advisory Findings ({findings.length})</span>
              <small style={{ fontSize: '11px', color: '#7a8c99', fontWeight: 500 }}>
                Evidence-linked observations with model provenance
              </small>
            </div>

            {loading ? (
              <div className="panel loading-panel" style={{ height: '140px' }}>
                <span className="spinner" /> Loading AI advisory report…
              </div>
            ) : findings.length === 0 ? (
              <div className="empty-observations-note">
                <strong>No AI observations were returned.</strong>
                <p>This does not establish safety or regulatory compliance. Authorized officers must complete independent visual and physical verification.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                {findings.map((finding) => {
                  const state = reviewStates[finding.id] || {
                    status: (['CONFIRMED', 'DISMISSED', 'MODIFIED'].includes(finding.reviewStatus)
                      ? finding.reviewStatus
                      : 'CONFIRMED') as 'CONFIRMED' | 'DISMISSED' | 'MODIFIED',
                    notes: finding.officerNotes || '',
                    hazardVerified: Boolean(finding.hazardVerified),
                    submitting: false,
                    feedback: null,
                  };
                  const isSelected = finding.id === activeFindingId;

                  return (
                    <div
                      key={finding.id}
                      className={`finding-card ${isSelected ? 'active-finding' : ''}`}
                      onClick={() => {
                        setActiveFindingId(finding.id);
                        if (finding.evidenceId) setSelectedEvidenceId(finding.evidenceId);
                      }}
                    >
                      <div className="finding-top-row">
                        <div className="finding-title-group">
                          <span className="finding-type-pill">{finding.findingType || 'OBSERVATION'}</span>
                          <h3 className="finding-heading">{finding.finding}</h3>
                        </div>
                        <div className="badges-group">
                          <span className={`severity-pill ${finding.severity || 'MEDIUM'}`}>
                            {finding.severity || 'MEDIUM'}
                          </span>
                          <span className="confidence-pill">
                            {Math.round(finding.confidence * 100)}% conf
                          </span>
                          <span className={`status-badge ${finding.reviewStatus || 'PENDING_REVIEW'}`}>
                            {finding.reviewStatus || 'PENDING_REVIEW'}
                          </span>
                        </div>
                      </div>

                      {finding.description && (
                        <p className="finding-description-text">{finding.description}</p>
                      )}

                      {finding.verificationGuidance && (
                        <div className="finding-guidance-box">
                          <strong>Statutory Verification Guidance</strong>
                          {finding.verificationGuidance}
                        </div>
                      )}

                      <div className="provenance-line">
                        <strong>Provenance:</strong> {finding.provenance || 'Computer Vision (CV Object Detection)'}
                        {finding.reconciliationStatus && (
                          <span> · <strong>Reconciliation:</strong> {finding.reconciliationStatus}</span>
                        )}
                        {finding.evidenceId && (
                          <span> · <strong>Evidence ID:</strong> {finding.evidenceId}</span>
                        )}
                      </div>

                      {/* Officer Review Form Box */}
                      <div className="review-form-box" onClick={(e) => e.stopPropagation()}>
                        <div className="review-form-row">
                          <label>
                            Officer Decision:
                            <select
                              className="review-select"
                              value={state.status}
                              onChange={(e) => {
                                const val = e.target.value as 'CONFIRMED' | 'DISMISSED' | 'MODIFIED';
                                setReviewStates((prev) => ({
                                  ...prev,
                                  [finding.id]: { ...state, status: val },
                                }));
                              }}
                            >
                              <option value="CONFIRMED">CONFIRMED — Condition Verified On-Site</option>
                              <option value="DISMISSED">DISMISSED — False Positive / Cleared</option>
                              <option value="MODIFIED">MODIFIED — Requires Modified Specification</option>
                            </select>
                          </label>

                          <label style={{ cursor: 'pointer' }}>
                            <input
                              type="checkbox"
                              checked={state.hazardVerified}
                              onChange={(e) => {
                                const val = e.target.checked;
                                setReviewStates((prev) => ({
                                  ...prev,
                                  [finding.id]: { ...state, hazardVerified: val },
                                }));
                              }}
                            />
                            Physical Hazard Verified
                          </label>
                        </div>

                        <textarea
                          className="review-notes-input"
                          placeholder="Officer verification notes, physical measurements, regulatory basis..."
                          value={state.notes}
                          onChange={(e) => {
                            const val = e.target.value;
                            setReviewStates((prev) => ({
                              ...prev,
                              [finding.id]: { ...state, notes: val },
                            }));
                          }}
                        />

                        <div className="review-submit-bar">
                          <button
                            type="button"
                            className="btn-persist-review"
                            disabled={state.submitting}
                            onClick={() => handleReviewSubmit(finding.id)}
                          >
                            {state.submitting ? 'Recording…' : 'Record Finding Verification'}
                          </button>

                          {state.feedback && (
                            <span
                              className={`review-feedback-tag ${
                                state.feedback.startsWith('Error') ? 'error' : 'success'
                              }`}
                            >
                              {state.feedback}
                            </span>
                          )}

                          <small style={{ fontSize: '10px', color: '#7a8c99', textAlign: 'right' }}>
                            Evidentiary record only · Does not advance statutory NOC status
                          </small>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

export default App;
