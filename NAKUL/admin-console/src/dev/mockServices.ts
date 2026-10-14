import type { SessionUser } from '../../../packages/api-client/src/types';

/** Fixed development fixtures. They are selected only by the explicit demo sign-in. */
const fixtures: Record<string, Record<string, unknown>[]> = {
  applications: [
    { id:'demo-noc-201', reference:'DEMO-NOC-201', applicant:'Sample Civic Hall', status:'DOCUMENT_VERIFICATION', updated:'Today', area:'North zone' },
    { id:'demo-noc-202', reference:'DEMO-NOC-202', applicant:'Sample Market Building', status:'INSPECTION_SCHEDULED', updated:'Yesterday', area:'Central zone' },
  ],
  inspections: [
    { id:'demo-insp-101', reference:'FS-DEMO-101', assignee:'Demo Field Officer', status:'ASSIGNED', scheduled:'Today 10:30', priority:'High' },
    { id:'demo-insp-102', reference:'FS-DEMO-102', assignee:'Demo Field Officer', status:'IN_PROGRESS', scheduled:'Today 14:00', priority:'Routine' },
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

const operationToFixture: Record<string,string> = {
  'admin.dashboard':'applications', 'admin.users':'users', 'admin.organizations':'organizations', 'admin.officers':'officers',
  'admin.applications':'applications', 'admin.inspections':'inspections', 'admin.assignments':'assignments', 'admin.incidents':'incidents',
  'admin.vehicles':'vehicles', 'admin.inventory':'inventory', 'admin.training':'training', 'admin.reports':'reports',
  'admin.certificates':'certificates', 'admin.notifications':'notifications', 'gis.layers':'gis',
};

export function loadDemoRecords(operation: string): Record<string,unknown>[] {
  return (fixtures[operationToFixture[operation] ?? operation] ?? []).map((record)=>({...record}));
}

export function createDemoAdmin(): { user: SessionUser; token: string } {
  return { user:{id:'local-admin',displayName:'Demo Administrator',email:'admin@demo.local',role:'ADMIN'}, token:'local-demo:ADMIN' };
}

export const isDemoToken = (token: string | null): boolean => token === 'local-demo:ADMIN';
