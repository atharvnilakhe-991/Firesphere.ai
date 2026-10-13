import { describe, expect, test } from 'vitest';
import { getChecklistCompletion, NOC_PRESENTATION_STATES, validateDemoNocDraft } from './nocModel';

describe('citizen NOC presentation models',()=>{
  test('validates only generic development form requirements',()=>{
    const errors=validateDemoNocDraft({applicant:'',address:' Road ',buildingUse:'Office',floors:'0'});
    expect(errors.applicant).toBe('Enter an applicant name.');
    expect(errors.address).toBeNull();
    expect(errors.floors).toBe('Enter a whole number from 1 to 999.');
  });
  test('keeps the user-provided lifecycle as presentation values',()=>{
    expect(NOC_PRESENTATION_STATES).toContain('PAYMENT_PENDING');
    expect(NOC_PRESENTATION_STATES).toContain('RENEWAL_DUE');
  });
  test('calculates generic checklist completion without workflow authority',()=>{
    expect(getChecklistCompletion({a:'PASS',b:'N/A'},['a','b','c'])).toBe(67);
    expect(getChecklistCompletion({},[])).toBe(100);
  });
});
