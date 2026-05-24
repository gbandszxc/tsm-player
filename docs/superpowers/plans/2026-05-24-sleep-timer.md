# Sleep Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android TV sleep timer that can be configured from playback, keeps counting while the playback service remains alive, stops playback, exits the app, and requests TV sleep/screen-off through device admin.

**Architecture:** Add a focused `sleep` package for timer state, permission control, app exit, and device sleep. Keep UI state in `SleepTimerActivity` and `PlaybackActivity`, while `PlaybackService` owns the background due check during playback. Persist target time in `SharedPreferences`, but do not use exact alarms or promise execution after process death.

**Tech Stack:** Kotlin, Android SDK minSdk 21, Media3 `MediaSessionService`, `DevicePolicyManager`, `SharedPreferences`, XML Android TV layouts, JUnit/Robolectric unit tests, Gradle wrapper on Windows PowerShell.

---

## File Structure

- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerState.kt`
  - Immutable state model plus minute rounding helper.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerStore.kt`
  - SharedPreferences persistence for enabled, target time, and duration.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerManager.kt`
  - Start/cancel/current/remaining/execute-if-due business logic.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepDeviceAdminReceiver.kt`
  - Android device admin receiver required by `DevicePolicyManager`.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepDeviceController.kt`
  - Device admin checks, authorization intent, and `lockNow()` wrapper.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepAppExitController.kt`
  - Activity registry and finish-all behavior.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SleepTimerActivity.kt`
  - TV-friendly timer setup page.
- Create `app/src/main/res/layout/activity_sleep_timer.xml`
  - Layout for presets, hour/minute steppers, remaining status, and actions.
- Create `app/src/main/res/xml/sleep_device_admin.xml`
  - Device admin policy metadata with `force-lock`.
- Create `app/src/main/res/drawable/ic_sleep_timer.xml`
  - Sleep icon for the compact playback button.
- Create `app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerManagerTest.kt`
  - Pure manager tests.
- Create `app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerStoreTest.kt`
  - Robolectric persistence tests.
- Modify `app/src/main/AndroidManifest.xml`
  - Register `SleepTimerActivity` and `SleepDeviceAdminReceiver`.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/MainActivity.kt`
  - First-launch device admin prompt.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackService.kt`
  - Periodic due check while service is alive.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BaseActivity.kt`
  - Register/unregister activities for app exit.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
  - Sleep button binding and minute refresh.
- Modify `app/src/main/res/layout/activity_playback.xml`
  - Add compact sleep button in bottom controls.
- Modify `app/src/main/res/values/dimens.xml`
  - Add sleep button expanded width.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt`
  - Add sleep permission item in app settings.
- Modify `DESIGN.md`
  - Document sleep timer UI, focus, and token rules.
- Modify `docs/MANUAL.md`
  - Document feature behavior and runtime boundary.

---

### Task 1: Sleep Timer Domain And Store

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerState.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerStore.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerManager.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerManagerTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerStoreTest.kt`

- [ ] **Step 1: Write the failing manager tests**

Create `app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerManagerTest.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerManagerTest {

    private class FakeStore : SleepTimerStoreContract {
        var state: SleepTimerState = SleepTimerState.Disabled
        override fun load(nowMs: Long): SleepTimerState = state
        override fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int) {
            state = SleepTimerState.Enabled(targetEpochMillis, durationMinutes)
        }
        override fun clear() {
            state = SleepTimerState.Disabled
        }
    }

    @Test
    fun `start stores target time and duration`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)

        manager.start(durationMinutes = 30, nowMs = 1_000L)

        assertEquals(SleepTimerState.Enabled(1_801_000L, 30), store.state)
    }

    @Test
    fun `remaining minutes rounds up while positive`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)
        store.state = SleepTimerState.Enabled(targetEpochMillis = 1_801_000L, durationMinutes = 30)

        assertEquals(30, manager.remainingMinutesCeil(nowMs = 1_000L))
        assertEquals(30, manager.remainingMinutesCeil(nowMs = 60_001L))
        assertEquals(29, manager.remainingMinutesCeil(nowMs = 61_000L))
    }

    @Test
    fun `remaining minutes returns null when disabled or expired`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)

        assertNull(manager.remainingMinutesCeil(nowMs = 1_000L))

        store.state = SleepTimerState.Enabled(targetEpochMillis = 2_000L, durationMinutes = 1)

        assertNull(manager.remainingMinutesCeil(nowMs = 2_000L))
        assertNull(manager.remainingMinutesCeil(nowMs = 3_000L))
    }

    @Test
    fun `execute if due clears once and calls action`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)
        var executed = false
        store.state = SleepTimerState.Enabled(targetEpochMillis = 2_000L, durationMinutes = 1)

        val first = manager.executeIfDue(nowMs = 2_000L) { executed = true }
        val second = manager.executeIfDue(nowMs = 2_001L) { error("should not run twice") }

        assertTrue(first)
        assertFalse(second)
        assertTrue(executed)
        assertEquals(SleepTimerState.Disabled, store.state)
    }

    @Test
    fun `cancel clears state`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)
        store.state = SleepTimerState.Enabled(targetEpochMillis = 2_000L, durationMinutes = 1)

        manager.cancel()

        assertEquals(SleepTimerState.Disabled, store.state)
    }
}
```

