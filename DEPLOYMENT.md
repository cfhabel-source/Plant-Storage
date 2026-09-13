# Plant Storage: private client access and hosting

The app now has one username/password login. Plants, photos and the identifier require a signed-in session. Google Drive remains the client's storage; the hosting service runs your Java program. No hosting account, deployment or domain purchase has been made by these changes.

## 1. Choose the login and test locally

Open PowerShell in `C:\Users\cfhab\IdeaProjects\PlantStorage`. Stop the previous running app with Ctrl+C first. Run:

```powershell
.\gradlew.bat --no-daemon classes
java -cp build/classes/java/main com.plantstorage.security.ConfigureLogin
```

If PowerShell cannot find `java`, use your installed Java 21 executable:

```powershell
& "C:\Users\cfhab\.jdks\liberica-21.0.4\bin\java.exe" -cp build/classes/java/main com.plantstorage.security.ConfigureLogin
```

Enter a username and a password/passphrase of at least 12 characters. Password typing is deliberately invisible. The utility saves a password **hash**, not the password, in `.login.properties`, and prints the username/hash for later hosting configuration. Keep this file private. It is excluded from Git and Docker. There is no default production password.

In the same PowerShell window, keep your existing five Google environment variables set: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REFRESH_TOKEN`, `GOOGLE_FOLDER_ID`, and `GOOGLE_JSON_FILE_ID`. Use the existing folder and JSON IDs; **do not run setupDrive again**. Then run:

```powershell
.\gradlew.bat --no-daemon run
```

Open <http://localhost:8080> and sign in with the password you chose, not its hash. Use `localhost` consistently, because requests are checked against the configured address. The local configuration deliberately listens on this computer only.

The optional `APP_USERNAME` and `APP_PASSWORD_HASH` environment variables override the local file. If either is set, set both correctly or remove both to use `.login.properties`.

## 2. Prepare the client's Drive access

New uploaded photos are saved inside `GOOGLE_FOLDER_ID` without granting public sharing. The browser retrieves them through the signed-in app.

Earlier versions granted **Anyone with the link** access to uploaded photos. This change does not revoke those old permissions. In the client's Google Drive, change the old photo files' General access to **Restricted** and check the storage folder's sharing too. Some older uploads may be in My Drive outside the PlantStorage folder. Keep access for the account that authorized this app, then confirm the old photos still display through the app.

Check the Google OAuth app's publishing status before handover. For an external OAuth app in **Testing**, refresh tokens used with Drive scopes normally expire after seven days. Move to the appropriate production configuration and obtain a fresh authorization from the client if required; Google may impose verification requirements depending on the scopes and use. Production tokens can still be revoked or expire for other reasons. See [Google's OAuth token expiration documentation](https://developers.google.com/identity/protocols/oauth2#expiration).

The client uses the app password to sign in. Their Google refresh token is a separate server credential and never goes into the browser or source code.

## 3. Put the code in a private repository

Create a private GitHub repository, preferably owned by the client, and upload the project source, Gradle files, Dockerfile and deployment configuration. Review the files before committing. Do not upload `.login.properties`, `.env` files, Google credential JSON, refresh tokens, local databases or private photos. The supplied `.gitignore` and `.dockerignore` exclude common locations; they cannot remove secrets from previously committed history or recognize every renamed credential file.

## 4. Create the Render service

1. In Render, create a **Web Service** connected to that repository.
2. Choose **Docker** as the runtime and the repository-root `Dockerfile`. It builds Java 21, runs the checks and bundles the browser files. A separate build/start command is unnecessary.
3. Choose a plan with the client. Keep **one instance**. Render's free web services currently sleep after 15 minutes of inactivity and can take about a minute to wake; a paid service avoids that particular limitation. Check current pricing before selecting a plan.
4. Set the health-check path to `/healthz`.
5. Add these environment variables in Render. Paste only the values, **without surrounding quotes**:

| Variable | Value |
| --- | --- |
| `APP_USERNAME` | The username chosen in step 1 |
| `APP_PASSWORD_HASH` | The entire `pbkdf2-sha256:...` hash printed by ConfigureLogin |
| `GOOGLE_CLIENT_ID` | Existing Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Existing Google OAuth client secret |
| `GOOGLE_REFRESH_TOKEN` | Refresh token authorized by the client |
| `GOOGLE_FOLDER_ID` | Existing PlantStorage folder ID |
| `GOOGLE_JSON_FILE_ID` | Existing plantstorage.json file ID |

Do not use the short-lived Google access token here. Do not put the app's plaintext password in Render settings. Keep the hash private as well.

6. Deploy. Render supplies `PORT` and `RENDER_EXTERNAL_URL`; the app uses them automatically. The container listens on all interfaces, while Render provides the external HTTPS connection. The runtime requires Google Drive access at startup.
7. Open the actual HTTPS address Render assigns and test sign-in, loading plants, a disposable plant/photo upload and deletion, the identifier and sign-out. These final live checks have not been performed on the client's Drive by this implementation.

The included `render.yaml` is an alternative Blueprint setup with the same variables; use either manual Web Service setup or a Blueprint, not both. Plan selection and deployment still require action in Render.

References: [Render Docker deployment](https://render.com/docs/docker), [environment variables](https://render.com/docs/configure-environment-variables), [free-service limits](https://render.com/docs/free).

## 5. Give the client a domain

The supplied `onrender.com` HTTPS address already works on a phone or home computer; the client does not install Java. Your development computer can be off once hosting is running.

For a custom address, buy a domain with the client's agreement and add it under the service's **Custom Domains** settings. Follow Render's DNS instructions at the domain registrar. Then set `APP_BASE_URL` in Render to the exact HTTPS address the client will use, for example `https://plants.example.com`, and redeploy. Use no path or trailing slash. The example domain is a placeholder, not a purchased address.

