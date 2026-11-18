package org.nooklin.calendar.ui.lock

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.nooklin.calendar.data.local.ContrastColor
import org.nooklin.calendar.ui.theme.DesignStyle
import org.nooklin.calendar.ui.theme.NooklinTheme

/**
 * The state of the app lock for the whole process. The lock asks for a fingerprint, a face or the screen lock of the
 * phone, using the system prompt. It is a gate in front of the screens: it does not encrypt anything.
 */
object AppLock {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    // null until the setting has been read, so the screens never show for a moment before the lock
    var enabled by mutableStateOf<Boolean?>(null)

    // True while the lock has to be passed
    var locked by mutableStateOf(true)

    var delaySeconds: Int = 60

    // When the app went to the background, 0 when it did not
    var leftAt: Long = 0

    // True while the system prompt is open, then leaving and returning to the app is not a reason to lock
    var prompting: Boolean = false
    private var lastEnd: Long = 0

    /** A new start of the app always begins locked. */
    fun reset() {
        enabled = null
        locked = true
        leftAt = 0
    }

    /** Whether the phone has a screen lock or a fingerprint or face to ask for. */
    fun available(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Called when the app comes back to the front: locks when it was away for longer than the chosen time. */
    fun lockIfAway() {
        if (enabled != true || prompting || locked || leftAt == 0L) return
        if (SystemClock.elapsedRealtime() - leftAt >= delaySeconds * 1000L) locked = true
        leftAt = 0
    }

    /** Opens the system prompt. [onDone] gets true when the person passed it. [automatic] skips a prompt right after one was closed. */
    fun authenticate(activity: Activity, title: String, automatic: Boolean = false, onDone: (Boolean) -> Unit) {
        if (prompting) return
        if (automatic && SystemClock.elapsedRealtime() - lastEnd < 1500) return
        prompting = true
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                prompting = false
                lastEnd = SystemClock.elapsedRealtime()
                onDone(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                prompting = false
                lastEnd = SystemClock.elapsedRealtime()
                onDone(false)
            }
        })
    }
}

/** Shows [content] when the app is not locked, otherwise the lock screen. The screens are not composed while locked. */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val enabled = AppLock.enabled
    if (enabled == null || (enabled && AppLock.locked)) {
        NooklinTheme(style = DesignStyle.IOS, darkTheme = isSystemInDarkTheme(), contrastColor = ContrastColor.DEFAULT) {
            LockScreen(waiting = enabled == null)
        }
    } else {
        content()
    }
}

@Composable
private fun LockScreen(waiting: Boolean) {
    val context = LocalContext.current
    fun ask(automatic: Boolean) {
        val activity = context as? Activity ?: return
        AppLock.authenticate(activity, "Unlock Nooklin Calendar", automatic) { passed -> if (passed) AppLock.locked = false }
    }
    // The prompt opens by itself whenever the app comes to the front; the button is for after a cancel
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!waiting) ask(automatic = true) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!waiting) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Nooklin Calendar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Locked", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { ask(automatic = false) }) { Text("Unlock") }
        }
    }
}
