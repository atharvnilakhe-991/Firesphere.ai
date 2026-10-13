import { describe,expect,test } from 'vitest';
import { canRetry,nextRetryState,retryDelayMs } from './syncQueueModel';

describe('local sync queue state helpers',()=>{
  test('permits retry for pending, failed, and conflict entries only',()=>{
    expect(canRetry('PENDING_SYNC')).toBe(true);
    expect(canRetry('SYNC_FAILED')).toBe(true);
    expect(canRetry('CONFLICT')).toBe(true);
    expect(canRetry('SYNCED')).toBe(false);
  });
  test('uses capped exponential delay values and reports offline retry failure state',()=>{
    expect(retryDelayMs(0)).toBe(1000);
    expect(retryDelayMs(4)).toBe(16000);
    expect(retryDelayMs(12)).toBe(60000);
    expect(nextRetryState(false)).toBe('SYNC_FAILED');
    expect(nextRetryState(true)).toBe('SYNCING');
  });
});
