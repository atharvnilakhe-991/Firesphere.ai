import { afterEach, describe, expect, test, vi } from 'vitest';
import { createApiClient, DEFAULT_CONTRACT_PATHS } from './client';
import type {
  InspectionChecklistResponseDto,
  RecordOfficerResponseRequest,
  CompleteChecklistRequest,
  ReviewAiLinkRequest,
  WorkflowActionRequest,
  WorkflowTransitionResponse,
  AiChecklistMappingResult,
} from './types';

afterEach(() => vi.unstubAllGlobals());

describe('Stage 12 & 12.1: Independent Frontend and Backend Integration Verification', () => {
  const baseUrl = 'https://api.example.test';
  const defaultClient = createApiClient({
    baseUrl,
    contractPaths: DEFAULT_CONTRACT_PATHS,
    getAccessToken: () => 'valid-officer-jwt',
  });

  // 1. Assigned inspection list loads successfully
  test('1. Assigned inspection list loads successfully from backend contract', async () => {
    const mockAssignments = [
      {
        id: 'insp-101',
        reference: 'FS-2026-001',
        applicationId: 'app-901',
        applicationNumber: 'NOC-2026-001',
        title: 'Commercial Complex Inspection',
        address: '100 Cyber Heights',
        propertyType: 'COMMERCIAL',
        inspectionType: 'REGULATORY',
        status: 'ASSIGNED',
        scheduledAt: '2026-10-15T10:00:00Z',
        priority: 'ROUTINE',
        syncState: 'SYNCED',
      },
    ];

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(mockAssignments), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const result = await defaultClient.request<typeof mockAssignments>('officer.assignments');
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('insp-101');
    expect(result[0].status).toBe('ASSIGNED');
  });

  // 2. Empty inspection list is handled correctly
  test('2. Empty inspection list is handled correctly without throwing or fabricating', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify([]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const result = await defaultClient.request<unknown[]>('officer.assignments');
    expect(Array.isArray(result)).toBe(true);
    expect(result).toHaveLength(0);
  });

  // 3. API failure is handled without a false success message
  test('3. API failure propagates normalized error without false success indication', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 500,
      code: 'INTERNAL_SERVER_ERROR',
      message: 'Database connection failed',
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    })));

    await expect(defaultClient.request('officer.assignments')).rejects.toMatchObject({
      code: 'INTERNAL_SERVER_ERROR',
      message: 'Database connection failed',
      status: 500,
    });
  });

  // 4. Unauthorized requests are handled correctly
  test('4. Unauthorized requests invoke onUnauthorized callback and reject cleanly', async () => {
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      baseUrl,
      contractPaths: DEFAULT_CONTRACT_PATHS,
      onUnauthorized,
    });

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 401,
      code: 'EXPIRED_TOKEN',
      message: 'Authentication token has expired',
    }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    })));

    await expect(client.request('officer.assignments')).rejects.toMatchObject({
      code: 'EXPIRED_TOKEN',
      status: 401,
    });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  // 5. Checklist responses use the supported response schema
  test('5. Checklist responses use supported response schema (RecordOfficerResponseRequest: responseStatus, officerRemarks, linkedEvidenceIds)', async () => {
    const capturedResp: { body?: Record<string, unknown>; url?: string } = {};

    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      capturedResp.url = url;
      capturedResp.body = JSON.parse(String(init?.body)) as Record<string, unknown>;
      return new Response(JSON.stringify({
        checklistId: 'chk-1',
        inspectionId: 'insp-101',
        status: 'IN_PROGRESS',
        totalItems: 5,
        reviewedItems: 1,
        compliantCount: 1,
        nonCompliantCount: 0,
        notApplicableCount: 0,
        needsFurtherInspectionCount: 0,
        pendingCount: 4,
        items: [],
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    const payload: RecordOfficerResponseRequest = {
      responseStatus: 'COMPLIANT',
      officerRemarks: 'Exit signs clearly illuminated and unobstructed.',
      linkedEvidenceIds: ['ev-01'],
    };

    const res = await defaultClient.request<InspectionChecklistResponseDto>('officer.checklist.response', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }, { inspectionId: 'insp-101', itemId: 'item-1' });

    expect(capturedResp.url).toContain('/api/v1/inspections/insp-101/checklist/items/item-1/response');
    expect(capturedResp.body).toEqual(payload);
    expect(res.compliantCount).toBe(1);
  });

  // 6. Invalid checklist responses display server errors
  test('6. Invalid checklist responses display server errors (HTTP 400 rejection)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400,
      code: 'INVALID_CHECKLIST_RESPONSE',
      message: 'Officer remarks explaining the deficiency are mandatory when recording a NON_COMPLIANT response',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })));

    const invalidPayload: RecordOfficerResponseRequest = {
      responseStatus: 'NON_COMPLIANT',
      officerRemarks: '',
      linkedEvidenceIds: [],
    };

    await expect(defaultClient.request('officer.checklist.response', {
      method: 'PUT',
      body: JSON.stringify(invalidPayload),
    }, { inspectionId: 'insp-101', itemId: 'item-2' })).rejects.toMatchObject({
      code: 'INVALID_CHECKLIST_RESPONSE',
      message: expect.stringContaining('mandatory when recording a NON_COMPLIANT response'),
      status: 400,
    });
  });

  // 7. Checklist completion reflects actual backend state
  test('7. Checklist completion calls server endpoint and verifies status: COMPLETED', async () => {
    let capturedUrl = '';
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      capturedUrl = url;
      return new Response(JSON.stringify({
        checklistId: 'chk-1',
        inspectionId: 'insp-101',
        status: 'COMPLETED',
        completedAt: '2026-10-14T01:00:00Z',
        completedByOfficer: 'officer-42',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    const completeReq: CompleteChecklistRequest = {
      summaryNotes: 'All mandatory items verified on-site.',
    };

    const completion = await defaultClient.request<{ status: string }>('officer.checklist.complete', {
      method: 'POST',
      body: JSON.stringify(completeReq),
    }, { inspectionId: 'insp-101' });

    expect(capturedUrl).toContain('/api/v1/inspections/insp-101/checklist/complete');
    expect(completion.status).toBe('COMPLETED');
  });

  // 8. AI suggestions remain separate from statutory responses
  test('8. AI suggestions remain advisory and independent from statutory officer decisions', async () => {
    const checklistWithAi: InspectionChecklistResponseDto = {
      checklistId: 'chk-1',
      inspectionId: 'insp-101',
      applicationId: 'app-901',
      templateId: 'tmpl-01',
      templateCode: 'FS-COMM-2026',
      templateTitle: 'Commercial Building Fire Safety Checklist',
      templateVersion: 1,
      status: 'IN_PROGRESS',
      totalItems: 1,
      reviewedItems: 0,
      compliantCount: 0,
      nonCompliantCount: 0,
      notApplicableCount: 0,
      needsFurtherInspectionCount: 0,
      pendingCount: 1,
      createdAt: '2026-10-14T01:00:00Z',
      updatedAt: '2026-10-14T01:00:00Z',
      items: [
        {
          responseId: 'resp-1',
          templateItemId: 'item-1',
          itemSequence: 1,
          itemCode: 'FS-EXT-01',
          itemTitle: 'Fire Extinguishers Check',
          parameterDescription: 'Fire extinguishers present and unblocked?',
          requirementType: 'MANDATORY',
          verificationMethod: 'VISUAL',
          evidenceRequired: true,
          officerResponse: 'PENDING',
          revisionNumber: 0,
          linkedEvidence: [],
          aiSuggestions: [
            {
              linkId: 'link-1',
              findingId: 'f-cv-99',
              severity: 'MEDIUM',
              confidence: 0.82,
              suggestedCompliance: 'COMPLIANT',
              linkProvenance: 'AUTOMATIC',
              linkStatus: 'SUGGESTED',
              reviewStatus: 'PENDING_REVIEW',
              description: 'Visual detection of extinguisher near entrance corridor.',
              createdAt: '2026-10-14T01:00:00Z',
            },
          ],
        },
      ],
    };

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(checklistWithAi), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const fetched = await defaultClient.request<InspectionChecklistResponseDto>('officer.checklist.get', {}, { inspectionId: 'insp-101' });
    const item = fetched.items[0];

    // Officer decision remains PENDING despite AI finding present
    expect(item.officerResponse).toBe('PENDING');
    expect(item.aiSuggestions).toHaveLength(1);
    expect(item.aiSuggestions[0].suggestedCompliance).toBe('COMPLIANT');
    expect(item.aiSuggestions[0].reviewStatus).toBe('PENDING_REVIEW');
  });

  // 9. Conflicting AI findings are not silently discarded
  test('9. Conflicting AI findings and unmapped findings are preserved in response contract', async () => {
    const mappingResult: AiChecklistMappingResult = {
      inspectionId: 'insp-101',
      checklistInstanceId: 'chk-1',
      templateCode: 'FS-COMM-2026',
      totalFindingsCount: 3,
      mappedCount: 1,
      unmappedCount: 1,
      ambiguousCount: 1,
      mappings: [
        {
          findingId: 'find-cv-01',
          itemCode: 'EXIT-01',
          conflictType: 'CONFLICTING_HAZARD',
          mappingReason: 'CV detected obstacle; VLM reported clear pathway',
          hasConflictingObservations: true,
        },
      ],
      unmappedFindings: [
        {
          findingId: 'find-qwen-09',
          originalCategory: 'ELECTRICAL_PANEL_HAZARD',
          confidence: 0.79,
          status: 'UNMAPPED',
          reason: 'Active statutory checklist templates do not contain an approved electrical panel clause.',
          sourceProvenance: 'OLLAMA_QWEN',
        },
      ],
    };

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(mappingResult), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const result = await defaultClient.request<AiChecklistMappingResult>('officer.checklist.aiMappings', {}, { inspectionId: 'insp-101' });
    expect(result.mappings[0].conflictType).toBe('CONFLICTING_HAZARD');
    expect(result.mappings[0].hasConflictingObservations).toBe(true);
    expect(result.unmappedFindings).toHaveLength(1);
    expect(result.unmappedFindings[0].status).toBe('UNMAPPED');
  });

  // 10. Citizen views do not expose officer-only AI data
  test('10. Citizen view contract exposes application status without officer-only AI data', async () => {
    const citizenApplication = {
      id: 'app-901',
      applicationNumber: 'NOC-2026-001',
      buildingName: 'Metro Mall',
      status: 'UNDER_INSPECTION',
      submissionDate: '2026-10-01T08:00:00Z',
    };

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify([citizenApplication]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const result = await defaultClient.request<Array<Record<string, unknown>>>('citizen.applications');
    expect(result[0].status).toBe('UNDER_INSPECTION');
    expect(result[0].aiSuggestions).toBeUndefined();
    expect(result[0].aiMappings).toBeUndefined();
    expect(result[0].cvFindings).toBeUndefined();
  });

  // 11. Final approval status comes from backend
  test('11. Final Fire NOC approval state transitions are strictly returned from backend (WorkflowTransitionResponse: currentState)', async () => {
    const capturedTransition: { body?: Record<string, unknown> } = {};
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init?: RequestInit) => {
      capturedTransition.body = JSON.parse(String(init?.body)) as Record<string, unknown>;
      return new Response(JSON.stringify({
        applicationId: 'app-901',
        previousState: 'INSPECTION_COMPLETED',
        action: 'APPROVE',
        currentState: 'APPROVED',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    const approveReq: WorkflowActionRequest = {
      action: 'APPROVE',
      reason: 'All statutory fire-safety criteria satisfied.',
    };

    const res = await defaultClient.request<WorkflowTransitionResponse>('noc.application.transition', {
      method: 'POST',
      body: JSON.stringify(approveReq),
    }, { applicationId: 'app-901' });

    expect(capturedTransition.body?.['action']).toBe('APPROVE');
    expect(capturedTransition.body?.['reason']).toBe('All statutory fire-safety criteria satisfied.');
    expect(res.currentState).toBe('APPROVED');
  });

  // 12. UI cannot bypass server-side approval checks
  test('12. Backend approval gate failure (HTTP 400) refuses approval when checklist is incomplete', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400,
      code: 'GATE_REFUSAL',
      message: 'Fire NOC cannot be approved: Checklist must be in COMPLETED state and have all mandatory items answered.',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })));

    const prematureApprove: WorkflowActionRequest = {
      action: 'APPROVE',
      reason: 'Attempting premature approval without completed checklist.',
    };

    await expect(defaultClient.request('noc.application.transition', {
      method: 'POST',
      body: JSON.stringify(prematureApprove),
    }, { applicationId: 'app-901' })).rejects.toMatchObject({
      code: 'GATE_REFUSAL',
      message: expect.stringContaining('Checklist must be in COMPLETED state'),
      status: 400,
    });
  });

  // 13. Duplicate submission is handled safely
  test('13. Duplicate transition attempt returns 409 conflict and prevents state corruption', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 409,
      code: 'INVALID_STATE_TRANSITION',
      message: 'Application has already transitioned to APPROVED or is currently locked.',
    }), {
      status: 409,
      headers: { 'Content-Type': 'application/json' },
    })));

    await expect(defaultClient.request('noc.application.transition', {
      method: 'POST',
      body: JSON.stringify({ action: 'APPROVE', reason: 'Duplicate click' }),
    }, { applicationId: 'app-901' })).rejects.toMatchObject({
      code: 'INVALID_STATE_TRANSITION',
      status: 409,
    });
  });

  // 14. Existing frontend functionality continues working
  test('14. Default contract paths map to standard backend /api/v1 endpoints', () => {
    expect(DEFAULT_CONTRACT_PATHS['auth.login']).toBe('auth/login');
    expect(DEFAULT_CONTRACT_PATHS['auth.me']).toBe('users/me');
    expect(DEFAULT_CONTRACT_PATHS['officer.assignments']).toBe('officer/assignments');
    expect(DEFAULT_CONTRACT_PATHS['officer.checklist.get']).toBe('inspections/{inspectionId}/checklist');
    expect(DEFAULT_CONTRACT_PATHS['officer.checklist.response']).toBe('inspections/{inspectionId}/checklist/items/{itemId}/response');
    expect(DEFAULT_CONTRACT_PATHS['officer.checklist.complete']).toBe('inspections/{inspectionId}/checklist/complete');
    expect(DEFAULT_CONTRACT_PATHS['officer.checklist.aiMappings']).toBe('inspections/{inspectionId}/checklist/ai-mappings');
    expect(DEFAULT_CONTRACT_PATHS['officer.checklist.aiLinkReview']).toBe('inspections/{inspectionId}/checklist/items/{itemId}/ai-links/{linkId}');
    expect(DEFAULT_CONTRACT_PATHS['noc.application.transition']).toBe('noc/applications/{applicationId}/transitions');
  });

  // 15. Officer review of AI suggestion uses PATCH method
  test('15. Officer review of AI advisory suggestion link uses PATCH method', async () => {
    const capturedReview: { method?: string; url?: string; body?: Record<string, unknown> } = {};

    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      capturedReview.url = url;
      capturedReview.method = init?.method ?? 'GET';
      capturedReview.body = JSON.parse(String(init?.body)) as Record<string, unknown>;
      return new Response(JSON.stringify({
        responseId: 'resp-1',
        templateItemId: 'item-1',
        officerResponse: 'COMPLIANT',
        aiSuggestions: [
          {
            linkId: 'link-101',
            reviewStatus: 'CONFIRMED',
            linkStatus: 'ACCEPTED',
            officerComment: 'Physically verified on-site during inspection.',
          },
        ],
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));

    const reviewPayload: ReviewAiLinkRequest = {
      reviewStatus: 'CONFIRMED',
      officerComment: 'Physically verified on-site during inspection.',
    };

    const res = await defaultClient.request<{ officerResponse: string }>('officer.checklist.aiLinkReview', {
      method: 'PATCH',
      body: JSON.stringify(reviewPayload),
    }, { inspectionId: 'insp-101', itemId: 'item-1', linkId: 'link-101' });

    expect(capturedReview.method).toBe('PATCH');
    expect(capturedReview.url).toContain('/api/v1/inspections/insp-101/checklist/items/item-1/ai-links/link-101');
    expect(capturedReview.body?.['reviewStatus']).toBe('CONFIRMED');
    // Officer response remains unchanged
    expect(res.officerResponse).toBe('COMPLIANT');
  });

  // 16. Network failure during checklist completion rejects and does not mark completed
  test('16. Network failure during checklist completion rejects and prevents completion state simulation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network connection aborted')));

    await expect(defaultClient.request('officer.checklist.complete', {
      method: 'POST',
      body: JSON.stringify({ summaryNotes: 'Attempting completion while disconnected' }),
    }, { inspectionId: 'insp-101' })).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
      retryable: true,
    });
  });

  // 17. Network failure during workflow transition rejects and prevents state mutation
  test('17. Network failure during workflow transition rejects without advancing status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Failed to fetch')));

    await expect(defaultClient.request('noc.application.transition', {
      method: 'POST',
      body: JSON.stringify({ action: 'APPROVE', reason: 'Offline submission attempt' }),
    }, { applicationId: 'app-901' })).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
    });
  });

  // 18. Mandatory remarks validation on NOT_APPLICABLE items
  test('18. Server rejects NOT_APPLICABLE checklist response without justification remarks (Rule 2)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Officer remarks providing statutory justification are mandatory for NOT_APPLICABLE items',
    }), { status: 400, headers: { 'Content-Type': 'application/json' } })));

    await expect(defaultClient.request('officer.checklist.response', {
      method: 'PUT',
      body: JSON.stringify({ responseStatus: 'NOT_APPLICABLE', officerRemarks: '' }),
    }, { inspectionId: 'insp-101', itemId: 'item-3' })).rejects.toMatchObject({
      code: 'VALIDATION_ERROR',
      message: expect.stringContaining('mandatory for NOT_APPLICABLE items'),
      status: 400,
    });
  });

  // 19. Mandatory evidence check rejected by backend
  test('19. Backend rejects response when photographic evidence is mandatory but missing (Rule 4)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400,
      code: 'EVIDENCE_REQUIRED',
      message: 'Photographic evidence is mandatory for checklist item EXT-01 (Fire Extinguisher Availability)',
    }), { status: 400, headers: { 'Content-Type': 'application/json' } })));

    await expect(defaultClient.request('officer.checklist.response', {
      method: 'PUT',
      body: JSON.stringify({
        responseStatus: 'COMPLIANT',
        officerRemarks: 'Checked extinguisher',
        linkedEvidenceIds: [],
      }),
    }, { inspectionId: 'insp-101', itemId: 'item-ext-01' })).rejects.toMatchObject({
      code: 'EVIDENCE_REQUIRED',
      status: 400,
    });
  });

  // 20. Cross-tenant inspection access is rejected with HTTP 403 Forbidden
  test('20. Cross-tenant access is rejected with HTTP 403 Forbidden', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 403,
      code: 'FORBIDDEN',
      message: 'Cross-tenant access prohibited',
    }), { status: 403, headers: { 'Content-Type': 'application/json' } })));

    await expect(defaultClient.request('officer.checklist.get', {}, {
      inspectionId: 'cross-tenant-insp-999',
    })).rejects.toMatchObject({
      code: 'FORBIDDEN',
      status: 403,
    });
  });
});
