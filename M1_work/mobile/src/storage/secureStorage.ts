import * as SecureStore from 'expo-secure-store';

const ACCESS_TOKEN = 'firesphere.access-token';
const REFRESH_TOKEN = 'firesphere.refresh-token';

export const secureStorage = {
  getAccessToken: () => SecureStore.getItemAsync(ACCESS_TOKEN),
  getRefreshToken: () => SecureStore.getItemAsync(REFRESH_TOKEN),
  async saveTokens(accessToken: string, refreshToken?: string) {
    await SecureStore.setItemAsync(ACCESS_TOKEN, accessToken);
    if (refreshToken) await SecureStore.setItemAsync(REFRESH_TOKEN, refreshToken);
  },
  async clear() {
    await Promise.all([SecureStore.deleteItemAsync(ACCESS_TOKEN), SecureStore.deleteItemAsync(REFRESH_TOKEN)]);
  },
};
