import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  openDatabaseAsync: vi.fn(),
  getOfflineDatabaseKey: vi.fn(),
}));

vi.mock('expo-sqlite', () => ({ openDatabaseAsync: mocks.openDatabaseAsync }));
vi.mock('./databaseKey', () => ({ getOfflineDatabaseKey: mocks.getOfflineDatabaseKey }));

describe('encrypted database opener', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.openDatabaseAsync.mockReset();
    mocks.getOfflineDatabaseKey.mockReset();
  });

  it('applies the secure key before its first query and shares one initialized connection', async () => {
    const db = { execAsync: vi.fn().mockResolvedValue(undefined), getFirstAsync: vi.fn().mockResolvedValue({ count: 1 }), closeAsync: vi.fn() };
    mocks.openDatabaseAsync.mockResolvedValue(db);
    mocks.getOfflineDatabaseKey.mockResolvedValue('a'.repeat(64));
    const { getOfflineDatabase } = await import('./database');

    const [first, second] = await Promise.all([getOfflineDatabase(), getOfflineDatabase()]);

    expect(first).toBe(db);
    expect(second).toBe(db);
    expect(mocks.openDatabaseAsync).toHaveBeenCalledTimes(1);
    expect(db.execAsync).toHaveBeenCalledWith(`PRAGMA key = "x'${'a'.repeat(64)}'"; PRAGMA cipher_memory_security = ON;`);
    expect(db.getFirstAsync).toHaveBeenCalledWith('SELECT count(*) FROM sqlite_master');
  });

  it('closes a connection and allows another open after key initialization fails', async () => {
    const broken = { execAsync: vi.fn(), getFirstAsync: vi.fn(), closeAsync: vi.fn().mockResolvedValue(undefined) };
    const recovered = { execAsync: vi.fn().mockResolvedValue(undefined), getFirstAsync: vi.fn().mockResolvedValue({ count: 1 }), closeAsync: vi.fn() };
    mocks.openDatabaseAsync.mockResolvedValueOnce(broken).mockResolvedValueOnce(recovered);
    mocks.getOfflineDatabaseKey.mockRejectedValueOnce(new Error('secure storage unavailable')).mockResolvedValueOnce('b'.repeat(64));
    const { getOfflineDatabase } = await import('./database');

    await expect(getOfflineDatabase()).rejects.toThrow('secure storage unavailable');
    expect(broken.closeAsync).toHaveBeenCalledOnce();
    await expect(getOfflineDatabase()).resolves.toBe(recovered);
    expect(mocks.openDatabaseAsync).toHaveBeenCalledTimes(2);
  });
});
