const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const crypto = require('crypto');
const path = require('path');
require('dotenv').config();

const app = express();
const port = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

const frontendDistPath = path.resolve(__dirname, '../frontend/dist');
const serveFrontend = express.static(frontendDistPath);
app.use(serveFrontend);

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  timezone: '+00:00',
});

const fallbackOverview = {
  organizations: 12,
  users: 284,
  incidents: 48,
  inspections: 96,
  ai_runs: 178,
  workflow_runs: 63,
};

const fallbackOrganizations = [
  { id: 1, name: 'FireSphere Demo Municipal Corporation', org_type: 'MUNICIPAL_CORPORATION', status: 'ACTIVE', city: 'Pune' },
  { id: 2, name: 'Smart City Fire Command', org_type: 'SMART_CITY', status: 'ACTIVE', city: 'Nagpur' },
  { id: 3, name: 'Industrial Safety Board', org_type: 'INDUSTRIAL_PARK', status: 'TRIAL', city: 'Pimpri-Chinchwad' }
];

const fallbackIncidents = [
  { id: 1, incident_no: 'FS-INC-2148', incident_type: 'Building Fire', status: 'DISPATCHED', severity: 'HIGH' },
  { id: 2, incident_no: 'FS-INC-2149', incident_type: 'Gas Leak', status: 'ON_SCENE', severity: 'MEDIUM' },
  { id: 3, incident_no: 'FS-INC-2150', incident_type: 'Rescue Call', status: 'RESOLVED', severity: 'LOW' }
];

const fallbackAiRuns = [
  { id: 1, status: 'COMPLETED', agent_name: 'Inspection Copilot', workflow_name: 'Fire Risk Review' },
  { id: 2, status: 'RUNNING', agent_name: 'Policy Assistant', workflow_name: 'Compliance Summaries' },
  { id: 3, status: 'FAILED', agent_name: 'Operations Assistant', workflow_name: 'Dispatch Orchestration' }
];

function hashPassword(password) {
  return crypto.createHash('sha256').update(String(password)).digest('hex');
}

function makeTenantCode(name) {
  const base = (name || 'firesphere')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 18) || 'firesphere';
  return `${base}-${Date.now().toString(36).slice(-4)}`.toUpperCase();
}

async function safeQuery(sql, params = []) {
  try {
    const [rows] = await pool.execute(sql, params);
    return rows;
  } catch (error) {
    console.error('Database query failed:', error.message);
    throw error;
  }
}

function getBearerToken(req) {
  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) return null;
  return authHeader.replace('Bearer ', '').trim();
}

async function requireAuth(req, res, next) {
  try {
    const token = getBearerToken(req);
    if (!token) {
      return res.status(401).json({ message: 'Authentication required.' });
    }

    const rows = await safeQuery(`
      SELECT u.id, u.org_id, u.username, u.email, u.first_name, u.last_name, u.status, u.is_super_admin, u.metadata_json,
             us.session_token, us.expires_at, us.revoked
      FROM user_sessions us
      INNER JOIN users u ON u.id = us.user_id
      WHERE us.session_token = ? AND us.revoked = 0 AND u.status = 'ACTIVE'
        AND (us.expires_at IS NULL OR us.expires_at > NOW())
      LIMIT 1
    `, [token]);

    if (!rows.length) {
      return res.status(401).json({ message: 'Session expired or invalid.' });
    }

    req.user = rows[0];
    req.orgId = Number(rows[0].org_id);
    next();
  } catch (error) {
    res.status(500).json({ message: 'Authentication failed.', error: error.message });
  }
}

function requireOrgAccess(req, res, next) {
  const requestedOrgId = Number(req.params.orgId || req.query.orgId || 0);
  if (!requestedOrgId || Number(req.user.org_id) !== requestedOrgId) {
    return res.status(403).json({ message: 'You do not have access to this organization.' });
  }
  next();
}

app.get('/api/health', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT 1 AS ok');
    res.json({ status: 'ok', database: process.env.DB_NAME, ping: rows[0] });
  } catch (error) {
    res.status(200).json({
      status: 'degraded',
      database: process.env.DB_NAME,
      message: error.message,
      fallback: 'demo-mode'
    });
  }
});

