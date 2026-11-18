package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val CinzelDisplay = FontFamily(Font(R.font.cinzel, FontWeight.Normal))
val OswaldIndustrial = FontFamily(Font(R.font.oswald, FontWeight.Normal))
val PlexMonoBody = FontFamily(Font(R.font.ibm_plex_mono, FontWeight.Normal))

val WardZeroTypography = Typography(
  displayLarge = TextStyle(
    fontFamily = CinzelDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    lineHeight = 50.sp,
    letterSpacing = 2.5.sp
  ),
  headlineMedium = TextStyle(
    fontFamily = OswaldIndustrial,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = 1.4.sp
  ),
  titleMedium = TextStyle(
    fontFamily = OswaldIndustrial,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 1.0.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = PlexMonoBody,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.2.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = PlexMonoBody,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp
  ),
  labelLarge = TextStyle(
    fontFamily = OswaldIndustrial,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 1.2.sp
  ),
  labelSmall = TextStyle(
    fontFamily = PlexMonoBody,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.6.sp
  )
)
