# Coordinate the Three Repositories

## Assign ownership

Treat each checkout as an independent Git repository. Do not run Git commands from their common parent or assume one commit can cover all three.

- `Peaks2000/ViaBedrock` owns Bedrock codecs, serializers, packet handlers, mappings, and protocol-specific translator tests. Use the maintained protocol branch selected by ViaFabricPlus; for 1.26.40 that branch is `peaks/1.26.40-fixes`.
- `Peaks2000/ViaFabricPlusPeaks` owns Fabric integration, LAN and Xbox discovery, NetherNet and RakNet transports, stock-versus-maintained route selection, focused compatibility mixins, the ViaBedrock branch selection, the distributable JAR, and the tracked skill mirror.
- `Peaks2000/maintain-viafabricplus-bedrock` owns the canonical skill instructions, references, scripts, and agent metadata. Mirror the complete skill directory into `ViaFabricPlus/.codex/skills/maintain-viafabricplus-bedrock/` so maintenance knowledge travels with the fork.

Put a fix in the lowest layer that owns the faulty behavior. Prefer ViaBedrock for reusable packet translation and ViaFabricPlus for fork-only routing or transport behavior. Do not carry duplicate implementations in both code repositories.

## Prepare sibling checkouts

Use a stable sibling layout so Gradle can substitute the local ViaBedrock checkout during development:

```text
<workspace>/
├── ViaBedrock/
├── ViaFabricPlus/
└── maintain-viafabricplus-bedrock/
```

Use `gh repo clone OWNER/REPO <path>` for missing checkouts and `gh repo view OWNER/REPO` to confirm repository identity. Before editing, run `git -C <path> status --short --branch`, `git -C <path> remote -v`, and `git -C <path> branch -vv` in every affected repository. Confirm that:

- `origin` points to the Peaks2000 repository;
- any `upstream` remote points to the original project;
- the active ViaBedrock branch matches the branch selected in ViaFabricPlus `build.gradle.kts`;
- unrelated tracked and untracked work is preserved.

Never add launcher logs, account data, tokens, build directories, Gradle caches, or generated JARs to Git. Do not push, open pull requests, create releases, or change remote sessions without user authorization.

## Develop across the dependency boundary

Make and validate ViaBedrock changes in the ViaBedrock checkout first. During the inner loop, run ViaFabricPlus tasks with the sibling checkout substituted explicitly:

```bash
./gradlew --include-build ../ViaBedrock compileJava
./gradlew --include-build ../ViaBedrock test
./gradlew --include-build ../ViaBedrock build
```

Run commands from the ViaFabricPlus root and verify Gradle substitutes `net.raphimc:ViaBedrock` with the expected local project. Do not permanently replace the VCS dependency with a local filesystem path.

After the ViaBedrock commit is pushed, rebuild ViaFabricPlus against the remote branch selected by its Gradle VCS dependency:

```bash
./gradlew --refresh-dependencies clean build
```

Treat this remote-backed build as the release build. Gradle labels its source-control checkout `:ViaBedrock`, so that task name alone does not prove sibling substitution. Locate the newest `ViaFabricPlus/.gradle/vcs-1/*/ViaBedrock` checkout and run `git -C <checkout> rev-parse HEAD`; require the pushed ViaBedrock commit. Then run the remaining validation from the main skill, identify the remapped main JAR, and record its SHA-256.

## Synchronize the skill

Edit the standalone `maintain-viafabricplus-bedrock` checkout as the canonical source. Copy every changed skill file into the ViaFabricPlus mirror, including `SKILL.md`, `agents/`, `references/`, and `scripts/`. Before committing either repository, require an empty recursive diff apart from standalone Git metadata:

```bash
diff -ru --exclude=.git \
  ../maintain-viafabricplus-bedrock \
  .codex/skills/maintain-viafabricplus-bedrock
```

Validate both directories with the skill creator's `quick_validate.py`. Test changed scripts directly. Keep task-specific protocol knowledge in the skill update that accompanies the code, but avoid copying source code or build artifacts into the skill repository.

## Commit and push safely

Review `git diff --check`, `git diff --stat`, `git diff`, and `git status --short` separately in each repository. Create focused commits that describe only that repository's responsibility.

For code changes, deliver in dependency order:

1. Commit and push ViaBedrock.
2. Build ViaFabricPlus against the pushed ViaBedrock branch, then commit and push ViaFabricPlus, including the synchronized skill mirror when it changed.
3. Commit and push the canonical standalone skill after its instructions reflect the delivered implementation.

For a skill-only change, commit and push the standalone skill first, then commit and push the identical ViaFabricPlus mirror. Do not create an empty ViaBedrock commit.

Push each explicit branch rather than relying on whichever upstream happens to be configured. Use `gh api repos/OWNER/REPO/commits/BRANCH --jq .sha` or `gh repo view` after pushing to confirm GitHub sees the intended branch and commit. Finish by reporting all repository commit URLs, the validation performed, and the JAR path and checksum when a build was requested.
