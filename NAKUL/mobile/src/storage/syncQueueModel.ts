export type QueueState = 'PENDING_SYNC'|'SYNCING'|'SYNCED'|'SYNC_FAILED'|'CONFLICT';

export function canRetry(state: QueueState): boolean { return state === 'SYNC_FAILED' || state === 'CONFLICT' || state === 'PENDING_SYNC'; }

export function retryDelayMs(attempt: number, base = 1_000, cap = 60_000): number {
  if (!Number.isFinite(attempt) || attempt < 0) throw new RangeError('Attempt must be a non-negative number.');
  return Math.min(cap, base * 2 ** Math.min(attempt, 16));
}

export function nextRetryState(online: boolean): QueueState {
  return online ? 'SYNCING' : 'SYNC_FAILED';
}
