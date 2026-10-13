import { afterEach, describe, expect, test, vi } from 'vitest';
import { createApiClient, collectPages } from './client';

afterEach(()=>vi.unstubAllGlobals());

describe('API retry and pagination helpers',()=>{
  test('retries retryable GET requests and stops after success',async()=>{
    const fetchMock=vi.fn().mockResolvedValueOnce(new Response('unavailable',{status:503})).mockResolvedValueOnce(new Response(JSON.stringify([{id:'one'}]),{status:200}));
    vi.stubGlobal('fetch',fetchMock);
    const client=createApiClient({baseUrl:'https://api.example.test',contractPaths:{'admin.users':'users'},timeoutMs:100});
    await expect(client.requestWithRetry('admin.users',{},2)).resolves.toEqual([{id:'one'}]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  test('does not retry mutation requests',async()=>{
    const fetchMock=vi.fn().mockResolvedValue(new Response('unavailable',{status:503}));
    vi.stubGlobal('fetch',fetchMock);
    const client=createApiClient({baseUrl:'https://api.example.test',contractPaths:{'admin.users':'users'},timeoutMs:100});
    await expect(client.requestWithRetry('admin.users',{method:'POST'},4)).rejects.toMatchObject({status:503});
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  test('collects pages until the declared total is reached',async()=>{
    const fetchPage=vi.fn().mockImplementation(async(page:number,size:number)=>({items:page===0?[1,2]:[3],totalItems:3}));
    await expect(collectPages(fetchPage,{size:2})).resolves.toEqual([1,2,3]);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });
});
