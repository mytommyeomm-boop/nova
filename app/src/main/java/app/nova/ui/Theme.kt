package app.nova.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object NovaColors {
    val Bg = Color(0xFF0E0F14)
    val Surface = Color(0xFF171923)
    val Surface2 = Color(0xFF212435)
    val Purple = Color(0xFFA78BFA)
    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF60A5FA)
    val Text = Color(0xFFE8E8F2)
    val Muted = Color(0xFF8E92A8)
    val Danger = Color(0xFFF87171)
}

val LocalLang = staticCompositionLocalOf { "th" }

@Composable
fun t(th: String, en: String): String = if (LocalLang.current == "th") th else en

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NovaColors.Purple, onPrimary = Color(0xFF1A1030),
            secondary = NovaColors.Cyan, tertiary = NovaColors.Blue,
            background = NovaColors.Bg, surface = NovaColors.Surface,
            surfaceVariant = NovaColors.Surface2, onSurface = NovaColors.Text,
            onBackground = NovaColors.Text, onSurfaceVariant = NovaColors.Muted,
            error = NovaColors.Danger, outline = Color(0xFF3A3E55)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

@Composable
fun NovaCard(modifier: Modifier = Modifier, accent: Color = NovaColors.Purple, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = NovaColors.Surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, color = NovaColors.Cyan, style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun NovaChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NovaColors.Purple.copy(alpha = 0.25f),
            selectedLabelColor = NovaColors.Text
        )
    )
}
