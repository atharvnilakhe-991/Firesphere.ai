export type UserRole = 'CITIZEN' | 'OFFICER' | 'ADMIN';
export type SyncState = 'LOCAL_ONLY' | 'PENDING_SYNC' | 'SYNCING' | 'SYNCED' | 'SYNC_FAILED' | 'CONFLICT';
export type NocStatus = 'DRAFT' | 'SUBMITTED' | 'PAYMENT' | 'DOCUMENT_VERIFICATION' | 'DOCUMENTS_VERIFIED' | 'CHECKLIST_GENERATED' | 'INSPECTION_SCHEDULED' | 'INSPECTION' | 'AI_VALIDATION' | 'PENDING_APPROVAL' | 'APPROVED' | 'CERTIFICATE' | 'ACTIVE' | 'RENEWAL' | 'CORRECTION_REQUIRED' | 'REINSPECTION';

export interface ApiErrorBody { timestamp?: string; status?: number; code?: string; message?: string; path?: string; traceId?: string; }
export interface ApiError extends Error { status?: number; code: string; traceId?: string; retryable: boolean; }
export interface AppConfig { minVersion: string; latestVersion: string; maintenanceMode: boolean; }
export interface SessionUser { id: string; displayName: string; email: string; role: UserRole; }
export interface Session { accessToken: string; refreshToken?: string; user: SessionUser; }
export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; }
export interface NocApplication { id: string; applicationNumber: string; status: NocStatus; createdAt: string; updatedAt?: string; }
export interface InspectionAssignment { id: string; reference: string; scheduledAt?: string; syncState: SyncState; }
export interface NotificationItem { id: string; title: string; body: string; createdAt: string; readAt?: string; }

export type FindingReviewStatus = 'PENDING_REVIEW' | 'CONFIRMED' | 'DISMISSED' | 'MODIFIED';

export interface BoundingBox {
  xMin: number;
  yMin: number;
  xMax: number;
  yMax: number;
}

export interface AiFindingDto {
  id: string;
  evidenceId: string | null;
  finding: string;
  confidence: number;
  severity: 'INFORMATIONAL' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  reviewStatus: FindingReviewStatus | string;
  isAdvisory: boolean;
  findingType?: string;
  description?: string;
  verificationGuidance?: string;
  boundingBoxJson?: string;
  provenance?: string;
  reconciliationStatus?: string;
  officerNotes?: string | null;
  hazardVerified?: boolean;
}

export interface AiAnalysisResponse {
  runId: string;
  applicationId: string;
  documentId?: string | null;
  status: 'REQUESTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | string;
  requestedAt: string;
  completedAt?: string | null;
  modelName?: string;
  modelVersion?: string;
  failureReason?: string | null;
  findings: AiFindingDto[];
  inspectionId?: string | null;
  cvStatus?: string;
  vlmStatus?: string;
  reconciliationStatus?: string;
  modelApprovalStatus?: string;
  calibrationStatus?: string;
}

export interface ReviewFindingRequest {
  reviewStatus: 'CONFIRMED' | 'DISMISSED' | 'MODIFIED';
  officerNotes?: string;
  hazardVerified?: boolean;
}

export interface OfficerReviewResponse {
  id?: number;
  uuid: string;
  findingId?: number;
  officerId: number;
  reviewStatus: string;
  officerNotes?: string;
  hazardVerified: boolean;
  reviewedAt: string;
}

export interface EvidenceResponse {
  id: string;
  inspectionId: string;
  fileName: string;
  contentType: string;
  fileSizeBytes: number;
  storageKey: string;
  sha256Hash: string;
  status: string;
  captureTimestamp?: string | null;
  gpsLatitude?: number | null;
  gpsLongitude?: number | null;
  createdAt: string;
}
