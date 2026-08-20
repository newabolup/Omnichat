package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
  primary = EmeraldPrimary,
  onPrimary = Color.White,
  primaryContainer = EmeraldPrimaryDark,
  onPrimaryContainer = Color.White,
  secondary = EmeraldPrimaryLight,
  onSecondary = Color.Black,
  background = ChatDarkBackground,
  onBackground = ChatDarkTextPrimary,
  surface = ChatDarkSurface,
  onSurface = ChatDarkTextPrimary,
  surfaceVariant = ChatDarkSurfaceVariant,
  onSurfaceVariant = ChatDarkTextSecondary,
  outline = ChatDarkBorder,
  outlineVariant = ChatDarkBorder.copy(alpha = 0.5f)
)

val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimary,
  onPrimary = Color.White,
  primaryContainer = EmeraldPrimary.copy(alpha = 0.15f),
  onPrimaryContainer = EmeraldPrimaryDark,
  secondary = EmeraldPrimaryDark,
  onSecondary = Color.White,
  background = ChatLightBackground,
  onBackground = ChatLightTextPrimary,
  surface = ChatLightSurface,
  onSurface = ChatLightTextPrimary,
  surfaceVariant = ChatLightSurfaceVariant,
  onSurfaceVariant = ChatLightTextSecondary,
  outline = ChatLightBorder,
  outlineVariant = ChatLightBorder.copy(alpha = 0.5f)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
