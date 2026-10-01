# Maintaining the Aselion fork

This fork tracks [EliteScouter/EliteEssentials](https://github.com/EliteScouter/EliteEssentials) (remote `upstream`)
and carries a few Aselion fixes on `main`. Fork releases are `<upstream version>-aselion.<n>`, built by GitHub Actions
and attached to a GitHub release. The Adventure server vendors the release jar in `AselionHYT/game-servers`
(`base-hytale/servers/adventure/mods/`, pinned in `base-hytale/deps.lock`).

Fork commits are listed in `CHANGELOG.md` under the `-aselion.N` versions. Keep each fork change a separate commit with
a message that explains the cause; that is what makes rebasing onto upstream and dropping a fix upstream already
made cheap.

## Build locally

    ./gradlew build                       # jar in build/libs/, runs the unit tests

Requirements: JDK 25. The server API (`com.hypixel.hytale:Server`) comes from `https://maven.hytale.com/release` (and
`/pre-release`); its version is `serverVersion` in `gradle.properties` and also sets the manifest's `ServerVersion`
range (`^<serverVersion>`, i.e. up to the next minor). Override it for a trial build with
`./gradlew build -PserverVersion=<version>`. The published versions are listed in
`https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml`.

The build is reproducible: the same commit and JDK give the same jar bytes, so a local build can be compared with the
release asset by its SHA-256.

## Pull upstream changes

Upstream publishes on CurseForge first and does not always commit every release (2.0.10 was never committed), so
check that the version you want is actually in git.

    git fetch upstream
    git log --oneline main..upstream/main          # what is new upstream
    git switch -c sync/upstream-<version> main
    git rebase upstream/main                       # replay the fork commits onto upstream
    ./gradlew build

When a fork fix conflicts, check whether upstream fixed the same thing; if so, drop the fork commit
(`git rebase --skip`) and remove its CHANGELOG line. Upstream's `build.gradle.kts`, `gradle.properties` and
`manifest.json` change on every release, so expect conflicts there. After the rebase:

- `gradle.properties`: `pluginVersion=<upstream version>-aselion.1`, `serverVersion` = the Hytale line we run.
- Keep the fork additions: the `slf4j-jdk14` binding, the test-only server dependency and JUL manager, the
  `Buuz135:SimpleClaims` optional dependency in `manifest.json`.
- Add a CHANGELOG entry for the new fork version on top of upstream's.

Push the branch, open a PR in this repository, merge it with a merge commit or fast-forward (not squash, so the fork
commits stay separate), then release (below). Never push to `upstream`; this fork does not open upstream issues or PRs
unless the Aselion team decides to.

Things worth watching upstream, since the fork works around them:

- Usage-variant permissions (`commands/base/*`): if upstream fixes `/home <name>` needing an engine node, compare
  and keep one fix.
- RTP fluid check (upstream PR #69) and SimpleClaims support (fork only).
- Warmup completing more than once (`WarmupService`).

## Port to a new Hytale patchline

1. Find the new API version in the Maven metadata above (release line in `/release`, pre-releases in
   `/pre-release`, e.g. `0.7.0-pre.3.1`).
2. Trial build on a branch: `./gradlew build -PserverVersion=<version>`. The compiler errors are the API breaks;
   `[removal]` warnings are the next ones. The Hytale sources (`hytale-shared-source`, branches `release` and
   `pre-release`) show what replaced a removed method.
3. Fix the breaks, then set `serverVersion` in `gradle.properties`. A minor change (`0.6` to `0.7`) always needs a new
   build, because the manifest range `^0.6.8` stops at `<0.7.0`; on a newer minor the server only logs a version
   mismatch but the plugin is loaded, and any changed method fails at runtime with `NoSuchMethodError` or
   `AbstractMethodError`.
4. Run the unit tests. `VariantPermissionTest.engineMarksVariantAsRegisteredWhenItIsAdded` checks the engine behaviour
   the variant-permission fix depends on; if it fails, re-check `CommandPermissionCompat`.
5. Test on a local server of that patchline (Adventure image, `--auth-mode insecure`, bots from `hytale-bot drive`):
   boot without SEVERE lines from EliteEssentials, `/sethome <name>` + `/home <name>` with only the plugin nodes, `/tpa
   <player>`, `/rtp` (not into claims or fluids), `/sethome` in a foreign claim refused, `/kit starter` once.
6. Release with a version that says which line it is for if both lines need builds at the same time.

### Status of the 0.7 port (2026-10-01)

Branch `port/hytale-0.7`:

- **0.7.0-pre.3.1** (our pre-release line): one break. `ISpawnProvider.getSpawnPoint(World, UUID)` became
  `getSpawnPointAsync(World, UUID)` returning `CompletableFuture<Transform>`; `NearestSpawnProvider` and
  `RandomSpawnProvider` now return a completed future. Compiles, unit tests pass. Not tested on a server and not
  released.
- **0.7.0-pre.5**: 20 more compile errors. Chunk access moved: `World.getChunkIfLoaded(long)`,
  `getChunkIfInMemory(long)`, `getChunkAsync(long)`, `IWorldChunks.getChunk(long)`,
  `WorldChunk.getBlockType(int, int, int)`, `getBlockType(Vector3i)` and `WorldChunk.getFluidId` are gone (RTP, `/top`,
  `/fly`, `FlyService`, `AliasService`, `SpawnUseBlockInteraction`), and `clearCustomPageAcknowledgements()` is gone
  (`HomeEditPage`, `HomeSelectionPage`). On pre.3.1 the same methods are already marked `forRemoval`. This is a real
  port (RTP's chunk loading in particular), not a rename.

## Release

1. On `main`, set `pluginVersion` in `gradle.properties` (e.g. `2.0.11-aselion.2`) and add the CHANGELOG entry.
2. `./gradlew build` locally; note the jar's SHA-256.
3. Commit, push, then tag and push the tag:

       git tag -a v2.0.11-aselion.2 -m "EliteEssentials 2.0.11-aselion.2"
       git push origin main v2.0.11-aselion.2

4. The `build` workflow builds, tests and, for a `v*` tag, creates the GitHub release with the jar and a `.sha256`
   file. It fails if the tag does not equal `v<pluginVersion>`.
5. Check that the release asset's SHA-256 equals your local build.
6. In `AselionHYT/game-servers`, replace the jar under `base-hytale/servers/adventure/mods/`, update its line in
   `base-hytale/deps.lock` (`shasum -a 256 -c deps.lock` must pass), and open a PR. Rollout follows that repo's
   process.
