package net.maerkl.kassierapp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendApiTest {
    private fun api(statusCode: HttpStatusCode, body: String, captureRequest: ((io.ktor.client.request.HttpRequestData) -> Unit)? = null): BackendApi {
        val engine = MockEngine { req ->
            captureRequest?.invoke(req)
            respond(
                content = ByteReadChannel(body),
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        return BackendApi(client, "https://example.test")
    }

    @Test
    fun `200 returns SumupTokenResponse`() = runTest {
        val api = api(HttpStatusCode.OK, """{"access_token":"abc","expires_in":3540}""")
        val result = api.fetchSumupToken("V1", "firebase-id", "appcheck-tok")
        assertTrue(result is SumupTokenResult.Success)
        assertEquals("abc", (result as SumupTokenResult.Success).accessToken)
        assertEquals(3540L, result.expiresInSeconds)
    }

    @Test
    fun `200 with merchant_code maps it into Success`() = runTest {
        val api = api(HttpStatusCode.OK, """{"access_token":"abc","expires_in":3540,"merchant_code":"M123"}""")
        val result = api.fetchSumupToken("V1", "firebase-id", "appcheck-tok")
        assertEquals("M123", (result as SumupTokenResult.Success).merchantCode)
    }

    @Test
    fun `200 without merchant_code maps to null`() = runTest {
        val api = api(HttpStatusCode.OK, """{"access_token":"abc","expires_in":3540}""")
        val result = api.fetchSumupToken("V1", "firebase-id", "appcheck-tok")
        assertEquals(null, (result as SumupTokenResult.Success).merchantCode)
    }

    @Test
    fun `200 with null merchant_code maps to null`() = runTest {
        val api = api(HttpStatusCode.OK, """{"access_token":"abc","expires_in":3540,"merchant_code":null}""")
        val result = api.fetchSumupToken("V1", "firebase-id", "appcheck-tok")
        assertEquals(null, (result as SumupTokenResult.Success).merchantCode)
    }

    @Test
    fun `sends Authorization and X-Firebase-AppCheck headers`() = runTest {
        var captured: io.ktor.client.request.HttpRequestData? = null
        val api = api(HttpStatusCode.OK, """{"access_token":"a","expires_in":1}""") { captured = it }
        api.fetchSumupToken("V1", "firebase-id", "appcheck-tok")
        assertEquals("Bearer firebase-id", captured!!.headers[HttpHeaders.Authorization])
        assertEquals("appcheck-tok", captured!!.headers["X-Firebase-AppCheck"])
    }

    @Test
    fun `401 app_check_invalid maps to AppCheckInvalid`() = runTest {
        val api = api(HttpStatusCode.Unauthorized, """{"error":"app_check_invalid"}""")
        assertEquals(SumupTokenResult.AppCheckInvalid, api.fetchSumupToken("V1", "t", "a"))
    }

    @Test
    fun `403 app_check_missing maps to AppCheckMissing`() = runTest {
        val api = api(HttpStatusCode.Forbidden, """{"error":"app_check_missing"}""")
        assertEquals(SumupTokenResult.AppCheckMissing, api.fetchSumupToken("V1", "t", "a"))
    }

    @Test
    fun `401 generic maps to Unauthorized`() = runTest {
        val api = api(HttpStatusCode.Unauthorized, """{"error":"Missing or invalid Authorization header"}""")
        assertEquals(SumupTokenResult.Unauthorized, api.fetchSumupToken("V1", "t", "a"))
    }

    @Test
    fun `403 device_revoked maps to DeviceRevoked`() = runTest {
        val api = api(HttpStatusCode.Forbidden, """{"error":"device_revoked"}""")
        assertEquals(SumupTokenResult.DeviceRevoked, api.fetchSumupToken("V1", "t", "a"))
    }

    @Test
    fun `409 not_connected maps to NotConnected`() = runTest {
        val api = api(HttpStatusCode.Conflict, """{"error":"not_connected"}""")
        assertEquals(SumupTokenResult.NotConnected, api.fetchSumupToken("V1", "t", "a"))
    }

    @Test
    fun `503 reauthorization_required maps to ReauthorizationRequired`() = runTest {
        val api = api(HttpStatusCode.ServiceUnavailable, """{"error":"reauthorization_required"}""")
        assertEquals(SumupTokenResult.ReauthorizationRequired, api.fetchSumupToken("V1", "t", "a"))
    }

    @Test
    fun `500 maps to InternalError`() = runTest {
        val api = api(HttpStatusCode.InternalServerError, """{"error":"internal_error"}""")
        assertEquals(SumupTokenResult.InternalError, api.fetchSumupToken("V1", "t", "a"))
    }
}
