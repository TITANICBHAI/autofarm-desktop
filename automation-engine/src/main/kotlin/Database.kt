package com.autofarm.engine

import com.autofarm.core.RunResult
import com.autofarm.core.StepResult
import com.autofarm.core.StepStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object RunsTable : Table("runs") {
    val id = long("id").autoIncrement()
    val generatedEmail = varchar("generated_email", 255)
    val startedAt = long("started_at")
    val finishedAt = long("finished_at").nullable()
    val overallStatus = varchar("overall_status", 20)
    val stepResultsJson = text("step_results_json")
    val errorMessage = text("error_message").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AppDatabase {
    private var initialized = false

    fun init(dbPath: String = System.getProperty("user.home") + File.separator + ".autofarm" + File.separator + "runs.db") {
        if (initialized) return
        File(dbPath).parentFile?.mkdirs()
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(RunsTable)
        }
        initialized = true
    }

    fun insertRun(run: RunResult): Long {
        return transaction {
            RunsTable.insertAndGetId {
                it[generatedEmail] = run.generatedEmail
                it[startedAt] = run.startedAt
                it[finishedAt] = run.finishedAt
                it[overallStatus] = run.overallStatus.name
                it[stepResultsJson] = Json.encodeToString(run.stepResults.map { sr -> sr.toSerializable() })
                it[errorMessage] = run.errorMessage
            }.value
        }
    }

    fun updateRun(id: Long, run: RunResult) {
        transaction {
            RunsTable.update({ RunsTable.id eq id }) {
                it[finishedAt] = run.finishedAt
                it[overallStatus] = run.overallStatus.name
                it[stepResultsJson] = Json.encodeToString(run.stepResults.map { sr -> sr.toSerializable() })
                it[errorMessage] = run.errorMessage
            }
        }
    }

    fun getAllRuns(): List<StoredRun> {
        return transaction {
            RunsTable.selectAll().orderBy(RunsTable.startedAt, SortOrder.DESC).map {
                StoredRun(
                    id = it[RunsTable.id],
                    generatedEmail = it[RunsTable.generatedEmail],
                    startedAt = it[RunsTable.startedAt],
                    finishedAt = it[RunsTable.finishedAt],
                    overallStatus = it[RunsTable.overallStatus],
                    stepResultsJson = it[RunsTable.stepResultsJson],
                    errorMessage = it[RunsTable.errorMessage]
                )
            }
        }
    }

    fun clearAllRuns() {
        transaction { RunsTable.deleteAll() }
    }
}

data class StoredRun(
    val id: Long,
    val generatedEmail: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val overallStatus: String,
    val stepResultsJson: String,
    val errorMessage: String?
)

@kotlinx.serialization.Serializable
data class SerializableStepResult(
    val stepDescription: String,
    val status: String,
    val message: String,
    val durationMs: Long,
    val screenshotPath: String? = null
)

fun StepResult.toSerializable() = SerializableStepResult(
    stepDescription = step.description ?: step.type.name,
    status = status.name,
    message = message,
    durationMs = durationMs,
    screenshotPath = screenshotPath
)
