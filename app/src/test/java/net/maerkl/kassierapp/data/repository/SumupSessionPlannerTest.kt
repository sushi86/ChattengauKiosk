package net.maerkl.kassierapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SumupSessionPlannerTest {

    private val session = SumupSession(accessToken = "tok-1", merchantCode = "M123")

    @Test
    fun `refreshes token and pays when sdk session is valid and merchant matches`() {
        assertEquals(
            SumupSessionAction.Refresh("tok-1"),
            SumupSessionPlanner.plan(loggedIn = true, session = session, sdkMerchantCode = "M123")
        )
    }

    @Test
    fun `logs in again when sdk session lapsed while the app kept running`() {
        assertEquals(
            SumupSessionAction.Login("tok-1"),
            SumupSessionPlanner.plan(loggedIn = false, session = session, sdkMerchantCode = null)
        )
    }

    @Test
    fun `logs in again even when a stale sdk merchant is still reported`() {
        assertEquals(
            SumupSessionAction.Login("tok-1"),
            SumupSessionPlanner.plan(loggedIn = false, session = session, sdkMerchantCode = "M999")
        )
    }

    @Test
    fun `logs out and in again when the sdk is on a foreign merchant`() {
        assertEquals(
            SumupSessionAction.LogoutAndLogin("tok-1"),
            SumupSessionPlanner.plan(loggedIn = true, session = session, sdkMerchantCode = "M999")
        )
    }

    @Test
    fun `blocks when the merchant cannot be verified`() {
        assertEquals(
            SumupSessionAction.Blocked,
            SumupSessionPlanner.plan(loggedIn = true, session = session, sdkMerchantCode = null)
        )
        assertEquals(
            SumupSessionAction.Blocked,
            SumupSessionPlanner.plan(
                loggedIn = true,
                session = SumupSession("tok-1", merchantCode = null),
                sdkMerchantCode = "M123"
            )
        )
    }

    @Test
    fun `checkout result codes that mean the sumup session died`() {
        // ERROR_NOT_LOGGED_IN
        assertEquals(true, SumupSessionPlanner.isSessionExpiredResult(8))
        // ERROR_INVALID_TOKEN
        assertEquals(true, SumupSessionPlanner.isSessionExpiredResult(5))
        // ERROR_TRANSACTION_FAILED / abgebrochen durch Kunden
        assertEquals(false, SumupSessionPlanner.isSessionExpiredResult(2))
        assertEquals(false, SumupSessionPlanner.isSessionExpiredResult(null))
    }
}
