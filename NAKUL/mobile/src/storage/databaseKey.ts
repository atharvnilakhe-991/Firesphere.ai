import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

const DATABASE_KEY = 'firesphere.offline-database-key.v1';

/** A random SQLCipher key lives in Android Keystore/iOS Keychain via SecureStore. */
export async function getOfflineDatabaseKey(): Promise<string> {
  const existing = await SecureStore.getItemAsync(DATABASE_KEY);
  if (existing && /^[0-9a-f]{64}$/.test(existing)) return existing;
  const generated = Array.from(await Crypto.getRandomBytesAsync(32), (byte) => byte.toString(16).padStart(2, '0')).join('');
  await SecureStore.setItemAsync(DATABASE_KEY, generated, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
  return generated;
}
