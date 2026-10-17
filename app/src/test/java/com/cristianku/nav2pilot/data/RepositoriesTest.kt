package com.cristianku.nav2pilot.data

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.testing.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {

    private val storage = InMemoryKeyValueStore()

    @Test
    fun favoritesPersistAndDeduplicate() {
        val repository = FavoritesRepository(storage)
        val home = Destination(45.4642, 9.19, "Home", "Milano")

        val first = repository.add(home)
        val second = repository.add(home.copy(name = "Same place"))

        assertEquals(first.id, second.id)
        assertEquals(1, repository.favorites.value.size)

        val reloaded = FavoritesRepository(storage)
        assertEquals(listOf(first), reloaded.favorites.value)

        reloaded.remove(first.id)
        assertTrue(reloaded.favorites.value.isEmpty())
        assertNull(reloaded.findByLocation(home))
    }

    @Test
    fun corruptedFavoritesAreIgnored() {
        storage.values[FavoritesRepository.KEY_FAVORITES] = "not json"
        assertTrue(FavoritesRepository(storage).favorites.value.isEmpty())
    }

    @Test
    fun accountRefreshKeepsManualDevices() {
        val repository = DeviceRepository(storage)
        val manual = PairedDevice("aaaaaaaaaaaaaaaa", "Borrowed", addedManually = true)
        assertTrue(repository.addManual(manual))
        assertFalse(repository.addManual(manual))
        assertEquals("aaaaaaaaaaaaaaaa", repository.selectedDongleId.value)

        val accountDevice = PairedDevice("1a2b3c4d5e6f7a8b", "Peugeot 3008", "three", 1760700000L)
        repository.replaceAccountDevices(listOf(accountDevice))

        assertEquals(listOf(accountDevice, manual), repository.devices.value)
        assertEquals("aaaaaaaaaaaaaaaa", repository.selectedDongleId.value)

        // the account now also lists the manual device: it becomes an account device, no duplicate
        repository.replaceAccountDevices(listOf(accountDevice, PairedDevice("aaaaaaaaaaaaaaaa", "Now owned")))
        assertEquals(2, repository.devices.value.size)
        assertFalse(repository.devices.value[1].addedManually)
    }

    @Test
    fun selectionFollowsRemovalAndSurvivesRestart() {
        val repository = DeviceRepository(storage)
        repository.replaceAccountDevices(
            listOf(PairedDevice("1111111111111111", "A"), PairedDevice("2222222222222222", "B", backend = BackendKind.COMMA_ATHENA)),
        )
        repository.select("2222222222222222")
        assertEquals("B", repository.selectedDevice()!!.alias)

        val reloaded = DeviceRepository(storage)
        assertEquals("2222222222222222", reloaded.selectedDongleId.value)

        reloaded.remove("2222222222222222")
        assertEquals("1111111111111111", reloaded.selectedDongleId.value)

        reloaded.select("9999999999999999")
        assertEquals("1111111111111111", reloaded.selectedDongleId.value)

        reloaded.remove("1111111111111111")
        assertEquals("", reloaded.selectedDongleId.value)
        assertNull(reloaded.selectedDevice())
    }

    @Test
    fun deviceJsonRoundTrip() {
        val devices = listOf(
            PairedDevice("1a2b3c4d5e6f7a8b", "Car", "three", 42L, false, BackendKind.SUNNYLINK, true),
        )
        assertEquals(devices, JsonCodec.devicesFromJson(JsonCodec.devicesToJson(devices)))
    }
}
