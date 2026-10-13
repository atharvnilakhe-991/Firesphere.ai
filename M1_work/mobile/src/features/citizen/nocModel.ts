export const NOC_PRESENTATION_STATES = [
  'DRAFT','SUBMITTED','PAYMENT_PENDING','PAYMENT_COMPLETED','DOCUMENT_VERIFICATION','DOCUMENTS_VERIFIED',
  'CHECKLIST_GENERATED','INSPECTION_SCHEDULED','INSPECTION_IN_PROGRESS','INSPECTION_COMPLETED','AI_VALIDATION',
  'PENDING_APPROVAL','APPROVED','CERTIFICATE_GENERATED','ACTIVE','RENEWAL_DUE',
] as const;

export interface DemoNocDraft { applicant: string; address: string; buildingUse: string; floors: string; }

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
