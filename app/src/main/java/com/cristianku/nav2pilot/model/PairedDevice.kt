package com.cristianku.nav2pilot.model

/** Which remote backend is used to reach a device. */
enum class BackendKind(val id: String) {
    COMMA_ATHENA("comma_athena"),
    SUNNYLINK("sunnylink");

    companion object {
        fun fromId(id: String): BackendKind {
            if (id == SUNNYLINK.id) {
                return SUNNYLINK
            }
            return COMMA_ATHENA
        }
    }
}

data class PairedDevice(
    val dongleId: String,
    val alias: String,
    val deviceType: String = "",
    /** Unix timestamp in seconds, as returned by `last_athena_ping` in /v1/me/devices/. 0 when unknown. */
    val lastAthenaPingSeconds: Long = 0,
    val isOwner: Boolean = true,
    val backend: BackendKind = BackendKind.COMMA_ATHENA,
    val addedManually: Boolean = false,
) {
    fun displayName(): String {
        if (alias.isNotBlank()) {
            return alias
        }
        return dongleId
    }
}

object DongleIdRules {
    // comma API schema: "A unique 16-character hexadecimal string" pattern ^[0-9a-f]{16}$
    private val dongleIdPattern = Regex("^[0-9a-f]{16}$")

    fun normalize(text: String): String {
        return text.trim().lowercase()
    }

    fun isValid(dongleId: String): Boolean {
        return dongleIdPattern.matches(dongleId)
    }
}
