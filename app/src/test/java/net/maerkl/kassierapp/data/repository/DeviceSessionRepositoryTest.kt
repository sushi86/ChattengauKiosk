package net.maerkl.kassierapp.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.maerkl.kassierapp.data.local.DeviceSessionStore
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSessionRepositoryTest {
    @Test
    fun `initial state reflects persisted session`() = runTest {
        val store = mockk<DeviceSessionStore>(relaxUnitFun = true)
        every { store.getVereinId() } returns "V1"
        every { store.getGeraetId() } returns "G1"
        val signOut = mockk<AuthSignOut>(relaxUnitFun = true)
        val repo = DeviceSessionRepository(store, signOut)
        assertEquals(PairingState.Paired("V1", "G1"), repo.pairingState.first())
    }

    @Test
    fun `initial state is Unpaired when store empty`() = runTest {
        val store = mockk<DeviceSessionStore>(relaxUnitFun = true)
        every { store.getVereinId() } returns null
        every { store.getGeraetId() } returns null
        val signOut = mockk<AuthSignOut>(relaxUnitFun = true)
        val repo = DeviceSessionRepository(store, signOut)
        assertEquals(PairingState.Unpaired, repo.pairingState.first())
    }

    @Test
    fun `markPaired updates store and flow`() = runTest {
        val store = mockk<DeviceSessionStore>(relaxUnitFun = true)
        every { store.getVereinId() } returns null
        every { store.getGeraetId() } returns null
        val signOut = mockk<AuthSignOut>(relaxUnitFun = true)
        val repo = DeviceSessionRepository(store, signOut)

        repo.markPaired("V2", "G2")
        assertEquals(PairingState.Paired("V2", "G2"), repo.pairingState.first())
        coVerify { store.save("V2", "G2") }
    }

    @Test
    fun `unpair notifies registered onUnpaired listener`() = runTest {
        val store = mockk<DeviceSessionStore>(relaxUnitFun = true)
        every { store.getVereinId() } returns "V1"
        every { store.getGeraetId() } returns "G1"
        val signOut = mockk<AuthSignOut>(relaxUnitFun = true)
        coEvery { signOut.signOut() } returns Unit
        val repo = DeviceSessionRepository(store, signOut)
        var notified = 0
        repo.addOnUnpairedListener { notified++ }

        repo.unpair()

        assertEquals(1, notified)
    }

    @Test
    fun `onUnpaired listener not called when already unpaired`() = runTest {
        val store = mockk<DeviceSessionStore>(relaxUnitFun = true)
        every { store.getVereinId() } returns null
        every { store.getGeraetId() } returns null
        val signOut = mockk<AuthSignOut>(relaxUnitFun = true)
        val repo = DeviceSessionRepository(store, signOut)
        var notified = 0
        repo.addOnUnpairedListener { notified++ }

        repo.unpair()

        assertEquals(0, notified)
    }

    @Test
    fun `unpair clears store and calls signOut and emits Unpaired`() = runTest {
        val store = mockk<DeviceSessionStore>(relaxUnitFun = true)
        every { store.getVereinId() } returns "V1"
        every { store.getGeraetId() } returns "G1"
        val signOut = mockk<AuthSignOut>(relaxUnitFun = true)
        coEvery { signOut.signOut() } returns Unit
        val repo = DeviceSessionRepository(store, signOut)

        repo.unpair()

        coVerify { store.clear() }
        coVerify { signOut.signOut() }
        assertEquals(PairingState.Unpaired, repo.pairingState.first())
    }
}
