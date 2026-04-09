# GitHub Initial Release Checklist

## 1. Create Repository
- Create a new GitHub repository (public or private).
- Name suggestion: `ironlog`.

## 2. Copy Release Docs
- Copy files from `IronLogGithub/` into your repo root:
  - `README.md`
  - `CHANGELOG.md`
  - `LICENSE`
  - `SECURITY.md`
  - `CONTRIBUTING.md`
  - `CODE_OF_CONDUCT.md`
  - `.github/` folder

## 3. Commit and Push
```bash
git add .
git commit -m "chore: prepare v1.0.0 public release"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

## 4. Tag Release
```bash
git tag -a v1.0.0 -m "IronLog v1.0.0"
git push origin v1.0.0
```

## 5. Publish Release in GitHub UI
- Open: `Releases` -> `Draft a new release`
- Tag: `v1.0.0`
- Title: `IronLog v1.0.0`
- Body: paste `RELEASE_NOTES_v1.0.0.md`
- Upload APK:
  - `android/app/build/outputs/apk/release/app-release.apk`
- Click `Publish release`

## 6. Repo Settings (Recommended)
- Enable `Issues`
- Enable `Discussions` (optional)
- Add topics:
  - `react-native`
  - `android`
  - `fitness`
  - `workout-tracker`
- Set default branch protection for `main` (optional)

