package com.tvgram.data.auth

import android.util.Log
import com.tvgram.td.TdConfiguration
import com.tvgram.td.TdException
import com.tvgram.td.TdLibLoader
import com.tvgram.td.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi

/**
 * Owns the whole authorization state machine.
 *
 * TDLib drives it: every step is announced through `UpdateAuthorizationState`, and the
 * only thing a client may do is answer the state it is currently in. The repository
 * therefore never guesses — it reacts, and translates the result into an [AuthStage].
 */
class AuthRepository(
    private val client: TelegramClient,
    private val configuration: TdConfiguration,
    private val scope: CoroutineScope,
) {

    private companion object {
        const val TAG = "AuthRepository"
    }

    private val _stage = MutableStateFlow<AuthStage>(AuthStage.Initializing)
    val stage: StateFlow<AuthStage> = _stage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val requestLock = Mutex()

    @Volatile
    private var started = false

    @Volatile
    private var lastPhoneNumber: String = ""

    /** Whether the QR path should be offered first; the user can always fall back to SMS. */
    @Volatile
    private var qrRequested = false

    fun bootstrap() {
        if (started) return
        started = true

        if (!configuration.isUsable) {
            _stage.value = AuthStage.MissingCredentials
            return
        }

        // Subscribe before the client exists so that the very first
        // UpdateAuthorizationState cannot slip through.
        scope.launch {
            client.updates
                .filterIsInstance<TdApi.UpdateAuthorizationState>()
                .collect { onAuthorizationState(it.authorizationState) }
        }

        if (!client.start()) {
            _stage.value = AuthStage.NativeLibraryMissing
            Log.e(TAG, "TDLib could not be loaded", TdLibLoader.loadError)
            return
        }

        // Belt and braces: ask for the current state in case the update was emitted
        // before the collector above was actually subscribed.
        scope.launch {
            client.sendOrNull(TdApi.GetAuthorizationState())?.let { onAuthorizationState(it) }
        }
    }

    private suspend fun onAuthorizationState(state: TdApi.AuthorizationState) {
        when (state.constructor) {
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> sendParameters()

            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                _stage.value = AuthStage.WaitPhoneNumber()
                if (!qrRequested) requestQrCode()
            }

            TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
                val link = (state as TdApi.AuthorizationStateWaitOtherDeviceConfirmation).link
                _stage.value = AuthStage.WaitQrCode(link)
            }

            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                val info = (state as TdApi.AuthorizationStateWaitCode).codeInfo
                lastPhoneNumber = info.phoneNumber ?: lastPhoneNumber
                _stage.value = AuthStage.WaitCode(
                    phoneNumber = lastPhoneNumber,
                    codeLength = info.type.codeLength(),
                    deliveredVia = info.type.delivery(),
                    canResend = info.nextType != null,
                )
            }

            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                val password = state as TdApi.AuthorizationStateWaitPassword
                _stage.value = AuthStage.WaitPassword(
                    hint = password.passwordHint?.takeIf { it.isNotBlank() },
                    hasRecoveryEmail = password.hasRecoveryEmailAddress,
                )
            }

            TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR ->
                _stage.value = AuthStage.WaitRegistration

            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                qrRequested = false
                _stage.value = AuthStage.Ready
            }

            TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR,
            TdApi.AuthorizationStateClosing.CONSTRUCTOR,
            -> _stage.value = AuthStage.LoggingOut

            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                // TDLib is single-use: after a log-out the client must be re-created.
                client.onClosed()
                qrRequested = false
                _stage.value = AuthStage.Initializing
                if (client.start()) {
                    client.sendOrNull(TdApi.GetAuthorizationState())?.let { onAuthorizationState(it) }
                }
            }

            else -> {
                // WaitEmailAddress / WaitEmailCode and any future step.
                _stage.value = AuthStage.Unsupported(state.javaClass.simpleName)
                Log.w(TAG, "Unhandled authorization state ${state.javaClass.simpleName}")
            }
        }
    }

    private suspend fun sendParameters() {
        val parameters = TdApi.SetTdlibParameters().apply {
            apiId = configuration.apiId
            apiHash = configuration.apiHash
            databaseDirectory = configuration.databaseDirectory
            filesDirectory = configuration.filesDirectory
            systemLanguageCode = configuration.systemLanguageCode
            deviceModel = configuration.deviceModel
            systemVersion = configuration.systemVersion
            applicationVersion = configuration.applicationVersion
            useTestDc = configuration.useTestDc
            useFileDatabase = configuration.useFileDatabase
            useChatInfoDatabase = configuration.useChatInfoDatabase
            useMessageDatabase = configuration.useMessageDatabase
            useSecretChats = configuration.useSecretChats
        }
        try {
            client.send(parameters)
        } catch (e: TdException) {
            Log.e(TAG, "SetTdlibParameters rejected: ${e.description}")
            _stage.value = AuthStage.WaitPhoneNumber(error = e.description)
        }
    }

    /** Switches to the QR flow, which is by far the least painful option on a remote control. */
    fun requestQrCode() {
        launchStep(onError = { message -> _stage.value = AuthStage.WaitPhoneNumber(error = message) }) {
            qrRequested = true
            client.send(TdApi.RequestQrCodeAuthentication(LongArray(0)))
        }
    }

    /** Leaves the QR flow and asks TDLib to go back to the phone-number step. */
    fun usePhoneNumberInstead() {
        launchStep(onError = { message -> _stage.value = AuthStage.WaitPhoneNumber(error = message) }) {
            qrRequested = true // do not auto-return to QR
            _stage.value = AuthStage.WaitPhoneNumber()
        }
    }

    fun setPhoneNumber(phoneNumber: String) {
        val normalized = phoneNumber.normalizedPhone()
        if (normalized.length < 5) {
            _stage.value = AuthStage.WaitPhoneNumber(error = "INVALID_PHONE_NUMBER")
            return
        }
        lastPhoneNumber = normalized
        launchStep(onError = { message -> _stage.value = AuthStage.WaitPhoneNumber(error = message) }) {
            client.send(TdApi.SetAuthenticationPhoneNumber(normalized, null))
        }
    }

    fun checkCode(code: String) {
        launchStep(
            onError = { message ->
                (_stage.value as? AuthStage.WaitCode)?.let { current ->
                    _stage.value = current.copy(error = message)
                }
            },
        ) {
            client.send(TdApi.CheckAuthenticationCode(code.trim()))
        }
    }

    fun resendCode() {
        launchStep(
            onError = { message ->
                (_stage.value as? AuthStage.WaitCode)?.let { current ->
                    _stage.value = current.copy(error = message)
                }
            },
        ) {
            client.send(TdApi.ResendAuthenticationCode())
        }
    }

    fun checkPassword(password: String) {
        launchStep(
            onError = { message ->
                (_stage.value as? AuthStage.WaitPassword)?.let { current ->
                    _stage.value = current.copy(error = message)
                }
            },
        ) {
            client.send(TdApi.CheckAuthenticationPassword(password))
        }
    }

    fun logOut() {
        launchStep(onError = { Log.w(TAG, "LogOut failed: $it") }) {
            _stage.value = AuthStage.LoggingOut
            client.send(TdApi.LogOut())
        }
    }

    private fun launchStep(
        onError: (String) -> Unit,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            requestLock.withLock {
                _busy.value = true
                try {
                    block()
                } catch (e: TdException) {
                    onError(e.description)
                } catch (e: IllegalStateException) {
                    onError(e.message ?: "TDLib is not running")
                } finally {
                    _busy.value = false
                }
            }
        }
    }
}

