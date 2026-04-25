package net.maerkl.kassierapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import net.maerkl.kassierapp.data.remote.FirebasePairingService
import net.maerkl.kassierapp.data.remote.PairingError
import net.maerkl.kassierapp.data.remote.PairingThrowable

class PairingRepository(
    private val service: FirebasePairingService,
    private val auth: FirebaseAuth,
    private val sessionRepo: DeviceSessionRepository
) {
    suspend fun pair(code: String): Result<Unit> {
        val activation = service.activate(code)
        val creds = activation.getOrElse { return Result.failure(it) }
        return try {
            auth.signInWithCustomToken(creds.customToken).await()
            sessionRepo.markPaired(creds.vereinId, creds.geraetId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(PairingThrowable(PairingError.Unknown(e.message)))
        }
    }
}
