package org.nooklin.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.travelLabel

/**
 * One line of a day list: times on the left, a bar in the colour of the calendar, then title and place.
 * [day] is the day the list shows, so an event that began the day before reads "..." as its start.
 */
@Composable
fun EventRow(occurrence: Occurrence, day: LocalDate, onClick: () -> Unit) {
    val formatter = timeFormatter
    val wholeDay = occurrence.allDay ||
        (occurrence.startDate.isBefore(day) && occurrence.lastDate.isAfter(day))
    val top: String
    val bottom: String?
    if (wholeDay) {
        top = "all day"
        bottom = null
    } else {
        top = if (occurrence.startDate.isBefore(day)) "..." else occurrence.start.toLocalTime().format(formatter)
        bottom = if (occurrence.lastDate.isAfter(day)) "..." else occurrence.end.toLocalTime().format(formatter)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = "Open event", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(IntrinsicSize.Min)
    ) {
        Column(Modifier.width(64.dp)) {
            Text(top, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (bottom != null) {
                Text(bottom, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier
                .padding(end = 10.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(occurrence.color))
        )
        Column(Modifier.weight(1f)) {
            Text(occurrence.title.ifEmpty { "(No title)" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (occurrence.location.isNotEmpty()) {
                Text(
                    occurrence.location,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (occurrence.travelMinutes > 0 && !occurrence.allDay) {
                Text(
                    "Travel " + travelLabel(occurrence.travelMinutes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