- [ ] **Step 2: Write the failing store tests**

Create `app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerStoreTest.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SleepTimerStoreTest {

    @Test
    fun `save and load enabled timer`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerStore(context)
        store.clear()

        store.saveEnabled(targetEpochMillis = 120_000L, durationMinutes = 2)

        assertEquals(SleepTimerState.Enabled(120_000L, 2), store.load(nowMs = 1_000L))
    }

    @Test
    fun `load clears expired timer`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerStore(context)
        store.clear()
        store.saveEnabled(targetEpochMillis = 120_000L, durationMinutes = 2)

        assertEquals(SleepTimerState.Disabled, store.load(nowMs = 120_000L))
        assertEquals(SleepTimerState.Disabled, store.load(nowMs = 121_000L))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SleepTimer*"
```

Expected: fail because `SleepTimerState`, `SleepTimerStoreContract`, `SleepTimerStore`, and `SleepTimerManager` do not exist.

- [ ] **Step 4: Implement the domain and store**

Create `SleepTimerState.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

sealed class SleepTimerState {
    data object Disabled : SleepTimerState()
    data class Enabled(
        val targetEpochMillis: Long,
        val durationMinutes: Int
    ) : SleepTimerState()
}
```

Create `SleepTimerStore.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import android.content.Context

interface SleepTimerStoreContract {
    fun load(nowMs: Long = System.currentTimeMillis()): SleepTimerState
    fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int)
    fun clear()
}

class SleepTimerStore(context: Context) : SleepTimerStoreContract {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun load(nowMs: Long): SleepTimerState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        if (!enabled) return SleepTimerState.Disabled
        val target = prefs.getLong(KEY_TARGET_EPOCH_MILLIS, 0L)
        val duration = prefs.getInt(KEY_DURATION_MINUTES, 0)
        if (target <= nowMs || duration <= 0) {
            clear()
            return SleepTimerState.Disabled
        }
        return SleepTimerState.Enabled(target, duration)
    }

    override fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_TARGET_EPOCH_MILLIS, targetEpochMillis)
            .putInt(KEY_DURATION_MINUTES, durationMinutes.coerceAtLeast(1))
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_TARGET_EPOCH_MILLIS)
            .remove(KEY_DURATION_MINUTES)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "sleep_timer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGET_EPOCH_MILLIS = "target_epoch_millis"
        private const val KEY_DURATION_MINUTES = "duration_minutes"
    }
}
```

Create `SleepTimerManager.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import kotlin.math.ceil

class SleepTimerManager(
    private val store: SleepTimerStoreContract
) {
    fun start(durationMinutes: Int, nowMs: Long = System.currentTimeMillis()): SleepTimerState.Enabled {
        val safeDuration = durationMinutes.coerceAtLeast(1)
        val target = nowMs + safeDuration * MILLIS_PER_MINUTE
        store.saveEnabled(targetEpochMillis = target, durationMinutes = safeDuration)
        return SleepTimerState.Enabled(target, safeDuration)
    }

    fun cancel() {
        store.clear()
    }

    fun currentState(nowMs: Long = System.currentTimeMillis()): SleepTimerState {
        return store.load(nowMs)
    }

    fun remainingMillis(nowMs: Long = System.currentTimeMillis()): Long? {
        val state = store.load(nowMs) as? SleepTimerState.Enabled ?: return null
        return (state.targetEpochMillis - nowMs).takeIf { it > 0L }
    }

    fun remainingMinutesCeil(nowMs: Long = System.currentTimeMillis()): Int? {
        val remaining = remainingMillis(nowMs) ?: return null
        return ceil(remaining / MILLIS_PER_MINUTE.toDouble()).toInt().coerceAtLeast(1)
    }

    fun executeIfDue(nowMs: Long = System.currentTimeMillis(), action: () -> Unit): Boolean {
        val state = store.load(Long.MIN_VALUE) as? SleepTimerState.Enabled ?: return false
        if (state.targetEpochMillis > nowMs) return false
        store.clear()
        action()
        return true
    }

    companion object {
        const val MILLIS_PER_MINUTE: Long = 60_000L
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SleepTimer*"
```

