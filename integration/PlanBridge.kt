// Copy into the existing Flutter dashboard's Android module. Set package to that module's package.
package REPLACE_WITH_DASHBOARD_PACKAGE

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.BinaryMessenger

/** Close this bridge in MainActivity.onDestroy(). Queries return raw JSON; calculations belong to dashboard. */
class PlanBridge(private val context: Context, messenger: BinaryMessenger) {
    private val base = Uri.parse("content://rs.project1k.plan.data")
    private val methods = MethodChannel(messenger, "project1k/plan")
    private val events = EventChannel(messenger, "project1k/plan_changes")
    private var sink: EventChannel.EventSink? = null
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { sink?.success("changed") }
    }
    init {
        methods.setMethodCallHandler { call, result ->
            try {
                val uri = when (call.method) {
                    "schema" -> base.buildUpon().appendPath("schema").build()
                    "libraries" -> base.buildUpon().appendPath("libraries").build()
                    "records" -> base.buildUpon().appendPath("libraries")
                        .appendPath(requireNotNull(call.argument<String>("libraryId")))
                        .appendPath("records").build()
                    "record" -> base.buildUpon().appendPath("records")
                        .appendPath(requireNotNull(call.argument<String>("recordId"))).build()
                    "references" -> base.buildUpon().appendPath("references")
                        .appendPath(requireNotNull(call.argument<String>("recordId"))).build()
                    else -> null
                }
                if (uri == null) { result.notImplemented(); return@setMethodCallHandler }
                val rows = mutableListOf<Map<String, Any?>>()
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = mutableMapOf<String, Any?>()
                        cursor.columnNames.forEachIndexed { i, name ->
                            row[name] = when (cursor.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                android.database.Cursor.FIELD_TYPE_NULL -> null
                                else -> cursor.getString(i)
                            }
                        }
                        rows.add(row)
                    }
                } ?: error("1K Test provider unavailable")
                result.success(rows)
            } catch (e: Exception) { result.error("PLAN_ACCESS", e.message, null) }
        }
        events.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink) {
                sink = eventSink
                try {
                    if (!observing) {
                        context.contentResolver.registerContentObserver(base, true, observer)
                        observing = true
                    }
                } catch (e: Exception) { eventSink.error("PLAN_ACCESS", e.message, null) }
            }
            override fun onCancel(arguments: Any?) {
                if (observing) context.contentResolver.unregisterContentObserver(observer)
                observing = false; sink = null
            }
        })
    }
    fun close() {
        if (observing) context.contentResolver.unregisterContentObserver(observer)
        observing = false; sink = null
        methods.setMethodCallHandler(null); events.setStreamHandler(null)
    }
}
