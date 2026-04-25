package net.maerkl.kassierapp.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckTokenProviderTest {
    @Test
    fun `returns token on success`() = runTest {
        val appCheck = mockk<FirebaseAppCheck>()
        val tokenResult = mockk<AppCheckToken>()
        every { tokenResult.token } returns "app-check-abc"
        every { appCheck.getAppCheckToken(false) } returns Tasks.forResult(tokenResult)

        val provider = AppCheckTokenProvider(appCheck)
        assertEquals("app-check-abc", provider.getToken())
    }

    @Test
    fun `wraps underlying failure in AppCheckException`() = runTest {
        val appCheck = mockk<FirebaseAppCheck>()
        every { appCheck.getAppCheckToken(false) } returns Tasks.forException(RuntimeException("boom"))

        val provider = AppCheckTokenProvider(appCheck)
        val thrown = try {
            provider.getToken()
            null
        } catch (e: AppCheckException) {
            e
        }
        assertTrue(thrown != null)
    }
}
