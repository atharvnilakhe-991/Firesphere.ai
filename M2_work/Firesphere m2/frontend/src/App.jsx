import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const API_BASE = '/api';
const SESSION_KEY = 'firesphere_session_token';

function App() {
  const [stats, setStats] = useState({
    organizations: 0,
    users: 0,
    incidents: 0,
    inspections: 0,
    aiRuns: 0,
    workflowRuns: 0,
  });
  const [organizations, setOrganizations] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [aiRuns, setAiRuns] = useState([]);
  const [dbStatus, setDbStatus] = useState('checking');
  const [tab, setTab] = useState('landing');
  const [activeSubmenu, setActiveSubmenu] = useState('overview');
  const [currentRole, setCurrentRole] = useState('SUPER_ADMIN');
  const [orgSignupForm, setOrgSignupForm] = useState({
    name: '',
    orgType: 'MUNICIPAL_CORPORATION',
    city: 'Pune',
    email: '',
    phone: '',
    ssoProvider: 'gmail',
    domain: 'firesphere.ai',
    adminFirstName: 'System',
    adminLastName: 'Administrator',
    adminEmail: '',
    adminUsername: '',
    password: 'Fire@1234'
  });
  const [signupMessage, setSignupMessage] = useState('');
  const [verificationForm, setVerificationForm] = useState({
    orgId: '',
    email: '',
    token: ''
  });
  const [userForm, setUserForm] = useState({
    orgId: '1',
    firstName: '',
    lastName: '',
    username: '',
    email: '',
    phone: '',
    roleCode: 'ORG_ADMIN',
    isGuest: false,
    password: 'Welcome@123'
  });
  const [userMessage, setUserMessage] = useState('');
  const [guestIncidentForm, setGuestIncidentForm] = useState({
    orgId: '1',
    guestName: '',
    guestPhone: '',
    incidentType: 'EMERGENCY_CALL',
    severity: 'MEDIUM',
    address: '',
    incidentDescription: ''
  });
  const [guestMessage, setGuestMessage] = useState('');
  const [orgUsers, setOrgUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });
  const [loginMessage, setLoginMessage] = useState('');

  const roleMenus = {
    SUPER_ADMIN: ['overview', 'organization', 'users', 'ai-agents', 'security'],
    ORG_ADMIN: ['overview', 'operations', 'users', 'alerts', 'reports'],
    OPS_MANAGER: ['overview', 'dispatch', 'stations', 'fleet', 'inspections'],
    FIRE_INSPECTOR: ['overview', 'inspections', 'violations', 'checklists', 'compliance'],
    GUEST: ['overview', 'raise-incident', 'support', 'profile']
  };

  const dashboardMenuLabels = {
    overview: 'Overview',
    organization: 'Organization',
    users: 'Users',
    'ai-agents': 'AI Agents',
    security: 'Security',
    operations: 'Operations',
    alerts: 'Alerts',
    reports: 'Reports',
    dispatch: 'Dispatch',
    stations: 'Stations',
    fleet: 'Fleet',
    inspections: 'Inspections',
    violations: 'Violations',
    checklists: 'Checklists',
    compliance: 'Compliance',
    'raise-incident': 'Raise Incident',
    support: 'Support',
    profile: 'Profile'
  };

  const refreshDashboard = async () => {
    const token = localStorage.getItem(SESSION_KEY);
    if (!token) {
      setDbStatus('degraded');
      return;
    }

    try {
      const authHeaders = { Authorization: `Bearer ${token}` };
      const [health, overview, orgs, incidentsData, aiRunsData] = await Promise.all([
        fetch(`${API_BASE}/health`, { headers: authHeaders }).then((res) => res.json()),
        fetch(`${API_BASE}/overview`, { headers: authHeaders }).then((res) => res.json()),
        fetch(`${API_BASE}/organizations`, { headers: authHeaders }).then((res) => res.json()),
        fetch(`${API_BASE}/incidents`, { headers: authHeaders }).then((res) => res.json()),
        fetch(`${API_BASE}/ai-runs`, { headers: authHeaders }).then((res) => res.json())
      ]);

      setDbStatus(health.status === 'ok' ? 'connected' : 'degraded');
      setStats({
        organizations: Number(overview.organizations || 0),
        users: Number(overview.users || 0),
        incidents: Number(overview.incidents || 0),
        inspections: Number(overview.inspections || 0),
        aiRuns: Number(overview.ai_runs || 0),
        workflowRuns: Number(overview.workflow_runs || 0),
      });
      setOrganizations(orgs || []);
      setIncidents(incidentsData || []);
      setAiRuns(aiRunsData || []);
    } catch (error) {
      setDbStatus('degraded');
    }
  };

  const handleLogin = async (event) => {
    event.preventDefault();
    setLoginMessage('');

    if (!loginForm.email || !loginForm.password) {
      setLoginMessage('Please enter your email and password to continue.');
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: loginForm.email,
          password: loginForm.password
        })
      });

      const data = await res.json();
      if (!res.ok) {
        setLoginMessage(data.message || 'Login failed.');
        return;
      }

      localStorage.setItem(SESSION_KEY, data.token);
      setIsAuthenticated(true);
      setTab('dashboard');
      setActiveSubmenu('overview');
      setCurrentRole('SUPER_ADMIN');
      setLoginMessage('');
      setLoginForm({ email: '', password: '' });
      await refreshDashboard();
    } catch (error) {
      setLoginMessage('Unable to reach the authentication service.');
    }
  };

  const handleLogout = async () => {
    const token = localStorage.getItem(SESSION_KEY);
    if (token) {
      try {
        await fetch(`${API_BASE}/auth/logout`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
          }
        });
      } catch (error) {
        // ignore logout failures and clear local session
      }
    }

    localStorage.removeItem(SESSION_KEY);
    setIsAuthenticated(false);
    setTab('landing');
    setActiveSubmenu('overview');
    setLoginForm({ email: '', password: '' });
    setLoginMessage('');
  };

  const loadUsersForOrg = async (orgId = userForm.orgId) => {
    const token = localStorage.getItem(SESSION_KEY);
    if (!token) {
      return;
    }

    try {
      const [usersRes, rolesRes] = await Promise.all([
        fetch(`${API_BASE}/organizations/${orgId}/users`, { headers: { Authorization: `Bearer ${token}` } }),
        fetch(`${API_BASE}/organizations/${orgId}/roles`, { headers: { Authorization: `Bearer ${token}` } })
      ]);
      const users = await usersRes.json();
      const rolesData = await rolesRes.json();
      setOrgUsers(users || []);
      setRoles(rolesData || []);
    } catch (error) {
      setOrgUsers([]);
      setRoles([]);
    }
  };

  useEffect(() => {
    const token = localStorage.getItem(SESSION_KEY);
    if (token) {
      setIsAuthenticated(true);
      refreshDashboard();
      loadUsersForOrg();
    }
  }, []);

  const handleSignup = async (event) => {
    event.preventDefault();
    setSignupMessage('');

    const payload = {
      ...orgSignupForm,
      ssoProvider: orgSignupForm.ssoProvider,
      domain: orgSignupForm.domain || orgSignupForm.email.split('@')[1] || 'firesphere.ai'
    };

    const res = await fetch(`${API_BASE}/organizations/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    const verificationText = orgSignupForm.ssoProvider === 'custom'
      ? `Verification email has been sent to ${orgSignupForm.adminEmail}.`
      : `${orgSignupForm.ssoProvider.toUpperCase()} authentication is enabled for verification.`;

    setSignupMessage(res.ok ? `Organization created: ${data.message}. ${verificationText}` : data.message || 'Signup failed');

    if (res.ok) {
      setCurrentRole('SUPER_ADMIN');
      const orgId = data.orgId;
      setVerificationForm({
        orgId: String(orgId),
        email: orgSignupForm.adminEmail,
        token: data.verificationToken || ''
      });
      setUserForm((prev) => ({ ...prev, orgId: String(orgId) }));
      setGuestIncidentForm((prev) => ({ ...prev, orgId: String(orgId) }));
      await refreshDashboard();
      await loadUsersForOrg(orgId);
      setTab('dashboard');
      setActiveSubmenu('overview');
    }
  };

  const handleVerify = async (event) => {
    event.preventDefault();
    if (!verificationForm.orgId || !verificationForm.email || !verificationForm.token) {
      setSignupMessage('Please complete the organization ID, admin email, and verification token before activating the account.');
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/organizations/${verificationForm.orgId}/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: verificationForm.email,
          token: verificationForm.token
        })
      });

      const data = await res.json();
      setSignupMessage(res.ok
        ? `Verification successful: ${data.message}. You can now log in with your admin email and password.`
        : data.message || 'Verification failed');

      if (res.ok) {
        setVerificationForm({ orgId: '', email: '', token: '' });
      }
    } catch (error) {
      setSignupMessage('Verification service is unavailable right now.');
    }
  };

  const handleAddUser = async (event) => {
    event.preventDefault();
    setUserMessage('');

    const res = await fetch(`${API_BASE}/organizations/${userForm.orgId}/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        firstName: userForm.firstName,
        lastName: userForm.lastName,
        username: userForm.username,
        email: userForm.email,
        phone: userForm.phone,
        roleCode: userForm.roleCode,
        isGuest: userForm.isGuest,
        password: userForm.password
      })
    });

    const data = await res.json();
    setUserMessage(res.ok ? `User added: ${data.message}` : data.message || 'Unable to add user');
    if (res.ok) {
      await loadUsersForOrg(userForm.orgId);
      await refreshDashboard();
      setUserForm((prev) => ({ ...prev, firstName: '', lastName: '', username: '', email: '', phone: '' }));
    }
  };

  const handleGuestIncident = async (event) => {
    event.preventDefault();
    setGuestMessage('');

    const res = await fetch(`${API_BASE}/organizations/${guestIncidentForm.orgId}/guest-incidents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        guestName: guestIncidentForm.guestName,
        guestPhone: guestIncidentForm.guestPhone,
        incidentType: guestIncidentForm.incidentType,
        severity: guestIncidentForm.severity,
        address: guestIncidentForm.address,
        incidentDescription: guestIncidentForm.incidentDescription,
        callerName: guestIncidentForm.guestName,
        callerPhone: guestIncidentForm.guestPhone
      })
    });

    const data = await res.json();
    setGuestMessage(res.ok ? `Incident raised: ${data.incidentNo}` : data.message || 'Unable to raise incident');
    if (res.ok) {
      setGuestIncidentForm((prev) => ({ ...prev, guestName: '', guestPhone: '', address: '', incidentDescription: '' }));
      await refreshDashboard();
    }
  };

  const chartData = [
    { name: 'Organizations', count: stats.organizations },
    { name: 'Users', count: stats.users },
    { name: 'Incidents', count: stats.incidents },
    { name: 'Inspections', count: stats.inspections },
    { name: 'AI Runs', count: stats.aiRuns },
  ];

  const currentMenuList = roleMenus[currentRole] || roleMenus.SUPER_ADMIN;

  const isDashboardVisible = isAuthenticated && tab === 'dashboard';
  const navItems = isAuthenticated
    ? [
        { key: 'landing', label: 'Home' },
        { key: 'dashboard', label: 'Dashboard' },
        { key: 'signup', label: 'Organization Signup' },
        { key: 'users', label: 'Manage Users' },
        { key: 'guest', label: 'Guest Incident' }
      ]
    : [
        { key: 'landing', label: 'Home' },
        { key: 'signup', label: 'Organization Signup' }
      ];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">F</div>
          <div>
            <h1>FireSphere.ai</h1>
            <p>Enterprise command</p>
          </div>
        </div>

        <nav className="nav">
          {navItems.map((item) => (
            <button
              key={item.key}
              className={tab === item.key ? 'nav-button active' : 'nav-button'}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </nav>

        {isAuthenticated && (
          <div className="role-card">
            <span className="eyebrow light">Access level</span>
            <select value={currentRole} onChange={(e) => setCurrentRole(e.target.value)}>
              <option value="SUPER_ADMIN">Super Admin</option>
              <option value="ORG_ADMIN">Organization Admin</option>
              <option value="OPS_MANAGER">Operations Manager</option>
              <option value="FIRE_INSPECTOR">Fire Inspector</option>
              <option value="GUEST">Guest User</option>
            </select>
          </div>
        )}
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <p className="eyebrow">Enterprise Safety Platform</p>
            <h2>{tab === 'landing' ? 'FireSphere.ai' : isAuthenticated ? 'Operations Dashboard' : 'Secure Access'}</h2>
          </div>
          <div className="header-actions">
            {isAuthenticated ? (
              <>
                <span className={`status-pill ${dbStatus}`}>
                  {dbStatus === 'connected' ? 'Live database connected' : 'Database connection is active but in demo fallback mode'}
                </span>
                <button className="secondary-btn" onClick={handleLogout}>Logout</button>
              </>
            ) : (
              <button className="primary-btn" onClick={() => setTab('landing')}>Login</button>
            )}
          </div>
        </header>

        {!isAuthenticated && tab === 'landing' && (
          <div className="landing-page">
            <section className="hero-panel">
              <div className="hero-copy">
                <p className="eyebrow accent">Modern fire, safety, and AI operations</p>
                <h3>Build safer cities with a single operational command center.</h3>
                <p>
                  Manage organizations, incidents, inspections, AI workflows, users, and compliance from one secure SaaS platform built for enterprise teams.
                </p>
                <div className="hero-actions">
                  <button className="primary-btn" onClick={() => setTab('signup')}>Start organization signup</button>
                  <button className="secondary-btn" onClick={() => setTab('landing')}>Login to dashboard</button>
                </div>

                <form onSubmit={handleLogin} className="form-grid" style={{ marginTop: '1.5rem', maxWidth: '420px' }}>
                  <input
                    value={loginForm.email}
                    onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
                    placeholder="Email address"
                    type="email"
                    required
                  />
                  <input
                    value={loginForm.password}
                    onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
                    placeholder="Password"
                    type="password"
                    required
                  />
                  <button className="primary-btn" type="submit">Login</button>
                </form>
                {loginMessage && <p className="form-message">{loginMessage}</p>}

                <div className="trust-row">
                  <span>SSO ready</span>
                  <span>Multi-org</span>
                  <span>AI automation</span>
                  <span>24/7 response</span>
                </div>
              </div>
              <div className="hero-visual">
                <div className="metric-box large">2.4x faster response</div>
                <div className="metric-box small">98.7% compliance visibility</div>
                <div className="metric-box small alt">AI-driven dispatch</div>
              </div>
            </section>

            <section className="services-section">
              <div className="section-heading">
                <p className="eyebrow">Core services</p>
                <h3>Everything your safety ecosystem needs</h3>
              </div>
              <div className="service-grid">
                <ServiceCard title="Incident Command" text="Dispatch response, route teams, track live status, and manage escalation workflows in real time." />
                <ServiceCard title="Compliance & NOC" text="Maintain permits, inspections, approvals, and digital documentation for every building, asset, and department." />
                <ServiceCard title="AI Agent Ops" text="Deploy inspection copilots, policy assistants, voice workflows, and intelligent workflow orchestration." />
                <ServiceCard title="Asset Intelligence" text="Monitor fire stations, vehicles, hydrants, inventory, and maintenance readiness across all regions." />
                <ServiceCard title="SSO & Access Control" text="Enable Gmail, Hotmail, Microsoft, or custom-domain sign-in for each organization with verification-based onboarding." />
                <ServiceCard title="Reporting & Governance" text="Track insights, dashboards, audits, and role-based governance for leadership and operational teams." />
              </div>
            </section>
          </div>
        )}

        {isDashboardVisible && (
          <>
            <div className="dashboard-submenu">
              {currentMenuList.map((menuKey) => (
                <button
                  key={menuKey}
                  className={activeSubmenu === menuKey ? 'submenu-btn active' : 'submenu-btn'}
                  onClick={() => setActiveSubmenu(menuKey)}
                >
                  {dashboardMenuLabels[menuKey] || menuKey}
                </button>
              ))}
            </div>

            <div className="role-banner">
              <strong>{dashboardMenuLabels[activeSubmenu]}</strong>
              <span>Role access: {currentRole.replace('_', ' ')}</span>
            </div>

            <section className="stats-grid">
              <StatCard title="Organizations" value={stats.organizations} accent="blue" />
              <StatCard title="Users" value={stats.users} accent="purple" />
              <StatCard title="Incidents" value={stats.incidents} accent="red" />
              <StatCard title="Inspections" value={stats.inspections} accent="green" />
              <StatCard title="AI Runs" value={stats.aiRuns} accent="amber" />
              <StatCard title="Workflow Runs" value={stats.workflowRuns} accent="teal" />
            </section>

            <section className="panel-grid">
              <div className="panel chart-panel">
                <div className="panel-header">
                  <h3>Platform Metrics</h3>
                </div>
                <div className="chart-wrap">
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="name" />
                      <YAxis allowDecimals={false} />
                      <Tooltip />
                      <Bar dataKey="count" radius={[6, 6, 0, 0]} fill="#2563eb" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div className="panel">
                <div className="panel-header">
                  <h3>Organizations</h3>
                </div>
                <ul className="list">
                  {organizations.map((org) => (
                    <li key={org.id}>
                      <div>
                        <strong>{org.name}</strong>
                        <span>{org.org_type}</span>
                      </div>
                      <span className="tag">{org.status}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>

            <section className="panel-grid lower-grid">
              <div className="panel">
                <div className="panel-header">
                  <h3>Recent Incidents</h3>
                </div>
                <ul className="list compact">
                  {incidents.map((incident) => (
                    <li key={incident.id}>
                      <div>
                        <strong>{incident.incident_no}</strong>
                        <span>{incident.incident_type}</span>
                      </div>
                      <span className={`tag ${incident.status.toLowerCase()}`}>{incident.status}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="panel">
                <div className="panel-header">
                  <h3>AI Agent Runs</h3>
                </div>
                <ul className="list compact">
                  {aiRuns.map((run) => (
                    <li key={run.id}>
                      <div>
                        <strong>{run.agent_name || 'AI Agent'}</strong>
                        <span>{run.workflow_name || 'Standalone'}</span>
                      </div>
                      <span className={`tag ${run.status.toLowerCase()}`}>{run.status}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>
          </>
        )}

        {!isAuthenticated && tab === 'signup' && (
          <section className="panel form-panel">
            <div className="panel-header">
              <h3>Organization Signup</h3>
            </div>
            <p className="form-message">Create an organization first, then log in to access the operational dashboard.</p>
            <form onSubmit={handleSignup} className="form-grid">
              <input value={orgSignupForm.name} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, name: e.target.value })} placeholder="Organization name" required />
              <select value={orgSignupForm.orgType} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, orgType: e.target.value })}>
                <option value="MUNICIPAL_CORPORATION">Municipal Corporation</option>
                <option value="SMART_CITY">Smart City</option>
                <option value="INDUSTRIAL_PARK">Industrial Park</option>
                <option value="STATE_FIRE_DEPARTMENT">State Fire Department</option>
                <option value="HOSPITAL">Hospital</option>
                <option value="CITY_COUNCIL">City Council</option>
              </select>
              <input value={orgSignupForm.city} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, city: e.target.value })} placeholder="City" />
              <input value={orgSignupForm.email} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, email: e.target.value })} placeholder="Organization email" type="email" required />
              <input value={orgSignupForm.phone} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, phone: e.target.value })} placeholder="Contact phone" />
              <select value={orgSignupForm.ssoProvider} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, ssoProvider: e.target.value })}>
                <option value="gmail">Google / Gmail</option>
                <option value="hotmail">Microsoft / Hotmail</option>
                <option value="microsoft">Microsoft Entra</option>
                <option value="custom">Custom email domain</option>
              </select>
              <input value={orgSignupForm.domain} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, domain: e.target.value })} placeholder="Domain or tenant" />
              <input value={orgSignupForm.adminFirstName} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminFirstName: e.target.value })} placeholder="Admin first name" />
              <input value={orgSignupForm.adminLastName} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminLastName: e.target.value })} placeholder="Admin last name" />
              <input value={orgSignupForm.adminUsername} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminUsername: e.target.value })} placeholder="Admin username" />
              <input value={orgSignupForm.adminEmail} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminEmail: e.target.value })} placeholder="Admin email" type="email" required />
              <input value={orgSignupForm.password} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, password: e.target.value })} placeholder="Password" type="password" required />
              <button className="primary-btn" type="submit">Create Organization</button>
            </form>
            {signupMessage && <p className="form-message">{signupMessage}</p>}

            <div className="form-panel verification-box" style={{ marginTop: '1.5rem' }}>
              <div className="panel-header">
                <h3>Activate Organization</h3>
              </div>
              <form onSubmit={handleVerify} className="form-grid">
                <input value={verificationForm.orgId} onChange={(e) => setVerificationForm({ ...verificationForm, orgId: e.target.value })} placeholder="Organization ID" required />
                <input value={verificationForm.email} onChange={(e) => setVerificationForm({ ...verificationForm, email: e.target.value })} placeholder="Admin email" type="email" required />
                <input value={verificationForm.token} onChange={(e) => setVerificationForm({ ...verificationForm, token: e.target.value })} placeholder="Verification token" required />
                <button className="secondary-btn" type="submit">Activate account</button>
              </form>
            </div>
          </section>
        )}

        {isAuthenticated && tab === 'signup' && (
          <section className="panel form-panel">
            <div className="panel-header">
              <h3>Organization Signup</h3>
            </div>
            <form onSubmit={handleSignup} className="form-grid">
              <input value={orgSignupForm.name} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, name: e.target.value })} placeholder="Organization name" required />
              <select value={orgSignupForm.orgType} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, orgType: e.target.value })}>
                <option value="MUNICIPAL_CORPORATION">Municipal Corporation</option>
                <option value="SMART_CITY">Smart City</option>
                <option value="INDUSTRIAL_PARK">Industrial Park</option>
                <option value="STATE_FIRE_DEPARTMENT">State Fire Department</option>
                <option value="HOSPITAL">Hospital</option>
                <option value="CITY_COUNCIL">City Council</option>
              </select>
              <input value={orgSignupForm.city} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, city: e.target.value })} placeholder="City" />
              <input value={orgSignupForm.email} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, email: e.target.value })} placeholder="Organization email" type="email" required />
              <input value={orgSignupForm.phone} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, phone: e.target.value })} placeholder="Contact phone" />
              <select value={orgSignupForm.ssoProvider} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, ssoProvider: e.target.value })}>
                <option value="gmail">Google / Gmail</option>
                <option value="hotmail">Microsoft / Hotmail</option>
                <option value="microsoft">Microsoft Entra</option>
                <option value="custom">Custom email domain</option>
              </select>
              <input value={orgSignupForm.domain} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, domain: e.target.value })} placeholder="Domain or tenant" />
              <input value={orgSignupForm.adminFirstName} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminFirstName: e.target.value })} placeholder="Admin first name" />
              <input value={orgSignupForm.adminLastName} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminLastName: e.target.value })} placeholder="Admin last name" />
              <input value={orgSignupForm.adminUsername} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminUsername: e.target.value })} placeholder="Admin username" />
              <input value={orgSignupForm.adminEmail} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminEmail: e.target.value })} placeholder="Admin email" type="email" required />
              <input value={orgSignupForm.password} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, password: e.target.value })} placeholder="Password" type="password" required />
              <button className="primary-btn" type="submit">Create Organization</button>
            </form>
            {signupMessage && <p className="form-message">{signupMessage}</p>}
          </section>
        )}

        {isAuthenticated && tab === 'users' && (
          <section className="panel form-panel">
            <div className="panel-header">
              <h3>Organization Signup</h3>
            </div>
            <form onSubmit={handleSignup} className="form-grid">
              <input value={orgSignupForm.name} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, name: e.target.value })} placeholder="Organization name" required />
              <select value={orgSignupForm.orgType} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, orgType: e.target.value })}>
                <option value="MUNICIPAL_CORPORATION">Municipal Corporation</option>
                <option value="SMART_CITY">Smart City</option>
                <option value="INDUSTRIAL_PARK">Industrial Park</option>
                <option value="STATE_FIRE_DEPARTMENT">State Fire Department</option>
                <option value="HOSPITAL">Hospital</option>
                <option value="CITY_COUNCIL">City Council</option>
              </select>
              <input value={orgSignupForm.city} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, city: e.target.value })} placeholder="City" />
              <input value={orgSignupForm.email} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, email: e.target.value })} placeholder="Organization email" type="email" required />
              <input value={orgSignupForm.phone} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, phone: e.target.value })} placeholder="Contact phone" />
              <select value={orgSignupForm.ssoProvider} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, ssoProvider: e.target.value })}>
                <option value="gmail">Google / Gmail</option>
                <option value="hotmail">Microsoft / Hotmail</option>
                <option value="microsoft">Microsoft Entra</option>
                <option value="custom">Custom email domain</option>
              </select>
              <input value={orgSignupForm.domain} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, domain: e.target.value })} placeholder="Domain or tenant" />
              <input value={orgSignupForm.adminFirstName} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminFirstName: e.target.value })} placeholder="Admin first name" />
              <input value={orgSignupForm.adminLastName} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminLastName: e.target.value })} placeholder="Admin last name" />
              <input value={orgSignupForm.adminUsername} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminUsername: e.target.value })} placeholder="Admin username" />
              <input value={orgSignupForm.adminEmail} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, adminEmail: e.target.value })} placeholder="Admin email" type="email" required />
              <input value={orgSignupForm.password} onChange={(e) => setOrgSignupForm({ ...orgSignupForm, password: e.target.value })} placeholder="Password" type="password" required />
              <button className="primary-btn" type="submit">Create Organization</button>
            </form>
            {signupMessage && <p className="form-message">{signupMessage}</p>}
          </section>
        )}

        {isAuthenticated && tab === 'users' && (
          <section className="panel form-panel">
            <div className="panel-header">
              <h3>Add User and Assign Role</h3>
            </div>

            <form onSubmit={handleAddUser} className="form-grid">
              <input value={userForm.orgId} onChange={(e) => setUserForm({ ...userForm, orgId: e.target.value })} placeholder="Organization ID" required />
              <input value={userForm.firstName} onChange={(e) => setUserForm({ ...userForm, firstName: e.target.value })} placeholder="First name" required />
              <input value={userForm.lastName} onChange={(e) => setUserForm({ ...userForm, lastName: e.target.value })} placeholder="Last name" required />
              <input value={userForm.username} onChange={(e) => setUserForm({ ...userForm, username: e.target.value })} placeholder="Username" required />
              <input value={userForm.email} onChange={(e) => setUserForm({ ...userForm, email: e.target.value })} placeholder="Email" type="email" required />
              <input value={userForm.phone} onChange={(e) => setUserForm({ ...userForm, phone: e.target.value })} placeholder="Phone" />
              <select value={userForm.roleCode} onChange={(e) => setUserForm({ ...userForm, roleCode: e.target.value })}>
                {roles.length ? roles.map((role) => (
                  <option key={role.id} value={role.code}>{role.name}</option>
                )) : (
                  <>
                    <option value="SUPER_ADMIN">Super Admin</option>
                    <option value="ORG_ADMIN">Organization Admin</option>
                    <option value="OPS_MANAGER">Operations Manager</option>
                    <option value="FIRE_INSPECTOR">Fire Inspector</option>
                    <option value="GUEST">Guest User</option>
                  </>
                )}
              </select>
              <label className="checkbox-row">
                <input type="checkbox" checked={userForm.isGuest} onChange={(e) => setUserForm({ ...userForm, isGuest: e.target.checked })} />
                Guest user
              </label>
              <input value={userForm.password} onChange={(e) => setUserForm({ ...userForm, password: e.target.value })} placeholder="Password" type="password" />
              <button className="primary-btn" type="submit">Add User</button>
            </form>
            {userMessage && <p className="form-message">{userMessage}</p>}

            <div className="list-block">
              <h4>Current Users</h4>
              <ul className="list compact">
                {orgUsers.length ? orgUsers.map((user) => (
                  <li key={user.id}>
                    <div>
                      <strong>{user.first_name} {user.last_name}</strong>
                      <span>{user.email} • {user.roles || 'No role'}</span>
                    </div>
                    <span className="tag">{user.is_guest === '1' ? 'Guest' : user.status}</span>
                  </li>
                )) : <li>No user records yet</li>}
              </ul>
            </div>
          </section>
        )}

        {isAuthenticated && tab === 'guest' && (
          <section className="panel form-panel">
            <div className="panel-header">
              <h3>Guest User Incident Submission</h3>
            </div>
            <form onSubmit={handleGuestIncident} className="form-grid">
              <input value={guestIncidentForm.orgId} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, orgId: e.target.value })} placeholder="Organization ID" required />
              <input value={guestIncidentForm.guestName} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, guestName: e.target.value })} placeholder="Guest name" required />
              <input value={guestIncidentForm.guestPhone} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, guestPhone: e.target.value })} placeholder="Guest phone" required />
              <select value={guestIncidentForm.incidentType} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, incidentType: e.target.value })}>
                <option value="EMERGENCY_CALL">Emergency Call</option>
                <option value="BUILDING_FIRE">Building Fire</option>
                <option value="GAS_LEAK">Gas Leak</option>
                <option value="RESCUE_CALL">Rescue Call</option>
                <option value="MEDICAL_EMERGENCY">Medical Emergency</option>
              </select>
              <select value={guestIncidentForm.severity} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, severity: e.target.value })}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
              <input value={guestIncidentForm.address} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, address: e.target.value })} placeholder="Address" />
              <textarea value={guestIncidentForm.incidentDescription} onChange={(e) => setGuestIncidentForm({ ...guestIncidentForm, incidentDescription: e.target.value })} placeholder="Incident description" rows="4" required />
              <button className="primary-btn" type="submit">Raise Incident</button>
            </form>
            {guestMessage && <p className="form-message">{guestMessage}</p>}
          </section>
        )}
      </main>
    </div>
  );
}

function ServiceCard({ title, text }) {
  return (
    <div className="service-card">
      <div className="service-icon">•</div>
      <h4>{title}</h4>
      <p>{text}</p>
    </div>
  );
}

function StatCard({ title, value, accent }) {
  return (
    <div className={`stat-card ${accent}`}>
      <span>{title}</span>
      <strong>{value}</strong>
    </div>
  );
}

export default App;
