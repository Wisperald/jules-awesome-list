package com.tvgram.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.tvgram.data.auth.AuthRepository
import com.tvgram.data.auth.AuthStage
import com.tvgram.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(container: AppContainer) : ViewModel() {

    private val auth: AuthRepository = container.auth

    val stage: StateFlow<AuthStage> = auth.stage
    val busy: StateFlow<Boolean> = auth.busy

    var phoneNumber by mutableStateOf("")
        private set

    var code by mutableStateOf("")
        private set

    var password by mutableStateOf("")

    fun appendPhoneDigit(digit: Char) {
        if (phoneNumber.length < 15) phoneNumber += digit
    }

    fun removePhoneDigit() {
        phoneNumber = phoneNumber.dropLast(1)
    }

    fun appendCodeDigit(digit: Char, maxLength: Int) {
        if (code.length < maxLength) code += digit
    }

    fun removeCodeDigit() {
        code = code.dropLast(1)
    }

    fun submitPhoneNumber() {
        if (phoneNumber.length < 5) return
        auth.setPhoneNumber(phoneNumber)
    }

    fun submitCode() {
        if (code.isEmpty()) return
        auth.checkCode(code)
        code = ""
    }

    fun submitPassword() {
        if (password.isEmpty()) return
        auth.checkPassword(password)
        password = ""
    }

    fun resendCode() = auth.resendCode()

    fun switchToQrCode() {
        code = ""
        auth.requestQrCode()
    }

    fun switchToPhoneNumber() = auth.usePhoneNumberInstead()

    fun retry() = auth.bootstrap()
}
