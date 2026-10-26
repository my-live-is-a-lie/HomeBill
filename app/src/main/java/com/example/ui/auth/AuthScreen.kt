package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.LocalUtilityPalette
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  val utilityPalette = LocalUtilityPalette.current
  val heroImageRequest =
    remember(context) {
      ImageRequest.Builder(context)
        .data(R.drawable.img_hero_dashboard)
        .crossfade(true)
        .build()
    }

  LaunchedEffect(Unit) {
    // Wait for initial window draw sync to finish before invoking Play Services CredentialManager IPC
    withFrameNanos {}
    delay(250)
    attemptAutoSignIn(
      context = context,
      credentialManager = CredentialManager.create(context),
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.extraLarge,
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(210.dp)
                .background(utilityPalette.heroGradientStart)
          ) {
            AsyncImage(
              model = heroImageRequest,
              contentDescription = stringResource(R.string.app_name),
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors =
                        listOf(
                          utilityPalette.heroGradientStart.copy(alpha = 0.25f),
                          utilityPalette.heroGradientStart.copy(alpha = 0.85f),
                        )
                    )
                  )
            )
            Column(
              modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            ) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
              ) {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = stringResource(R.string.auth_headline),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
          text = stringResource(R.string.auth_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.large,
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            FeatureRow(
              icon = Icons.Default.DocumentScanner,
              text = stringResource(R.string.auth_feature_scan),
            )
            FeatureRow(
              icon = Icons.Default.NotificationsActive,
              text = stringResource(R.string.auth_feature_alert),
            )
            FeatureRow(
              icon = Icons.Default.BarChart,
              text = stringResource(R.string.auth_feature_charts),
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          ) {
            Text(
              text = errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = CredentialManager.create(context),
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = { isLoading = false },
            )
          },
          enabled = !isLoading,
          shape = MaterialTheme.shapes.large,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .height(56.dp)
              .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.5.dp,
            )
          } else {
            Icon(
              imageVector = Icons.Default.AccountCircle,
              contentDescription = null,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = stringResource(R.string.sign_in_with_google),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(R.string.security_prototype_notice),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