Expected: PASS for manager and store tests.

- [ ] **Step 6: Commit**

Run:

```powershell
git add -- app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep
git commit -m "feat: 增加睡眠定时器核心状态"
```

---

### Task 2: Device Admin And App Exit Infrastructure

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepDeviceAdminReceiver.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepDeviceController.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepAppExitController.kt`
- Create: `app/src/main/res/xml/sleep_device_admin.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BaseActivity.kt`

- [ ] **Step 1: Add device admin receiver and metadata**

Create `SleepDeviceAdminReceiver.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import android.app.admin.DeviceAdminReceiver

class SleepDeviceAdminReceiver : DeviceAdminReceiver()
```

Create `app/src/main/res/xml/sleep_device_admin.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
```

- [ ] **Step 2: Register receiver in the manifest**

Modify `app/src/main/AndroidManifest.xml` inside `<application>`:

```xml
<receiver
    android:name=".sleep.SleepDeviceAdminReceiver"
    android:description="@string/sleep_device_admin_description"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/sleep_device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="sleep_device_admin_description">用于睡眠定时结束时让电视进入睡眠或息屏。</string>
```

- [ ] **Step 3: Add device controller**

Create `SleepDeviceController.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class SleepDeviceController(private val context: Context) {
    private val appContext = context.applicationContext
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(appContext, SleepDeviceAdminReceiver::class.java)

    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    fun openDeviceAdminSettings(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "用于睡眠定时结束时让电视进入睡眠或息屏。"
            )
        }
        activity.startActivity(intent)
    }

    fun sleepNow(): Boolean {
        if (!isDeviceAdminActive()) return false
        return runCatching {
            devicePolicyManager.lockNow()
            true
        }.getOrDefault(false)
    }
}
```

- [ ] **Step 4: Add activity registry for app exit**

Create `SleepAppExitController.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.sleep

import android.app.Activity
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object SleepAppExitController {
    private val activities = CopyOnWriteArrayList<WeakReference<Activity>>()

    fun register(activity: Activity) {
        activities.removeAll { it.get() == null || it.get() === activity }
        activities.add(WeakReference(activity))
    }

    fun unregister(activity: Activity) {
        activities.removeAll { it.get() == null || it.get() === activity }
    }

    fun finishAll() {
        activities.mapNotNull { it.get() }
            .filterNot { it.isFinishing }
            .forEach { activity -> activity.runOnUiThread { activity.finish() } }
        activities.removeAll { it.get() == null || it.get()?.isFinishing == true }
    }
}
```

- [ ] **Step 5: Register BaseActivity instances**

Modify `BaseActivity.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.github.gbandszxc.tvmediaplayer.sleep.SleepAppExitController

abstract class BaseActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SleepAppExitController.register(this)
    }

    override fun onDestroy() {
        SleepAppExitController.unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyImmersiveFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiSettingsApplier.applyImmersiveFullscreen(this)
    }
}
```

- [ ] **Step 6: Build to verify manifest and resources**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: Debug APKs build successfully.

- [ ] **Step 7: Commit**

Run:

```powershell
git add -- app/src/main/AndroidManifest.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BaseActivity.kt app/src/main/res/xml/sleep_device_admin.xml app/src/main/res/values/strings.xml
git commit -m "feat: 增加睡眠设备管理能力"
```

---

### Task 3: Playback Service Due Execution

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep/SleepTimerManager.kt`

- [ ] **Step 1: Add a test seam for due execution**

Modify `SleepTimerManager.kt` so `executeIfDue` can load stored expired timers without using `load(now)` auto-clear semantics:

```kotlin
interface SleepTimerStoreContract {
    fun load(nowMs: Long = System.currentTimeMillis()): SleepTimerState
    fun loadRaw(): SleepTimerState
    fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int)
    fun clear()
}
```

Update `SleepTimerStore`:

