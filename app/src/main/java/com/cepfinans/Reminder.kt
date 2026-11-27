package com.cepfinans
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.time.*
import java.util.concurrent.TimeUnit

class ReminderWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("r", "Hatırlatmalar", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(applicationContext, "r").setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Cep Finans").setContentText("Bugünkü harcamalarını girmeyi unutma.").build()
        nm.notify(1, n); return Result.success()
    }
}

fun scheduleDaily(c: Context, hour: Int = 21) {
    val now = LocalDateTime.now(); var at = now.toLocalDate().atTime(hour, 0)
    if (!at.isAfter(now)) at = at.plusDays(1)
    val delay = Duration.between(now, at).toMinutes()
    val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).setInitialDelay(delay, TimeUnit.MINUTES).build()
    WorkManager.getInstance(c).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.UPDATE, req)
}
