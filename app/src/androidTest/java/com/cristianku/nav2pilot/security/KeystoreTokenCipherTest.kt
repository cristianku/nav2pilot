package com.cristianku.nav2pilot.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

/** Runs on a device or emulator: the Android Keystore does not exist on the JVM. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {

    private val cipher = KeystoreTokenCipher(keyAlias = "nav2pilot.test.key")

    @After
    fun tearDown() {
        cipher.deleteKey()
    }

    @Test
    fun roundTrip() {
        val token = "eyJhbGciOiJub25lIn0.eyJpZGVudGl0eSI6InRlc3QifQ.c2ln"
        val encrypted = cipher.encrypt(token)

        assertFalse(encrypted.contains(token))
        assertEquals(token, cipher.decrypt(encrypted))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test(expected = GeneralSecurityException::class)
    fun tamperedCiphertextIsRejected() {
        val encrypted = cipher.encrypt("secret")
        val parts = encrypted.split(':')
        val tampered = parts[0] + ":" + parts[1] + ":" + "AAAA" + parts[2].substring(4)
        cipher.decrypt(tampered)
    }
}
