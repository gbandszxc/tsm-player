# WAV SMB Artwork Fast Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded RIFF/WAVE metadata copier so SMB WAV artwork and tags can be parsed without downloading the full PCM payload.

**Architecture:** Introduce a focused, JVM-testable `WavMetadataProbeCopier` that validates RIFF/WAVE structure, retains metadata chunks, truncates the `data` chunk, skips the remaining PCM using `InputStream.skip`, and emits a self-consistent WAV. Wire it into `SmbAudioMetadataProbe` for `.wav` and `.wave`; retain the existing full-file retry when the fast result cannot be parsed.

**Tech Stack:** Kotlin/JVM 17, JUnit 4, jaudiotagger 3.0.1, jcifs-ng 2.1.10, Gradle Android plugin.

---

## File map

- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopier.kt`: RIFF parsing, bounded chunk retention, PCM skipping, and rewritten output.
- Create `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopierTest.kt`: synthetic WAV/ID3/APIC fixtures and malformed-input regression tests.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/SmbAudioMetadataProbe.kt`: route `wav` and `wave` to the copier.
- Modify `docs/MANUAL.md`: record the WAV SMB fast-probe behavior and fallback.

### Task 1: Implement and verify the RIFF metadata copier

**Files:**
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopierTest.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopier.kt`

- [ ] **Step 1: Write failing tests with synthetic WAV fixtures**

Create tests that build RIFF bytes with a standard 16-byte PCM `fmt ` chunk, a 256 KiB `data` chunk, and a valid ID3v2.3 `APIC` frame containing a small PNG byte array. Cover both `fmt/data/id3 ` and `fmt/id3 /data` order, an odd-sized `JUNK` chunk before ID3, and an invalid `NOPE` signature.

The core assertions must be:

```kotlin
val copied = ByteArrayOutputStream()
assertTrue(WavMetadataProbeCopier.copy(ByteArrayInputStream(source), copied))
assertTrue(copied.size() < source.size / 2)

val temp = createTempFile(suffix = ".wav")
temp.writeBytes(copied.toByteArray())
val artwork = AudioFileIO.read(temp).tag.firstArtwork
assertArrayEquals(pngBytes, artwork.binaryData)
```

For the invalid signature case:

```kotlin
val copied = ByteArrayOutputStream()
assertFalse(WavMetadataProbeCopier.copy(ByteArrayInputStream(source), copied))
assertArrayEquals(source, copied.toByteArray())
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.playback.WavMetadataProbeCopierTest"
```

Expected: compilation fails because `WavMetadataProbeCopier` does not exist.

- [ ] **Step 3: Implement the minimal bounded copier**

Create an `internal object WavMetadataProbeCopier` with:

```kotlin
internal object WavMetadataProbeCopier {
    private const val DATA_PROBE_BYTES = 64L * 1024
    private const val MAX_METADATA_CHUNK_BYTES = 32L * 1024 * 1024

    fun copy(input: InputStream, output: OutputStream): Boolean
}
```

`copy` must:

1. Read and preserve the first 12 bytes. If they are not `RIFF....WAVE`, copy the remainder unchanged and return `false`.
2. Interpret RIFF and chunk lengths as unsigned little-endian 32-bit values.
3. Walk chunks within the RIFF-declared boundary, including the one-byte pad after odd chunk sizes.
4. Retain `fmt `, `LIST`, `id3 `, and `ID3 ` chunks when their declared size is at most `MAX_METADATA_CHUNK_BYTES`.
5. Retain at most `DATA_PROBE_BYTES` from `data`, rewrite that chunk length, and call a `skipFully` helper for the remaining PCM bytes. `skipFully` must prefer `InputStream.skip`; if it returns zero, consume one byte to guarantee progress. This is an O(1) network seek for jcifs-ng `SmbFileInputStream` 2.1.10.
6. Skip unknown chunks, retain correct padding, then emit `RIFF`, a rewritten RIFF size, `WAVE`, and retained chunks from a `ByteArrayOutputStream`.
7. Return `true` for a structurally valid RIFF/WAVE fast copy, including truncated input; parse failure is handled by the caller's existing full-copy retry.

Use explicit helpers with these signatures:

```kotlin
private fun InputStream.readFully(buffer: ByteArray): Int
private fun readUInt32Le(bytes: ByteArray, offset: Int = 0): Long
private fun OutputStream.writeUInt32Le(value: Long)
private fun copyExactly(input: InputStream, output: OutputStream, bytes: Long): Long
private fun skipFully(input: InputStream, bytes: Long): Long
private fun isChunkRetained(id: String): Boolean
```

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run the focused test command from Step 2.

Expected: all `WavMetadataProbeCopierTest` tests pass, including jaudiotagger APIC extraction.

- [ ] **Step 5: Commit Task 1**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopier.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopierTest.kt
git commit -m "perf: 增加 WAV 元数据快速读取"
```

