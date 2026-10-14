export const NOC_PRESENTATION_STATES = [
  'DRAFT','SUBMITTED','PAYMENT_PENDING','PAYMENT_COMPLETED','DOCUMENT_VERIFICATION','DOCUMENTS_VERIFIED',
  'CHECKLIST_GENERATED','INSPECTION_SCHEDULED','INSPECTION_IN_PROGRESS','INSPECTION_COMPLETED','AI_VALIDATION',
  'PENDING_APPROVAL','APPROVED','CERTIFICATE_GENERATED','ACTIVE','RENEWAL_DUE',
] as const;

export function popNocRoute<T>(history: readonly T[]): { route: T | undefined; history: T[] } {
  const index = history.length - 1;
  return { route: history[index], history: history.slice(0, index) };
}

export interface NocDraftFields { title: string; buildingName: string; address: string; description: string; }
export interface NocCreateFields { title: string; buildingName: string; address: string; description?: string; }
export interface DemoNocDraft { applicant: string; address: string; buildingUse: string; floors: string; }

export function validateNocCreateFields(fields: NocDraftFields): Record<keyof NocDraftFields, string | null> {
  return {
    title: fields.title.trim() ? null : 'Enter an application title.',
    buildingName: fields.buildingName.trim() ? null : 'Enter the building name.',
    address: fields.address.trim() ? null : 'Enter the premises address.',
    description: fields.description.length <= 2000 ? null : 'Description must be 2,000 characters or fewer.',
  };
}

export function buildNocCreateRequest(fields: NocDraftFields): NocCreateFields {
  const request: NocCreateFields = {
    title: fields.title.trim(),
    buildingName: fields.buildingName.trim(),
    address: fields.address.trim(),
  };
  const description = fields.description.trim();
  if (description) request.description = description;
  return request;
}

/** Only expose actions that exist for the static local draft preview. */
export function createNocDraftMenuActions(openDraft: () => void) {
  return [
    { text: 'Open local draft', onPress: openDraft },
    { text: 'Cancel', style: 'cancel' as const },
  ];
}

/** Generic client-side demo validation only; never represents regulatory validation. */
export function validateDemoNocDraft(draft: DemoNocDraft): Record<keyof DemoNocDraft, string | null> {
  return {
    applicant: draft.applicant.trim() ? null : 'Enter an applicant name.',
    address: draft.address.trim() ? null : 'Enter a premises address.',
    buildingUse: draft.buildingUse.trim() ? null : 'Enter a building use.',
    floors: /^[1-9]\d{0,2}$/.test(draft.floors.trim()) ? null : 'Enter a whole number from 1 to 999.',
  };
}

export function getChecklistCompletion(answers: Record<string,string>, itemIds: readonly string[]): number {
  if (itemIds.length === 0) return 100;
  const complete = itemIds.filter((id)=>['PASS','FAIL','N/A'].includes(answers[id])).length;
  return Math.round(complete / itemIds.length * 100);
}
