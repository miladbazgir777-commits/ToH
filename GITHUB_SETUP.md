# GitHub setup

Recommended repository name: `TomeOfHealing`

Recommended initial visibility: **Private** until the first full Android build and physical-tablet regression pass.

After creating an empty repository on GitHub:

```bash
git remote add origin https://github.com/<YOUR_USERNAME>/TomeOfHealing.git
git push -u origin main --tags
```

Then open the repository's **Actions** tab. The included Android CI workflow will test, lint, build, and upload a debug APK artifact.

For manual APK generation, run **Actions → Build APK manually → Run workflow**.
