import { describe,expect,test } from 'vitest';
import { createDemoAdmin,loadDemoRecords } from './mockServices';

describe('admin development adapter',()=>{
  test('uses stable, explicitly identified local fixtures',()=>{
    const first=loadDemoRecords('admin.applications');
    expect(first).toHaveLength(2);
    expect(first[0].id).toBe('demo-noc-201');
    expect(loadDemoRecords('admin.applications')).toEqual(first);
    expect(createDemoAdmin().token).toBe('local-demo:ADMIN');
  });
  test('keeps service-only pages visibly disconnected in development fixtures',()=>{
    expect(loadDemoRecords('admin.vehicles')[0].status).toBe('Not connected');
    expect(loadDemoRecords('gis.layers')[0].source).toBe('No geographic data loaded');
  });
});
