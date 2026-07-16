package ru.xvmblitz.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OverlayFab(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Статистика",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color(0xE63D7EA6), RoundedCornerShape(16.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
    )
}
