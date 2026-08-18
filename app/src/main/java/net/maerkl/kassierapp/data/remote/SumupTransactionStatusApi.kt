package net.maerkl.kassierapp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ergebnis einer einzelnen Statusabfrage bei der SumUp-Transactions-API. */
sealed class SumupTxStatus {
    /** Abbuchung ist (mindestens einmal) durchgegangen. */
    data class Successful(val transactionCode: String?) : SumupTxStatus()

    /** Transaktion sicher nicht zustande gekommen. */
    data object Failed : SumupTxStatus()

    /** Noch nicht entscheidbar (PENDING, noch nicht sichtbar, Server-/Netzfehler) — weiter pollen. */
    data object Pending : SumupTxStatus()

    /** Token darf die Transactions-API nicht lesen — weiteres Pollen ist sinnlos. */
    data object Unauthorized : SumupTxStatus()
}

@Serializable
private data class TransactionStatusBody(
    val status: String? = null,
    @SerialName("transaction_code") val transactionCode: String? = null,
)

/**
 * Fragt den Status einer Kartenzahlung ueber deren foreignTransactionId ab.
 * Wird gebraucht, wenn das Reader-SDK den Ausgang nicht kennt
 * (ERROR_UNKNOWN_TRANSACTION_STATUS) — nur diese Abfrage entscheidet dann,
 * ob kassiert wurde oder nicht.
 */
class SumupTransactionStatusApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.sumup.com",
) {
    suspend fun fetchStatus(
        merchantCode: String,
        foreignTransactionId: String,
        accessToken: String,
    ): SumupTxStatus {
        val response: HttpResponse = try {
            client.get("$baseUrl/v2.1/merchants/$merchantCode/transactions") {
                parameter("foreign_transaction_id", foreignTransactionId)
                header("Authorization", "Bearer $accessToken")
            }
        } catch (e: Exception) {
            return SumupTxStatus.Pending
        }
        return when {
            response.status.isSuccess() -> {
                val body = try {
                    response.body<TransactionStatusBody>()
                } catch (e: Exception) {
                    return SumupTxStatus.Pending
                }
                when (body.status) {
                    "SUCCESSFUL", "REFUNDED" -> SumupTxStatus.Successful(body.transactionCode)
                    "FAILED", "CANCELLED" -> SumupTxStatus.Failed
                    else -> SumupTxStatus.Pending
                }
            }
            response.status == HttpStatusCode.Unauthorized ||
                response.status == HttpStatusCode.Forbidden -> SumupTxStatus.Unauthorized
            else -> SumupTxStatus.Pending
        }
    }
}
