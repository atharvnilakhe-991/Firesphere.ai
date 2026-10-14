import { afterEach, describe, expect, test, vi } from 'vitest';
import { createApiClient, normalizeApiError } from './client';

afterEach(() => vi.unstubAllGlobals());

describe('shared API client', () => {
  test('normalizes the documented backend error envelope', () => {
    const error = normalizeApiError({
      status: 422,
      code: 'NOC_VALIDATION_ERROR',
      message: 'Required building information is missing.',
      traceId: 'trace-123',
    }, 422);

    expect(error.code).toBe('NOC_VALIDATION_ERROR');
    expect(error.message).toBe('Required building information is missing.');
    expect(error.traceId).toBe('trace-123');
    expect(error.retryable).toBe(false);
  });

  test('fails closed when a base URL or approved operation path is missing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const noBase = createApiClient({ contractPaths: { 'citizen.applications': 'citizen/applications' } });
    const noContract = createApiClient({ baseUrl: 'https://api.example.test' });

    await expect(noBase.request('citizen.applications')).rejects.toMatchObject({ code: 'API_NOT_CONFIGURED' });
    await expect(noContract.request('gis.layers')).rejects.toMatchObject({ code: 'API_CONTRACT_UNAVAILABLE' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('applies the central /api/v1 prefix and session token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ id: 'noc-1' }]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createApiClient({
      baseUrl: 'https://api.example.test/',
      contractPaths: { 'citizen.applications': '/citizen/applications' },
      getAccessToken: () => 'access-token',
    });

    await expect(client.request('citizen.applications')).resolves.toEqual([{ id: 'noc-1' }]);
    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('https://api.example.test/api/v1/citizen/applications');
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer access-token');
    expect(init.credentials).toBe('include');
  });

  test('uses confirmed reference routes and lets login hydration pass its newly issued bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'user-1' }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createApiClient({ baseUrl: 'https://api.example.test', getAccessToken: () => 'old-token' });
    await client.request('auth.me', { headers: { Authorization: 'Bearer login-token' } });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('https://api.example.test/api/v1/users/me');
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer login-token');
  });

  test('uses the confirmed NOC and assignment routes for Member 1 integrations', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response('[]', { status: 200 })));
    vi.stubGlobal('fetch', fetchMock);
    const client = createApiClient({ baseUrl: 'https://api.example.test' });
    await client.request('citizen.application.create', { method: 'POST', body: JSON.stringify({ title: 'NOC', buildingName: 'Tower', address: '1 Main Road' }) });
    await client.request('officer.assignments');
    await client.request('admin.inspections');
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      'https://api.example.test/api/v1/noc/applications',
      'https://api.example.test/api/v1/officer/assignments',
      'https://api.example.test/api/v1/inspections',
    ]);
  });

  test('substitutes encoded identifiers in confirmed path templates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createApiClient({ baseUrl: 'https://api.example.test' });
    await client.request('citizen.application.update', { method: 'PATCH', body: '{}' }, { id: 'id/with space' });
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.example.test/api/v1/noc/applications/id%2Fwith%20space');
  });

  test('clears the session on unauthorized responses', async () => {
    const onUnauthorized = vi.fn();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Session expired.',
    }), { status: 401, headers: { 'Content-Type': 'application/json' } })));
    const client = createApiClient({
      baseUrl: 'https://api.example.test',
      contractPaths: { 'citizen.applications': 'citizen/applications' },
      onUnauthorized,
    });

    await expect(client.request('citizen.applications')).rejects.toMatchObject({ code: 'UNAUTHORIZED', status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  test('rejects insecure non-local origins and absolute contract URLs', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const insecure = createApiClient({ baseUrl: 'http://api.example.test', contractPaths: { 'citizen.applications': 'citizen/applications' } });
    const absolutePath = createApiClient({ baseUrl: 'https://api.example.test', contractPaths: { 'citizen.applications': 'https://other.example.test/data' } });

    await expect(insecure.request('citizen.applications')).rejects.toMatchObject({ code: 'INSECURE_API_URL' });
    await expect(absolutePath.request('citizen.applications')).rejects.toMatchObject({ code: 'INVALID_API_PATH' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('aborts a request after its configured timeout', async () => {
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) => new Promise((_resolve, reject) => {
      if (init?.signal?.aborted) reject(new Error('aborted'));
      else init?.signal?.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
    })));
    const client = createApiClient({ baseUrl: 'https://api.example.test', contractPaths: { 'admin.users': '/users' }, timeoutMs: 5 });
    await expect(client.request('admin.users')).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT', status: 408, retryable: true });
  });

  test('forwards cancellation to the underlying request', async () => {
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) => new Promise((_resolve, reject) => {
      if (init?.signal?.aborted) reject(new Error('aborted'));
      else init?.signal?.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
    })));
    const abort = new AbortController();
    const client = createApiClient({ baseUrl: 'https://api.example.test', contractPaths: { 'admin.users': '/users' } });
    const pending = client.request('admin.users', { signal: abort.signal });
    abort.abort();
    await expect(pending).rejects.toMatchObject({ code: 'REQUEST_CANCELLED' });
  });

  test('rejects contract paths with query or fragment components', async () => {
    const client = createApiClient({ baseUrl: 'https://api.example.test', contractPaths: { 'admin.users': '/users?admin=true' } });
    await expect(client.request('admin.users')).rejects.toMatchObject({ code: 'INVALID_API_PATH' });
  });
});
