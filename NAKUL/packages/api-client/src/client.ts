import type { ApiError, ApiErrorBody } from './types';

export type Operation = 'auth.login' | 'auth.logout' | 'auth.me' | 'auth.refresh' | 'app.config' | 'admin.dashboard' | 'admin.users' | 'admin.organizations' | 'admin.officers' | 'admin.applications' | 'admin.inspections' | 'admin.assignments' | 'admin.incidents' | 'admin.vehicles' | 'admin.inventory' | 'admin.training' | 'admin.reports' | 'admin.certificates' | 'admin.notifications' | 'citizen.applications' | 'citizen.notifications' | 'citizen.application.create' | 'citizen.application.update' | 'citizen.documents' | 'citizen.certificate' | 'officer.assignments' | 'officer.inspections' | 'officer.assignment.download' | 'officer.inspection.submit' | 'officer.evidence.upload' | 'gis.layers';

export interface ApiClientOptions { baseUrl?: string; contractPaths?: Partial<Record<Operation, string>>; getAccessToken?: () => Promise<string | null> | string | null; onUnauthorized?: () => void; timeoutMs?: number; }

/** Confirmed by the public reference repository's Member 2 controllers. */
export const DEFAULT_CONTRACT_PATHS: Partial<Record<Operation, string>> = {
  'auth.login': 'auth/login',
  'auth.me': 'users/me',
  'admin.users': 'users',
  'admin.applications': 'noc/applications',
  'admin.inspections': 'inspections',
  'officer.inspections': 'inspections',
  'officer.assignments': 'officer/assignments',
  'citizen.applications': 'noc/applications',
  'citizen.application.create': 'noc/applications',
  'citizen.application.update': 'noc/applications/{id}',
};

export function normalizeApiError(payload: unknown, status?: number): ApiError {
  const body = (payload && typeof payload === 'object' ? payload : {}) as ApiErrorBody;
  const error = new Error(body.message || (status ? `The service returned error ${status}.` : 'Unable to connect. Check your connection and retry.')) as ApiError;
  error.status = status ?? body.status;
  error.code = body.code || (status === 401 ? 'UNAUTHORIZED' : status ? 'REQUEST_FAILED' : 'NETWORK_ERROR');
  error.traceId = body.traceId;
  error.retryable = !status || status >= 500 || status === 408 || status === 429;
  return error;
}

export function createApiClient(options: ApiClientOptions) {
  const baseUrl = options.baseUrl?.replace(/\/$/, '');
  const timeoutMs = options.timeoutMs ?? 15_000;
  const contractPaths = { ...DEFAULT_CONTRACT_PATHS, ...options.contractPaths };
  const request = async <T>(operation: Operation, init: RequestInit = {}, params?: Record<string, string>): Promise<T> => {
      if (!baseUrl) throw normalizeApiError({ code: 'API_NOT_CONFIGURED', message: 'Connect an approved HTTPS API endpoint to load this feature.' });
      const rawPath = contractPaths[operation];
      if (!rawPath) throw normalizeApiError({ code: 'API_CONTRACT_UNAVAILABLE', message: `The approved API contract for ${operation} is not configured.` });
      if (/^[a-z]+:\/\//i.test(rawPath) || rawPath.includes('..') || rawPath.includes('?') || rawPath.includes('#')) throw normalizeApiError({ code: 'INVALID_API_PATH', message: 'API paths must be relative to the configured service and cannot include traversal, query, or fragment components.' });
      let path = rawPath;
      if (params) for (const [key, value] of Object.entries(params)) path = path.replace(new RegExp(`\\{${key}\\}`, 'g'), encodeURIComponent(value));
      const token = await options.getAccessToken?.();
      const headers = new Headers(init.headers);
      if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`);
      if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
      const normalizedBase = baseUrl;
      if (!normalizedBase.startsWith('https://') && !(/^http:\/\/localhost(?::\d+)?$/i.test(normalizedBase))) throw normalizeApiError({ code: 'INSECURE_API_URL', message: 'The API must use HTTPS outside local development.' });
      let response: Response;
      const controller = new AbortController();
      let timedOut = false;
      const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
      const forwardAbort = () => controller.abort();
      if (init.signal?.aborted) controller.abort();
      else init.signal?.addEventListener('abort', forwardAbort, { once: true });
      try {
        response = await fetch(`${normalizedBase}/api/v1/${path.replace(/^\//, '')}`, { credentials: 'include', ...init, headers, signal: controller.signal });
      } catch {
        if (timedOut) {
          const error = normalizeApiError({ code: 'REQUEST_TIMEOUT', message: 'The service took too long to respond. Check your connection and retry.' }, 408);
          error.retryable = true;
          throw error;
        }
        if (init.signal?.aborted) throw normalizeApiError({ code: 'REQUEST_CANCELLED', message: 'The request was cancelled.' });
        throw normalizeApiError(null);
      } finally {
        clearTimeout(timer);
        init.signal?.removeEventListener('abort', forwardAbort);
      }
      const raw = await response.text();
      let payload: unknown = raw;
      if (raw) { try { payload = JSON.parse(raw); } catch { /* non-JSON response */ } }
      if (!response.ok) {
        const error = normalizeApiError(payload, response.status);
        if (response.status === 401) options.onUnauthorized?.();
        throw error;
      }
      return payload as T;
    };
  return {
    request,
    async requestWithRetry<T>(operation: Operation, init: RequestInit = {}, maxAttempts = 3): Promise<T> {
      if (!Number.isInteger(maxAttempts) || maxAttempts < 1) throw new RangeError('maxAttempts must be a positive integer.');
      if ((init.method ?? 'GET').toUpperCase() !== 'GET') return request<T>(operation, init);
      let lastError: ApiError | undefined;
      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        try { return await request<T>(operation, init); }
        catch (error) {
          lastError = error as ApiError;
          if (!lastError.retryable || attempt === maxAttempts - 1 || init.signal?.aborted) throw lastError;
          await new Promise<void>((resolve, reject) => {
            const cleanup=()=>init.signal?.removeEventListener('abort',onAbort);
            const timer = setTimeout(()=>{cleanup();resolve()}, Math.min(500 * 2 ** attempt, 4_000));
            const onAbort=()=>{clearTimeout(timer);cleanup();reject(normalizeApiError({ code: 'REQUEST_CANCELLED', message: 'The request was cancelled.' }))};
            init.signal?.addEventListener('abort',onAbort,{once:true});
            if(init.signal?.aborted)onAbort();
          });
        }
      }
      throw lastError;
    },
  };
}

export async function collectPages<T>(fetchPage: (page: number, size: number, signal?: AbortSignal) => Promise<{ items: T[]; totalItems?: number; totalPages?: number }>, options: { size?: number; signal?: AbortSignal; maxPages?: number } = {}): Promise<T[]> {
  const size = options.size ?? 50;
  const maxPages = options.maxPages ?? 100;
  if (size < 1 || maxPages < 1) throw new RangeError('Page size and maximum pages must be positive.');
  const all: T[] = [];
  for (let page = 0; page < maxPages; page++) {
    const result = await fetchPage(page, size, options.signal);
    all.push(...result.items);
    if (result.totalItems !== undefined && all.length >= result.totalItems) break;
    if (result.totalPages !== undefined && page + 1 >= result.totalPages) break;
    if (result.items.length < size) break;
  }
  return all;
}

export function parseContractMap(value: string | undefined): Partial<Record<Operation, string>> {
  if (!value) return {};
  try {
    const parsed: unknown = JSON.parse(value);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed).filter(([, path]) => typeof path === 'string')) as Partial<Record<Operation, string>>;
  } catch { return {}; }
}