app.post('/api/auth/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) {
      return res.status(400).json({ message: 'Email and password are required.' });
    }

    const normalizedEmail = String(email).trim().toLowerCase();
    const [user] = await safeQuery(
      `SELECT id, org_id, username, email, password_hash, first_name, last_name, status
       FROM users
       WHERE email = ?
       LIMIT 1`,
      [normalizedEmail]
    );

    if (!user) {
      return res.status(401).json({ message: 'Invalid email or password.' });
    }

    if (user.status !== 'ACTIVE') {
      return res.status(403).json({ message: 'Account is not active. Complete verification first.' });
    }

    const suppliedHash = hashPassword(password);
    if (suppliedHash !== user.password_hash) {
      return res.status(401).json({ message: 'Invalid email or password.' });
    }

    const sessionToken = crypto.randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');

    await safeQuery(
      `INSERT INTO user_sessions (user_id, session_token, expires_at, user_agent, ip_address, device_name, revoked)
       VALUES (?, ?, ?, ?, ?, ?, 0)`,
      [user.id, sessionToken, expiresAt, req.headers['user-agent'] || 'web', req.ip || 'unknown', 'web-browser']
    );

    res.json({
      message: 'Login successful.',
      token: sessionToken,
      user: {
        id: user.id,
        orgId: user.org_id,
        username: user.username,
        email: user.email,
        firstName: user.first_name,
        lastName: user.last_name
      }
    });
  } catch (error) {
    res.status(500).json({ message: 'Login failed.', error: error.message });
  }
});

app.get('/api/auth/me', requireAuth, async (req, res) => {
  res.json({
    user: {
      id: req.user.id,
      orgId: Number(req.user.org_id),
      username: req.user.username,
      email: req.user.email,
      firstName: req.user.first_name,
      lastName: req.user.last_name,
      status: req.user.status,
      isSuperAdmin: Boolean(req.user.is_super_admin)
    }
  });
});

app.post('/api/auth/logout', requireAuth, async (req, res) => {
  const token = getBearerToken(req);
  if (!token) {
    return res.status(400).json({ message: 'No active session to log out.' });
  }

  await safeQuery(`UPDATE user_sessions SET revoked = 1 WHERE session_token = ?`, [token]);
  res.json({ message: 'Logged out successfully.' });
});

app.get('/api/dashboard', requireAuth, async (req, res) => {
  try {
    const [orgCount] = await pool.query('SELECT COUNT(*) AS count FROM organizations');
    const [userCount] = await pool.query('SELECT COUNT(*) AS count FROM users');
    const [incidentCount] = await pool.query('SELECT COUNT(*) AS count FROM incidents');
    const [aiRunCount] = await pool.query('SELECT COUNT(*) AS count FROM ai_runs');
    const [inspectionCount] = await pool.query('SELECT COUNT(*) AS count FROM inspections');

    res.json({
      stats: {
        organizations: Number(orgCount[0]?.count || 0),
        users: Number(userCount[0]?.count || 0),
        incidents: Number(incidentCount[0]?.count || 0),
        aiRuns: Number(aiRunCount[0]?.count || 0),
        inspections: Number(inspectionCount[0]?.count || 0),
      }
    });
  } catch (error) {
    res.json({
      stats: {
        organizations: fallbackOverview.organizations,
        users: fallbackOverview.users,
        incidents: fallbackOverview.incidents,
        aiRuns: fallbackOverview.ai_runs,
        inspections: fallbackOverview.inspections,
      },
      fallback: true,
      message: 'Using demo data because the MariaDB connection is unavailable.'
    });
  }
});

app.get('/api/organizations', requireAuth, async (req, res) => {
  try {
    const rows = await safeQuery(`
      SELECT id, tenant_code, name, org_type, status, city, email, created_at
      FROM organizations
      ORDER BY created_at DESC
      LIMIT 10
    `);
    res.json(rows);
  } catch (error) {
    res.json(fallbackOrganizations);
  }
});

app.get('/api/organizations/:orgId/users', requireAuth, requireOrgAccess, async (req, res) => {
  try {
    const rows = await safeQuery(`
      SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.status, u.is_super_admin,
             JSON_UNQUOTE(JSON_EXTRACT(u.metadata_json, '$.guest')) AS is_guest,
             GROUP_CONCAT(r.name ORDER BY r.name SEPARATOR ', ') AS roles
      FROM users u
      LEFT JOIN user_roles ur ON ur.user_id = u.id
      LEFT JOIN roles r ON r.id = ur.role_id
      WHERE u.org_id = ?
      GROUP BY u.id, u.username, u.email, u.first_name, u.last_name, u.status, u.is_super_admin, u.metadata_json
      ORDER BY u.created_at DESC
    `, [req.params.orgId]);
    res.json(rows);
  } catch (error) {
    res.status(500).json({ message: 'Failed to load organization users', error: error.message });
  }
});

