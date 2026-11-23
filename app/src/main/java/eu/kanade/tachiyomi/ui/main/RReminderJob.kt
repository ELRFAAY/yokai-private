package eu.kanade.tachiyomi.ui.main

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.RReadLog
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** R: a calm "I missed you" notification from the little dragon after a few days without reading (switch in R settings). */
class RReminderJob(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            val prefs: PreferencesHelper = Injekt.get()
            if (!prefs.rReminder().get()) return Result.success()
            val s = RReadLog.summary(prefs)
            val ago = s.lastDayAgo ?: return Result.success() // never read yet: nothing to miss
            val limit = prefs.rReminderDays().get().coerceAtLeast(1)
            if (ago < limit) return Result.success()
            // one notification per day, and not at night
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour < 10 || hour > 22) return Result.success()
            val sp = context.getSharedPreferences("r_reminder", Context.MODE_PRIVATE)
            val today = System.currentTimeMillis() / 86_400_000L
            if (sp.getLong("last", -1L) == today) return Result.success()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير التنين", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = when {
                ago >= 14 -> "بقالك $ago يوم مش بتقرا. المناهج بتستناك، وأنا كمان 🐉"
                ago >= 7 -> "أسبوع كامل من غير قراءة! تعالى كمّل شبتر واحد بس"
                else -> "وحشتني! بقالك $ago أيام ما قريتش. شبتر واحد وتبقى تمام"
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_yokai)
                .setContentTitle("Yokai Private")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
            sp.edit().putLong("last", today).apply()
        } catch (_: Exception) {
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "r_reminder_channel"
        private const val NOTIF_ID = 8841
        private const val TAG = "r_reminder"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RReminderJob>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
