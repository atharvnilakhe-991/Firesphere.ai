import type { Session } from '@firesphere/api-client/types';

export interface AuthAdapter {
  login(credentials:{email:string;password:string}):Promise<Session>;
  restore():Promise<Session|null>;
  logout():Promise<void>;
  beginPasswordReset(email:string):Promise<void>;
  submitMfa(code:string):Promise<Session>;
  beginSso():Promise<void>;
}

/** Production auth intentionally fails closed until Member 2 supplies its approved contract. */
export const unavailableAuthAdapter:AuthAdapter={
  async login(){throw new Error('Organization sign-in is not connected. The approved authentication adapter is required.');},
  async restore(){return null;},
  async logout(){},
  async beginPasswordReset(){throw new Error('Password reset is not connected to the organization identity service.');},
  async submitMfa(){throw new Error('MFA cannot be completed until the identity-service contract is configured.');},
  async beginSso(){throw new Error('Organization SSO is not configured for this application.');},
};
