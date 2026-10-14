export type UserRole = 'CITIZEN' | 'OFFICER' | 'ADMIN';
export type SyncState = 'LOCAL_ONLY' | 'PENDING_SYNC' | 'SYNCING' | 'SYNCED' | 'SYNC_FAILED' | 'CONFLICT';
export type NocStatus = 'DRAFT' | 'SUBMITTED' | 'PAYMENT' | 'DOCUMENT_VERIFICATION' | 'DOCUMENTS_VERIFIED' | 'CHECKLIST_GENERATED' | 'INSPECTION_SCHEDULED' | 'INSPECTION' | 'AI_VALIDATION' | 'PENDING_APPROVAL' | 'APPROVED' | 'CERTIFICATE' | 'ACTIVE' | 'RENEWAL' | 'CORRECTION_REQUIRED' | 'REINSPECTION';

export interface ApiErrorBody { timestamp?: string; status?: number; code?: string; message?: string; path?: string; traceId?: string; }
export interface ApiError extends Error { status?: number; code: string; traceId?: string; retryable: boolean; }
export interface AppConfig { minVersion: string; latestVersion: string; maintenanceMode: boolean; }
export interface SessionUser { id: string; displayName: string; email: string; role: UserRole; organizationId?: string | null; status?: string; }
export interface AuthLoginResponse { accessToken: string; userId: string; email: string; status: string; }
export interface UserProfileResponse { id: string; email: string; status: string; role: UserRole; organizationId: string | null; createdAt: string; updatedAt: string; }
export interface Session { accessToken: string; refreshToken?: string; user: SessionUser; }
export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; }
export interface NocCreateRequest { title: string; buildingName: string; address: string; description?: string; }
export interface NocApplication { id: string; applicantId?: string; organizationId?: string; applicationNumber?: string; title: string; description?: string; buildingName: string; address: string; status: NocStatus | string; createdAt: string; updatedAt?: string; }
export interface InspectionAssignment { id: string; reference: string; applicationId: string; applicationNumber: string; title: string; address: string; propertyType: string; inspectionType: string; status: string; scheduledAt?: string; priority?: string; syncState?: SyncState | string; }
export interface NotificationItem { id: string; title: string; body: string; createdAt: string; readAt?: string; }
