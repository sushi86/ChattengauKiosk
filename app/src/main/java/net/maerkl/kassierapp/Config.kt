package net.maerkl.kassierapp

object Config {
    const val SUMUP_AFFILIATE_KEY = "sup_afk_rHWkZweea7fqDcFbRWNYqQ1KSpTYQ87f"
    const val BACKEND_BASE_URL = "https://europe-west3-vereinskasse-prod.cloudfunctions.net"
    const val FIREBASE_FUNCTIONS_REGION = "europe-west3"

    // Fixed App Check debug secret for debug builds, so it survives reinstalls
    // instead of a new random one being generated each time (which would need
    // re-registering in the Firebase Console every time). Must be registered
    // once under App Check → Apps → Kassierapp → Debug tokens.
    const val APP_CHECK_DEBUG_TOKEN = "e704e557-df2a-4e9e-8547-cc6b869cdfcc"
}
