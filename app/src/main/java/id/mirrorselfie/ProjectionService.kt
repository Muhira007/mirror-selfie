package id.mirrorselfie

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class ProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { Session.stop(); stopSelf(); return START_NOT_STICKY }
        val data = intent?.getParcelableExtra<Intent>("permission")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("mirror", "Berbagi layar", NotificationManager.IMPORTANCE_LOW))
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, ProjectionService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "mirror").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.capture_note))
            .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Berhenti", stopIntent).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)
        Session.host(applicationContext, data, intent.getIntExtra("width", 720), intent.getIntExtra("height", 1280), requireNotNull(intent.getStringExtra("host")))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Session.state.mode == "host") Session.stop("Layanan berbagi layar dihentikan")
        super.onDestroy()
    }
}
