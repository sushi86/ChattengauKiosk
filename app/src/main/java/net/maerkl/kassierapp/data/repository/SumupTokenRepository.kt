package net.maerkl.kassierapp.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import net.maerkl.kassierapp.data.remote.AppCheckTokenProvider
import net.maerkl.kassierapp.data.remote.BackendApi
import net.maerkl.kassierapp.data.remote.SumupTokenResult
import java.time.Clock
import java.time.Instant

interface IdTokenSource {
    suspend fun getIdToken(forceRefresh: Boolean): String
}

class SumupTokenException(val result: SumupTokenResult) : Exception("SumUp token request failed: $result")

/**
 * merchantCode ist der SumUp-Merchant, der laut Backend fuer den aktuell
 * gekoppelten Verein erwartet wird — null, wenn das Backend keinen kennt.
 */
data class SumupSession(val accessToken: String, val merchantCode: String?)

class SumupTokenRepository(
    private val api: BackendApi,
    private val idTokenSource: IdTokenSource,
    private val appCheck: AppCheckTokenProvider,
    private val sessionRepo: DeviceSessionRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Cached(val session: SumupSession, val expiresAt: Instant)

    @Volatile private var cache: Cached? = null
    private val mutex = Mutex()

    suspend fun getAccessToken(): String = getSession().accessToken

    suspend fun getSession(): SumupSession = mutex.withLock {
        cache?.let {
            if (it.expiresAt.isAfter(clock.instant().plusSeconds(30))) return it.session
        }
        val vereinId = sessionRepo.currentVereinId()
            ?: throw SumupTokenException(SumupTokenResult.Unauthorized)

        val appCheckToken = appCheck.getToken()
        val idToken = idTokenSource.getIdToken(false)

        val firstResult = api.fetchSumupToken(vereinId, idToken, appCheckToken)
        val finalResult = when (firstResult) {
            is SumupTokenResult.Success -> firstResult
            SumupTokenResult.Unauthorized -> {
                val refreshed = idTokenSource.getIdToken(true)
                val retry = api.fetchSumupToken(vereinId, refreshed, appCheckToken)
                if (retry is SumupTokenResult.Unauthorized) {
                    sessionRepo.unpair()
                    throw SumupTokenException(retry)
                }
                retry
            }
            SumupTokenResult.DeviceRevoked -> {
                sessionRepo.unpair()
                throw SumupTokenException(firstResult)
            }
            SumupTokenResult.InternalError -> retryWithBackoff(vereinId, idToken, appCheckToken)
            else -> firstResult
        }

        when (finalResult) {
            is SumupTokenResult.Success -> {
                val session = SumupSession(finalResult.accessToken, finalResult.merchantCode)
                cache = Cached(session, clock.instant().plusSeconds(finalResult.expiresInSeconds))
                session
            }
            else -> throw SumupTokenException(finalResult)
        }
    }

    private suspend fun retryWithBackoff(vereinId: String, idToken: String, appCheckToken: String): SumupTokenResult {
        var last: SumupTokenResult = SumupTokenResult.InternalError
        val delays = listOf(0L, 200L, 800L)
        for (i in 1..2) {
            delay(delays[i])
            val r = api.fetchSumupToken(vereinId, idToken, appCheckToken)
            last = r
            if (r !is SumupTokenResult.InternalError) return r
        }
        return last
    }

    fun invalidate() { cache = null }
}

class FirebaseIdTokenSource(
    private val auth: com.google.firebase.auth.FirebaseAuth
) : IdTokenSource {
    override suspend fun getIdToken(forceRefresh: Boolean): String {
        val user = auth.currentUser
            ?: throw SumupTokenException(SumupTokenResult.Unauthorized)
        val result = user.getIdToken(forceRefresh).await()
        return result.token
            ?: throw SumupTokenException(SumupTokenResult.Unauthorized)
    }
}
