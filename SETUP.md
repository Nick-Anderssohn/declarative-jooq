# Credential Setup

GPG key + Sonatype Portal token + GitHub Secrets for Maven Central publishing.

---

## 1. GPG Key

Generate a fresh dedicated signing key:

```bash
gpg --full-generate-key
```

Select RSA and RSA (option 1), 4096 bits, no expiry (or 2y). Use your project or personal email.

List keys to find IDs:

```bash
gpg --list-keys --keyid-format long
gpg --list-secret-keys --keyid-format long
```

Find the `ssb` line (signing subkey), e.g.:

```
ssb   rsa4096/AABBCCDD11223344 2024-01-01 [S]
```

`AABBCCDD11223344` is the subkey ID. The last 8 hex chars (`11223344`) become `SIGNING_KEY_ID`.

Export the signing subkey for CI:

> PITFALL: The `!` suffix on the subkey ID is mandatory. Without it, GPG exports the master key instead of the signing subkey, and CI signing will fail silently or produce invalid signatures.

```bash
gpg --export-secret-subkeys AABBCCDD11223344! | base64
```

The base64 output becomes the `SIGNING_KEY` GitHub Secret value.

Upload the public key so Maven Central can verify signatures:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys MASTERKEYID
```

(`MASTERKEYID` is the fingerprint on the `pub` line.)

---

## 2. GPG Verification

Smoke test before proceeding:

```bash
echo "test" | gpg --clearsign
```

Expected output contains `-----BEGIN PGP SIGNED MESSAGE-----`. If this fails, the key is not usable — stop and fix before Phase 11.

---

## 3. Sonatype Portal Token

1. Log in to https://central.sonatype.com
2. Click avatar (top-right) > **View Account**
3. Under **User Token**, click **Generate User Token**
4. Copy the generated username and password

> PITFALL: The Portal user token is NOT your login password. It is a separate username/password pair generated from the Portal UI. Using your account login credentials will fail with 401.

The token username becomes `SONATYPE_USERNAME`, the token password becomes `SONATYPE_PASSWORD`.

Verify credentials:

```bash
curl -u 'SONATYPE_USERNAME:SONATYPE_PASSWORD' \
  https://central.sonatype.com/api/v1/publisher/published?namespace=com.nickanderssohn
```

Expected: HTTP 200 (empty array `[]` is fine for a new namespace). A 401 means wrong credentials — regenerate the token.

> PITFALL: OSSRH (oss.sonatype.org) is dead. Do not follow any guide that references OSSRH URLs or Nexus staging. The only path is Central Portal (central.sonatype.com) with `SonatypeHost.CENTRAL_PORTAL` in the vanniktech plugin config.

---

## 4. GitHub Secrets

Settings > Secrets and variables > Actions > **New repository secret**

| Secret | Value | Source |
|--------|-------|--------|
| `SONATYPE_USERNAME` | Portal user token username | Central Portal > Account > User Token |
| `SONATYPE_PASSWORD` | Portal user token password | Central Portal > Account > User Token |
| `SIGNING_KEY` | base64-encoded armored private subkey | `gpg --export-secret-subkeys SUBKEYID! \| base64` |
| `SIGNING_KEY_ID` | Last 8 hex chars of subkey fingerprint | `gpg --list-secret-keys --keyid-format long` |
| `SIGNING_PASSWORD` | GPG key passphrase | Set during `gpg --full-generate-key` |

---

## 5. Local gradle.properties

**Preferred: in-memory signing** (used by Phase 11 vanniktech config)

`~/.gradle/gradle.properties`:

```properties
# Vanniktech in-memory signing
signingInMemoryKeyId=SIGNING_KEY_ID_VALUE
signingInMemoryKeyPassword=SIGNING_PASSWORD_VALUE
signingInMemoryKey=BASE64_ENCODED_ARMORED_KEY

# Maven Central credentials
mavenCentralUsername=SONATYPE_USERNAME_VALUE
mavenCentralPassword=SONATYPE_PASSWORD_VALUE
```

`signingInMemoryKey` is the same base64 output as the `SIGNING_KEY` GitHub Secret.

**Alternative: keyring file** (legacy — NOT compatible with the vanniktech plugin configuration used by this project)

```properties
signing.keyId=SIGNING_KEY_ID_VALUE
signing.password=SIGNING_PASSWORD_VALUE
signing.secretKeyRingFile=/path/to/secring.gpg
sonatypeUsername=SONATYPE_USERNAME_VALUE
sonatypePassword=SONATYPE_PASSWORD_VALUE
```

Phase 11 publishing config uses vanniktech's `signingInMemoryKey` properties. The legacy `signing.key` / `signing.keyId` / `signing.password` names are NOT recognized by the vanniktech plugin.
