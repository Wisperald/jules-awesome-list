package com.tvgram.data.auth

/**
 * The authorization state machine as the UI needs to see it: TDLib's own
 * `AuthorizationState` carries a dozen states the user never has to know about
 * (`WaitTdlibParameters`, `Closing`, …) and none of the local failure modes.
 */
sealed interface AuthStage {

    /** TDLib is starting up or answering a request; show a spinner. */
    object Initializing : AuthStage

    /** `libtdjni.so` could not be loaded — a build problem, not a user problem. */
    object NativeLibraryMissing : AuthStage

    /** api_id / api_hash were not baked into the build. */
    object MissingCredentials : AuthStage

    /** Log in by scanning a QR code with an already authorized phone. */
    data class WaitQrCode(val link: String) : AuthStage

    data class WaitPhoneNumber(val error: String? = null) : AuthStage

    data class WaitCode(
        val phoneNumber: String,
        val codeLength: Int,
        val deliveredVia: CodeDelivery,
        val canResend: Boolean = true,
        val error: String? = null,
    ) : AuthStage

    data class WaitPassword(
        val hint: String?,
        val hasRecoveryEmail: Boolean,
        val error: String? = null,
    ) : AuthStage

    /** A brand-new account; TV is a poor place to sign up, so we only explain that. */
    object WaitRegistration : AuthStage

    /** An authorization step this client deliberately does not implement (e.g. e-mail). */
    data class Unsupported(val step: String) : AuthStage

    object Ready : AuthStage

    object LoggingOut : AuthStage
}

enum class CodeDelivery { TELEGRAM_APP, SMS, CALL, MISSED_CALL, FRAGMENT, OTHER }
