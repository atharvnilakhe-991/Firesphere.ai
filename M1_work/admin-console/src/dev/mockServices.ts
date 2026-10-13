import type { SessionUser } from '../../../packages/api-client/src/types';

/** Fixed development fixtures. They are selected only by the explicit demo sign-in. */
const fixtures: Record<string, Record<string, unknown>[]> = {
  applications: [
    { id:'demo-noc-201', reference:'DEMO-NOC-201', applicant:'Sample Civic Hall', status:'DOCUMENT_VERIFICATION', updated:'Today', area:'North zone' },
    { id:'demo-noc-202', reference:'DEMO-NOC-202', applicant:'Sample Market Building', status:'INSPECTION_SCHEDULED', updated:'Yesterday', area:'Central zone' },
  ],
  inspections: [
    { id:'demo-insp-101', reference:'FS-DEMO-101', applicationId:'demo-noc-201', applicant:'Sample Civic Hall', assignee:'Demo Field Officer', status:'ASSIGNED', scheduled:'Today 10:30', priority:'High' },
    { id:'demo-insp-102', reference:'FS-DEMO-102', applicationId:'demo-noc-202', applicant:'Sample Market Building', assignee:'Demo Field Officer', status:'IN_PROGRESS', scheduled:'Today 14:00', priority:'Routine' },
  ],
  incidents: [{ id:'demo-incident-1', reference:'DEMO-INC-01', category:'Sample incident', status:'PRESENTATION_ONLY', received:'—', area:'Local fixture' }],
  officers: [{ id:'demo-officer-1', name:'Demo Field Officer', assignments:2, status:'DEVELOPMENT_FIXTURE', zone:'Sample zone' }],
  users: [{ id:'demo-user-1', name:'Demo Citizen', role:'CITIZEN', status:'DEVELOPMENT_FIXTURE' }],
  organizations: [{ id:'demo-org-1', name:'Sample Fire Authority', status:'DEVELOPMENT_FIXTURE', users:1 }],
  assignments: [{ id:'demo-assign-1', reference:'FS-DEMO-101', officer:'Demo Field Officer', status:'ASSIGNED', priority:'High' }],
  vehicles: [{ id:'demo-vehicle-1', label:'Sample vehicle', status:'Not connected', location:'—' }],
  inventory: [{ id:'demo-inventory-1', label:'Sample inventory item', status:'Not connected', quantity:'—' }],
  training: [{ id:'demo-training-1', label:'Sample training record', status:'Not connected', due:'—' }],
  reports: [{ id:'demo-report-1', label:'Sample report view', status:'Preview only', period:'—' }],
  certificates: [{ id:'demo-certificate-1', label:'No official certificate', status:'Not issued', reference:'—' }],
  notifications: [{ id:'demo-notice-1', title:'Development preview active', message:'Displayed data is local fixture content.', status:'Unread', created:'Now' }],
  gis: [{ id:'demo-map-1', label:'Map integration', status:'Not connected', source:'No geographic data loaded' }],
};

export interface DemoEvidenceItem {
  id: string;
  inspectionId: string;
  fileName: string;
  contentType: string;
  fileSizeBytes: number;
  storageKey: string;
  sha256Hash: string;
  status: string;
  captureTimestamp: string;
  gpsLatitude: number;
  gpsLongitude: number;
  dataUrl?: string;
}

export interface DemoFindingItem {
  id: string;
  evidenceId: string;
  finding: string;
  confidence: number;
  severity: string;
  reviewStatus: string;
  isAdvisory: boolean;
  findingType: string;
  description: string;
  verificationGuidance: string;
  boundingBoxJson: string;
  provenance: string;
  reconciliationStatus: string;
  officerNotes?: string;
  hazardVerified?: boolean;
}

export interface DemoAiAnalysisRun {
  runId: string;
  applicationId: string;
  status: string;
  requestedAt: string;
  completedAt: string;
  modelName: string;
  modelVersion: string;
  cvStatus: string;
  vlmStatus: string;
  reconciliationStatus: string;
  modelApprovalStatus: string;
  calibrationStatus: string;
  failureReason: null;
  findings: DemoFindingItem[];
}

const demoEvidenceStore: Record<string, DemoEvidenceItem[]> = {
  'demo-insp-101': [
    {
      id: 'demo-ev-01',
      inspectionId: 'demo-insp-101',
      fileName: 'corridor_wall_north.jpg',
      contentType: 'image/jpeg',
      fileSizeBytes: 2480192,
      storageKey: 'tenants/demo/evidence/demo-ev-01.jpg',
      sha256Hash: 'a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0',
      status: 'ACTIVE',
      captureTimestamp: '2026-10-11T10:15:00Z',
      gpsLatitude: 19.0760,
      gpsLongitude: 72.8777,
    },
    {
      id: 'demo-ev-02',
      inspectionId: 'demo-insp-101',
      fileName: 'stairwell_exit_access.jpg',
      contentType: 'image/jpeg',
      fileSizeBytes: 3105291,
      storageKey: 'tenants/demo/evidence/demo-ev-02.jpg',
      sha256Hash: 'b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef01',
      status: 'ACTIVE',
      captureTimestamp: '2026-10-11T10:22:00Z',
      gpsLatitude: 19.0762,
      gpsLongitude: 72.8779,
    },
  ],
};

