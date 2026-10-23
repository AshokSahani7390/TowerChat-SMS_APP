package com.towerchat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.QuotaState
import com.towerchat.app.ui.theme.GmSurface
import com.towerchat.app.ui.theme.GmTextPrimary
import com.towerchat.app.ui.theme.QuotaExhaustedColor
import com.towerchat.app.ui.theme.QuotaSafeColor
import com.towerchat.app.ui.theme.QuotaWarningColor

@Composable
fun QuotaPill(
    quota: QuotaState,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            quota.isExhausted -> QuotaExhaustedColor
            quota.isWarning -> QuotaWarningColor
            else -> QuotaSafeColor
        },
        animationSpec = tween(durationMillis = 300),
        label = "quota_indicator_color"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(GmSurface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${quota.remaining}/100",
                color = GmTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
