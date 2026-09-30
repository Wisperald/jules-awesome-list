package com.tvgram.td

import org.drinkless.tdlib.TdApi

/** A `TdApi.Error` turned into something that can travel through `suspend` functions. */
class TdException(
    val code: Int,
    val description: String,
    val request: String? = null,
) : RuntimeException(buildString {
    append("TDLib error ").append(code).append(": ").append(description)
    if (request != null) append(" (while sending ").append(request).append(')')
}) {

    /** TDLib answers 404 to "give me more" requests once a list is exhausted. */
    val isNotFound: Boolean get() = code == 404

    val isFloodWait: Boolean get() = description.startsWith("FLOOD_WAIT")

    /** Seconds to wait, for `FLOOD_WAIT_<n>` errors. */
    val floodWaitSeconds: Int?
        get() = description.substringAfter("FLOOD_WAIT_", "").toIntOrNull()

    companion object {
        fun from(error: TdApi.Error, request: String? = null) =
            TdException(error.code, error.message, request)
    }
}
