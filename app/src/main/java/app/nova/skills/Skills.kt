package app.nova.skills

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.nova.Channels
import app.nova.R
import app.nova.container
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppInfo(val label: String, val pkg: String)

object Skills {
    fun installedApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun openApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }

    fun openUrl(ctx: Context, raw: String): Boolean {
        var u = raw.trim()
        if (u.isEmpty()) return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    /** Opens the system Clock app with a timer prefilled, so the user confirms it there. */
    fun setTimer(ctx: Context, seconds: Int, label: String): Boolean {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (e: Exception) { false }
    }

    /** Inexact reminder (no special exact-alarm permission needed). May fire a little late under battery saving. */
    fun scheduleReminder(ctx: Context, text: String, minutes: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, ReminderReceiver::class.java).putExtra("text", text).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pi)
    }

    fun battery(ctx: Context, lang: String): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0) level * 100 / scale else -1
        if (pct < 0) return if (lang == "th") "อ่านค่าแบตเตอรี่ไม่ได้" else "Couldn't read the battery level"
        return if (lang == "th") "แบตเตอรี่ $pct%" + (if (charging) " (กำลังชาร์จ)" else "")
        else "Battery $pct%" + (if (charging) " (charging)" else "")
    }

    fun dateTime(lang: String): String {
        val loc = if (lang == "th") Locale("th", "TH") else Locale.ENGLISH
        return SimpleDateFormat("EEEE d MMMM yyyy  HH:mm", loc).format(Date())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ctx.container.settings.state.value.remindNotif) return
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("NOVA")
            .setContentText(intent.getStringExtra("text").orEmpty())
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(intent.getIntExtra("id", 1) + 100, n)
    }
}
