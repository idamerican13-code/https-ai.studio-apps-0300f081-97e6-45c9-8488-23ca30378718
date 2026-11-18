package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WardZeroDarkScheme = darkColorScheme(
  primary = TorchAmber,
  onPrimary = VoidBlack,
  primaryContainer = WardElevated,
  onPrimaryContainer = HospitalCream,
  secondary = PeelingTeal,
  onSecondary = HospitalCream,
  secondaryContainer = WardSurface,
  onSecondaryContainer = HospitalCream,
  tertiary = SafeMint,
  error = EmergencyCrimson,
  onError = HospitalCream,
  background = VoidBlack,
  onBackground = HospitalCream,
  surface = CorridorSlate,
  onSurface = HospitalCream,
  surfaceVariant = WardSurface,
  onSurfaceVariant = MutedAsh,
  outline = PeelingTeal
)

@Composable
fun WardZeroTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = WardZeroDarkScheme,
    typography = WardZeroTypography,
    content = content
  )
}
