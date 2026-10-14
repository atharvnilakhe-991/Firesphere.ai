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

export type ChecklistLifecycleStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED';
export type ChecklistResponseStatus = 'COMPLIANT' | 'NON_COMPLIANT' | 'NOT_APPLICABLE' | 'NEEDS_FURTHER_INSPECTION' | 'PENDING';

export interface LinkedEvidenceDto {
  evidenceId: string;
  fileName: string;
  contentType: string;
  storageKey: string;
  notes?: string | null;
  attachedAt: string;
}

export interface AiSuggestionLinkDto {
  linkId: string;
  findingId: string;
  findingType?: string;
  severity: string;
  confidence: number;
  suggestedCompliance: string;
  linkProvenance: string;
  sourceProvenance?: string;
  mappingReason?: string;
  modelName?: string;
  modelVersion?: string;
  linkStatus: string;
  reviewStatus: string;
  officerComment?: string | null;
  description?: string;
  boundingBoxJson?: string;
  createdAt: string;
}

export interface InspectionChecklistItemDetailDto {
  responseId: string;
  templateItemId: string;
  itemSequence: number;
  itemCode: string;
  itemTitle: string;
  parameterDescription: string;
  requirementType: string;
  verificationMethod: string;
  evidenceRequired: boolean;
  aiCategoryTag?: string | null;
  helpGuidance?: string | null;
  officerResponse: ChecklistResponseStatus;
  officerRemarks?: string | null;
  officerId?: string | null;
  respondedAt?: string | null;
  revisionNumber: number;
  linkedEvidence: LinkedEvidenceDto[];
  aiSuggestions: AiSuggestionLinkDto[];
}

export interface InspectionChecklistResponseDto {
  checklistId: string;
  inspectionId: string;
  applicationId: string;
  templateId: string;
  templateCode: string;
  templateTitle: string;
  templateVersion: number;
  status: ChecklistLifecycleStatus;
  totalItems: number;
  reviewedItems: number;
  compliantCount: number;
  nonCompliantCount: number;
  notApplicableCount: number;
  needsFurtherInspectionCount: number;
  pendingCount: number;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
  completedByOfficer?: string | null;
  summaryNotes?: string | null;
  items: InspectionChecklistItemDetailDto[];
}

export interface RecordOfficerResponseRequest {
  responseStatus: ChecklistResponseStatus | string;
  officerRemarks?: string;
  linkedEvidenceIds?: string[];
}

export interface CompleteChecklistRequest {
  summaryNotes?: string;
}

export interface ReviewAiLinkRequest {
  reviewStatus: 'CONFIRMED' | 'DISMISSED' | 'MODIFIED' | string;
  officerComment?: string;
  verifiedCompliance?: ChecklistResponseStatus | string;
}

export interface CreateInspectionChecklistRequest {
  templateCode?: string;
  occupancyType?: string;
  inspectionType?: string;
}

export interface AiFindingMappingDto {
  linkId?: string;
  findingId: string;
  inspectionId?: string;
  checklistInstanceId?: string;
  templateItemId?: string;
  itemCode?: string;
  checklistItemCode?: string;
  itemTitle?: string;
  sourceProvenance?: string;
  provenance?: string;
  originalCategory?: string;
  category?: string;
  findingSeverity?: string;
  confidence?: number;
  findingConfidence?: number;
  evidenceId?: string | null;
  mappingStatus?: string;
  suggestedCompliance?: string;
  suggestedStatus?: string;
  linkStatus?: string;
  reviewStatus?: string;
  mappingReason?: string;
  mappingRule?: string;
  explanation?: string;
  officerComment?: string | null;
  modelName?: string;
  modelVersion?: string;
  conflictType?: string | null;
  hasConflictingObservations?: boolean;
  crossValidated?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface UnmappedFindingDto {
  findingId: string;
  inspectionId?: string;
  evidenceId?: string | null;
  originalCategory?: string;
  category?: string;
  findingSeverity?: string;
  confidence: number;
  status?: string; // UNMAPPED or AMBIGUOUS
  reason: string;
  sourceProvenance?: string;
  provenance?: string;
  modelName?: string;
  modelVersion?: string;
}

export interface AiChecklistMappingResult {
  inspectionId: string;
  checklistInstanceId: string;
  templateCode: string;
  totalFindingsCount: number;
  mappedCount: number;
  unmappedCount: number;
  ambiguousCount: number;
  mappings: AiFindingMappingDto[];
  unmappedFindings: UnmappedFindingDto[];
}

export type WorkflowAction =
  | 'SUBMIT'
  | 'START_REVIEW'
  | 'VERIFY_DOCUMENTS'
  | 'REJECT_DOCUMENTS'
  | 'SCHEDULE_INSPECTION'
  | 'START_INSPECTION'
  | 'COMPLETE_INSPECTION'
  | 'APPROVE'
  | 'REJECT'
  | 'REQUEST_CORRECTIONS'
  | 'REINSPECT'
  | 'GENERATE_CERTIFICATE';

export interface WorkflowActionRequest {
  action: WorkflowAction;
  reason?: string;
}

export interface WorkflowTransitionResponse {
  applicationId: string;
  previousState: NocStatus;
  action: WorkflowAction;
  currentState: NocStatus;
}

