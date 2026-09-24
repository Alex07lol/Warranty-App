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
- **Optional visible copy.** With *Visible copy in My Drive* enabled (the default), the same file is
  also written to a `WarrantyVault` folder in the user's My Drive, so it appears in the Drive app and
  can be downloaded from any device, or imported by hand via *Import data (JSON)*. Restore always
  reads the private app-data copy — the mirror is for the user, not for the sync.
- **First backup is automatic.** Once consent is granted the vault is uploaded immediately (unless
  the guard below applies), and the file is replaced on every later backup.
- **Auto-backup after changes.** With auto-backup on (the default once linked), any change to the
  vault — scan confirm, add/edit, import, service history — schedules an upload about 10 seconds
  later, coalescing a burst of edits into a single request. It runs through WorkManager, waits for
  connectivity, and retries a few times before giving up.
- **The overwrite guard.** Automatic uploads never replace a backup holding *more* products than
  this device, otherwise a fresh install (which seeds demo products) or a half-restored phone would
  overwrite a real backup with seed data. When that happens Settings explains it and points at
  Restore; *Back up now* is the explicit opt-in to overwrite anyway.
- **Never prompts in the background.** If the grant is lost, background syncing pauses and Settings
  offers a one-tap re-link instead of a consent screen appearing on its own.
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
   - Add the scopes `https://www.googleapis.com/auth/drive.appdata` (private backup) and
     `https://www.googleapis.com/auth/drive.file` (the visible copy). `drive.file` is deliberately
     used instead of the full `drive` scope: the app can then only see and manage files it created
     itself, never the rest of the user's Drive.
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
- The visible copy is plain JSON, readable by anything with access to that Drive account (product
  names, serial numbers, IMEIs). Turn *Visible copy in My Drive* off to keep the backup private to
  the app.
- Because of the narrow `drive.file` scope, the app cannot see a `WarrantyVault` folder you created
  by hand, so it will create its own folder of that name rather than reusing yours.
- Grants made before `drive.file` was added do not cover it; if the mirror reports a permission
  error, use *Re-link Google Drive* in Settings to approve the new scope.
- Sync is scheduled on vault changes while the app runs; it is not a continuous background daemon,
  and nothing is uploaded while the app is closed.
- Automatic uploads are skipped when the device holds fewer products than the Drive backup (see the
  guard above). Use *Back up now* to overwrite deliberately.
