# Azure Web App deployment configuration

## Recommended app settings

Set these values in Azure App Service Configuration > Application settings:

- DB_HOST
- DB_PORT=3306
- DB_USER
- DB_PASSWORD
- DB_NAME
- PORT=8080
- NODE_ENV=production

## Startup command

```bash
npm install --prefix backend && npm install --prefix frontend && npm run build --prefix frontend && npm run start --prefix backend
```

## Runtime notes

- The backend Express app serves the React build output from `frontend/dist`.
- Keep any secret credentials in Azure App Settings and never in source control.
- Use a managed Azure database or the existing MariaDB-hosted environment for production data.
- Add HTTPS-only and secure CORS configuration for production deployment.
