package net.maerkl.kassierapp.data.remote

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.tasks.await

class AppCheckException(cause: Throwable) : Exception(cause)

class AppCheckTokenProvider(
    private val appCheck: FirebaseAppCheck
) {
    suspend fun getToken(): String {
        try {
            val result = appCheck.getAppCheckToken(false).await()
            return result.token
        } catch (e: Exception) {
            throw AppCheckException(e)
        }
    }
}
