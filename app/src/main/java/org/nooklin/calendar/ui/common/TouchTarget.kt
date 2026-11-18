package org.nooklin.calendar.ui.common

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import org.nooklin.calendar.ui.theme.MinTouchTarget

// Something you tap is at least 48 dp wide and high, also when the drawing on it is smaller
fun Modifier.touchTarget(): Modifier = this.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
