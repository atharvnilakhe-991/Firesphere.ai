import { describe,expect,test } from 'vitest';
import { unavailableAuthAdapter } from './authAdapter';

describe('production auth integration seam',()=>{
  test('fails closed without sending credentials when no approved adapter exists',async()=>{
    await expect(unavailableAuthAdapter.login({email:'person@example.test',password:'not-a-real-password'})).rejects.toThrow(/approved authentication adapter/);
  });
  test('does not invent password reset, MFA, or SSO operations',async()=>{
    await expect(unavailableAuthAdapter.beginPasswordReset('person@example.test')).rejects.toThrow(/not connected/);
    await expect(unavailableAuthAdapter.submitMfa('123456')).rejects.toThrow(/configured/);
    await expect(unavailableAuthAdapter.beginSso()).rejects.toThrow(/not configured/);
  });
});
