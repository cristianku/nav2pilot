package com.cristianku.nav2pilot.security

import com.cristianku.nav2pilot.testing.FakeTokenCipher
import com.cristianku.nav2pilot.testing.Fixtures
import com.cristianku.nav2pilot.testing.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStoreTest {

    private val storage = InMemoryKeyValueStore()
    private val cipher = FakeTokenCipher()
    private val tokenStore = TokenStore(storage, cipher)

    @Test
    fun storesOnlyCiphertext() {
        tokenStore.save(Fixtures.TEST_JWT)

        val stored = storage.values[TokenStore.KEY_COMMA_TOKEN]!!
        assertFalse(stored.contains(Fixtures.TEST_JWT))
        assertEquals(Fixtures.TEST_JWT, tokenStore.load())
        assertTrue(tokenStore.hasToken())
    }

    @Test
    fun normalizesPastedHeaderValues() {
        assertEquals("abc.def.ghi", TokenStore.normalize("  JWT abc.def.ghi \n"))
        assertEquals("abc.def.ghi", TokenStore.normalize("Bearer abc.def.ghi"))
        assertEquals("abc.def.ghi", TokenStore.normalize("abc.def.ghi"))
    }

    @Test
    fun unreadableCiphertextIsDropped() {
        tokenStore.save(Fixtures.TEST_JWT)
        cipher.failDecrypt = true

        assertNull(tokenStore.load())
        assertFalse(tokenStore.hasToken())
    }

    @Test
    fun clearRemovesTheToken() {
        tokenStore.save(Fixtures.TEST_JWT)
        tokenStore.clear()
        assertNull(tokenStore.load())
    }

    @Test
    fun jwtExpiryIsRead() {
        assertTrue(JwtInfo.looksLikeJwt(Fixtures.TEST_JWT))
        assertEquals(1893456000L, JwtInfo.expirySeconds(Fixtures.TEST_JWT))
    }

    @Test
    fun notAJwt() {
        assertFalse(JwtInfo.looksLikeJwt("abc"))
        assertFalse(JwtInfo.looksLikeJwt("a..c"))
        assertFalse(JwtInfo.looksLikeJwt("a.b c.d"))
        assertNull(JwtInfo.expirySeconds("a.b.c"))
        // payload without exp
        assertNull(JwtInfo.expirySeconds("eyJhbGciOiJub25lIn0.eyJpZGVudGl0eSI6InRlc3QifQ.c2ln"))
    }
}
