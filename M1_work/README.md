# FireSphere client applications

This folder contains the FireSphere administrative web console and citizen/officer mobile client. It is a client-only workspace; backend and database code are intentionally out of scope.

## Current integration boundary

The approved backend/API and database specifications were not present when this workspace was initialized. The existing sibling FireSphere project describes Express/MariaDB, while the supplied product brief describes Spring Boot/PostgreSQL. No feature API paths or payload contracts can safely be inferred from either source.

The UI and client infrastructure therefore fail closed when a feature contract is not configured. Configure `VITE_API_BASE_URL` (must be HTTPS outside local development) and `VITE_API_CONTRACTS` as a JSON map from operation IDs to approved relative paths only after the backend contract is supplied. The path map must not contain secrets. Do not put credentials or API keys in frontend environment values.

## Development

Install dependencies with `npm install`, then run `npm run dev` for the Admin Web console and open <http://127.0.0.1:5173/>. The server binds to this computer only. On the sign-in screen, choose **Open admin development preview** to inspect local fixture data; organization credentials are not connected or sent.

Run `npm run dev:mobile` to start the native Citizen/Officer Expo app. A desktop web preview for those apps is not configured: the mobile workspace does not include `react-native-web` or an Expo web runtime setup. Web production build: `npm run build`. API client checks: `npm test`.

## Mobile production setup

`mobile/app.json` has a placeholder package identifier. Replace it with the approved organization-owned Android application ID, then set production API configuration through the release environment. Do not publish a build until API contracts, permissions, privacy disclosures, signing, and Play Store configuration are approved.
