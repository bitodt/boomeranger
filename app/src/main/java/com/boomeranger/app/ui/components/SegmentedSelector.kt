package com.boomeranger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boomeranger.app.ui.theme.Leaf
import com.boomeranger.app.ui.theme.Mist
import com.boomeranger.app.ui.theme.Moss

@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Leaf.copy(alpha = 0.22f) else Moss)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Leaf else Mist.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelected(option) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Leaf else Mist,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
