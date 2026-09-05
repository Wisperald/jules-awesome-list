package com.tvgram.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.data.auth.AuthStage
import com.tvgram.data.auth.CodeDelivery
import com.tvgram.di.AppContainer
import com.tvgram.di.containerViewModel
import com.tvgram.ui.components.DigitKeypad
import com.tvgram.ui.components.ErrorState
import com.tvgram.ui.components.LoadingState
import com.tvgram.ui.components.QrCode
import com.tvgram.ui.components.TvButton
import com.tvgram.ui.components.TvSpinner
import com.tvgram.ui.components.TvTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * The whole sign-in flow, driven entirely by TDLib's authorization state.
 *
 * QR login comes first on purpose: scanning a code with a phone that is already logged in
 * is the only way to sign in to Telegram without typing a phone number and a code on a
 * remote control.
 */
@Composable
fun AuthScreen(
    onAuthorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = containerViewModel { container: AppContainer -> AuthViewModel(container) }
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    LaunchedEffect(stage) {
        if (stage is AuthStage.Ready) onAuthorized()
    }

    Row(modifier = modifier.fillMaxSize()) {
        Branding(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(56.dp),
        )

        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = stage) {
                is AuthStage.Initializing -> LoadingState(text = "Connecting to Telegram…")

                is AuthStage.NativeLibraryMissing -> ErrorState(
                    title = "TDLib is missing",
                    detail = "libtdjni.so could not be loaded for this device's ABI. " +
                        "Rebuild the app with the TDLib artifacts in app/libs.",
                )

                is AuthStage.MissingCredentials -> ErrorState(
                    title = "api_id / api_hash are not configured",
                    detail = "Create an application at my.telegram.org/apps and pass " +
                        "TELEGRAM_API_ID and TELEGRAM_API_HASH to the build.",
                )

                is AuthStage.WaitQrCode -> QrStep(
                    link = current.link,
                    onUsePhone = viewModel::switchToPhoneNumber,
                )

                is AuthStage.WaitPhoneNumber -> PhoneStep(
                    phoneNumber = viewModel.phoneNumber,
                    error = current.error,
                    busy = busy,
                    onDigit = viewModel::appendPhoneDigit,
                    onBackspace = viewModel::removePhoneDigit,
                    onSubmit = viewModel::submitPhoneNumber,
                    onUseQr = viewModel::switchToQrCode,
                )

                is AuthStage.WaitCode -> CodeStep(
                    stage = current,
                    code = viewModel.code,
                    busy = busy,
                    onDigit = { viewModel.appendCodeDigit(it, current.codeLength.coerceAtLeast(4)) },
                    onBackspace = viewModel::removeCodeDigit,
                    onSubmit = viewModel::submitCode,
                    onResend = viewModel::resendCode,
                )

                is AuthStage.WaitPassword -> PasswordStep(
                    stage = current,
                    password = viewModel.password,
                    busy = busy,
                    onPasswordChange = { viewModel.password = it },
                    onSubmit = viewModel::submitPassword,
                )

                is AuthStage.WaitRegistration -> ErrorState(
                    title = "This phone number has no Telegram account",
                    detail = "Create the account in the Telegram mobile app first, " +
                        "then sign in here.",
                    actionLabel = "Back",
                    onAction = viewModel::switchToQrCode,
                )

                is AuthStage.Unsupported -> ErrorState(
                    title = "Unsupported sign-in step",
                    detail = "Telegram asked for \"${current.step}\", which this TV client " +
                        "does not implement. Finish signing in on a phone and use the QR code.",
                    actionLabel = "Use QR code",
                    onAction = viewModel::switchToQrCode,
                )

                is AuthStage.LoggingOut -> LoadingState(text = "Signing out…")

                is AuthStage.Ready -> LoadingState(text = "Loading chats…")
            }
        }
    }
}

