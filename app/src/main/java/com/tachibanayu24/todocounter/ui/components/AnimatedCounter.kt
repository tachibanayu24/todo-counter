package com.tachibanayu24.todocounter.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 数字がスライドしながら変化するアニメーション付きカウンター
 */
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    fontSize: TextUnit = 96.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    var oldCount by remember { mutableIntStateOf(count) }

    SideEffect {
        oldCount = count
    }

    Row(modifier = modifier) {
        val countString = count.toString()
        val oldCountString = oldCount.toString()

        for (i in countString.indices) {
            val oldChar = oldCountString.getOrNull(i)
            val newChar = countString[i]
            val char = if (oldChar == newChar) {
                oldCountString[i]
            } else {
                countString[i]
            }

            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (count > oldCount) {
                        // 増加: 下から上へ
                        slideInVertically { it } togetherWith slideOutVertically { -it }
                    } else {
                        // 減少: 上から下へ
                        slideInVertically { -it } togetherWith slideOutVertically { it }
                    }
                },
                label = "digitAnimation"
            ) { digit ->
                Text(
                    text = digit.toString(),
                    style = style,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color
                )
            }
        }
    }
}
