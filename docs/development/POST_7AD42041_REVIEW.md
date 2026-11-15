# Post-7ad42041 code review

Reviewed on 2026-11-16.

## Scope and baseline

Compared the working tree with local commit `7ad4204175a6a68c9b749bd8936652ff690362bc` (`style(carousel): update hero carousel scrim and dim tokens`). The configured origin is `blue-poet/YumaPlayer`; the requested MuwMx GitHub page could not be retrieved, so this review uses the available exact commit object rather than claiming a fresh remote comparison. The initial tracked delta contained 55 files, 2,750 insertions and 175 deletions, plus untracked caption work.

Applied the repository's CONTRIBUTING, editor configuration, YUMA_RULES, CODING_STANDARD, DEVELOPMENT_GUIDE, architecture and YDS/component guidelines. This is a review of the added feature paths, not certification of the entire pre-existing codebase.

## Findings addressed

- **Activity retention and binding races:** canvas decoder dependencies now receive application context. Service binding rejects late connection callbacks, retains Android registration bookkeeping through service disconnection, and unbinds once during cleanup. Existing connection disposal remains intact.
- **Cancellation swallowed as missing data:** lyric candidate checks and audio alignment decoding propagate cancellation. Caption network work runs on IO and parsing runs on Default; provider timeouts remain explicit.
- **Network work owned by composables:** video search and playback preparation now use domain contracts, data repositories, use cases and lifecycle-aware ViewModels. Legacy entry points remain small compatibility facades. The domain has no presentation dependency.
- **Search request races and pagination:** new searches cancel older work, stale responses cannot overwrite newer queries, pagination failures preserve existing results, duplicate items are removed, and repeated continuation tokens terminate pagination.
- **Playback state races:** request identity isolates songs and retry generations. A late alignment result cannot revive a failed decoder. Alignment failure preserves playable video instead of failing the whole source.
- **Range decoding and stale cache data:** alignment range reads validate requested bounds, complete Content-Range values and declared length. Corrupt or out-of-range cached offsets are discarded. Chunk memory remains bounded.
- **Duplicated infrastructure:** media HTTP setup is shared by video streaming/alignment while preserving YouTube proxy and DNS behavior. Repeated proxy authentication attempts are bounded.
- **UI consistency:** artwork/video choices use the shared segmented preference shape and selected checkmarks. Video dimensions, typography, colors and timing use semantic tokens. New transitions respect disabled animations and consume dynamic alpha/offset values in draw phases.
- **Caption fallback ordering:** YouTube captions remain last even with older stored provider orders. Failed caption checks can be retried. Malformed track parsing is reported rather than silently discarded.

Updated architecture, development and design documentation alongside implementation. Added focused tests for lifecycle races, stale search results, pagination recovery, cancellation, range validation and corrupt caches.

## Behavior preserved

Preserved automatic video matching order, manual per-song video selection, same-video reuse, SponsorBlock timeline mapping, 80-second alignment samples with a one-minute offset range, fullscreen playback/seekbar, word-timed lyrics, local caption visibility, three-second controls/status timeout and 500 ms artwork crossfade.

The frozen gesture/sheet controllers and LyricsColumn were not edited during this review. Generated release binaries already present in the baseline delta were not changed. Removed the obsolete fullscreen time-label helper and its single test because the fullscreen UI already delegates labels to the shared player seekbar; seek safety tests remain.

## Validation

Production Kotlin/Java, Hilt bindings and unit-test sources compiled for `fossMobileArm64Debug`. The targeted run executed 96 tests: 95 passed, with one new cancellation test failing because it incorrectly required exception object identity across coroutine stack-trace recovery. Its assertion now checks the cancellation message and original cause. The focused `--tests '*LyricsCancellationTest'` rerun passed both tests (BUILD SUCCESSFUL, exit 0); production sources were unchanged between runs. All 96 selected cases have therefore passed across the initial run and focused rerun.

The 96-test selection covers MusicVideo tests, RangeAudioReader, lyric ordering/cancellation/captions, service binding lifecycle, fullscreen seek safety, word lyric timing, player action routing and canvas cache source isolation. Whitespace validation passed with `git diff --check` using the repository's Windows line-ending allowance.

No full test suite, release/R8 build, device instrumentation or fresh LeakCanary capture was run. Native surface transitions, fullscreen rotation and actual device retention still require device verification. The separate Android/OEM ResourcesImpl-to-WorkManager service retention trace was not suppressed or claimed fixed by these application lifecycle changes. No commits or staging were performed.