```kotlin
override fun loadRaw(): SleepTimerState {
    val enabled = prefs.getBoolean(KEY_ENABLED, false)
    if (!enabled) return SleepTimerState.Disabled
    val target = prefs.getLong(KEY_TARGET_EPOCH_MILLIS, 0L)
    val duration = prefs.getInt(KEY_DURATION_MINUTES, 0)
    if (target <= 0L || duration <= 0) return SleepTimerState.Disabled
    return SleepTimerState.Enabled(target, duration)
}
```

Update `load(nowMs)` to call `loadRaw()` and clear expired state:

```kotlin
override fun load(nowMs: Long): SleepTimerState {
    val state = loadRaw()
    if (state is SleepTimerState.Enabled && state.targetEpochMillis <= nowMs) {
        clear()
        return SleepTimerState.Disabled
    }
    return state
}
```

Update `executeIfDue`:

```kotlin
fun executeIfDue(nowMs: Long = System.currentTimeMillis(), action: () -> Unit): Boolean {
    val state = store.loadRaw() as? SleepTimerState.Enabled ?: return false
    if (state.targetEpochMillis > nowMs) return false
    store.clear()
    action()
    return true
}
```

Update the fake store in `SleepTimerManagerTest`:

```kotlin
override fun loadRaw(): SleepTimerState = state
```

- [ ] **Step 2: Run sleep timer tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SleepTimer*"
```

Expected: PASS.

- [ ] **Step 3: Integrate due execution in PlaybackService**

Modify `PlaybackService.kt` imports:

```kotlin
import com.github.gbandszxc.tvmediaplayer.sleep.SleepAppExitController
import com.github.gbandszxc.tvmediaplayer.sleep.SleepDeviceController
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
```

Add fields:

```kotlin
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
private var sleepTimerJob: Job? = null
private lateinit var sleepTimerManager: SleepTimerManager
private lateinit var sleepDeviceController: SleepDeviceController
```

In `onCreate()` before building the player:

```kotlin
sleepTimerManager = SleepTimerManager(SleepTimerStore(this))
sleepDeviceController = SleepDeviceController(this)
startSleepTimerChecker()
```

Add helper methods:

```kotlin
private fun startSleepTimerChecker() {
    sleepTimerJob?.cancel()
    sleepTimerJob = serviceScope.launch {
        while (isActive) {
            sleepTimerManager.executeIfDue {
                executeSleepTimerAction()
            }
            delay(SLEEP_TIMER_CHECK_INTERVAL_MS)
        }
    }
}

private fun executeSleepTimerAction() {
    saveSnapshotFromPlayer()
    mediaSession?.player?.run {
        pause()
        stop()
    }
    SleepAppExitController.finishAll()
    sleepDeviceController.sleepNow()
}
```

In `onDestroy()` before `super.onDestroy()`:

```kotlin
sleepTimerJob?.cancel()
serviceScope.coroutineContext.cancelChildren()
```

Add companion constant:

```kotlin
companion object {
    private const val SLEEP_TIMER_CHECK_INTERVAL_MS = 30_000L
}
```

If `cancelChildren()` is used, add:

```kotlin
import kotlinx.coroutines.cancelChildren
```

- [ ] **Step 4: Build and run targeted tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SleepTimer*"
.\gradlew.bat assembleDebug
```

Expected: tests pass and Debug APKs build.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackService.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/sleep app/src/test/java/com/github/gbandszxc/tvmediaplayer/sleep
git commit -m "feat: 后台播放时执行睡眠倒计时"
```

---

### Task 4: Sleep Timer Activity UI

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SleepTimerActivity.kt`
- Create: `app/src/main/res/layout/activity_sleep_timer.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/dimens.xml`

- [ ] **Step 1: Create the XML layout**