const demoAnalysesStore: Record<string, DemoAiAnalysisRun[]> = {
  'demo-noc-201': [
    {
      runId: 'demo-run-001',
      applicationId: 'demo-noc-201',
      status: 'COMPLETED',
      requestedAt: '2026-10-11T10:25:00Z',
      completedAt: '2026-10-11T10:25:08Z',
      modelName: 'firesphere-perception-baseline-v0.8.0',
      modelVersion: '0.8.0',
      cvStatus: 'NOT_APPROVED_GATED',
      vlmStatus: 'UNAVAILABLE',
      reconciliationStatus: 'SINGLE_PROVIDER',
      modelApprovalStatus: 'NOT_APPROVED',
      calibrationStatus: 'NOT_CALIBRATED',
      failureReason: null,
      findings: [
        {
          id: 'demo-finding-1',
          evidenceId: 'demo-ev-01',
          findingType: 'FIRE_EXTINGUISHER_MISSING',
          finding: 'Fire Extinguisher Missing from Designated Bracket',
          confidence: 0.88,
          severity: 'HIGH',
          reviewStatus: 'PENDING_REVIEW',
          isAdvisory: true,
          provenance: 'Computer Vision (CV Object Detection)',
          reconciliationStatus: 'SINGLE_PROVIDER',
          description: 'Empty wall bracket detected in north corridor. No extinguisher cylinder located in immediate area.',
          verificationGuidance: 'Check if equipment is temporarily removed for maintenance or relocated without authorization.',
          boundingBoxJson: '{"xMin":0.22,"yMin":0.35,"xMax":0.48,"yMax":0.78}',
        },
        {
          id: 'demo-finding-2',
          evidenceId: 'demo-ev-02',
          findingType: 'EMERGENCY_EXIT_OBSTRUCTED',
          finding: 'Emergency Exit Egress Partially Obstructed',
          confidence: 0.94,
          severity: 'CRITICAL',
          reviewStatus: 'PENDING_REVIEW',
          isAdvisory: true,
          provenance: 'Computer Vision (CV Object Detection)',
          reconciliationStatus: 'SINGLE_PROVIDER',
          description: 'Stacked boxes and pallets encroach onto egress stairway landing reducing clearance below statutory minimum.',
          verificationGuidance: 'Inspect clearance width with tape measure; issue immediate rectification notice if clearance < 1.2m.',
          boundingBoxJson: '{"xMin":0.52,"yMin":0.20,"xMax":0.85,"yMax":0.82}',
        },
      ],
    },
  ],
};

const operationToFixture: Record<string,string> = {
  'admin.dashboard':'applications', 'admin.users':'users', 'admin.organizations':'organizations', 'admin.officers':'officers',
  'admin.applications':'applications', 'admin.inspections':'inspections', 'admin.assignments':'assignments', 'admin.incidents':'incidents',
  'admin.vehicles':'vehicles', 'admin.inventory':'inventory', 'admin.training':'training', 'admin.reports':'reports',
  'admin.certificates':'certificates', 'admin.notifications':'notifications', 'gis.layers':'gis',
};

export function loadDemoRecords(operation: string): Record<string,unknown>[] {
  return (fixtures[operationToFixture[operation] ?? operation] ?? []).map((record)=>({...record}));
}

export function loadDemoAiAnalyses(applicationId: string): DemoAiAnalysisRun[] {
  return demoAnalysesStore[applicationId] ?? [];
}

export function loadDemoEvidence(inspectionId: string): DemoEvidenceItem[] {
  return demoEvidenceStore[inspectionId] ?? [];
}

export function updateDemoFindingReview(
  applicationId: string,
  runId: string,
  findingId: string,
  reviewStatus: string,
  officerNotes?: string,
  hazardVerified?: boolean
): boolean {
  const runs = demoAnalysesStore[applicationId];
  if (!runs) return false;
  const run = runs.find((r) => r.runId === runId);
  if (!run) return false;
  const finding = run.findings.find((f) => f.id === findingId);
  if (!finding) return false;
  finding.reviewStatus = reviewStatus;
  finding.officerNotes = officerNotes;
  finding.hazardVerified = hazardVerified;
  return true;
}

export function createDemoAdmin(): { user: SessionUser; token: string } {
  return { user:{id:'local-admin',displayName:'Demo Administrator',email:'admin@demo.local',role:'ADMIN'}, token:'local-demo:ADMIN' };
}

export const isDemoToken = (token: string | null): boolean => token === 'local-demo:ADMIN';