### Task 2: Integrate WAV routing and document the behavior

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/SmbAudioMetadataProbe.kt`
- Modify: `docs/MANUAL.md`

- [ ] **Step 1: Add a failing source-level routing regression test**

Add this test to `WavMetadataProbeCopierTest.kt`, resolving the production source from the repository root:

```kotlin
@Test
fun `SMB probe routes wav and wave extensions through fast copier`() {
    val source = File("src/main/java/com/github/gbandszxc/tvmediaplayer/playback/SmbAudioMetadataProbe.kt").readText()
    assertTrue(source.contains("\"wav\", \"wave\" -> WavMetadataProbeCopier.copy(input, output)"))
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run the focused test command from Task 1.

Expected: the new routing regression test fails because the WAV branch is absent.

- [ ] **Step 3: Add the WAV/WAVE fast-path branch**

In `copyFastMetadataProbe`, add the following branch without changing existing branches:

```kotlin
"wav", "wave" -> WavMetadataProbeCopier.copy(input, output)
```

Because `copy` returns `true` for valid RIFF/WAVE data, `load` will automatically run the existing full SMB copy when jaudiotagger returns no metadata. Invalid signatures return `false` only after preserving the full input, so no second download is required.

- [ ] **Step 4: Update project memory**

Append a concise entry to `docs/MANUAL.md` stating that WAV/WAVE now uses RIFF chunk-aware SMB metadata probing, skips bulk PCM data, supports ID3/APIC before or after `data`, and falls back to full-copy parsing when the fast result yields no metadata. Preserve all unrelated existing manual edits.

- [ ] **Step 5: Run focused and full unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.playback.WavMetadataProbeCopierTest"
.\gradlew.bat testDebugUnitTest
```

Expected: both commands exit 0 with no failed tests.

- [ ] **Step 6: Build both Debug APKs**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: exit 0 and these files exist:

```text
app/build/outputs/apk/debug/tsm-player-debug-armeabi-v7a-1.0.10.apk
app/build/outputs/apk/debug/tsm-player-debug-arm64-v8a-1.0.10.apk
```

- [ ] **Step 7: Commit Task 2**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/SmbAudioMetadataProbe.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/WavMetadataProbeCopierTest.kt docs/MANUAL.md
git commit -m "perf: 优化 WAV 封面回显"
```

### Task 3: Final verification and review

**Files:**
- Verify only; modify files only for review findings.

- [ ] **Step 1: Inspect the final diff and working tree ownership**

Run:

```powershell
git status --short
git diff HEAD~2 --check
git diff HEAD~2 -- app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback docs/MANUAL.md
```

Expected: no whitespace errors; no unrelated user-owned files are included in task commits.

- [ ] **Step 2: Re-run completion evidence**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`, all unit tests pass, and both ABI APKs remain present.

- [ ] **Step 3: Confirm requirements**

Check the implementation against `docs/superpowers/specs/2026-06-21-wav-smb-artwork-probe-design.md`: WAV/WAVE routing exists, APIC works before/after `data`, padding is tested, malformed signature is safe, full-copy fallback remains unchanged, documentation is updated, and no UI files changed.
