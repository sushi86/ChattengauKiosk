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
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SumupTransactionStatusApiTest {
    private fun api(
        statusCode: HttpStatusCode,
        body: String,
        captureRequest: ((io.ktor.client.request.HttpRequestData) -> Unit)? = null
    ): SumupTransactionStatusApi {
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
        return SumupTransactionStatusApi(client, "https://api.test")
    }

    @Test
    fun `SUCCESSFUL maps to Successful with transaction code`() = runTest {
        val api = api(HttpStatusCode.OK, """{"status":"SUCCESSFUL","transaction_code":"TX42"}""")
        assertEquals(
            SumupTxStatus.Successful("TX42"),
            api.fetchStatus("M123", "foreign-1", "tok")
        )
    }

    @Test
    fun `REFUNDED still means the charge went through`() = runTest {
        val api = api(HttpStatusCode.OK, """{"status":"REFUNDED","transaction_code":"TX42"}""")
        assertEquals(SumupTxStatus.Successful("TX42"), api.fetchStatus("M123", "f", "tok"))
    }

    @Test
    fun `FAILED and CANCELLED map to Failed`() = runTest {
        assertEquals(
            SumupTxStatus.Failed,
            api(HttpStatusCode.OK, """{"status":"FAILED"}""").fetchStatus("M123", "f", "tok")
        )
        assertEquals(
            SumupTxStatus.Failed,
            api(HttpStatusCode.OK, """{"status":"CANCELLED"}""").fetchStatus("M123", "f", "tok")
        )
    }

    @Test
    fun `PENDING maps to Pending`() = runTest {
        val api = api(HttpStatusCode.OK, """{"status":"PENDING"}""")
        assertEquals(SumupTxStatus.Pending, api.fetchStatus("M123", "f", "tok"))
    }

    @Test
    fun `404 means not visible yet and keeps polling`() = runTest {
        val api = api(HttpStatusCode.NotFound, """{"message":"not found"}""")
        assertEquals(SumupTxStatus.Pending, api.fetchStatus("M123", "f", "tok"))
    }

    @Test
    fun `401 and 403 map to Unauthorized`() = runTest {
        assertEquals(
            SumupTxStatus.Unauthorized,
            api(HttpStatusCode.Unauthorized, "{}").fetchStatus("M123", "f", "tok")
        )
        assertEquals(
            SumupTxStatus.Unauthorized,
            api(HttpStatusCode.Forbidden, "{}").fetchStatus("M123", "f", "tok")
        )
    }

    @Test
    fun `server errors and network failures are retryable`() = runTest {
        assertEquals(
            SumupTxStatus.Pending,
            api(HttpStatusCode.InternalServerError, "{}").fetchStatus("M123", "f", "tok")
        )
        val failing = SumupTransactionStatusApi(
            HttpClient(MockEngine { throw IOException("offline") }) {
                install(ContentNegotiation) { json() }
            },
            "https://api.test"
        )
        assertEquals(SumupTxStatus.Pending, failing.fetchStatus("M123", "f", "tok"))
    }

    @Test
    fun `queries the merchant transactions endpoint with foreign id and bearer token`() = runTest {
        var captured: io.ktor.client.request.HttpRequestData? = null
        val api = api(HttpStatusCode.OK, """{"status":"SUCCESSFUL"}""") { captured = it }
        api.fetchStatus("M123", "foreign-1", "tok")
        val url = captured!!.url.toString()
        assertEquals(true, url.startsWith("https://api.test/v2.1/merchants/M123/transactions"))
        assertEquals("foreign-1", captured!!.url.parameters["foreign_transaction_id"])
        assertEquals("Bearer tok", captured!!.headers[HttpHeaders.Authorization])
    }
}
