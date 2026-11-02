package pl.mariusz.postprzerywany

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period

/** Daily step totals from Health Connect (Samsung Health, Google Fit, Mi Fitness, Garmin… write there). */
class Health(private val act: Activity, private val web: WebView) {

    companion object {
        const val REQ = 77
        private const val PROVIDER = "com.google.android.apps.healthdata"
        private val PERMS = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** "ok" | "install" | "update" */
    fun sdk(): String = when (HealthConnectClient.getSdkStatus(act, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> "ok"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
        else -> "install"
    }

    fun install() {
        act.runOnUiThread {
            val uri = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
            try {
                act.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
            } catch (e: Exception) {
                try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER"))) } catch (_: Exception) { }
            }
        }
    }

    fun request() {
        act.runOnUiThread {
            if (sdk() != "ok") { install(); return@runOnUiThread }
            try {
                val i = PermissionController.createRequestPermissionResultContract().createIntent(act, PERMS)
                act.startActivityForResult(i, REQ)
            } catch (e: Exception) {
                send("""{"state":"error"}""")
            }
        }
    }

    fun onResult() { fetch(30) }

    /** Answers asynchronously via window.onSteps({state, days:{'YYYY-MM-DD': steps}}) */
    fun fetch(days: Int) {
        scope.launch {
            val out = JSONObject()
            try {
                if (sdk() != "ok") { out.put("state", sdk()); send(out.toString()); return@launch }
                val client = HealthConnectClient.getOrCreate(act)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(PERMS)) { out.put("state", "perm"); send(out.toString()); return@launch }
                val end = LocalDate.now().plusDays(1).atStartOfDay()
                val start = LocalDate.now().minusDays((days.coerceIn(1, 90) - 1).toLong()).atStartOfDay()
                val res = withContext(Dispatchers.IO) {
                    client.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end),
                            timeRangeSlicer = Period.ofDays(1)
                        )
                    )
                }
                val map = JSONObject()
                for (g in res) map.put(g.startTime.toLocalDate().toString(), g.result[StepsRecord.COUNT_TOTAL] ?: 0L)
                out.put("state", "ok").put("days", map)
            } catch (e: Exception) {
                out.put("state", "error").put("msg", e.javaClass.simpleName)
            }
            send(out.toString())
        }
    }

    private fun send(json: String) {
        act.runOnUiThread { web.evaluateJavascript("window.onSteps&&window.onSteps($json)", null) }
    }
}
