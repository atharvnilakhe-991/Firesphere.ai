import type { Session, SessionUser, UserRole } from '@firesphere/api-client/types';

/** Deterministic frontend-only fixtures. Never imported by production app paths. */
export interface DemoAssignment {
  id: string;
  reference: string;
  scheduledAt: string;
  priority: 'Routine' | 'High';
  status: 'Assigned' | 'In progress' | 'Completed';
  address: string;
  propertyType: string;
  previousInspection: string;
  previousViolations: number;
}

export const demoAssignments: DemoAssignment[] = [
  { id: 'demo-inspection-101', reference: 'FS-DEMO-101', scheduledAt: 'Today · 10:30 AM', priority: 'High', status: 'Assigned', address: '18 Civic Centre Road', propertyType: 'Public assembly', previousInspection: 'No prior inspection recorded', previousViolations: 0 },
  { id: 'demo-inspection-102', reference: 'FS-DEMO-102', scheduledAt: 'Today · 2:00 PM', priority: 'Routine', status: 'Assigned', address: '42 Market Street', propertyType: 'Commercial', previousInspection: 'Development record · 12 Feb 2026', previousViolations: 1 },
];

export const genericChecklist = [
  { id: 'access', section: 'Access & egress', question: 'Are access routes clear?', required: true },
  { id: 'equipment', section: 'Fire safety equipment', question: 'Is the observed equipment accessible?', required: true },
  { id: 'signage', section: 'Safety information', question: 'Is safety signage visible?', required: false },
] as const;

export const demoNotifications = [
  { id: 'demo-note-1', title: 'Development mode is active', body: 'All displayed records are local fixtures and are not official FireSphere data.', date: 'Now', unread: true },
  { id: 'demo-note-2', title: 'Field safety reminder', body: 'Follow your organization’s field safety procedures during site visits.', date: 'Today', unread: false },
];
export const demoCitizenApplications = [
  { id:'demo-citizen-noc-1', applicationNumber:'DEMO-NOC-501', status:'PAYMENT_PENDING', updatedAt:'Development fixture', label:'Sample Community Hall' },
];
export function loadMobileDemoRecords(operation:string):unknown[] {
  if(operation==='citizen.notifications')return demoNotifications.map((item)=>({...item}));
  if(operation==='citizen.applications')return demoCitizenApplications.map((item)=>({...item}));
  return [];
}

export function createDemoSession(role: Exclude<UserRole, 'ADMIN'>, email: string): Session {
  const safeEmail = email.trim() || `${role.toLowerCase()}@demo.local`;
  const name = role === 'OFFICER' ? 'Demo Field Officer' : 'Demo Citizen';
  return {
    accessToken: `local-demo:${role}:${encodeURIComponent(safeEmail)}`,
    user: { id: `local-${role.toLowerCase()}`, displayName: name, email: safeEmail, role } satisfies SessionUser,
  };
}

export function restoreDemoSession(token: string): SessionUser | null {
  const match = /^local-demo:(CITIZEN|OFFICER):(.+)$/.exec(token);
  if (!match) return null;
  return createDemoSession(match[1] as Exclude<UserRole, 'ADMIN'>, decodeURIComponent(match[2])).user;
}
