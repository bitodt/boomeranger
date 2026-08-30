# Debug keystore (shared)

`boomeranger-debug.jks` is an intentional, non-secret debug signing key for sideloaded
CI/local APKs and GitHub Release APKs.

- Alias: `boomeranger-debug`
- Store / key password: `android`
- Purpose: stable signing identity across GitHub Actions runners, developer machines,
  and GitHub Release assets. Debug builds use `applicationId` `com.boomeranger.app.debug`
  (Boomeranger Dev); Release builds stay `com.boomeranger.app`. Same cert, two apps,
  so each lineage updates in place without replacing the other.

Do **not** use this keystore for Play Store / production releases.