Create `activity_sleep_timer.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ui_bg_app"
    android:orientation="vertical"
    android:padding="@dimen/ui_space_screen">

    <TextView
        style="@style/TsmTextPageTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="睡眠定时" />

    <TextView
        android:id="@+id/tv_sleep_status"
        style="@style/TsmTextSecondary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_3xl"
        android:text="当前未开启" />

    <TextView
        style="@style/TsmTextSectionTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_4xl"
        android:text="预设时间" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_lg"
        android:orientation="horizontal">

        <Button
            android:id="@+id/btn_sleep_15"
            style="@style/TsmButtonDark"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:minWidth="112dp"
            android:text="15 分钟"
            android:textAllCaps="false" />

        <Button
            android:id="@+id/btn_sleep_30"
            style="@style/TsmButtonDark"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ui_space_lg"
            android:minWidth="112dp"
            android:text="30 分钟"
            android:textAllCaps="false" />

        <Button
            android:id="@+id/btn_sleep_60"
            style="@style/TsmButtonDark"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ui_space_lg"
            android:minWidth="112dp"
            android:text="60 分钟"
            android:textAllCaps="false" />

        <Button
            android:id="@+id/btn_sleep_120"
            style="@style/TsmButtonDark"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ui_space_lg"
            android:minWidth="128dp"
            android:text="120 分钟"
            android:textAllCaps="false" />
    </LinearLayout>

    <TextView
        style="@style/TsmTextSectionTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_5xl"
        android:text="手动指定" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_lg"
        android:orientation="horizontal">

        <Button
            android:id="@+id/btn_sleep_hours"
            style="@style/TsmButtonPrimary"
            android:layout_width="@dimen/ui_sleep_stepper_width"
            android:layout_height="wrap_content"
            android:text="0 小时"
            android:textAllCaps="false" />

        <Button
            android:id="@+id/btn_sleep_minutes"
            style="@style/TsmButtonPrimary"
            android:layout_width="@dimen/ui_sleep_stepper_width"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ui_space_lg"
            android:text="30 分钟"
            android:textAllCaps="false" />
    </LinearLayout>

    <TextView
        style="@style/TsmTextSecondary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_lg"
        android:text="焦点停在小时或分钟时，按左右切换控件，按确认增加；长按确认快速增加。" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ui_space_5xl"
        android:orientation="horizontal">

        <Button
            android:id="@+id/btn_sleep_start"
            style="@style/TsmButtonSuccess"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:minWidth="132dp"
            android:text="开启睡眠"
            android:textAllCaps="false" />

        <Button
            android:id="@+id/btn_sleep_cancel"
            style="@style/TsmButtonWarning"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ui_space_lg"
            android:minWidth="132dp"
            android:text="关闭睡眠"
            android:textAllCaps="false" />

        <Button
            android:id="@+id/btn_sleep_back"
            style="@style/TsmButtonPrimary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/ui_space_lg"
            android:minWidth="100dp"
            android:text="返回"
            android:textAllCaps="false" />
    </LinearLayout>
</LinearLayout>
```

Add to `dimens.xml`:

```xml
<dimen name="ui_sleep_stepper_width">132dp</dimen>
```

- [ ] **Step 2: Implement SleepTimerActivity**

Create `SleepTimerActivity.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerState
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore

class SleepTimerActivity : BaseActivity() {
    private lateinit var manager: SleepTimerManager
    private lateinit var tvStatus: TextView
    private lateinit var btnHours: Button
    private lateinit var btnMinutes: Button
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button
    private var selectedHours = 0
    private var selectedMinutes = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_timer)
        UiSettingsApplier.applyAll(this)
        manager = SleepTimerManager(SleepTimerStore(this))
        bindViews()
        bindActions()
        render()
        findViewById<Button>(R.id.btn_sleep_30).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tv_sleep_status)
        btnHours = findViewById(R.id.btn_sleep_hours)
        btnMinutes = findViewById(R.id.btn_sleep_minutes)
        btnStart = findViewById(R.id.btn_sleep_start)
        btnCancel = findViewById(R.id.btn_sleep_cancel)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.btn_sleep_15).setOnClickListener { setDurationAndStart(15) }
        findViewById<Button>(R.id.btn_sleep_30).setOnClickListener { setDurationAndStart(30) }
        findViewById<Button>(R.id.btn_sleep_60).setOnClickListener { setDurationAndStart(60) }
        findViewById<Button>(R.id.btn_sleep_120).setOnClickListener { setDurationAndStart(120) }
        btnHours.setOnClickListener {
            selectedHours = (selectedHours + 1) % 24
            renderManualSelection()
        }
        btnMinutes.setOnClickListener {
            selectedMinutes = (selectedMinutes + 5) % 60
            renderManualSelection()
        }
        btnStart.setOnClickListener {
            val duration = selectedHours * 60 + selectedMinutes
            if (duration <= 0) {
                Toast.makeText(this, "请至少选择 1 分钟", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setDurationAndStart(duration)
        }
        btnCancel.setOnClickListener {
            manager.cancel()
            Toast.makeText(this, "已关闭睡眠定时", Toast.LENGTH_SHORT).show()
            render()
        }
        findViewById<Button>(R.id.btn_sleep_back).setOnClickListener { finish() }
    }

    private fun setDurationAndStart(durationMinutes: Int) {
        manager.start(durationMinutes)
        Toast.makeText(this, "将在 ${durationMinutes} 分钟后睡眠", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun render() {
        val remaining = manager.remainingMinutesCeil()
        val state = manager.currentState()
        tvStatus.text = when {
            remaining != null -> "当前已开启，剩余约 ${remaining} 分钟"
            state is SleepTimerState.Disabled -> "当前未开启"
            else -> "当前未开启"
        }
        btnStart.text = if (remaining != null) "更新睡眠" else "开启睡眠"
        btnCancel.isEnabled = remaining != null
        renderManualSelection()
    }

    private fun renderManualSelection() {
        btnHours.text = "${selectedHours} 小时"
        btnMinutes.text = "${selectedMinutes} 分钟"
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
```

