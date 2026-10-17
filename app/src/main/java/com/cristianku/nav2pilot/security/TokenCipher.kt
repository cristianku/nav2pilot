package com.cristianku.nav2pilot.security

import java.security.GeneralSecurityException

/** Encrypts secrets before they are written to storage. */
interface TokenCipher {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plainText: String): String

    @Throws(GeneralSecurityException::class)
    fun decrypt(encrypted: String): String
}
