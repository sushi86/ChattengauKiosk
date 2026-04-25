package net.maerkl.kassierapp.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

sealed class PairingError {
    data object InvalidCodeFormat : PairingError()
    data object CodeUnknown : PairingError()
    data object CodeAlreadyUsed : PairingError()
    data object CodeExpired : PairingError()
    data object AppCheckRejected : PairingError()
    data class Unknown(val message: String?) : PairingError()
}

data class PairingCredentials(
    val vereinId: String,
    val geraetId: String,
    val customToken: String
)

class PairingThrowable(val error: PairingError) : Exception()

class FirebasePairingService(
    private val functions: FirebaseFunctions
) {
    suspend fun activate(code: String): Result<PairingCredentials> {
        return try {
            val result = functions.getHttpsCallable("geraetAktivieren")
                .call(mapOf("code" to code))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as Map<String, Any>
            Result.success(
                PairingCredentials(
                    vereinId = data["vereinId"] as String,
                    geraetId = data["geraetId"] as String,
                    customToken = data["customToken"] as String
                )
            )
        } catch (e: FirebaseFunctionsException) {
            Result.failure(PairingThrowable(mapHttpsError(e)))
        } catch (e: Exception) {
            Result.failure(PairingThrowable(PairingError.Unknown(e.message)))
        }
    }

    companion object {
        fun mapHttpsError(e: FirebaseFunctionsException): PairingError {
            val msg = e.message.orEmpty()
            return when (e.code) {
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> PairingError.InvalidCodeFormat
                FirebaseFunctionsException.Code.NOT_FOUND -> PairingError.CodeUnknown
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> when {
                    msg.contains("already used", ignoreCase = true) -> PairingError.CodeAlreadyUsed
                    msg.contains("expired", ignoreCase = true) -> PairingError.CodeExpired
                    else -> PairingError.Unknown(msg)
                }
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> PairingError.AppCheckRejected
                else -> PairingError.Unknown(msg)
            }
        }
    }
}
