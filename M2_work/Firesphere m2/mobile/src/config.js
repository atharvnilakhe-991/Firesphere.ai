export const API_BASE = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:5000/api';

export const DEMO_USER = {
  id: 1,
  orgId: 1,
  username: 'firesphere.admin',
  email: 'admin@firesphere.ai',
  firstName: 'System',
  lastName: 'Administrator',
  status: 'ACTIVE',
  isSuperAdmin: true,
};

export const DEMO_STATS = {
  organizations: 12,
  users: 284,
  incidents: 48,
  inspections: 96,
  aiRuns: 178,
  workflowRuns: 63,
};

export const DEMO_ORGANIZATIONS = [
  { id: 1, name: 'FireSphere Demo Municipal Corporation', org_type: 'MUNICIPAL_CORPORATION', status: 'ACTIVE', city: 'Pune' },
  { id: 2, name: 'Smart City Fire Command', org_type: 'SMART_CITY', status: 'ACTIVE', city: 'Nagpur' },
  { id: 3, name: 'Industrial Safety Board', org_type: 'INDUSTRIAL_PARK', status: 'TRIAL', city: 'Pimpri-Chinchwad' },
];

export const DEMO_INCIDENTS = [
  { id: 1, incident_no: 'FS-INC-2148', incident_type: 'Building Fire', status: 'DISPATCHED', severity: 'HIGH' },
  { id: 2, incident_no: 'FS-INC-2149', incident_type: 'Gas Leak', status: 'ON_SCENE', severity: 'MEDIUM' },
  { id: 3, incident_no: 'FS-INC-2150', incident_type: 'Rescue Call', status: 'RESOLVED', severity: 'LOW' },
];

export const DEMO_AI_RUNS = [
  { id: 1, status: 'COMPLETED', agent_name: 'Inspection Copilot', workflow_name: 'Fire Risk Review' },
  { id: 2, status: 'RUNNING', agent_name: 'Policy Assistant', workflow_name: 'Compliance Summaries' },
  { id: 3, status: 'FAILED', agent_name: 'Operations Assistant', workflow_name: 'Dispatch Orchestration' },
];

export const DEMO_REPORTS = [
  { id: 1, title: 'Regional Fire Risk Score', value: '87%', trend: '+6.2%' },
  { id: 2, title: 'Inspection Completion', value: '94%', trend: '+4.1%' },
  { id: 3, title: 'Average Response Time', value: '12 min', trend: '-1.8 min' },
  { id: 4, title: 'Compliance Coverage', value: '98.7%', trend: '+3.5%' },
];
