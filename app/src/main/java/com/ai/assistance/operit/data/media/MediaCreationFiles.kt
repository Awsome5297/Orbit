package com.ai.assistance.operit.data.media

import android.content.Context
import java.io.File

class MediaCreationFiles(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "media_creation")

    fun sessionDir(sessionId: String): File =
        File(root, sessionId).apply { mkdirs() }

    fun recordDir(sessionId: String, recordId: String): File =
        File(sessionDir(sessionId), recordId).apply { mkdirs() }

    fun deleteSession(sessionId: String) {
        File(root, sessionId).deleteRecursively()
    }

    fun deleteRecord(sessionId: String, recordId: String) {
        File(sessionDir(sessionId), recordId).deleteRecursively()
    }
}