@Composable
private fun Branding(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✈",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Telegram",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "for Android TV",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "An unofficial client built on TDLib.\n" +
                "Your messages stay between you and Telegram.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QrStep(
    link: String,
    onUsePhone: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Scan to log in",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QrCode(
            content = link,
            modifier = Modifier.size(320.dp),
        )
        Text(
            text = "1. Open Telegram on your phone\n" +
                "2. Settings → Devices → Link Desktop Device\n" +
                "3. Point the camera at this screen",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TvButton(text = "Use phone number", onClick = onUsePhone)
    }
}

@Composable
private fun PhoneStep(
    phoneNumber: String,
    error: String?,
    busy: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onUseQr: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Enter your phone number",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ValueDisplay(
            value = if (phoneNumber.isEmpty()) "+" else "+$phoneNumber",
            placeholder = "+",
        )
        AuthError(error)
        if (busy) {
            TvSpinner(modifier = Modifier.size(28.dp))
        } else {
            DigitKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onSubmit = onSubmit,
                submitEnabled = phoneNumber.length >= 5,
                submitLabel = "Next",
                firstKeyFocusRequester = firstKey,
            )
        }
        TvButton(text = "Use QR code instead", onClick = onUseQr)
    }
}

@Composable
private fun CodeStep(
    stage: AuthStage.WaitCode,
    code: String,
    busy: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Enter the code",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = when (stage.deliveredVia) {
                CodeDelivery.TELEGRAM_APP -> "Sent to your other Telegram apps"
                CodeDelivery.SMS -> "Sent by SMS to ${stage.phoneNumber}"
                CodeDelivery.CALL -> "You will receive a phone call to ${stage.phoneNumber}"
                CodeDelivery.MISSED_CALL -> "Check the number of the incoming call"
                CodeDelivery.FRAGMENT -> "Sent to your Fragment account"
                CodeDelivery.OTHER -> "Sent to ${stage.phoneNumber}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        ValueDisplay(
            value = code.padEnd(stage.codeLength.coerceAtLeast(4), '·'),
            placeholder = "·····",
        )
        AuthError(stage.error)
        if (busy) {
            TvSpinner(modifier = Modifier.size(28.dp))
        } else {
            DigitKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onSubmit = onSubmit,
                submitEnabled = code.isNotEmpty(),
                submitLabel = "Log in",
                firstKeyFocusRequester = firstKey,
            )
        }
        if (stage.canResend) {
            TvButton(text = "Resend the code", onClick = onResend)
        }
    }
}

@Composable
private fun PasswordStep(
    stage: AuthStage.WaitPassword,
    password: String,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.width(520.dp),
    ) {
        Text(
            text = "Two-step verification",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stage.hint?.let { "Hint: $it" }
                ?: "Enter the password you set up in Telegram",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TvTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Password",
            masked = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            focusRequester = field,
        )
        AuthError(stage.error)
        if (busy) {
            TvSpinner(modifier = Modifier.size(28.dp))
        } else {
            TvButton(
                text = "Continue",
                onClick = onSubmit,
                primary = true,
                enabled = password.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun ValueDisplay(value: String, placeholder: String) {
    Box(
        modifier = Modifier
            .width(360.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AuthError(error: String?) {
    if (error.isNullOrBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = error.humanReadable(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/** Telegram's error codes are SCREAMING_SNAKE_CASE; make them look like sentences. */
private fun String.humanReadable(): String = when {
    startsWith("PHONE_NUMBER_INVALID") -> "That phone number is not valid"
    startsWith("PHONE_CODE_INVALID") -> "That code is not correct"
    startsWith("PHONE_CODE_EXPIRED") -> "That code has expired — request a new one"
    startsWith("PASSWORD_HASH_INVALID") -> "Wrong password"
    startsWith("INVALID_PHONE_NUMBER") -> "That phone number is not valid"
    startsWith("FLOOD_WAIT") -> "Too many attempts. Please wait a few minutes."
    else -> lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
