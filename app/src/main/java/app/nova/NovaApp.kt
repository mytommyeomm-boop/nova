package app.nova

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import app.nova.chat.ChatEngine
import app.nova.data.NovaDb
import app.nova.settings.SettingsStore
import app.nova.voice.BlipVoice

object Channels {
    const val OVERLAY = "nova_overlay"
    const val REMINDERS = "nova_reminders"
}

class AppContainer(app: Application) {
    val db: NovaDb = Room.databaseBuilder(app, NovaDb::class.java, "nova.db").build()
    val settings = SettingsStore(app)
    val blip = BlipVoice(app, settings)
    val engine = ChatEngine(app, db, settings, blip)
}

class NovaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Channels.OVERLAY, "Floating companion", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(Channels.REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

val Context.container: AppContainer get() = (applicationContext as NovaApp).container
