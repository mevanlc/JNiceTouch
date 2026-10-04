# Releasing JNiceTouch

Stable Git tags are the source of release versions. `v0.1.0` publishes
`io.github.mevanlc:jnicetouch:0.1.0`; the workflow passes the version to Gradle
through `-PreleaseVersion=0.1.0`. Version numbers have three numeric components,
with no leading zeros, prerelease qualifiers, or build metadata. Development
builds use `0.0.0-SNAPSHOT` and are not published to Central.

## One-time Maven Central setup

1. Sign into the [Central Portal](https://central.sonatype.com/) using the
   `mevanlc` GitHub account. Check **View Namespaces** for a verified
   `io.github.mevanlc` namespace. GitHub signup normally provisions this
   namespace automatically. If it is missing, follow Sonatype's
   [namespace registration instructions](https://central.sonatype.org/register/namespace/).
2. Follow the [Portal token instructions](https://central.sonatype.org/publish/generate-portal-token/)
   to generate a publishing token. Its generated username and password are
   separate from the credentials used to sign into the website.
3. Create or reuse a dedicated, passphrase-protected artifact-signing key with
   `gpg --full-generate-key`. Select an RSA signing key of at least 3072 bits,
   enter your name and email, and choose an expiration you can maintain.
   See Sonatype's [PGP signing guide](https://central.sonatype.org/publish/requirements/gpg/).
4. Find the key's full fingerprint:

   ```sh
   gpg --list-secret-keys --keyid-format long
   ```

   Use the primary key's full fingerprint. It must have signing capability
   (`[S]` or `[SC]`), not only encryption capability (`[E]`).

5. Publish the public key so Central and consumers can verify signatures:

   ```sh
   gpg --keyserver hkps://keyserver.ubuntu.com --send-keys YOUR_FULL_FINGERPRINT
   ```

6. Add these **repository Actions secrets** to
   [JNiceTouch's Actions settings](https://github.com/mevanlc/JNiceTouch/settings/secrets/actions):

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | Portal token's generated username |
   | `MAVEN_CENTRAL_PASSWORD` | Portal token's generated password |
   | `SIGNING_KEY` | Complete ASCII-armored private signing key |
   | `SIGNING_KEY_PASSWORD` | Signing key's passphrase |

   To send the private key directly to GitHub without printing it:

   ```sh
   gpg --armor --export-secret-keys YOUR_FULL_FINGERPRINT |
     gh secret set SIGNING_KEY --repo mevanlc/JNiceTouch
   gh secret set MAVEN_CENTRAL_USERNAME --repo mevanlc/JNiceTouch
   gh secret set MAVEN_CENTRAL_PASSWORD --repo mevanlc/JNiceTouch
   gh secret set SIGNING_KEY_PASSWORD --repo mevanlc/JNiceTouch
   ```

   The last three commands prompt for their values. Verify the secret names with
   `gh secret list --repo mevanlc/JNiceTouch`. Complete this setup before pushing
   the first release tag; missing credentials fail the publication step.

## Build and release

Run the same checks locally on macOS:

```sh
./gradlew clean build :lib:generatePomFileForMavenPublication -PreleaseVersion=0.1.0
bash .github/scripts/verify-artifacts.sh 0.1.0
```

Inspect the JARs under `lib/build/libs` and the generated publication metadata
under `lib/build/publications/maven`. The artifacts include the binary,
sources, and Javadoc JARs, and the binary includes the universal native library.
No publishing credentials are needed for these checks.

When the intended commit is on GitHub and its CI is green, create and push the
release tag:

```sh
git tag -a v0.1.0 -m 'Release 0.1.0'
git push origin v0.1.0
```

The Release workflow validates the tag, builds and tests on both `macos-15`
(ARM64) and `macos-15-intel`, then builds and verifies the publication on ARM64.
Only its final publishing step receives credentials. Gradle signs the
publication, uploads it through the Central Portal, and waits for `PUBLISHED`.
Workflow runs for the same tag are serialized. This does not create a GitHub
release page.

The workflow maps the four secrets to the publishing plugin's Gradle properties:

| Secret | Environment variable |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` |
| `SIGNING_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` |
| `SIGNING_KEY_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` |

With those environment variables configured locally, the equivalent command is:

```sh
./gradlew --no-configuration-cache :lib:publishToMavenCentral -PreleaseVersion=0.1.0
```

This command publishes immediately after Central validates the deployment.
Publishing disables the configuration cache so signing material is not stored
in that cache. The release job also uses a read-only Gradle cache.

## Failures and verification

- Invalid tags stop before the build. The release workflow accepts only stable
  `vX.Y.Z` tags; other `v*` tags fail validation.
- Java, native, or artifact checks on either architecture block publication.
  Test reports remain available as workflow artifacts after test failures.
- CI executes native ABI and queue tests and a Java FFM discovery test. The
  provider's GUI lifecycle test skips when its runner is headless; passing CI
  does not establish physical gesture or GUI behavior.
- Central requires the registered namespace, license/POM metadata, source and
  Javadoc artifacts, and valid signatures. See its
  [publication requirements](https://central.sonatype.org/publish/requirements/).
- After success, confirm the version is available on Central and resolve it
  from a consumer using `mavenCentral()`. Consumers must enable JDK native
  access as described in the README.
- An already published release is immutable. Fixes need a new version and tag.
  If a failed run already uploaded a deployment, inspect its status in the
  Portal before retrying. Never move a published release tag.

Maintain the signing key's expiration and update GitHub secrets when the
Portal token or key changes. Native ABI version 1 is a separate compatibility
contract and is not changed by ordinary release version bumps.