- [ ] **Step 3: Register the Activity**

Add to `AndroidManifest.xml`:

```xml
<activity
    android:name=".ui.SleepTimerActivity"
    android:exported="false"
    android:screenOrientation="landscape" />
```

- [ ] **Step 4: Build**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: Debug APKs build successfully.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SleepTimerActivity.kt app/src/main/res/layout/activity_sleep_timer.xml app/src/main/res/values/dimens.xml app/src/main/AndroidManifest.xml
git commit -m "feat: 增加睡眠定时设置页"
```

---

### Task 5: Playback Button Integration

**Files:**
- Create: `app/src/main/res/drawable/ic_sleep_timer.xml`
- Modify: `app/src/main/res/layout/activity_playback.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
- Modify: `app/src/main/res/values/dimens.xml`

- [ ] **Step 1: Add sleep icon**

Create `ic_sleep_timer.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M20.3,14.4c-1.1,3.4 -4.3,5.9 -8.1,5.9c-4.7,0 -8.5,-3.8 -8.5,-8.5c0,-3.8 2.5,-7 5.9,-8.1c0.6,-0.2 1.1,0.4 0.8,1c-0.5,0.9 -0.8,2 -0.8,3.1c0,3.6 2.9,6.5 6.5,6.5c1.1,0 2.2,-0.3 3.1,-0.8c0.6,-0.3 1.2,0.3 1.1,0.9zM12.2,18.3c2.1,0 4,-0.9 5.2,-2.4c-0.4,0.1 -0.9,0.1 -1.3,0.1c-4.5,0 -8.2,-3.7 -8.2,-8.2c0,-0.4 0,-0.9 0.1,-1.3c-1.5,1.2 -2.4,3.1 -2.4,5.2c0,3.7 3,6.6 6.6,6.6z" />
</vector>
```

- [ ] **Step 2: Add playback layout button**

In `activity_playback.xml`, insert after `btn_play_mode` and before `btn_lyrics_fullscreen`:

```xml
<Button
    android:id="@+id/btn_sleep_timer"
    style="@style/TsmButtonSuccess"
    android:layout_width="@dimen/ui_playback_mode_button_collapsed_width"
    android:layout_height="wrap_content"
    android:layout_marginStart="@dimen/ui_space_lg"
    android:drawablePadding="@dimen/ui_space_sm"
    android:gravity="center"
    android:minWidth="@dimen/ui_playback_mode_button_collapsed_width"
    android:nextFocusUp="@id/pb_playback"
    android:text=""
    android:textAllCaps="false" />
```

Add to `dimens.xml`:

```xml
<dimen name="ui_playback_sleep_button_expanded_min_width">96dp</dimen>
```

- [ ] **Step 3: Bind and render the button**

Modify `PlaybackActivity.kt` imports:

```kotlin
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore
```

Add fields:

```kotlin
private lateinit var sleepTimerManager: SleepTimerManager
private lateinit var btnSleepTimer: Button
private var renderedSleepTimerLabel: String? = null
private var renderedSleepTimerFocused: Boolean? = null
```

In `onCreate()` after `configStore = ...`:

```kotlin
sleepTimerManager = SleepTimerManager(SleepTimerStore(applicationContext))
```

In `bindViews()`:

```kotlin
btnSleepTimer = findViewById(R.id.btn_sleep_timer)
renderSleepTimerButton()
```

In `bindActions()`:

```kotlin
btnSleepTimer.setOnClickListener {
    startActivity(Intent(this, SleepTimerActivity::class.java))
}
btnSleepTimer.setOnFocusChangeListener { _, _ -> renderSleepTimerButton(force = true) }
```

Add to `startProgressTicker()` loop after progress render:

```kotlin
renderSleepTimerButton()
```

Add a sleep-specific renderer. It keeps the icon centered when disabled, and shows icon plus minute number when enabled:

```kotlin
private fun renderSleepTimerButton(force: Boolean = false) {
    val remaining = sleepTimerManager.remainingMinutesCeil()
    val label = remaining?.toString().orEmpty()
    val focused = btnSleepTimer.hasFocus()
    if (!force && renderedSleepTimerLabel == label && renderedSleepTimerFocused == focused) return
    renderedSleepTimerLabel = label
    renderedSleepTimerFocused = focused

    btnSleepTimer.setBackgroundResource(R.drawable.bg_button_amber)
    btnSleepTimer.minWidth = resources.getDimensionPixelSize(
        if (remaining == null) R.dimen.ui_playback_mode_button_collapsed_width
        else R.dimen.ui_playback_sleep_button_expanded_min_width
    )
    val targetWidth = if (remaining == null) {
        resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_collapsed_width)
    } else {
        ViewGroup.LayoutParams.WRAP_CONTENT
    }
    val layoutParams = btnSleepTimer.layoutParams
    if (layoutParams.width != targetWidth) {
        layoutParams.width = targetWidth
        btnSleepTimer.layoutParams = layoutParams
    }

    btnSleepTimer.overlay.clear()
    val icon = ContextCompat.getDrawable(this, R.drawable.ic_sleep_timer)?.mutate()
    val wrapped = icon?.let(DrawableCompat::wrap)
    if (wrapped != null) {
        DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.ui_text_on_accent))
        wrapped.setBounds(0, 0, wrapped.intrinsicWidth, wrapped.intrinsicHeight)
    }

    if (remaining == null) {
        btnSleepTimer.text = ""
        btnSleepTimer.setCompoundDrawables(null, null, null, null)
        wrapped?.let { iconDrawable ->
            btnSleepTimer.post { drawCenteredButtonIcon(btnSleepTimer, iconDrawable) }
        }
    } else {
        btnSleepTimer.text = label
        btnSleepTimer.setCompoundDrawables(wrapped, null, null, null)
    }
    btnSleepTimer.contentDescription = if (remaining == null) "睡眠定时" else "睡眠定时，剩余 ${remaining} 分钟"
}
```

- [ ] **Step 4: Build and smoke test**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: Debug APKs build successfully and playback layout compiles.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- app/src/main/res/drawable/ic_sleep_timer.xml app/src/main/res/layout/activity_playback.xml app/src/main/res/values/dimens.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt
git commit -m "feat: 播放页增加睡眠定时入口"
```

---

### Task 6: Settings And First-Launch Authorization

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/MainActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/UiSettingsStore.kt`

- [ ] **Step 1: Add first prompt state**

Modify `UiSettingsStore.kt`:

```kotlin
private const val KEY_SLEEP_ADMIN_PROMPT_SHOWN = "sleep_admin_prompt_shown"

fun sleepAdminPromptShown(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_SLEEP_ADMIN_PROMPT_SHOWN, false)
}

fun setSleepAdminPromptShown(context: Context, shown: Boolean) {
    prefs(context).edit().putBoolean(KEY_SLEEP_ADMIN_PROMPT_SHOWN, shown).apply()
}
```

- [ ] **Step 2: Prompt on first MainActivity launch**

Modify `MainActivity.kt` imports:

```kotlin
import android.app.AlertDialog
import com.github.gbandszxc.tvmediaplayer.sleep.SleepDeviceController
import com.github.gbandszxc.tvmediaplayer.ui.UiSettingsStore
```

Add after `AppUpdateManager.maybeCheckOnAppStart(this)` in `onCreate()`:

```kotlin
maybePromptSleepDeviceAdmin()
```

Add method:

```kotlin
private fun maybePromptSleepDeviceAdmin() {
    val controller = SleepDeviceController(this)
    if (controller.isDeviceAdminActive()) return
    if (UiSettingsStore.sleepAdminPromptShown(this)) return
    UiSettingsStore.setSleepAdminPromptShown(this, true)
    AlertDialog.Builder(this)
        .setTitle("开启睡眠权限")
        .setMessage("授权后，睡眠定时结束时可以让电视进入睡眠或息屏。暂不授权也可以继续使用播放器，并可之后在设置页重新授权。")
        .setPositiveButton("去授权") { _, _ -> controller.openDeviceAdminSettings(this) }
        .setNegativeButton("暂不授权", null)
        .show()
}
```