Use that one address for sign-in; the origin check intentionally rejects writes from an alternate hostname. Once the custom domain works, you can disable the default Render subdomain in its settings. See [Render's custom-domain instructions](https://render.com/docs/custom-domains).

## 6. Handover and maintenance

- Give the client their app address and login privately. Keep the hosting/repository/domain ownership and recovery details accessible to them.
- Stop the local app when the hosted copy becomes the working version. The current storage design rewrites a shared JSON file and does not coordinate simultaneous writes across servers or devices. Use one instance and avoid overlapping edits from separate tabs/devices.
- Sessions expire after 30 minutes without server requests, after eight hours in total, on sign-out, or when the server restarts. Unsaved form text is not automatically saved.
- Login attempts are limited to ten per five minutes across the single-client service. If blocked, wait five minutes. Restarting clears the counter and sessions.
- To reset the password, rerun ConfigureLogin locally, update `APP_PASSWORD_HASH` (and username if changed) in Render, and redeploy. There is no email-based password reset.
- Retain a recoverable backup of the Drive JSON and photos before future migrations. Existing Drive files have not been changed during development of this feature.

## Implementation and verification

The login uses PBKDF2-HMAC-SHA256 with a random salt and 600,000 iterations, server-side sessions, HttpOnly/SameSite cookies, HTTPS Secure cookies, CSRF/origin checks, no-store responses and a restrictive content security policy. Stored plant text is escaped before display. Jetty was updated from 11 to 12.0.39 (Jakarta EE10). See [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Run the standalone checks with:

```powershell
.\gradlew.bat --no-daemon check
```

During preparation, 36 security checks and 282 decision-tree checks passed. A real headless Edge browser also passed login, incorrect-password handling, escaped plant rendering, CSRF on form/photo-upload requests, identifier access, logout, expired-session redirect and mobile login layout checks. These used a disposable local server with fake plant/photo responses, not the client's Drive.

The preparation environment produced the Java classes but Gradle's compiler then encountered a Windows sandbox `AccessDeniedException` while closing dependency JAR files. Running the compiled checks directly succeeded. Distribution packaging also succeeded with compilation skipped. A clean full Gradle build must still be confirmed in your normal terminal or Render. Docker itself could not be run here because its local daemon was unavailable; the production image and live Google integration still need the deployment checks above.
