# Releasing

Artifacts are distributed as **GitHub Release assets** — there is no Maven Central / Sonatype
publishing, so **no account, GPG key, or repository secrets are required**. The release is driven
by the manual workflow [`.github/workflows/release-java.yml`](.github/workflows/release-java.yml),
which builds the native library on all six platforms, assembles the jars, and attaches them to a
GitHub Release.

## What a release contains

For version `X.Y.Z`, the GitHub Release `java-vX.Y.Z` gets these assets:

- `liteparse-java-bundle-X.Y.Z-<classifier>.jar` — **one self-contained bundle per platform**
  (Java API + shaded Jackson + native binaries). This is what users download. Classifiers:
  `linux-x86_64`, `linux-aarch64`, `macos-x86_64`, `macos-aarch64`, `windows-x86_64`, `windows-aarch64`.
- `liteparse-java-X.Y.Z.jar` — plain API jar (classes only; for users who manage deps themselves).
- `liteparse-java-X.Y.Z-sources.jar` and `liteparse-java-X.Y.Z-javadoc.jar` — for IDEs.

## Cutting a release

1. Make sure CI is green on `main`.
2. Bump the version to keep the binding aligned with the LiteParse core version, in **both**:
   - [`gradle.properties`](gradle.properties) — `version=X.Y.Z`
   - [`rust/Cargo.toml`](rust/Cargo.toml) — `version = "X.Y.Z"`

   Commit and push.
3. **Dry run** — build & assemble everything without creating the release:

   ```bash
   gh workflow run release-java.yml -f version=X.Y.Z -f dry-run=true --repo mapo80/liteparse-java
   ```

   Check the run summary lists the plain jar + sources + javadoc + six `*-bundle-*-<classifier>.jar`.
4. **Publish** — same workflow with `dry-run=false`:

   ```bash
   gh workflow run release-java.yml -f version=X.Y.Z -f dry-run=false --repo mapo80/liteparse-java
   ```

   This tags `java-vX.Y.Z` and creates the GitHub Release with all jars attached.

   (You can also trigger both from the GitHub UI: **Actions → Release - Java → Run workflow**.)

## Consuming a release

See the [Installation section of the README](README.md#installation): download the
`liteparse-java-bundle-<version>-<classifier>.jar` for your platform and put it on the classpath.
