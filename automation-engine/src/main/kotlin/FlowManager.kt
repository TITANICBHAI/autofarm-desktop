package com.autofarm.engine

import com.autofarm.core.*
import com.autofarm.mail.ImapClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.util.Collections

data class LoopProgress(
    val iteration: Int,
    val total: Int,
    val result: RunResult? = null
)

class FlowManager(
    private val config: AppConfig,
    private val mailConfig: MailConfig
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    // Thread-safe list — multiple coroutines can add/remove jobs concurrently
    private val activeJobs: MutableList<Job> = Collections.synchronizedList(mutableListOf())

    fun parseSteps(jsonText: String): Result<List<Step>> = runCatching {
        json.decodeFromString<List<Step>>(jsonText)
    }.recoverCatching {
        throw Exception("JSON parse error: ${it.message}")
    }

    /**
     * Start a run — supports:
     *  • loopCount > 1: runs the flow N times sequentially (with loopDelayMs gap)
     *  • config.concurrency > 1: runs N parallel instances simultaneously
     *  • onInputRequired: called when a PAUSE_FOR_INPUT step is hit; suspend until user provides value
     *  • onLoopProgress: called after each loop iteration with iteration number + result
     */
    fun startRun(
        flow: FlowConfig,
        scope: CoroutineScope,
        onStepUpdate: (Int, StepResult) -> Unit = { _, _ -> },
        onRunComplete: (RunResult) -> Unit = {},
        onLoopProgress: (LoopProgress) -> Unit = {},
        onInputRequired: suspend (prompt: String) -> String = { "" }
    ): Job {
        AppDatabase.init()

        val job = scope.launch(Dispatchers.IO) {
            val loopCount = flow.loopCount.coerceAtLeast(1)
            val concurrency = config.concurrency.coerceAtLeast(1)

            if (concurrency == 1) {
                // Sequential loops
                repeat(loopCount) { iteration ->
                    if (!isActive) return@repeat
                    val result = runSingle(flow, onStepUpdate, onInputRequired)
                    onRunComplete(result)
                    onLoopProgress(LoopProgress(iteration + 1, loopCount, result))
                    if (iteration < loopCount - 1) delay(flow.loopDelayMs)
                }
            } else {
                // Parallel: launch `concurrency` instances at a time, wait, repeat
                val totalRuns = loopCount * concurrency
                var completed = 0
                for (batch in 0 until loopCount) {
                    if (!isActive) break
                    val batchJobs = (1..concurrency).map { idx ->
                        async {
                            runSingle(
                                flow,
                                // Only forward step updates from instance 1 to avoid UI race
                                onStepUpdate = if (idx == 1) onStepUpdate else { _, _ -> },
                                onInputRequired = onInputRequired
                            )
                        }
                    }
                    val results = batchJobs.awaitAll()
                    results.forEach { result ->
                        completed++
                        onRunComplete(result)
                        onLoopProgress(LoopProgress(completed, totalRuns, result))
                    }
                    if (batch < loopCount - 1) delay(flow.loopDelayMs)
                }
            }
        }

        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
        return job
    }

    private suspend fun runSingle(
        flow: FlowConfig,
        onStepUpdate: (Int, StepResult) -> Unit,
        onInputRequired: suspend (String) -> String
    ): RunResult {
        val mailClient = ImapClient(mailConfig)
        val runner = StepRunner(config, mailClient, onStepUpdate, onInputRequired)
        val email = generateEmail(flow.emailDomain)
        val run = RunResult(generatedEmail = email)
        val dbId = AppDatabase.insertRun(run)
        val result = runner.runFlow(flow, email)
        val final = result.copy(id = dbId)
        AppDatabase.updateRun(dbId, final)
        return final
    }

    fun cancelAll() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun loadRunHistory(): List<StoredRun> {
        AppDatabase.init(); return AppDatabase.getAllRuns()
    }

    fun clearHistory() {
        AppDatabase.init(); AppDatabase.clearAllRuns()
    }
}
