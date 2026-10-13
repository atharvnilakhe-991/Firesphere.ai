import * as SQLite from 'expo-sqlite';
import { getOfflineDatabaseKey } from './databaseKey';

const DATABASE_NAME = 'firesphere-offline.db';
let databasePromise: Promise<SQLite.SQLiteDatabase> | null = null;

/** Every connection uses the same secure SQLCipher key before any query can run. */
export function getOfflineDatabase(): Promise<SQLite.SQLiteDatabase> {
  if (!databasePromise) {
    databasePromise = (async () => {
      const db = await SQLite.openDatabaseAsync(DATABASE_NAME);
      try {
        const key = await getOfflineDatabaseKey();
        await db.execAsync(`PRAGMA key = "x'${key}'"; PRAGMA cipher_memory_security = ON;`);
        await db.getFirstAsync('SELECT count(*) FROM sqlite_master');
        return db;
      } catch (error) {
        await db.closeAsync().catch(() => undefined);
        throw error;
      }
    })().catch((error) => {
      databasePromise = null;
      throw error;
    });
  }
  return databasePromise;
}