app.get('/api/organizations/:orgId/roles', requireAuth, requireOrgAccess, async (req, res) => {
  try {
    const rows = await safeQuery(`
      SELECT id, name, code, description, is_system
      FROM roles
      WHERE org_id = ?
      ORDER BY name ASC
    `, [req.params.orgId]);
    res.json(rows);
  } catch (error) {
    res.status(500).json({ message: 'Failed to load roles', error: error.message });
  }
});

app.post('/api/organizations/signup', async (req, res) => {
  try {
    const {
      name,
      orgType = 'MUNICIPAL_CORPORATION',
      city = 'Pune',
      email,
      phone,
      website = '',
      adminFirstName = 'System',
      adminLastName = 'Administrator',
      adminUsername,
      adminEmail,
      password,
      ssoProvider = 'custom',
      domain = ''
    } = req.body || {};

    if (!name || !adminEmail || !password) {
      return res.status(400).json({ message: 'Organization name, admin email, and password are required.' });
    }

    const providerKey = String(ssoProvider || 'custom').toLowerCase();
    const normalizedDomain = String(domain || (adminEmail.includes('@') ? adminEmail.split('@')[1] : '') || `${name.toLowerCase().replace(/[^a-z0-9]/g, '')}.local`).trim();
    const providerTypeMap = {
      gmail: { type: 'GOOGLE_WORKSPACE', providerName: 'Google Workspace', issuer: 'google' },
      hotmail: { type: 'OIDC', providerName: 'Microsoft', issuer: 'microsoft' },
      microsoft: { type: 'AZURE_AD', providerName: 'Microsoft Entra', issuer: 'entra' },
      custom: { type: 'CUSTOM', providerName: 'Custom SSO', issuer: 'custom' }
    };
    const providerConfig = providerTypeMap[providerKey] || providerTypeMap.custom;
    const tenantCode = makeTenantCode(name);
    const conn = await pool.getConnection();

    try {
      await conn.beginTransaction();

      const [orgInsert] = await conn.execute(
        `INSERT INTO organizations (tenant_code, name, legal_name, org_type, status, country_code, state_code, city, address, website, email, phone, locale, branding_json, config_json)
         VALUES (?, ?, ?, ?, 'TRIAL', 'IN', 'MH', ?, ?, ?, ?, ?, 'en_IN', JSON_OBJECT('primary_color', '#0F172A', 'secondary_color', '#2563EB', 'brand_name', ?), JSON_OBJECT('multi_tenant', true, 'ai_enabled', true, 'gis_enabled', true, 'default_language', 'en'))`,
        [tenantCode, name, name, orgType, city, `${city}, India`, website, email, phone || '+91-0000000000', name]
      );

      const orgId = orgInsert.insertId;
      const roleRows = [
        ['Super Admin', 'SUPER_ADMIN', 'Full platform administration and tenant control'],
        ['Organization Admin', 'ORG_ADMIN', 'Organization-wide administrative control'],
        ['Operations Manager', 'OPS_MANAGER', 'Operational workflow management'],
        ['Fire Inspector', 'FIRE_INSPECTOR', 'Inspection and compliance review'],
        ['Guest User', 'GUEST', 'Limited access to raise incidents and requests']
      ];

      for (const [roleName, roleCode, description] of roleRows) {
        await conn.execute(
          `INSERT INTO roles (org_id, name, code, description, is_system)
           VALUES (?, ?, ?, ?, 0)
           ON DUPLICATE KEY UPDATE name = VALUES(name)`,
          [orgId, roleName, roleCode, description]
        );
      }

      await conn.execute(
        `INSERT INTO org_sso_providers (org_id, provider_name, provider_type, domain, issuer_url, client_id, is_active, is_default)
         VALUES (?, ?, ?, ?, ?, ?, 1, 1)
         ON DUPLICATE KEY UPDATE provider_type = VALUES(provider_type), domain = VALUES(domain), client_id = VALUES(client_id), is_active = VALUES(is_active)`,
        [orgId, providerConfig.providerName, providerConfig.type, normalizedDomain, providerConfig.issuer, `${providerConfig.providerName.toLowerCase().replace(/\s+/g, '-')}-${Date.now().toString(36)}`,]
      );

      await conn.execute(
        `INSERT INTO org_settings (org_id, setting_key, setting_value)
         VALUES (?, 'setup_complete', JSON_OBJECT('completed', true, 'verification_required', true, 'sso_provider', ?)),
                (?, 'sso_provider', JSON_OBJECT('provider', ?, 'domain', ?, 'type', ?))
         ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)`,
        [orgId, providerConfig.providerName, orgId, providerConfig.providerName, normalizedDomain, providerConfig.type]
      );

      const username = (adminUsername || adminEmail.split('@')[0] || 'admin').toLowerCase();
      const verificationToken = `${orgId}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
      const [userInsert] = await conn.execute(
        `INSERT INTO users (org_id, username, email, phone, password_hash, first_name, last_name, employee_code, status, is_super_admin, sso_provider, metadata_json)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, ?, JSON_OBJECT('role_summary', 'platform_admin', 'sso_provider', ?, 'verification_required', true, 'verification_token', ?))`,
        [orgId, username, adminEmail, phone || '+91-0000000000', hashPassword(password), adminFirstName, adminLastName, `ORG-${Date.now().toString(36).toUpperCase()}`, providerConfig.providerName, providerConfig.providerName, verificationToken]
      );

      const [superRole] = await conn.execute(`SELECT id FROM roles WHERE org_id = ? AND code = 'SUPER_ADMIN'`, [orgId]);
      await conn.execute(`INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)`, [userInsert.insertId, superRole[0].id]);

      await conn.commit();
      res.status(201).json({
        message: 'Organization registered successfully. Verification is required to activate the account.',
        orgId,
        tenantCode,
        userId: userInsert.insertId,
        username,
        ssoProvider: providerConfig.providerName,
        verificationToken,
        status: 'PENDING_VERIFICATION'
      });
    } catch (error) {
      await conn.rollback();
      throw error;
    } finally {
      conn.release();
    }
  } catch (error) {
    res.status(500).json({ message: 'Failed to create organization', error: error.message });
  }
});

app.post('/api/organizations/:orgId/verify', async (req, res) => {
  try {
    const { email, token } = req.body || {};
    if (!email || !token) {
      return res.status(400).json({ message: 'Email and verification token are required.' });
    }

    const rows = await safeQuery(
      `SELECT id, status, metadata_json FROM users WHERE org_id = ? AND email = ? LIMIT 1`,
      [req.params.orgId, email]
    );

    if (!rows.length) {
      return res.status(404).json({ message: 'Organization admin record not found.' });
    }

    const user = rows[0];
    const meta = user.metadata_json ? JSON.parse(user.metadata_json) : {};

    if (meta.verification_token !== token) {
      return res.status(403).json({ message: 'Invalid verification token for this organization.' });
    }

    await safeQuery(
      `UPDATE users
       SET status = 'ACTIVE',
           metadata_json = JSON_OBJECT(
             'role_summary', COALESCE(?, 'platform_admin'),
             'sso_provider', COALESCE(?, 'custom'),
             'verified', true,
             'verification_token', NULL
           )
       WHERE id = ?`,
      [meta.role_summary || 'platform_admin', meta.sso_provider || 'custom', user.id]
    );

    res.json({
      message: 'Account verified and activated successfully.',
      orgId: Number(req.params.orgId),
      status: 'ACTIVE'
    });
  } catch (error) {
    res.status(500).json({ message: 'Failed to verify organization account', error: error.message });
  }
});

app.post('/api/organizations/:orgId/users', requireAuth, requireOrgAccess, async (req, res) => {
  try {
    const { firstName, lastName, email, phone, username, roleCode = 'ORG_ADMIN', isGuest = false, password = 'Welcome@123' } = req.body || {};
    if (!email || !username) {
      return res.status(400).json({ message: 'Email and username are required.' });
    }

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      const [userInsert] = await conn.execute(
        `INSERT INTO users (org_id, username, email, phone, password_hash, first_name, last_name, employee_code, status, is_super_admin, metadata_json)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, JSON_OBJECT('guest', ?, 'created_via_admin', true))`,
        [req.params.orgId, username, email, phone || '', hashPassword(password), firstName || 'New', lastName || 'User', `USR-${Date.now().toString(36).toUpperCase()}`, Boolean(isGuest)]
      );

      const [roleRow] = await conn.execute(`SELECT id FROM roles WHERE org_id = ? AND code = ?`, [req.params.orgId, roleCode]);
      if (roleRow[0]) {
        await conn.execute(`INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)`, [userInsert.insertId, roleRow[0].id]);
      }

      await conn.commit();
      res.status(201).json({ message: 'User added successfully', userId: userInsert.insertId });
    } catch (error) {
      await conn.rollback();
      throw error;
    } finally {
      conn.release();
    }
  } catch (error) {
    res.status(500).json({ message: 'Failed to add user', error: error.message });
  }
});

app.post('/api/organizations/:orgId/guest-incidents', async (req, res) => {
  try {
    const {
      guestName,
      guestPhone,
      incidentType = 'EMERGENCY_CALL',
      severity = 'MEDIUM',
      address = '',
      incidentDescription = '',
      latitude = null,
      longitude = null,
      callerName,
      callerPhone
    } = req.body || {};

    const orgId = Number(req.params.orgId);
    if (!orgId) {
      return res.status(400).json({ message: 'Organization ID is required.' });
    }

    const guestUserName = `guest_${Date.now().toString(36)}`;
    const conn = await pool.getConnection();

    try {
      await conn.beginTransaction();

      const [guestUserInsert] = await conn.execute(
        `INSERT INTO users (org_id, username, email, phone, password_hash, first_name, last_name, employee_code, status, is_super_admin, metadata_json)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, JSON_OBJECT('guest', true, 'guest_created_for_incident', true))`,
        [orgId, guestUserName, `${guestUserName}@guest.local`, guestPhone || callerPhone || '', hashPassword('guest@123'), guestName || callerName || 'Guest', 'User', `GUEST-${Date.now().toString(36).toUpperCase()}`]
      );

      const [guestRole] = await conn.execute(`SELECT id FROM roles WHERE org_id = ? AND code = 'GUEST'`, [orgId]);
      if (guestRole[0]) {
        await conn.execute(`INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)`, [guestUserInsert.insertId, guestRole[0].id]);
      }

      const incidentNo = `INC-${Date.now().toString(36).toUpperCase()}`;
      const [incidentInsert] = await conn.execute(
        `INSERT INTO incidents (org_id, incident_no, incident_type, severity, status, caller_name, caller_phone, latitude, longitude, address, incident_description, assigned_station_id, dispatched_by)
         VALUES (?, ?, ?, ?, 'REPORTED', ?, ?, ?, ?, ?, ?, NULL, ?)
        `,
        [orgId, incidentNo, incidentType, severity, callerName || guestName || 'Guest User', callerPhone || guestPhone || '', latitude || null, longitude || null, address, incidentDescription || 'Raised by guest user', guestUserInsert.insertId]
      );

      await conn.commit();
      res.status(201).json({
        message: 'Guest incident submitted successfully',
        incidentId: incidentInsert.insertId,
        incidentNo,
        guestUserId: guestUserInsert.insertId,
      });
    } catch (error) {
      await conn.rollback();
      throw error;
    } finally {
      conn.release();
    }
  } catch (error) {
    res.status(500).json({ message: 'Failed to submit guest incident', error: error.message });
  }
});

app.get('/api/ai-runs', async (req, res) => {
  try {
    const rows = await safeQuery(`
      SELECT ar.id, ar.status, ar.created_at, ar.token_usage, ag.name AS agent_name, aw.workflow_name
      FROM ai_runs ar
      LEFT JOIN ai_agents ag ON ag.id = ar.agent_id
      LEFT JOIN ai_agent_workflows aw ON aw.id = ar.workflow_id
      ORDER BY ar.created_at DESC
      LIMIT 10
    `);
    res.json(rows);
  } catch (error) {
    res.json(fallbackAiRuns);
  }
});

app.get('/api/incidents', async (req, res) => {
  try {
    const rows = await safeQuery(`
      SELECT i.id, i.incident_no, i.incident_type, i.status, i.severity, i.created_at, f.name AS station_name
      FROM incidents i
      LEFT JOIN fire_stations f ON f.id = i.assigned_station_id
      ORDER BY i.created_at DESC
      LIMIT 10
    `);
    res.json(rows);
  } catch (error) {
    res.json(fallbackIncidents);
  }
});

app.get('/api/overview', async (req, res) => {
  try {
    const dashboard = await safeQuery(`
      SELECT 
        (SELECT COUNT(*) FROM organizations) AS organizations,
        (SELECT COUNT(*) FROM users) AS users,
        (SELECT COUNT(*) FROM incidents) AS incidents,
        (SELECT COUNT(*) FROM inspections) AS inspections,
        (SELECT COUNT(*) FROM ai_runs) AS ai_runs,
        (SELECT COUNT(*) FROM workflow_runs) AS workflow_runs
    `);

    res.json(dashboard[0]);
  } catch (error) {
    res.json(fallbackOverview);
  }
});

app.get(/^\/(?!api).*/, (req, res, next) => {
  const indexPath = path.join(frontendDistPath, 'index.html');
  res.sendFile(indexPath, (error) => {
    if (error) {
      next(error);
    }
  });
});

app.use((req, res) => {
  res.status(404).json({ message: 'Route not found' });
});

app.listen(port, () => {
  console.log(`FireSphere.ai backend listening on http://localhost:${port}`);
});
