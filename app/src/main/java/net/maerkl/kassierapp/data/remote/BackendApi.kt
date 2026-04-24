package net.maerkl.kassierapp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SumupTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long
)

@Serializable
private data class ErrorBody(val error: String? = null)

sealed class SumupTokenResult {
    data class Success(val accessToken: String, val expiresInSeconds: Long) : SumupTokenResult()
    data object Unauthorized : SumupTokenResult()
    data object DeviceRevoked : SumupTokenResult()
    data object DeviceVereinMismatch : SumupTokenResult()
    data object DeviceOnly : SumupTokenResult()
    data object AppCheckMissing : SumupTokenResult()
    data object AppCheckInvalid : SumupTokenResult()
    data object NotConnected : SumupTokenResult()
    data object ReauthorizationRequired : SumupTokenResult()
    data object InternalError : SumupTokenResult()
    data class NetworkError(val cause: Throwable) : SumupTokenResult()
}

class BackendApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun fetchSumupToken(
        vereinId: String,
        firebaseIdToken: String,
        appCheckToken: String
    ): SumupTokenResult {
        return try {
            val response: HttpResponse = client.get("$baseUrl/api/api/v1/clubs/$vereinId/sumup/token") {
                header("Authorization", "Bearer $firebaseIdToken")
                header("X-Firebase-AppCheck", appCheckToken)
            }
            if (response.status.isSuccess()) {
                val body = response.body<SumupTokenResponse>()
                SumupTokenResult.Success(body.accessToken, body.expiresIn)
            } else {
                mapError(response)
            }
        } catch (e: Exception) {
            SumupTokenResult.NetworkError(e)
        }
    }

    private suspend fun mapError(response: HttpResponse): SumupTokenResult {
        val body = try { response.body<ErrorBody>() } catch (_: Exception) { ErrorBody() }
        return when (response.status) {
            HttpStatusCode.Unauthorized -> when (body.error) {
                "app_check_invalid" -> SumupTokenResult.AppCheckInvalid
                else -> SumupTokenResult.Unauthorized
            }
            HttpStatusCode.Forbidden -> when (body.error) {
                "app_check_missing" -> SumupTokenResult.AppCheckMissing
                "device_revoked" -> SumupTokenResult.DeviceRevoked
                "device_verein_mismatch" -> SumupTokenResult.DeviceVereinMismatch
                "device_only" -> SumupTokenResult.DeviceOnly
                else -> SumupTokenResult.Unauthorized
            }
            HttpStatusCode.Conflict -> SumupTokenResult.NotConnected
            HttpStatusCode.ServiceUnavailable -> SumupTokenResult.ReauthorizationRequired
            HttpStatusCode.InternalServerError -> SumupTokenResult.InternalError
            else -> SumupTokenResult.InternalError
        }
    }
}
