# Debug keystore (shared)

`boomeranger-debug.jks` is an intentional, non-secret debug signing key for sideloaded
CI/local APKs and GitHub Release APKs.

- Alias: `boomeranger-debug`
- Store / key password: `android`
- Purpose: stable signing identity across GitHub Actions runners, developer machines,
  and GitHub Release assets so installs can update in place

Do **not** use this keystore for Play Store / production releases.
