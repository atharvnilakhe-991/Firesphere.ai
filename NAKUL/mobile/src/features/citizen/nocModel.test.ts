import { describe, expect, test } from 'vitest';
import { buildNocCreateRequest, createNocDraftMenuActions, getChecklistCompletion, NOC_PRESENTATION_STATES, popNocRoute, validateDemoNocDraft, validateNocCreateFields } from './nocModel';

describe('citizen NOC presentation models',()=>{
  test('validates only generic development form requirements',()=>{
    const errors=validateDemoNocDraft({applicant:'',address:' Road ',buildingUse:'Office',floors:'0'});
    expect(errors.applicant).toBe('Enter an applicant name.');
    expect(errors.address).toBeNull();
    expect(errors.floors).toBe('Enter a whole number from 1 to 999.');
  });
  test('validates and builds exactly the confirmed NOC create payload',()=>{
    const blank={title:' ',buildingName:'',address:'',description:' '};
    expect(validateNocCreateFields(blank)).toEqual({title:'Enter an application title.',buildingName:'Enter the building name.',address:'Enter the premises address.',description:null});
    expect(buildNocCreateRequest({title:'  Fire NOC  ',buildingName:'  Apex Tower ',address:'  1 Main Road ',description:'  '})).toEqual({title:'Fire NOC',buildingName:'Apex Tower',address:'1 Main Road'});
    expect(buildNocCreateRequest({title:'Fire NOC',buildingName:'Apex Tower',address:'1 Main Road',description:'Commercial building'})).toEqual({title:'Fire NOC',buildingName:'Apex Tower',address:'1 Main Road',description:'Commercial building'});
  });
  test('keeps the user-provided lifecycle as presentation values',()=>{
    expect(NOC_PRESENTATION_STATES).toContain('PAYMENT_PENDING');
    expect(NOC_PRESENTATION_STATES).toContain('RENEWAL_DUE');
  });
  test('returns to the prior NOC screen and empties route history at the root',()=>{
    expect(popNocRoute(['home','form','documents'])).toEqual({ route:'documents', history:['home','form'] });
    expect(popNocRoute([])).toEqual({ route:undefined, history:[] });
  });
  test('calculates generic checklist completion without workflow authority',()=>{
    expect(getChecklistCompletion({a:'PASS',b:'N/A'},['a','b','c'])).toBe(67);
    expect(getChecklistCompletion({},[])).toBe(100);
  });
  test('draft menu exposes only open and cancel actions and invokes their handlers',()=>{
    let opened=0;
    const actions=createNocDraftMenuActions(()=>opened++);
    actions[0].onPress?.();
    actions[1].onPress?.();
    expect(actions.map(({text})=>text)).toEqual(['Open local draft','Cancel']);
    expect(actions[1].style).toBe('cancel');
    expect(opened).toBe(1);
  });
});
