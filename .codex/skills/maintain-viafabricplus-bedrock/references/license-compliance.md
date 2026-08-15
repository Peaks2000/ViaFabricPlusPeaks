# Licence and attribution gate

Apply this gate before copying external code, committing a port, vendoring a binary, or publishing a JAR.

## Classify provenance

For every external input, record its repository, exact commit or release, licence, and how it influenced the fork:

- **Reference only:** protocol facts, schemas, or behavior were studied; no expressive source was copied.
- **Adapted:** implementation was translated or materially derived; preserve the source licence, required notices, and attribution.
- **Copied:** retain the original copyright header and licence text, and mark local modifications when required.
- **Binary:** record the artifact source, build commit/run, original hash, embedded components, and redistribution notices.

Do not describe code as reference-only when its structure or expression was copied. Use commit history and a direct diff against the cited source when provenance is uncertain.

## Check compatibility

The ViaFabricPlus and ViaBedrock forks are GPL-3.0-or-later. MIT, BSD-2/3-Clause, Apache-2.0, and compatible GPL code can generally be incorporated when their conditions are preserved. Do not import GPL-2.0-only, proprietary, source-available, or unknown-licence material without resolving compatibility first.

Keep existing upstream headers. Give new source files the owning repository's standard header. Do not replace upstream copyright notices with the fork maintainer's name or invent copyright ownership.

For adapted pull-request work, cite the source PR/commit in the adapting commit body and a durable project notice. Preserve author credit where Git can do so accurately; do not falsify authorship.

## Distribute source and notices

For every released JAR:

1. Keep the complete GPL licence available with the binary.
2. Make corresponding modified source available at the exact ViaFabricPlus and ViaBedrock commits used to build it.
3. Package required third-party licence and NOTICE texts in a readable JAR path such as META-INF/licenses/.
4. Keep the same notices in the Git repository and link them from user-facing documentation.
5. Put exact dependency commits and source URLs in release notes when the binary embeds code from another repository or a movable VCS branch.
6. Inspect the final remapped JAR, not only the development classpath.

For vendored native/JNI artifacts, include the wrapper licence, every upstream NOTICE, bundled native-library licence, and helper-library notices from the exact source commit. A POM coordinate and source link alone do not satisfy a licence requiring a reproduced notice.

## Keep comments restrained

Prefer descriptive names, focused tests, commit bodies, and skill references over narrative source comments. Add a code comment only for a non-obvious invariant, protocol asymmetry, safety constraint, or temporary workaround that cannot be expressed clearly in code. Keep it short and state why, not what the next line does.

## Release audit

Before publishing, require:

- all new/modified source files have the correct owning-project header;
- LICENSE, third-party notices, and attribution links are present and accurate;
- vendored artifact hashes match the recorded provenance;
- the built JAR contains the expected META-INF/licenses/ entries;
- release notes identify both source commits and any adapted external PR;
- no logs, credentials, account identifiers, or unrelated third-party source trees are committed.

If a licence or provenance is uncertain, stop the release and resolve it rather than guessing.
