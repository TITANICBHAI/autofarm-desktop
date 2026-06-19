package com.autofarm.mail

import com.autofarm.core.MailConfig
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.RecipientStringTerm
import jakarta.mail.search.RecipientType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Properties

class ImapClient(private val config: MailConfig) {

    private fun createSession(): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", config.imapHost)
            put("mail.imaps.port", config.imapPort.toString())
            put("mail.imaps.ssl.enable", config.useSSL.toString())
            put("mail.imaps.timeout", "10000")
            put("mail.imaps.connectiontimeout", "10000")
        }
        return Session.getInstance(props)
    }

    suspend fun pollForOtp(
        recipientEmail: String,
        otpRegex: String = config.otpRegex,
        timeoutSec: Int = 60,
        pollIntervalMs: Long = 3000
    ): String? = withContext(Dispatchers.IO) {
        val regex = Regex(otpRegex)
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() < deadline) {
            try {
                val session = createSession()
                val store = session.getStore("imaps")
                store.connect(config.imapHost, config.imapUser, config.imapPassword)
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_WRITE)

                val terms = RecipientStringTerm(RecipientType.TO, recipientEmail)
                val messages = inbox.search(terms)

                for (msg in messages.sortedByDescending { it.sentDate?.time ?: 0L }) {
                    val receivedAt = msg.receivedDate?.time ?: msg.sentDate?.time ?: 0L
                    if (receivedAt >= startTime - 5000) {
                        val body = extractBody(msg)
                        val match = regex.find(body)
                        if (match != null) {
                            val otp = match.groupValues[1].ifEmpty { match.value }
                            msg.setFlag(Flags.Flag.DELETED, true)
                            inbox.close(true)
                            store.close()
                            return@withContext otp
                        }
                    }
                }

                inbox.close(false)
                store.close()
            } catch (e: Exception) {
                // ignore transient IMAP errors, keep polling
            }

            delay(pollIntervalMs)
        }
        null
    }

    private fun extractBody(message: Message): String {
        return when {
            message.isMimeType("text/plain") -> message.content.toString()
            message.isMimeType("multipart/*") -> {
                val mp = message.content as Multipart
                buildString {
                    for (i in 0 until mp.count) {
                        val bp = mp.getBodyPart(i)
                        if (bp.isMimeType("text/plain") || bp.isMimeType("text/html")) {
                            append(bp.content.toString())
                        }
                    }
                }
            }
            else -> message.content.toString()
        }
    }

    fun testConnection(): Result<String> {
        return try {
            val session = createSession()
            val store = session.getStore("imaps")
            store.connect(config.imapHost, config.imapUser, config.imapPassword)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val count = inbox.messageCount
            inbox.close(false)
            store.close()
            Result.success("Connected. $count messages in inbox.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
