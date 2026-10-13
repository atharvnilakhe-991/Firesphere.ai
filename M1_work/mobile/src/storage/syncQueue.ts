import type { SyncState } from '@firesphere/api-client/types';
import { getOfflineDatabase } from './database';

const database = getOfflineDatabase();

export interface QueuedChange {
  id: string;
  operation: string;
  entityId: string;
  payload: unknown;
  createdAt: string;
  attempts: number;
  state: SyncState;
  lastError?: string;
}

export async function listQueuedChanges(): Promise<QueuedChange[]> {
  const db = await database;
  const rows = await db.getAllAsync<{ id:string; operation:string; entity_id:string; payload_json:string; created_at:string; attempts:number; state:SyncState; last_error:string|null }>(
    'SELECT id, operation, entity_id, payload_json, created_at, attempts, state, last_error FROM sync_queue ORDER BY created_at DESC'
  );
  return rows.map((row) => ({ id:row.id, operation:row.operation, entityId:row.entity_id, payload:JSON.parse(row.payload_json), createdAt:row.created_at, attempts:row.attempts, state:row.state, lastError:row.last_error ?? undefined }));
}

export async function updateQueuedChange(id: string, state: SyncState, lastError?: string) {
  const db = await database;
  await db.runAsync('UPDATE sync_queue SET state = ?, attempts = attempts + ?, last_error = ? WHERE id = ?', state, state === 'SYNCING' || state === 'SYNC_FAILED' ? 1 : 0, lastError ?? null, id);
}

export async function retryQueuedChange(id: string) {
  const db = await database;
  await db.runAsync("UPDATE sync_queue SET state = 'PENDING_SYNC', last_error = NULL WHERE id = ? AND state IN ('SYNC_FAILED', 'CONFLICT')", id);
}

export async function retryAllQueuedChanges() {
  const db = await database;
  await db.runAsync("UPDATE sync_queue SET state = 'PENDING_SYNC', last_error = NULL WHERE state IN ('SYNC_FAILED', 'CONFLICT')");
}

export async function confirmQueuedChange(id: string) {
  const db = await database;
  await db.withExclusiveTransactionAsync(async (tx) => {
    await tx.runAsync("UPDATE sync_queue SET state = 'SYNCED', last_error = NULL WHERE id = ?", id);
    await tx.runAsync("UPDATE inspection_work SET sync_state = 'SYNCED' WHERE inspection_id = (SELECT entity_id FROM sync_queue WHERE id = ?)", id);
  });
}
