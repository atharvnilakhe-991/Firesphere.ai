import { describe, expect, test } from 'vitest';
import { createDemoSession, demoAssignments, restoreDemoSession } from './mockServices';

describe('mobile development adapter',()=>{
  test('provides deterministic citizen and officer demo sessions',()=>{
    expect(createDemoSession('CITIZEN','citizen@demo.local').user.role).toBe('CITIZEN');
    expect(createDemoSession('OFFICER','officer@demo.local').user.role).toBe('OFFICER');
    expect(demoAssignments.map((item)=>item.id)).toEqual(['demo-inspection-101','demo-inspection-102']);
  });
  test('restores only explicitly tagged development sessions',()=>{
    expect(restoreDemoSession('local-demo:OFFICER:officer%40demo.local')?.role).toBe('OFFICER');
    expect(restoreDemoSession('arbitrary-token')).toBeNull();
  });
});
