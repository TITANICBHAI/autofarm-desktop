package com.autofarm.engine

import com.autofarm.core.FlowConfig
import com.autofarm.core.Step
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists named flows to ~/.autofarm/flows/<name>.json
 * Each file contains a full FlowConfig (name, baseUrl, emailDomain, steps).
 */
object FlowLibrary {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }
    private val dir get() = File(System.getProperty("user.home") + "/.autofarm/flows").also { it.mkdirs() }

    /** Save or overwrite a flow. Returns the file it was saved to. */
    fun save(flow: FlowConfig): File {
        val safe = flow.name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim().take(80)
        val file = File(dir, "$safe.json")
        file.writeText(json.encodeToString(flow))
        return file
    }

    /** List all saved flow names (sorted alphabetically). */
    fun list(): List<String> =
        dir.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.nameWithoutExtension }
            ?.map { it.nameWithoutExtension } ?: emptyList()

    /** Load a flow by name. Returns null if not found. */
    fun load(name: String): FlowConfig? {
        val safe = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
        val file = File(dir, "$safe.json")
        return if (file.exists()) runCatching { json.decodeFromString<FlowConfig>(file.readText()) }.getOrNull() else null
    }

    /** Load from an arbitrary file (for import). */
    fun loadFile(file: File): FlowConfig? =
        runCatching { json.decodeFromString<FlowConfig>(file.readText()) }.getOrNull()

    /** Parse a steps-only JSON array into a FlowConfig (for pasting bare step arrays). */
    fun parseStepsJson(stepsJson: String): Result<List<Step>> = runCatching {
        json.decodeFromString<List<Step>>(stepsJson)
    }

    /** Export a flow to an arbitrary file. */
    fun export(flow: FlowConfig, file: File) {
        file.writeText(json.encodeToString(flow))
    }

    /** Delete a saved flow. */
    fun delete(name: String) {
        val safe = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
        File(dir, "$safe.json").delete()
    }

    /** Rename a saved flow. */
    fun rename(oldName: String, newName: String): Boolean {
        val flow = load(oldName) ?: return false
        save(flow.copy(name = newName))
        delete(oldName)
        return true
    }
}