- [ ] **Step 3: Add settings entry**

Modify `SettingsActivity.kt` imports:

```kotlin
import com.github.gbandszxc.tvmediaplayer.sleep.SleepDeviceController
```

In “应用设置” items list after “应用休眠保护”, add:

```kotlin
SettingsItem(
    title = "睡眠权限",
    descriptionProvider = { "用于睡眠定时结束时让电视进入睡眠或息屏；未授权时仍会停播并退出应用" },
    valueProvider = {
        if (SleepDeviceController(this).isDeviceAdminActive()) "已授权" else "未授权"
    }
) {
    SleepDeviceController(this).openDeviceAdminSettings(this)
}
```

In `onResume()`, existing `rebuildCurrentCategory(moveFocusToDetail = false)` refreshes the value after returning from system authorization.

- [ ] **Step 4: Build**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: Debug APKs build successfully.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- app/src/main/java/com/github/gbandszxc/tvmediaplayer/MainActivity.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/UiSettingsStore.kt
git commit -m "feat: 增加睡眠权限授权入口"
```

---

### Task 7: Documentation And Verification

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/MANUAL.md`

- [ ] **Step 1: Update DESIGN.md**

Add a “睡眠定时器” note under `Components` or playback page section:

```markdown
- 播放页按钮栏在“播放模式”和“歌词全屏”之间提供睡眠定时入口。未开启时按钮显示睡眠图标；开启后显示睡眠图标与剩余分钟数字，分钟向上取整，仅精确到分钟。
- 睡眠定时设置页使用独立横屏 Activity，提供 15 / 30 / 60 / 120 分钟预设和小时/分钟手动选择。焦点顺序为预设按钮、小时选择、分钟选择、开启/更新、关闭、返回。
- 设置页“应用设置”显示睡眠权限状态，并提供重新授权入口。文案统一使用“睡眠/息屏”，不使用“关机”表达。
```

- [ ] **Step 2: Update docs/MANUAL.md**

Add a new section:

```markdown
## 睡眠定时器

播放页提供睡眠定时按钮。未开启时显示睡眠图标；开启后显示睡眠图标与剩余分钟数字。点击按钮进入睡眠定时设置页，可选择 15、30、60、120 分钟预设，或手动选择小时和分钟。

睡眠定时依赖后台播放服务存活。只要播放器仍在后台播放，倒计时会继续，到点后保存当前播放快照、停止播放、退出应用，并通过设备管理权限请求电视睡眠或息屏。如果设备管理权限未授权，到点仍会停止播放并退出应用，但不能保证电视息屏。该功能不承诺真正断电关机，也不使用 Android 12+ 精确闹钟权限。
```

- [ ] **Step 3: Run full unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: all unit tests pass.

- [ ] **Step 4: Run Debug package build**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: both Debug APKs are produced:

```text
app\build\outputs\apk\debug\tsm-player-debug-armeabi-v7a-1.0.6.apk
app\build\outputs\apk\debug\tsm-player-debug-arm64-v8a-1.0.6.apk
```

- [ ] **Step 5: Inspect git diff**

Run:

```powershell
git status --short
git diff --check
```

Expected: `git diff --check` reports no whitespace errors.

- [ ] **Step 6: Commit**

Run:

```powershell
git add -- DESIGN.md docs/MANUAL.md
git commit -m "docs: 记录睡眠定时器行为"
```

---

## Self-Review Notes

- Spec coverage: first-launch authorization is covered in Task 6; settings authorization is covered in Task 6; playback compact button is covered in Task 5; standalone setup Activity is covered in Task 4; playback-service-lifetime execution is covered in Task 3; app exit and device sleep are covered in Tasks 2 and 3; docs are covered in Task 7.
- Placeholder scan: every task has concrete file paths, commands, and code snippets; no task leaves implementation details unnamed.
- Type consistency: `SleepTimerState`, `SleepTimerStoreContract`, `SleepTimerStore`, `SleepTimerManager`, `SleepDeviceController`, and `SleepAppExitController` are introduced before use by playback, settings, and UI tasks.
- Scope check: the feature is one coherent subsystem with UI, service, permission, and docs. It is suitable for one implementation plan with multiple commits.
