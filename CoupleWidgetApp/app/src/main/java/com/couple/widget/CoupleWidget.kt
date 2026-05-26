package com.couple.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import java.io.File

class CoupleWidget : AppWidgetProvider() {

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CoupleWidget::class.java))
            if (ids.isNotEmpty()) {
                ids.forEach { id -> renderWidget(context, manager, id) }
            }
        }

        private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(P2PService.PREFS, Context.MODE_PRIVATE)
            val hasImage = prefs.getBoolean(P2PService.KEY_HAS_IMAGE, false)
            val lastText = prefs.getString(P2PService.KEY_LAST_TEXT, "") ?: ""
            val isConnected = prefs.getBoolean(P2PService.KEY_IS_CONNECTED, false)

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            views.setTextViewText(
                R.id.widget_status,
                if (isConnected) "Online" else "Offline"
            )

            if (hasImage) {
                val file = File(context.filesDir, P2PService.IMAGE_FILENAME)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_text, View.GONE)
                    }
                }
            } else {
                views.setViewVisibility(R.id.widget_image, View.GONE)
                views.setViewVisibility(R.id.widget_text, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_text,
                    lastText.ifEmpty { "Tippen zum Senden" }
                )
            }

            val composePi = PendingIntent.getActivity(
                context, widgetId,
                Intent(context, ComposeActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, composePi)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { renderWidget(context, appWidgetManager, it) }
        context.startForegroundService(Intent(context, P2PService::class.java))
    }

    override fun onEnabled(context: Context) {
        context.startForegroundService(Intent(context, P2PService::class.java))
    }
}