private fun String.normalizedPhone(): String {
    val digits = filter { it.isDigit() }
    return if (digits.isEmpty()) "" else "+$digits"
}

private fun TdApi.AuthenticationCodeType.codeLength(): Int = when (this) {
    is TdApi.AuthenticationCodeTypeTelegramMessage -> length
    is TdApi.AuthenticationCodeTypeSms -> length
    is TdApi.AuthenticationCodeTypeCall -> length
    is TdApi.AuthenticationCodeTypeMissedCall -> length
    is TdApi.AuthenticationCodeTypeFragment -> length
    else -> DEFAULT_AUTH_CODE_LENGTH
}

private fun TdApi.AuthenticationCodeType.delivery(): CodeDelivery = when (this) {
    is TdApi.AuthenticationCodeTypeTelegramMessage -> CodeDelivery.TELEGRAM_APP
    is TdApi.AuthenticationCodeTypeSms -> CodeDelivery.SMS
    is TdApi.AuthenticationCodeTypeCall -> CodeDelivery.CALL
    is TdApi.AuthenticationCodeTypeMissedCall -> CodeDelivery.MISSED_CALL
    is TdApi.AuthenticationCodeTypeFragment -> CodeDelivery.FRAGMENT
    else -> CodeDelivery.OTHER
}

private const val DEFAULT_AUTH_CODE_LENGTH = 5
