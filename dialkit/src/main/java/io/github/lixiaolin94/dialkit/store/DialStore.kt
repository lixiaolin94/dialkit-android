package io.github.lixiaolin94.dialkit.store

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import io.github.lixiaolin94.dialkit.R
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import io.github.lixiaolin94.dialkit.DialCell
import io.github.lixiaolin94.dialkit.DialCells
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialPersist
import io.github.lixiaolin94.dialkit.OpenHook
import io.github.lixiaolin94.dialkit.overlay.OverlayController
import io.github.lixiaolin94.dialkit.overlay.OverlayMode
import io.github.lixiaolin94.dialkit.extractImportJson
import io.github.lixiaolin94.dialkit.kotlinHints
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.buildCopyInstruction
import io.github.lixiaolin94.dialkit.model.toDialValues
import io.github.lixiaolin94.dialkit.model.toJsonObject
import io.github.lixiaolin94.dialkit.persistedValues
import io.github.lixiaolin94.dialkit.resetToDefault
import io.github.lixiaolin94.dialkit.resolveImport
import io.github.lixiaolin94.dialkit.restore
import io.github.lixiaolin94.dialkit.valueCells
import io.github.lixiaolin94.dialkit.walk

/** Where an import came from, for its messages. */
internal enum class ImportSource(@StringRes val label: Int?) {
    Clipboard(R.string.dialkit_source_clipboard),
    File(R.string.dialkit_source_file),
    Adb(R.string.dialkit_source_adb),
    NoPicker(null),
}

/** A saved version. SOURCE DialStore.ts `Preset { id, name, values }`. */
internal data class DialPreset(val id: String, val name: String, val values: Map<String, DialValue>)

/** A registered group as shown in the overlay. One panel per id; mounts with the same id share it. */
internal class DialPanel(
    val id: String,
    val name: String,
    val group: DialGroup,
    defaultOpen: Boolean,
    /** null: in-process only. SOURCE DialStore.normalizePersistConfig: key defaults to `dialkit:<id>`. */
    persist: DialPersist?,
    /** Declared defaults (with `__mode` entries), captured before anything was restored. */
    val defaults: Map<String, DialValue>,
) {
    var mounts = 0
    var onAction: ((String) -> Unit)? = null
    val persistKey: String? = persist?.let { it.key ?: "dialkit:$id" }
    val persistPresets: Boolean = persist?.presets ?: true

    /** Section open state when several panels are listed under the "DialKit" root. */
    val open = DialCell(defaultOpen)

    val presets = DialCell<List<DialPreset>>(emptyList())
    val activeId = DialCell<String?>(null)
    var activePresetId: String?
        get() = activeId.value
        set(v) {
            activeId.value = v
        }

    /** "Version 1": what edits write into while no saved version is active. */
    var baseValues: Map<String, DialValue> = defaults

    val activePreset: DialPreset? get() = presets.value.firstOrNull { it.id == activePresetId }
}

private class RetainedState(
    val values: Map<String, DialValue>,
    val baseValues: Map<String, DialValue>,
    val presets: List<DialPreset>,
    val activePresetId: String?,
)

/**
 * Process-wide registry. Main thread only. Refcounted per id like DialKit's DialStore.
 *
 * DESIGN (deviates from DialKit): values are always kept in-process after the last unmount, keyed by id.
 * DialKit drops them unless an explicit id is given; on Android that would reset every dial on rotation,
 * dark-mode switches and other configuration changes.
 *
 * Every write to a panel's snapshot state — from the overlay, host code or a preset load — is picked up by
 * one apply observer and auto-saved into the active version (or the base values), then persisted, as
 * DialKit's `updateValue` does.
 */
internal object DialStore {
    private const val TAG = "DialKit"
    private const val PREFS = "io.github.lixiaolin94.dialkit.values"

