# Debug keystore (shared)

`boomeranger-debug.jks` is an intentional, non-secret debug signing key for sideloaded
CI/local APKs.

- Alias: `boomeranger-debug`
- Store / key password: `android`
- Purpose: stable signing identity across GitHub Actions runners and developer machines

Do **not** use this keystore for Play Store / production releases.
