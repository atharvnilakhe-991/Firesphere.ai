import type { SyncState } from '@firesphere/api-client/types';
import { getOfflineDatabase } from './database';

const database = getOfflineDatabase();

export async function initializeOfflineDatabase() {
  const db = await database;
  await db.execAsync(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS inspection_cache (
      id TEXT PRIMARY KEY NOT NULL,
      assignment_json TEXT NOT NULL,
      downloaded_at TEXT NOT NULL,
      sync_state TEXT NOT NULL DEFAULT 'SYNCED'
    );
    CREATE TABLE IF NOT EXISTS inspection_work (
      inspection_id TEXT PRIMARY KEY NOT NULL,
      payload_json TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      sync_state TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS citizen_drafts (
      id TEXT PRIMARY KEY NOT NULL,
      payload_json TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS inspection_evidence (
      evidence_id TEXT PRIMARY KEY NOT NULL,
      inspection_id TEXT NOT NULL,
      file_name TEXT NOT NULL,
      mime_type TEXT NOT NULL,
      base64_data TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      checklist_item_id TEXT,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS inspection_evidence_parent ON inspection_evidence(inspection_id, created_at);
    CREATE TABLE IF NOT EXISTS sync_queue (
      id TEXT PRIMARY KEY NOT NULL,
      operation TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      payload_json TEXT NOT NULL,
      created_at TEXT NOT NULL,
      attempts INTEGER NOT NULL DEFAULT 0,
      state TEXT NOT NULL DEFAULT 'PENDING_SYNC',
      last_error TEXT
    );
    CREATE INDEX IF NOT EXISTS sync_queue_state_created ON sync_queue(state, created_at);
  `);
  const evidenceColumns = await db.getAllAsync<{ name:string }>('PRAGMA table_info(inspection_evidence)');
  if (!evidenceColumns.some((column) => column.name === 'description')) await db.execAsync("ALTER TABLE inspection_evidence ADD COLUMN description TEXT NOT NULL DEFAULT ''");
  if (!evidenceColumns.some((column) => column.name === 'checklist_item_id')) await db.execAsync('ALTER TABLE inspection_evidence ADD COLUMN checklist_item_id TEXT');
}

export interface LocalEvidenceFile { id:string; inspectionId:string; name:string; mimeType:string; base64:string; description:string; checklistItemId:string|null; createdAt:string; }

/** Evidence bytes are held in the SQLCipher-protected database, never a persistent plaintext file. */
export async function saveLocalEvidence(file:Omit<LocalEvidenceFile,'createdAt'>) {
  const db=await database;
  await db.runAsync('INSERT OR REPLACE INTO inspection_evidence (evidence_id, inspection_id, file_name, mime_type, base64_data, description, checklist_item_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',file.id,file.inspectionId,file.name,file.mimeType,file.base64,file.description,file.checklistItemId,new Date().toISOString());
}

export async function listLocalEvidence(inspectionId:string):Promise<LocalEvidenceFile[]> {
  const db=await database;
  const rows=await db.getAllAsync<{evidence_id:string;inspection_id:string;file_name:string;mime_type:string;base64_data:string;description:string;checklist_item_id:string|null;created_at:string}>('SELECT evidence_id, inspection_id, file_name, mime_type, base64_data, description, checklist_item_id, created_at FROM inspection_evidence WHERE inspection_id = ? ORDER BY created_at',inspectionId);
  return rows.map((row)=>({id:row.evidence_id,inspectionId:row.inspection_id,name:row.file_name,mimeType:row.mime_type,base64:row.base64_data,description:row.description,checklistItemId:row.checklist_item_id,createdAt:row.created_at}));
}

export async function deleteLocalEvidence(evidenceId:string) {
  const db=await database;
  await db.runAsync('DELETE FROM inspection_evidence WHERE evidence_id = ?',evidenceId);
}

export async function cacheAssignment(assignment: { id: string; [key: string]: unknown }) {
  const db = await database;
  await db.runAsync('INSERT OR REPLACE INTO inspection_cache (id, assignment_json, downloaded_at, sync_state) VALUES (?, ?, ?, ?)', assignment.id, JSON.stringify(assignment), new Date().toISOString(), 'SYNCED');
}

export async function saveInspectionWork(inspectionId: string, payload: unknown, syncState: SyncState = 'PENDING_SYNC') {
  const db = await database;
  const serialized = JSON.stringify(payload);
  const now = new Date().toISOString();
  await db.withExclusiveTransactionAsync(async (tx) => {
    await tx.runAsync('INSERT OR REPLACE INTO inspection_work (inspection_id, payload_json, updated_at, sync_state) VALUES (?, ?, ?, ?)', inspectionId, serialized, now, syncState);
    await tx.runAsync(`INSERT INTO sync_queue (id, operation, entity_id, payload_json, created_at, state)
      VALUES (?, ?, ?, ?, ?, 'PENDING_SYNC') ON CONFLICT(id) DO UPDATE SET payload_json = excluded.payload_json, created_at = excluded.created_at, state = 'PENDING_SYNC', attempts = 0, last_error = NULL`, `inspection:${inspectionId}`, 'officer.inspection.submit', inspectionId, serialized, now);
  });
}

export async function saveCitizenDraft(id: string, payload: unknown) {
  const db = await database;
  await db.runAsync('INSERT OR REPLACE INTO citizen_drafts (id, payload_json, updated_at) VALUES (?, ?, ?)', id, JSON.stringify(payload), new Date().toISOString());
}

export async function loadCitizenDraft<T>(id: string): Promise<T | null> {
  const db = await database;
  const row = await db.getFirstAsync<{ payload_json: string }>('SELECT payload_json FROM citizen_drafts WHERE id = ?', id);
  return row ? JSON.parse(row.payload_json) as T : null;
}

export async function getPendingSyncCount() {
  const db = await database;
  const row = await db.getFirstAsync<{ count: number }>("SELECT COUNT(*) AS count FROM sync_queue WHERE state IN ('PENDING_SYNC', 'SYNCING', 'SYNC_FAILED', 'CONFLICT')");
  return row?.count ?? 0;
}

export async function listCachedAssignments() {
  const db = await database;
  const rows = await db.getAllAsync<{ assignment_json: string; sync_state: SyncState }>('SELECT assignment_json, sync_state FROM inspection_cache ORDER BY downloaded_at DESC');
  return rows.map((row) => ({ ...JSON.parse(row.assignment_json), syncState: row.sync_state }));
}

export async function getInspectionWork(inspectionId: string): Promise<Record<string, unknown> | null> {
  const db = await database;
  const row = await db.getFirstAsync<{ payload_json: string; sync_state: SyncState }>('SELECT payload_json, sync_state FROM inspection_work WHERE inspection_id = ?', inspectionId);
  return row ? { ...(JSON.parse(row.payload_json) as Record<string, unknown>), localSyncState: row.sync_state } : null;
}
