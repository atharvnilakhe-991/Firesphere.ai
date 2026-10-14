import { createApiClient, parseContractMap } from '@firesphere/api-client/client';
import { handleUnauthorized } from '../auth/sessionEvents';
import { secureStorage } from '../storage/secureStorage';

export const api = createApiClient({
  baseUrl: process.env.EXPO_PUBLIC_API_BASE_URL,
  contractPaths: parseContractMap(process.env.EXPO_PUBLIC_API_CONTRACTS),
  getAccessToken: () => secureStorage.getAccessToken(),
  onUnauthorized: handleUnauthorized,
});
