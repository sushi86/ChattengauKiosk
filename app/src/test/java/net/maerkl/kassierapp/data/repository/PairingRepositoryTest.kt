package net.maerkl.kassierapp.data.repository

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.maerkl.kassierapp.data.remote.FirebasePairingService
import net.maerkl.kassierapp.data.remote.PairingCredentials
import net.maerkl.kassierapp.data.remote.PairingError
import net.maerkl.kassierapp.data.remote.PairingThrowable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRepositoryTest {
    @Test
    fun `successful pairing calls signInWithCustomToken and markPaired`() = runTest {
        val service = mockk<FirebasePairingService>()
        coEvery { service.activate("CODE") } returns Result.success(
            PairingCredentials("V1", "G1", "custom-token")
        )
        val auth = mockk<FirebaseAuth>()
        every { auth.signInWithCustomToken("custom-token") } returns Tasks.forResult(mockk<AuthResult>())
        val sessionRepo = mockk<DeviceSessionRepository>(relaxUnitFun = true)

        val repo = PairingRepository(service, auth, sessionRepo)
        val result = repo.pair("CODE")

        assertTrue(result.isSuccess)
        verify { auth.signInWithCustomToken("custom-token") }
        verify { sessionRepo.markPaired("V1", "G1") }
    }

    @Test
    fun `activate failure is propagated as PairingError`() = runTest {
        val service = mockk<FirebasePairingService>()
        coEvery { service.activate("X") } returns Result.failure(PairingThrowable(PairingError.CodeExpired))
        val auth = mockk<FirebaseAuth>()
        val sessionRepo = mockk<DeviceSessionRepository>(relaxUnitFun = true)

        val repo = PairingRepository(service, auth, sessionRepo)
        val result = repo.pair("X")

        val err = (result.exceptionOrNull() as PairingThrowable).error
        assertEquals(PairingError.CodeExpired, err)
    }

    @Test
    fun `signIn failure is wrapped as Unknown and session not marked`() = runTest {
        val service = mockk<FirebasePairingService>()
        coEvery { service.activate("CODE") } returns Result.success(PairingCredentials("V1", "G1", "t"))
        val auth = mockk<FirebaseAuth>()
        every { auth.signInWithCustomToken("t") } returns Tasks.forException(RuntimeException("boom"))
        val sessionRepo = mockk<DeviceSessionRepository>(relaxUnitFun = true)

        val repo = PairingRepository(service, auth, sessionRepo)
        val result = repo.pair("CODE")

        assertTrue(result.isFailure)
        val err = (result.exceptionOrNull() as PairingThrowable).error
        assertTrue(err is PairingError.Unknown)
    }
}
