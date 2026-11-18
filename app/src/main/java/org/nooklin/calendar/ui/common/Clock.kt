package org.nooklin.calendar.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.delay

/** The time zone of the phone and the time on its clock, to the minute. Changes when either changes. */
data class ClockState(val zone: ZoneId, val now: LocalDateTime) {
    val today: LocalDate get() = now.toLocalDate()
}

/**
 * Keeps [ClockState] current: every minute, when the app comes back to the front, and when the phone reports that
 * the date, the time or the time zone was changed. That is what moves "today" past midnight while the app is open.
 */
@Composable
fun rememberClock(): ClockState {
    val context = LocalContext.current
    var zoneVersion by remember { mutableIntStateOf(0) }
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                // The cached default zone of the process is dropped, so the new zone of the phone is read
                TimeZone.setDefault(null)
                zoneVersion++
                minute = System.currentTimeMillis() / 60_000L
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        zoneVersion++
        minute = System.currentTimeMillis() / 60_000L
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            minute = System.currentTimeMillis() / 60_000L
        }
    }

    val zone = remember(zoneVersion) { ZoneId.systemDefault() }
    val now = remember(minute, zone) { LocalDateTime.now(zone).withSecond(0).withNano(0) }
    return remember(zone, now) { ClockState(zone, now) }
}
