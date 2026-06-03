# Releasing to Maven Central

The release is driven by the manual workflow
[`.github/workflows/release-java.yml`](.github/workflows/release-java.yml). It builds the
native library on all six platforms, assembles the API jar plus one native jar per platform,
signs everything, and publishes to Maven Central via the Sonatype Central Portal.

## One-time setup

### 1. Maven Central (Sonatype Central Portal) account + namespace

1. Create an account at <https://central.sonatype.com/>.
2. Register and **verify the namespace `io.github.mapo80`**. For a GitHub-based namespace the
   portal asks you to create a public verification repository under `github.com/mapo80`; follow
   the on-screen instructions. (If you publish under a different `groupId`, change it in
   [`gradle.properties`](gradle.properties) and verify that namespace instead.)
3. Generate a **user token** (Account → Generate User Token). The token's username/password are
   used as `OSSRH_USERNAME` / `OSSRH_PASSWORD`.

### 2. GPG signing key

```bash
# Generate a key (RSA 4096, no expiry is fine for CI)
gpg --quick-generate-key "Your Name <you@example.com>" rsa4096 sign never

# Find the key id, then export:
KEYID=$(gpg --list-secret-keys --keyid-format=long | awk '/sec/{print $2}' | cut -d/ -f2 | head -1)
gpg --armor --export-secret-keys "$KEYID" > private-key.asc          # -> SIGNING_KEY
# Publish the public key so Central can verify the signatures:
gpg --keyserver keyserver.ubuntu.com --send-keys "$KEYID"
```

`SIGNING_KEY` is the full ASCII-armored private key (contents of `private-key.asc`);
`SIGNING_PASSWORD` is its passphrase.

### 3. Repository secrets

```bash
gh secret set OSSRH_USERNAME  --repo mapo80/liteparse-java
gh secret set OSSRH_PASSWORD  --repo mapo80/liteparse-java
gh secret set SIGNING_KEY     --repo mapo80/liteparse-java < private-key.asc
gh secret set SIGNING_PASSWORD --repo mapo80/liteparse-java
```

## Cutting a release

1. Make sure CI is green on `main`.
2. Bump the version in both [`gradle.properties`](gradle.properties) (`version=`) and
   [`rust/Cargo.toml`](rust/Cargo.toml) (`version =`) to keep the binding aligned with the
   LiteParse core version, and commit.
3. **Dry run** first — build & assemble all artifacts without publishing:

   ```bash
   gh workflow run release-java.yml -f version=<VERSION> -f dry-run=true --repo mapo80/liteparse-java
   ```

   Confirm the run produces the API jar + sources + javadoc + six `*-<classifier>.jar` native jars.
4. **Publish** — same workflow with `dry-run=false`:

   ```bash
   gh workflow run release-java.yml -f version=<VERSION> -f dry-run=false --repo mapo80/liteparse-java
   ```

   This publishes to the Central Portal staging repository, closes and releases it, tags
   `java-v<VERSION>`, and creates a GitHub Release with the jars attached.

## Consuming a published release

```kotlin
implementation("io.github.mapo80:liteparse-java:<VERSION>")
runtimeOnly("io.github.mapo80:liteparse-java:<VERSION>:linux-x86_64")   // your platform(s)
```
