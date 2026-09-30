package com.tvgram.td

/** Everything TDLib needs in `SetTdlibParameters`, gathered in one place. */
data class TdConfiguration(
    val apiId: Int,
    val apiHash: String,
    val databaseDirectory: String,
    val filesDirectory: String,
    val deviceModel: String,
    val systemVersion: String,
    val applicationVersion: String,
    val systemLanguageCode: String = "en",
    val useTestDc: Boolean = false,
    val useFileDatabase: Boolean = true,
    val useChatInfoDatabase: Boolean = true,
    val useMessageDatabase: Boolean = true,
    val useSecretChats: Boolean = false,
) {
    val isUsable: Boolean get() = apiId != 0 && apiHash.isNotBlank()
}
