package com.imad.planner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(val playing: Boolean = false, val page: Int? = null, val index: Int = 0, val count: Int = 0, val missing: List<Int> = emptyList(), val finished: Boolean = false)

/** Plays the memorized pages for today's review from the user's folder (one audio file per mushaf page). */
object QuranPlayer {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    internal fun update(s: PlayerState) { _state.value = s }

    private val audioExt = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr", "3gp")

    /** Page number from a file name: the last group of digits (001.mp3, page 12.m4a, ص 45.mp3). */
    fun pageOf(name: String): Int? {
        val base = name.substringBeforeLast('.')
        val n = Regex("\\d+").findAll(digits(base)).lastOrNull()?.value?.toIntOrNull() ?: return null
        return n.takeIf { it in 1..MUSHAF_PAGES }
    }

    /** Maps page number to file in the chosen folder (and one level of sub-folders). */
    fun pageFiles(context: Context, tree: Uri): Map<Int, Uri> {
        val out = mutableMapOf<Int, Uri>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 2) walk(id, depth + 1)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExt) {
                        val page = pageOf(name) ?: continue
                        out.putIfAbsent(page, DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }

    fun play(context: Context, tree: Uri, pages: List<Int>): PlayerState {
        val files = runCatching { pageFiles(context, tree) }.getOrDefault(emptyMap())
        val found = pages.filter { it in files }
        val missing = pages.filter { it !in files }
        if (found.isEmpty()) {
            val s = PlayerState(missing = missing); update(s); return s
        }
        val intent = Intent(context, PlayerService::class.java).setAction(PlayerService.PLAY)
            .putExtra("pages", found.toIntArray())
            .putExtra("uris", found.map { files.getValue(it).toString() }.toTypedArray())
            .putExtra("missing", missing.toIntArray())
        ContextCompat.startForegroundService(context, intent)
        return PlayerState(true, found.first(), 0, found.size, missing)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, PlayerService::class.java).setAction(PlayerService.STOP))
    }

    fun next(context: Context) {
        context.startService(Intent(context, PlayerService::class.java).setAction(PlayerService.NEXT))
    }
}

class PlayerService : Service() {
    private var player: MediaPlayer? = null
    private var pages = IntArray(0)
    private var uris = arrayOf<String>()
    private var missing = IntArray(0)
    private var index = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                pages = intent.getIntArrayExtra("pages") ?: IntArray(0)
                uris = intent.getStringArrayExtra("uris") ?: arrayOf()
                missing = intent.getIntArrayExtra("missing") ?: IntArray(0)
                index = 0
                foreground()
                playCurrent()
            }
            NEXT -> { index++; playCurrent() }
            STOP -> finish(false)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "مراجعة الحفظ", NotificationManager.IMPORTANCE_LOW))
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, ID, notification(), type)
    }

    private fun notification(): Notification {
        val page = pages.getOrNull(index)
        val stop = PendingIntent.getService(this, 7, Intent(this, PlayerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val next = PendingIntent.getService(this, 8, Intent(this, PlayerService::class.java).setAction(NEXT), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("مراجعة سماع")
            .setContentText(if (page != null) "صفحة $page · ${index + 1} من ${pages.size}" else "")
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(Alarms.openApp(this))
            .addAction(0, "التالي", next)
            .addAction(0, "إيقاف", stop)
            .build()
    }

    private fun playCurrent() {
        player?.release(); player = null
        if (index >= pages.size) { finish(true); return }
        QuranPlayer.update(PlayerState(true, pages[index], index, pages.size, missing.toList()))
        getSystemService(NotificationManager::class.java).notify(ID, notification())
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, Uri.parse(uris[index]))
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { index++; playCurrent() }
            mp.setOnErrorListener { _, _, _ -> index++; playCurrent(); true }
            mp.prepareAsync()
        } catch (_: Exception) {
            index++; playCurrent()
        }
    }

    private fun finish(done: Boolean) {
        player?.release(); player = null
        QuranPlayer.update(PlayerState(playing = false, missing = missing.toList(), finished = done))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release(); player = null
        super.onDestroy()
    }

    companion object {
        const val PLAY = "com.imad.planner.PLAY"
        const val NEXT = "com.imad.planner.NEXT"
        const val STOP = "com.imad.planner.STOP"
        private const val CHANNEL = "planner_player_v1"
        private const val ID = 3
    }
}
