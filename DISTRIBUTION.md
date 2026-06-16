# Distribution Guide

This project can be shared outside WhatsApp by publishing the APK as a GitHub Release asset and pointing a simple download page to that asset.

## Current APK

- File: `app/release/qibla-v1.0.5.apk`
- Version: `1.0.5`
- Size: `11.54 MB`
- SHA-256: `c5ac1c8e800058059b8854f9edf0f85887749b2aa959312066e109b1598e7b28`

## Recommended Flow

1. Build a release APK with the same signing key every time.
2. Calculate the APK SHA-256:

```powershell
Get-FileHash -Path .\app\release\qibla-v1.0.5.apk -Algorithm SHA256
```

3. Create a GitHub Release named `v1.0.5`.
4. Upload `qibla-v1.0.5.apk` as a release asset.
5. Open `docs/index.html` and replace:

```text
https://github.com/alghanim100/alghanim/releases/latest/download/qibla-v1.0.5.apk
https://github.com/alghanim100/alghanim/releases/latest
```

with the real repository links.

6. If the APK changes, update the version, size, and SHA-256 in `docs/index.html` and this file.
7. Enable GitHub Pages from the repository settings:

```text
Settings > Pages > Build and deployment > Source: Deploy from a branch > Branch: main > Folder: /docs
```

## User Message

Use this short message when sharing the app:

```text
حمل تطبيق Qibla من الصفحة الرسمية فقط، وتأكد أن بصمة SHA-256 في الصفحة مطابقة للملف. قد تظهر رسالة Play Protect لأن التطبيق مثبت من خارج Google Play، وهذا لا يعني بحد ذاته أن التطبيق خطر.
```