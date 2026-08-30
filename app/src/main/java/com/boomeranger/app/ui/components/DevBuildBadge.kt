package com.boomeranger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boomeranger.app.BuildConfig
import com.boomeranger.app.ui.theme.Ink
import com.boomeranger.app.ui.theme.Sand

/**
 * Visible only on [BuildConfig.DEBUG] installs (`com.boomeranger.app.debug`).
 */
@Composable
fun DevBuildBadge(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    Text(
        text = "Dev",
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.6.sp),
        color = Ink,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Sand)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