    val panels = DialCell<List<DialPanel>>(emptyList())
    private val retained = HashMap<String, RetainedState>()
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val main = Handler(Looper.getMainLooper())
    private val dirty = LinkedHashSet<DialPanel>()
    private var syncScheduled = false

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun <G : DialGroup> acquire(
        id: String,
        name: String,
        defaultCollapsed: Boolean,
        persist: DialPersist?,
        factory: () -> G,
    ): DialPanel {
        panels.value.firstOrNull { it.id == id }?.let { existing ->
            existing.mounts++
            return existing
        }
        val group = factory()
        group.assignPaths("")
        val panel = DialPanel(id, name, group, !defaultCollapsed, persist, group.persistedValues())
        group.resetHook = { reset(panel) }
        group.openHook = object : OpenHook {
            override fun isOpen() = if (panels.peek.size <= 1) OverlayController.mode == OverlayMode.Expanded else panel.open.value

            // SOURCE DialRoot subscribePanelOpen: the only panel opens / closes the shell; with several, opening
            // a section also opens the shell.
            override fun setOpen(open: Boolean) {
                panel.open.value = open
                if (panels.peek.size <= 1) {
                    if (open) OverlayController.reveal() else OverlayController.collapse()
                } else if (open) {
                    OverlayController.reveal()
                }
            }
        }

        // SOURCE DialStore.registerPanel: in-memory values win over persisted ones.
        val memory = retained.remove(id)
        val stored = if (memory == null) readPersisted(panel) else null
        (memory ?: stored)?.let { state ->
            panel.baseValues = state.baseValues
            panel.presets.value = state.presets
            panel.activePresetId = state.activePresetId?.takeIf { pid -> state.presets.any { it.id == pid } }
            group.restore(state.values)
        }
        panel.mounts = 1
        group.valueCells().forEach { cell -> DialCells.own(cell) { markDirty(panel) } }
        panels.value = panels.value + panel
        return panel
    }

    fun release(panel: DialPanel) {
        panel.mounts--
        if (panel.mounts > 0) return
        flush(panel)
        panels.value = panels.value - panel
        panel.group.openHook = null
        panel.group.valueCells().forEach(DialCells::disown)
        retained[panel.id] = RetainedState(panel.group.persistedValues(), panel.baseValues, panel.presets.value, panel.activePresetId)
    }

    fun dispatchAction(panel: DialPanel, path: String) {
        panel.onAction?.invoke(path)
    }

    // ---- versions (SOURCE DialStore.ts:saveNewPreset/loadPreset/deletePreset/clearActivePreset/resetValues)

    fun saveNewPreset(panel: DialPanel) {
        flush(panel)
        val highest = panel.presets.value.fold(1) { max, p ->
            Regex("^Version (\\d+)$").matchEntire(p.name)?.groupValues?.get(1)?.toInt()?.coerceAtLeast(max) ?: max
        }
        val id = "preset-${System.currentTimeMillis()}-${randomSuffix()}"
        panel.presets.value = panel.presets.value + DialPreset(id, "Version ${highest + 1}", panel.group.persistedValues())
        panel.activePresetId = id
        persist(panel)
    }

    fun loadPreset(panel: DialPanel, presetId: String) {
        val preset = panel.presets.value.firstOrNull { it.id == presetId } ?: return
        flush(panel)
        panel.activePresetId = presetId
        panel.group.restore(preset.values)
        persist(panel)
    }

    /** "Version 1": leave the active version and show the base values. */
    fun clearActivePreset(panel: DialPanel) {
        flush(panel)
        panel.activePresetId = null
        panel.group.restore(panel.baseValues)
        persist(panel)
    }

    fun deletePreset(panel: DialPanel, presetId: String) {
        panel.presets.value = panel.presets.value.filter { it.id != presetId }
        if (panel.activePresetId == presetId) panel.activePresetId = null
        persist(panel)
    }

    fun reset(panel: DialPanel) {
        panel.group.walk { it.resetToDefault() }
        panel.baseValues = panel.defaults
        panel.activePresetId = null
        persist(panel)
    }

