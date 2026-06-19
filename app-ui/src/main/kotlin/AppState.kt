package com.autofarm

import androidx.compose.runtime.*
import com.autofarm.core.AppConfig
import com.autofarm.core.MailConfig
import com.autofarm.engine.FlowManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object AppPrefs {
    private val configDir = System.getProperty("user.home") + File.separator + ".autofarm"
    private val configFile = File("$configDir${File.separator}config.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class PersistedConfig(
        val app: AppConfig = AppConfig(),
        val mail: MailConfig = MailConfig()
    )

    fun load(): Pair<AppConfig, MailConfig> {
        return try {
            if (configFile.exists()) {
                val persisted = json.decodeFromString<PersistedConfig>(configFile.readText())
                persisted.app to persisted.mail
            } else {
                AppConfig() to MailConfig()
            }
        } catch (e: Exception) {
            AppConfig() to MailConfig()
        }
    }

    fun save(appConfig: AppConfig, mailConfig: MailConfig) {
        try {
            File(configDir).mkdirs()
            configFile.writeText(json.encodeToString(PersistedConfig(appConfig, mailConfig)))
        } catch (_: Exception) {}
    }
}
