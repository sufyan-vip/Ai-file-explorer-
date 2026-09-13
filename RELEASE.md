# NEXARQ — Release & Publishing

## Versioning

- `versionCode` / `versionName` live in `app/build.gradle.kts`.
- Bump `versionCode` for every store upload; use semver for `versionName`.

## Signing

Signing keys are **never** committed. To create a release keystore (once):

```bash
keytool -genkey -v -keystore nexarq-release.jks \
  -alias nexarq -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root (git-ignored):

```properties
storeFile=nexarq-release.jks
storePassword=REPLACE_ME
keyAlias=nexarq
keyPassword=REPLACE_ME
```

Then build:

```bash
./gradlew assembleRelease
```

The unsigned APK is produced at `app/build/outputs/apk/release/` when
`keystore.properties` is absent (useful for local testing only).

## Pre-release checklist

1. `./gradlew clean test lint` — all green.
2. Manual QA matrix in `TESTING.md` complete.
3. Version bumped; changelog updated.
4. No secrets in Git (`git grep -iE "api[_-]?key|password|token"` review).
5. Privacy policy reviewed (see `PRIVACY.md`).
6. Screenshots captured (phone + tablet, dark + light).
7. Data-safety form answers prepared (see below).

## Play Store data-safety (draft)

- **Location** — not collected.
- **Personal info** — not collected.
- **Financial info** — not collected.
- **Photos/media, Files** — accessed on-device to provide file management; not
  uploaded; optional when the user selects folders/files.
- **App activity** — not collected (no analytics).
- **Device or other IDs** — not collected.
- **Data encryption in transit** — AI requests use HTTPS.
- **User control** — AI is opt-in; users can remove API keys and clear history.

## Store metadata (draft)

- **Title:** NEXARQ — Archive & File Manager
- **Short description:** Modern archive manager, file browser, root tools and
  optional AI assistant for Android.
- **Full description:** see feature list in `README.md`.
- **Category:** Tools / Productivity.

## Known limitations

- Writing encrypted 7z is not supported by the bundled engine (use encrypted ZIP).
- Root features require a rooted device.
- Scoped storage restricts direct access to other apps' private data.
