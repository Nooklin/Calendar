package org.nooklin.calendar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design tokens, the same names as in the other Nooklin apps. Screens refer to these instead of writing sizes and colors in place

object Spacing {
    val Lg = 16.dp
    val Xl = 24.dp
}

// Text sizes by role. Body is never below 14 sp, Caption (12 sp) is for meta information only
object AppText {
    val Caption = 12.sp
    val Body = 14.sp
}

val MinTouchTarget = 48.dp

object AppColors {
    val DestructiveText = Color(0xFFFF3B30)
    val PopupDark = Color(0xFF1C1C1E)
}
