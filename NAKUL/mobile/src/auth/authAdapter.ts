import type { AuthLoginResponse, Session, SessionUser, UserProfileResponse } from '@firesphere/api-client/types';
import type { Operation } from '@firesphere/api-client/client';

type Requester = { request<T>(operation: Operation, init?: RequestInit): Promise<T> };

export interface AuthAdapter {
  login(credentials: { email: string; password: string }): Promise<Session>;
  restore(): Promise<SessionUser | null>;
  logout(): Promise<void>;
  beginPasswordReset(email: string): Promise<void>;
  submitMfa(code: string): Promise<Session>;
  beginSso(): Promise<void>;
}

/** Production fallback remains fail-closed when a caller chooses no API adapter. */
export const unavailableAuthAdapter: AuthAdapter = {
  async login() { throw new Error('Organization sign-in is not connected. The approved authentication adapter is required.'); },
  async restore() { return null; },
  async logout() {},
  async beginPasswordReset() { throw new Error('Password reset is not connected to the organization identity service.'); },
  async submitMfa() { throw new Error('MFA cannot be completed until the identity-service contract is configured.'); },
  async beginSso() { throw new Error('Organization SSO is not configured for this application.'); },
};

/** Implements the confirmed POST /auth/login -> GET /users/me sequence. */
export function createConfiguredAuthAdapter(api: Requester): AuthAdapter {
  return {
    async login(credentials) {
      const response = await api.request<AuthLoginResponse>('auth.login', {
        method: 'POST',
        body: JSON.stringify(credentials),
      });
      if (!isLoginResponse(response)) throw new Error('The authentication service returned an unsupported login response.');
      const profile = await api.request<UserProfileResponse>('auth.me', {
        headers: { Authorization: `Bearer ${response.accessToken}` },
      });
      const user = mapUserProfile(profile);
      if (user.id !== response.userId || user.email.toLowerCase() !== response.email.toLowerCase()) {
        throw new Error('The authenticated profile does not match the login response. Sign-in was not completed.');
      }
      return { accessToken: response.accessToken, user };
    },
    async restore() {
      return mapUserProfile(await api.request<UserProfileResponse>('auth.me'));
    },
    // The reference backend has no logout route; AuthContext always clears local credentials.
    async logout() {},
    async beginPasswordReset() { throw new Error('Password reset is not connected to the organization identity service.'); },
    async submitMfa() { throw new Error('MFA cannot be completed until the identity-service contract is configured.'); },
    async beginSso() { throw new Error('Organization SSO is not configured for this application.'); },
  };
}

function isLoginResponse(value: unknown): value is AuthLoginResponse {
  if (!value || typeof value !== 'object') return false;
  const response = value as Partial<AuthLoginResponse>;
  return typeof response.accessToken === 'string' && typeof response.userId === 'string'
    && typeof response.email === 'string' && typeof response.status === 'string';
}

function mapUserProfile(value: unknown): SessionUser {
  if (!value || typeof value !== 'object') throw new Error('The authentication service returned an unsupported user profile.');
  const profile = value as Partial<UserProfileResponse>;
  if (typeof profile.id !== 'string' || typeof profile.email !== 'string' || typeof profile.status !== 'string'
      || !['CITIZEN', 'OFFICER', 'ADMIN'].includes(String(profile.role))
      || !(profile.organizationId === null || typeof profile.organizationId === 'string')) {
    throw new Error('The authentication service returned an unsupported user profile.');
  }
  return {
    id: profile.id,
    displayName: profile.email,
    email: profile.email,
    role: profile.role as SessionUser['role'],
    organizationId: profile.organizationId,
    status: profile.status,
  };
}
