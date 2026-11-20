# Prepared tag channels (not deployed)

This change prepares workflows only. It creates no tag, secret, environment, signing key, release or repository setting. Both certificate pins are deliberately empty and the verified public code floor is unset; tag builds fail closed until those values are verified and approved. Never fill a guessed certificate or generate a fallback key.

## Exactly two applications

- Public: `com.jaguar.attendancetracker`, visible source version (currently `6.0.0`).
- Dev: `com.jaguar.attendancetracker.dev`, visible `6.0.0-dev`.

`version.properties` is the single version source and preserves current values. No public version bump is included. Ordinary debug builds now use `.dev`, not the former `.b`. Existing `.b` installs are historical installs, not an upgrade target. Do not uninstall them or clear their data as part of testing.

A locally debug-signed `.dev` APK and distributed Dev APK have the same package but normally different certificates. They **cannot update each other** without the same signer. Do not uninstall to resolve this automatically; choose a separate test device/profile or an explicitly approved migration plan. Distributed Dev updates require the same persistent Dev key and an increasing internal versionCode. Public upgrade compatibility still requires verifying the prior public APK signer and testing an actual production upgrade; a Dev artifact does not prove it.

## Dev tag

Create `dev-build/<positive integer>` only on the current reviewed `dev` head after checks. No leading zeros and maximum 2,100,000,000. The number is an internal Android versionCode only; SHA/tree/run are metadata, not visible version decoration. Never reuse, move or delete build tags. The gate checks current tags plus previous successful intended Dev workflow runs to reject reused/non-increasing counters. Deleted workflow history and tags cannot be reconstructed by this gate; preserve that evidence.

The workflow validates unsigned release configuration, JVM tests and lint, compiles instrumentation, then signs with existing `DEV_KEYSTORE_BASE64`, `DEV_KEYSTORE_PASSWORD`, `DEV_KEY_ALIAS`, `DEV_KEY_PASSWORD`. It verifies the actual signed APK's package, version and certificate against `release/signers.json`. No secret is created or inspected in this preparation.

Output is an Actions artifact only: `track-at-dev-<n>` with APK, metadata and SHA256SUMS, retained 90 days. It is not a GitHub Release. No overwrite/delete fallback exists. Expired provenance blocks production promotion until a new verified Dev build is made.

## Public tag

Only `vMAJOR.MINOR.PATCH`, exactly matching source version, on the current approved `main` head. Public code is source-managed and must exceed the verified installed baseline and later published metadata. The user-approved tag is the release authorization; there is no new repository environment or approval setting created here. Keep `main` frozen until a separate promotion decision.

Promotion requires an actual successful `.github/workflows/dev-build.yml` push run in this repository with the **same Git tree**. Its tag must still identify its source commit on dev history. The artifact is fetched from that run, checksum verified, and its actual APK certificate/identity verified. A claimed SHA or user-supplied metadata alone is not enough. Different merge SHAs with identical trees are acceptable; missing/expired artifacts fail closed. Historical v6.0.0 and earlier releases predate this metadata; the approved fingerprint and version-code floor must be verified from the old APK before enabling the first promotion.

Public signing uses existing `PUBLIC_KEYSTORE_BASE64`, `PUBLIC_KEYSTORE_PASSWORD`, `PUBLIC_KEY_ALIAS`, `PUBLIC_KEY_PASSWORD`, with no fallback. A new draft release is staged, server asset sizes/digests checked, then published as normal latest release, not beta. Existing releases/assets are never overwritten/deleted. A partial staging failure remains a draft for manual review; do not recreate or purge it automatically.

## Cost and limits

Separate tag workflows run only on their explicit tags, not every push. Same-channel publication is serialized and not canceled once running; GitHub's concurrency pending queue may replace older pending requests. Do not queue several tags at once. PR checks stay Linux-only and read-only. Instrumentation is compiled, not executed. Signing, device installation, historical 9→10 migration and production upgrade validation remain unverified until explicitly performed.

Full reruns may encounter an already-uploaded candidate/artifact or staged draft. They fail rather than overwrite or delete it; inspect the existing attempt and retry only a safe failed job or create a new authorized Dev tag. Ref checks reduce but cannot atomically prevent a tag mutation between the last check and GitHub publication; never move tags. No branch/tag protection settings are silently configured.

## Trust boundary before activation

A tag-triggered workflow is read from the tagged revision. Repository scripts and certificate pins are safeguards against mistakes, **not** a security boundary against an actor allowed to create arbitrary tags or change that workflow. Before adding usable signing credentials or enabling distribution, the repository owner must establish GitHub-native trusted tag-actor restrictions and protected signing approvals/environments (or an equivalent reviewed control). The public publishing token also needs that trusted tag boundary. This PR does not create or modify those settings, credentials or resources. Leave the pins/code floor unset and do not create release tags until that activation review is complete. The intended workflow currently fails before signing while its pins remain empty; missing key secrets also fail without fallback.
