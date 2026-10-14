import { describe,expect,test,vi } from 'vitest';
import { createConfiguredAuthAdapter, unavailableAuthAdapter } from './authAdapter';

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

describe('configured auth adapter',()=>{
  test('uses login token to hydrate the authoritative role and organization profile before returning a session',async()=>{
    const request=vi.fn()
      .mockResolvedValueOnce({accessToken:'fresh-token',userId:'u-1',email:'test@example.test',status:'ACTIVE'})
      .mockResolvedValueOnce({id:'u-1',email:'test@example.test',status:'ACTIVE',role:'OFFICER',organizationId:'org-7',createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-02T00:00:00Z'});
    const adapter=createConfiguredAuthAdapter({request});
    await expect(adapter.login({email:'test@example.test',password:'secret'})).resolves.toEqual({
      accessToken:'fresh-token',
      user:{id:'u-1',displayName:'test@example.test',email:'test@example.test',status:'ACTIVE',role:'OFFICER',organizationId:'org-7'},
    });
    expect(request).toHaveBeenNthCalledWith(1,'auth.login',{method:'POST',body:JSON.stringify({email:'test@example.test',password:'secret'})});
    expect(request).toHaveBeenNthCalledWith(2,'auth.me',{headers:{Authorization:'Bearer fresh-token'}});
  });
  test('does not complete login if profile hydration fails or identifies another account',async()=>{
    const brokenProfile=createConfiguredAuthAdapter({request:vi.fn()
      .mockResolvedValueOnce({accessToken:'fresh-token',userId:'u-1',email:'test@example.test',status:'ACTIVE'})
      .mockResolvedValueOnce({id:'u-1',email:'test@example.test',status:'ACTIVE',role:'UNKNOWN',organizationId:null})});
    await expect(brokenProfile.login({email:'test@example.test',password:'secret'})).rejects.toThrow(/unsupported user profile/);
    const otherProfile=createConfiguredAuthAdapter({request:vi.fn()
      .mockResolvedValueOnce({accessToken:'fresh-token',userId:'u-1',email:'test@example.test',status:'ACTIVE'})
      .mockResolvedValueOnce({id:'u-2',email:'test@example.test',status:'ACTIVE',role:'CITIZEN',organizationId:null})});
    await expect(otherProfile.login({email:'test@example.test',password:'secret'})).rejects.toThrow(/does not match/);
  });
  test('restores the server-authoritative user profile',async()=>{
    const request=vi.fn().mockResolvedValue({id:'u-1',email:'test@example.test',status:'ACTIVE',role:'CITIZEN',organizationId:null,createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-02T00:00:00Z'});
    await expect(createConfiguredAuthAdapter({request}).restore()).resolves.toMatchObject({id:'u-1',role:'CITIZEN',organizationId:null});
    expect(request).toHaveBeenCalledWith('auth.me');
  });
  test('keeps logout local because the reference backend exposes no logout endpoint',async()=>{
    const request=vi.fn();
    await createConfiguredAuthAdapter({request}).logout();
    expect(request).not.toHaveBeenCalled();
  });
});