    // ---- Copy

    private fun exportText(panel: DialPanel): String {
        flush(panel)
        return buildCopyInstruction(
            panel.group::class.simpleName ?: "DialGroup",
            panel.name,
            panel.group.persistedValues(),
            panel.group.kotlinHints(),
        )
    }

    /** logcat caps a line near 4 KB; chunk by lines so `adb logcat -s DialKit` gets all of it. */
    private fun log(text: String) = text.lines().chunked(40).forEach { Log.i(TAG, it.joinToString("\n")) }

    fun copy(panel: DialPanel): Boolean {
        val text = exportText(panel)
        log(text)
        val clipboard = appContext?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        return runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("DialKit", text)) }
            .onFailure { Log.w(TAG, "Clipboard write failed; the export is in logcat above", it) }
            .isSuccess
    }

    /**
     * DESIGN (DialKit has none): `adb shell am broadcast … --es cmd dump [--es id <panel id>]` writes Copy's text for
     * that panel, or every registered panel, to logcat between marker lines and leaves the clipboard alone, so a
     * script or coding agent can read the tuned values with `adb logcat -d -v raw -s DialKit`. Returns the count.
     */
    fun dump(panelId: String?): Int {
        val targets = panels.peek.filter { panelId == null || it.id == panelId }
        if (targets.isEmpty()) {
            Log.w(TAG, if (panelId == null) "dump: no panel is registered" else "dump: no panel with id \"$panelId\"")
            return 0
        }
        targets.forEach { panel ->
            Log.i(TAG, "----- DialKit dump: ${panel.id} -----")
            log(exportText(panel))
            Log.i(TAG, "----- end of DialKit dump: ${panel.id} -----")
        }
        return targets.size
    }

    // ---- Import (DESIGN; DialKit has none): the inverse of Copy.

    /** Told (panel id, success) after every import, whichever path started it, for the toolbar's feedback. */
    val importResults = ArrayList<(String, Boolean) -> Unit>()

    /** Clipboard text for the Import menu, or null when there is none. */
    fun clipboardText(): String? {
        val context = appContext ?: return null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return runCatching { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Applies DialKit JSON (see extractImportJson / resolveImport) to the panel [panelId] (null: the first one).
     * DESIGN: the imported values land in a new version, so whatever was on screen stays one tap away in the
     * version menu. Reports the outcome as a Toast and in logcat; returns the number of controls set.
     */
    fun importText(panelId: String?, text: String?, source: ImportSource): Int {
        val context = appContext ?: return 0
        val panel = panels.peek.firstOrNull { panelId == null || it.id == panelId }
        val from = source.label?.let(context::getString).orEmpty()
        var count = 0
        val message = when {
            panel == null -> context.getString(R.string.dialkit_import_no_panel)
            source == ImportSource.NoPicker -> context.getString(R.string.dialkit_import_no_picker)
            text.isNullOrBlank() -> context.getString(R.string.dialkit_import_empty, from)
            else -> {
                val json = extractImportJson(text)
                val result = json?.let { panel.group.resolveImport(it) }
                if (result == null || result.count == 0) {
                    result?.ignored?.takeIf { it.isNotEmpty() }?.let { Log.i(TAG, "Import ignored: ${it.joinToString()}") }
                    if (json == null) {
                        context.getString(R.string.dialkit_import_no_json, from)
                    } else {
                        context.getString(R.string.dialkit_import_no_match, from, panel.name)
                    }
                } else {
                    saveNewPreset(panel)
                    panel.group.restore(result.values)
                    flush(panel)
                    count = result.count
                    val version = panel.activePreset?.name ?: context.getString(R.string.dialkit_version_base)
                    val done = context.resources.getQuantityString(R.plurals.dialkit_import_done, count, count, version)
                    if (result.ignored.isNotEmpty()) Log.i(TAG, "Import ignored: ${result.ignored.joinToString()}")
                    if (result.ignored.isEmpty()) done else context.resources.getQuantityString(R.plurals.dialkit_import_ignored, result.ignored.size, done, result.ignored.size)
                }
            }
        }
        if (count == 0) Log.w(TAG, message) else Log.i(TAG, "$message (from ${source.name})")
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        panel?.let { p -> importResults.toList().forEach { it(p.id, count > 0) } }
        return count
    }

    // ---- auto-save: every value cell of a registered panel reports here (DialCells), from any writer.

    private fun markDirty(panel: DialPanel) {
        dirty += panel
        if (!syncScheduled) {
            syncScheduled = true
            main.post { syncDirty() }
        }
    }

    private fun syncDirty() {
        syncScheduled = false
        val batch = dirty.toList()
        dirty.clear()
        batch.forEach { if (it in panels.peek) sync(it) }
    }

    /** Writes pending state into the active version / base values right away (before preset operations). */
    private fun flush(panel: DialPanel) {
        if (dirty.remove(panel)) sync(panel)
    }

    private fun sync(panel: DialPanel) {
        val values = panel.group.persistedValues()
        val active = panel.activePreset
        if (active != null) {
            if (active.values != values) {
                panel.presets.value = panel.presets.value.map { if (it.id == active.id) active.copy(values = values) else it }
            }
        } else {
            panel.baseValues = values
        }
        persistSoon(panel)
    }

    private val pendingPersist = LinkedHashSet<DialPanel>()
    private val persistPending = Runnable {
        val batch = pendingPersist.toList()
        pendingPersist.clear()
        batch.forEach(::persist)
    }

    /** DESIGN: drags write every frame; coalesce disk writes to 250 ms of quiet. */
    private fun persistSoon(panel: DialPanel) {
        if (panel.persistKey == null) return
        pendingPersist += panel
        main.removeCallbacks(persistPending)
        main.postDelayed(persistPending, 250)
    }

    // ---- persistence: SOURCE DialStore.ts:persistPanel shape `{ version: 1, values, baseValues, presets, activePresetId }`;
    // with `presets: false` only `{ version: 1, values }` (SOURCE persistPanel / loadPersistedPanel).

    private fun persist(panel: DialPanel) {
        pendingPersist.remove(panel)
        val key = panel.persistKey ?: return
        val p = prefs ?: return
        val state = JSONObject()
            .put("version", 1)
            .put("values", panel.group.persistedValues().toJsonObject())
        if (panel.persistPresets) {
            state.put("baseValues", panel.baseValues.toJsonObject())
                .put("presets", JSONArray(panel.presets.value.map { JSONObject().put("id", it.id).put("name", it.name).put("values", it.values.toJsonObject()) }))
                .put("activePresetId", panel.activePresetId ?: JSONObject.NULL)
        }
        p.edit().putString(key, state.toString()).apply()
    }

    private fun readPersisted(panel: DialPanel): RetainedState? {
        val key = panel.persistKey ?: return null
        val raw = prefs?.getString(key, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            if (o.optInt("version") != 1) return null
            val values = o.getJSONObject("values").toDialValues()
            val presets = if (!panel.persistPresets) emptyList() else o.optJSONArray("presets")?.let { arr ->
                List(arr.length()) { i ->
                    arr.getJSONObject(i).let { DialPreset(it.getString("id"), it.getString("name"), it.getJSONObject("values").toDialValues()) }
                }
            }.orEmpty()
            RetainedState(
                values,
                o.optJSONObject("baseValues")?.takeIf { panel.persistPresets }?.toDialValues() ?: values,
                presets,
                if (o.isNull("activePresetId")) null else o.optString("activePresetId"),
            )
        }.onFailure { Log.w(TAG, "Ignoring unreadable persisted state for $key", it) }.getOrNull()
    }

    private fun randomSuffix(): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        return String(CharArray(7) { alphabet[Random.nextInt(alphabet.length)] }) // SOURCE: Math.random().toString(36).slice(2, 9)
    }
}
