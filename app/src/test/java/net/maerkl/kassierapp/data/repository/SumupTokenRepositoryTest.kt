package net.maerkl.kassierapp.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.maerkl.kassierapp.data.remote.AppCheckTokenProvider
import net.maerkl.kassierapp.data.remote.BackendApi
import net.maerkl.kassierapp.data.remote.SumupTokenResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SumupTokenRepositoryTest {

    private fun fixedClock(epochSeconds: Long) = Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

    private fun makeRepo(
        api: BackendApi,
        idTokens: List<String> = listOf("id-1"),
        appCheckToken: String = "ac-1",
        sessionVereinId: String? = "V1",
        clock: Clock = fixedClock(1000),
        sessionRepo: DeviceSessionRepository = mockk<DeviceSessionRepository>(relaxUnitFun = true).also {
            coEvery { it.currentVereinId() } returns sessionVereinId
        }
    ): SumupTokenRepository {
        val tokens = idTokens.toMutableList()
        val idTokenSource = object : IdTokenSource {
            override suspend fun getIdToken(forceRefresh: Boolean): String =
                if (tokens.isEmpty()) throw IllegalStateException("no more tokens")
                else tokens.removeAt(0)
        }
        val appCheck = mockk<AppCheckTokenProvider>()
        coEvery { appCheck.getToken() } returns appCheckToken
        return SumupTokenRepository(api, idTokenSource, appCheck, sessionRepo, clock)
    }

    @Test
    fun `returns token on 200 and caches it`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.Success("S1", 3540)
        val repo = makeRepo(api)

        assertEquals("S1", repo.getAccessToken())
        assertEquals("S1", repo.getAccessToken())
        coVerify(exactly = 1) { api.fetchSumupToken(any(), any(), any()) }
    }

    @Test
    fun `getSession returns merchant code and caches it with the token`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns
            SumupTokenResult.Success("S1", 3540, "M123")
        val repo = makeRepo(api)

        val session = repo.getSession()
        assertEquals("S1", session.accessToken)
        assertEquals("M123", session.merchantCode)
        assertEquals("M123", repo.getSession().merchantCode)
        coVerify(exactly = 1) { api.fetchSumupToken(any(), any(), any()) }
    }

    @Test
    fun `getSession merchant code is null when backend omits it`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns
            SumupTokenResult.Success("S1", 3540, null)
        val repo = makeRepo(api)

        assertEquals(null, repo.getSession().merchantCode)
    }

    @Test
    fun `force-refreshes id token once on 401 and retries`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), "id-1", any()) } returns SumupTokenResult.Unauthorized
        coEvery { api.fetchSumupToken(any(), "id-2", any()) } returns SumupTokenResult.Success("S", 3540)
        val repo = makeRepo(api, idTokens = listOf("id-1", "id-2"))

        assertEquals("S", repo.getAccessToken())
        coVerify(exactly = 2) { api.fetchSumupToken(any(), any(), any()) }
    }

    @Test
    fun `persistent 401 triggers unpair`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.Unauthorized
        val sessionRepo = mockk<DeviceSessionRepository>(relaxUnitFun = true)
        coEvery { sessionRepo.currentVereinId() } returns "V1"
        val repo = makeRepo(api, idTokens = listOf("id-1", "id-2"), sessionRepo = sessionRepo)

        val thrown = try { repo.getAccessToken(); null } catch (e: SumupTokenException) { e }
        assertTrue(thrown?.result == SumupTokenResult.Unauthorized)
        coVerify { sessionRepo.unpair() }
    }

    @Test
    fun `device_revoked triggers unpair`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.DeviceRevoked
        val sessionRepo = mockk<DeviceSessionRepository>(relaxUnitFun = true)
        coEvery { sessionRepo.currentVereinId() } returns "V1"
        val repo = makeRepo(api, sessionRepo = sessionRepo)

        val thrown = try { repo.getAccessToken(); null } catch (e: SumupTokenException) { e }
        assertTrue(thrown?.result == SumupTokenResult.DeviceRevoked)
        coVerify { sessionRepo.unpair() }
    }

    @Test
    fun `app check errors do not trigger unpair`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.AppCheckInvalid
        val sessionRepo = mockk<DeviceSessionRepository>(relaxUnitFun = true)
        coEvery { sessionRepo.currentVereinId() } returns "V1"
        val repo = makeRepo(api, sessionRepo = sessionRepo)

        val thrown = try { repo.getAccessToken(); null } catch (e: SumupTokenException) { e }
        assertTrue(thrown?.result == SumupTokenResult.AppCheckInvalid)
        coVerify(exactly = 0) { sessionRepo.unpair() }
    }

    @Test
    fun `500 retries with exponential backoff then fails`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.InternalError
        val repo = makeRepo(api)

        val thrown = try { repo.getAccessToken(); null } catch (e: SumupTokenException) { e }
        assertTrue(thrown?.result == SumupTokenResult.InternalError)
        coVerify(exactly = 3) { api.fetchSumupToken(any(), any(), any()) }
    }

    @Test
    fun `not_connected and reauthorization_required do not retry`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.NotConnected
        val repo = makeRepo(api)

        try { repo.getAccessToken() } catch (_: SumupTokenException) {}
        coVerify(exactly = 1) { api.fetchSumupToken(any(), any(), any()) }
    }

    @Test
    fun `invalidate clears cache`() = runTest {
        val api = mockk<BackendApi>()
        coEvery { api.fetchSumupToken(any(), any(), any()) } returns SumupTokenResult.Success("S", 3540)
        val repo = makeRepo(api, idTokens = listOf("id-1", "id-2"))
        repo.getAccessToken()
        repo.invalidate()
        repo.getAccessToken()
        coVerify(exactly = 2) { api.fetchSumupToken(any(), any(), any()) }
    }

    @Test
    fun `throws SessionNotPaired if no vereinId`() = runTest {
        val api = mockk<BackendApi>()
        val sessionRepo = mockk<DeviceSessionRepository>()
        coEvery { sessionRepo.currentVereinId() } returns null
        val repo = makeRepo(api, sessionRepo = sessionRepo)

        val thrown = try { repo.getAccessToken(); null } catch (e: SumupTokenException) { e }
        assertTrue(thrown?.result is SumupTokenResult.Unauthorized)
    }
}
