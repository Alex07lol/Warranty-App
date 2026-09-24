# Google Drive backup — one-time setup

The app-side code is complete (`com.warrantyvault.backup`). Google, however, will not issue an
access token to an Android app it cannot identify, so **one manual step in the Google Cloud
Console is required before the "Back up to Google Drive" button can work**. Without it Google
returns `DEVELOPER_ERROR` / `12500` and the app shows that message instead of the consent screen.

This is a per-installation developer task, not something an end user ever sees.

---

## What the feature does

- **Permission first.** Tapping *Back up to Google Drive* opens Google's consent screen for the
  `drive.appdata` scope. Nothing is uploaded before the user approves.
- **Private by design.** The backup is written to the Drive **app data folder**: it belongs to the
  user's Drive but only this app can see or read it. It is deliberately *not* visible in the normal
  Drive file list.
- **First backup is automatic.** Once consent is granted the vault is uploaded immediately, and the
  file is replaced on every later backup (`Back up now`).
- **Restore is re-linking.** On a new phone, tapping the same button links the account, reads
  `warrantyvault-backup.json` back and runs it through the normal validated import preview — nothing
  is written until the user confirms, and existing records are never deleted.
- **Unlink** revokes the app's OAuth grant and forgets the account locally. The Drive file stays, so
  re-linking restores from it.

## Required setup

1. **Create / pick a Google Cloud project** — <https://console.cloud.google.com/>
2. **Enable the Google Drive API**: *APIs & Services → Library → Google Drive API → Enable*.
3. **Configure the OAuth consent screen**: *APIs & Services → OAuth consent screen*
   - User type: **External** (or Internal for a Workspace org)
   - Add the scope `https://www.googleapis.com/auth/drive.appdata`
   - While the app is unverified, add your own Google account under **Test users**
4. **Create an Android OAuth client**: *APIs & Services → Credentials → Create credentials →
   OAuth client ID → Android*
   - Package name: `com.warrantyvault`
   - SHA-1: see below (register one client per signing key — debug and release differ)

### Debug signing SHA-1 (this machine)

```
68:06:51:EF:0B:D6:B9:CC:3A:8B:68:23:7C:F5:A2:A1:5D:82:BB:FE
```

Regenerate it any time with:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

For a release build, use the release keystore's SHA-1 (`keytool -list -v -keystore <your>.jks`) and
add a second Android OAuth client.

### If you see `DEVELOPER_ERROR (12500)`

Google could not match the app to the OAuth client. Check, in order:

1. The package name is exactly `com.warrantyvault` and the SHA-1 matches the keystore the APK was
   actually signed with (debug vs release).
2. The Drive API is enabled in the *same* project that owns that OAuth client.
3. If your build still cannot resolve the project, add the Firebase/Google Services plugin so the
   project is pinned in the APK:
   - Add `google-services.json` (from the same Cloud project) to `app/`
   - Add `id("com.google.gms.google-services")` to the app plugins and the plugin to the root
     `build.gradle.kts` classpath, then rebuild.

## Notes and limitations

- The app-data folder is invisible in the Drive UI by design. To confirm a backup exists, use the
  Settings row (it shows *last backup &lt;time&gt; (N product(s))*), or the Drive API
  `files.list?spaces=appDataFolder` endpoint.
- The backup file is a versioned envelope (`BackupEnvelope`) around the same document as
  *Export JSON*, so it can also be imported by hand via *Import data (JSON)* on any device.
- Only products, warranty periods and service history are backed up; attached document images stay
  on the device.
- Backup is manual (button) or on first link. There is no background auto-sync yet.
