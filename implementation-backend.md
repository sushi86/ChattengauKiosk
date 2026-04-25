# SumUp OAuth Multi-Tenant Integration — Backend-Implementierung

Dieses Dokument ist die Umsetzungsanweisung für das Backend-Projekt unter `/Users/sascha/Code/vereinskasse/webapp`. Ziel: Die Android-Kassierapp soll sich ohne SumUp-Credentials anmelden können. Pro Verein wird der OAuth-Flow einmalig durchlaufen; das Backend hält den Refresh-Token und liefert Tablets bei Bedarf frische Access-Tokens.

## Kontext

- **Ebene**: SumUp-Anbindung gilt **pro Verein** (Club), **nicht** pro Team. Ein Verein = eine SumUp-Integration, die für alle Teams des Vereins gilt.
- **OAuth-App**: Eine einzige zentrale OAuth-App des Plattform-Betreibers (Bundle ID `net.maerkl.kassierapp`, Client ID `cc_classic_jjl8mtix13HmAN2LiLoPvlkjmLYtV`). Jeder Verein authorisiert **diese eine** App gegen sein eigenes SumUp-Händler-Konto via Standard-OAuth. Keine OAuth-App pro Kunde.
- **Gelder**: Zahlungen fließen direkt SumUp → Bankkonto des Vereins. Das Backend ist nie im Zahlungsfluss, hält keine Gelder.
- **Unterschied zu PayPal**: PayPal im bestehenden Code nutzt *client_credentials* (M2M) und ist team-scoped. SumUp nutzt *authorization_code* (User-Consent erforderlich) und ist verein-scoped.

## Vorhandene Muster zum Orientieren

Diese existierenden Bausteine nachnutzen — kein neues Rad erfinden:

- **Secret-Manager-Pattern**: `functions/src/secrets/manager.ts` — insbesondere `paypalSecretName(clubId, teamId, type)` L88-98
- **Connect-Flow-Pattern**: `functions/src/api/v1/paypal.ts` L38-63 (`POST /connect` mit Validierung + Secret-Store + Flag-Update)
- **OAuth-Client-Pattern**: `functions/src/paypal/client.ts` L23-68 (`getAccessToken` mit In-Memory-Cache und Expiration)
- **Middleware**: `authMiddleware`, `clubMiddleware`, `adminOnly` — wie bei PayPal verwenden
- **Zod-Schemas** in `/types/` — alle neuen Datenmodelle dort

## Globale Konfiguration (Secret Manager, einmalig)

Als globale Secrets im Google Cloud Secret Manager anlegen (nicht per-Tenant):

- `sumup_client_id` — Application Identifier der zentralen OAuth-App
- `sumup_client_secret` — privat, nur Backend
- `sumup_redirect_uri` — z. B. `https://<backend-host>/api/v1/sumup/callback` — muss **exakt** im SumUp-Dashboard als autorisierte Weiterleitungs-URL eingetragen sein
- `sumup_state_secret` — HMAC-Schlüssel für die Signatur des OAuth-State-Parameters

Werte müssen beim initialen Deploy einmalig manuell via `gcloud secrets versions add` befüllt werden. `scripts/setup-gcp.sh` soll die Secret-Stubs automatisch anlegen, wenn nicht vorhanden. In der Deploy-Doku entsprechend dokumentieren.

## Pro-Verein Secrets

Neue Funktion in `functions/src/secrets/manager.ts`:

```ts
export function sumupSecretName(
  clubId: string,
  type: "refresh_token"
): string {
  return `sumup_${type}_${clubId}`;
}
```

**Access-Tokens** werden **nicht** im Secret Manager gespeichert — nur In-Memory-Cache (wie PayPals `getAccessToken`), da sie nur ~1h gültig sind und ständig neu erzeugt werden.

## Schema-Erweiterungen

**`types/verein.ts`** — neue Felder auf dem Verein-/Club-Schema:

```ts
sumupConnected: z.boolean(),
sumupMerchantCode: z.string().optional(),
sumupMerchantName: z.string().optional(),
sumupRefreshTokenUpdatedAt: z.custom<Timestamp>().optional(),
```

Default `sumupConnected: false` bei Verein-Erstellung setzen (`on-verein-create.ts`).

**Keine Änderungen** an `types/team.ts`, `types/geraet.ts`, `types/aktivierungscode.ts` für SumUp. Der bestehende Pairing-Flow (Aktivierungscode → Gerät kennt `vereinId`) reicht aus; das Tablet braucht nur `vereinId`/`clubId`, um den Token-Endpoint zu erreichen.

## Neue API-Routen

Datei: `functions/src/api/v1/sumup.ts`

Router-Präfix: `/api/v1/clubs/:clubId/sumup`
Middleware-Stack: `authMiddleware` → `clubMiddleware` (außer bei `/callback`)

### `GET /connect-url` (adminOnly)

Generiert die SumUp-Authorize-URL für den Consent-Flow.

- Erzeugt einen signierten `state`-Token (HMAC-JWT mit Payload `{clubId, nonce, exp: now+10min}`, signiert mit `sumup_state_secret`)
- Speichert den `nonce` kurzfristig in Firestore (`/sumup_pending_auth/{nonce}` mit TTL-Feld), um Einmal-Verbrauch durchzusetzen
- Antwort:
  ```json
  {
    "authorizeUrl": "https://api.sumup.com/authorize?response_type=code&client_id=...&redirect_uri=...&scope=user.app-settings+user.profile_readonly+transactions.history+transactions.refund&state=..."
  }
  ```
- Das Admin-UI öffnet diese URL in einem neuen Tab

**Scope-Liste**: `user.app-settings user.profile_readonly transactions.history transactions.refund`. Falls `transactions.refund` als restricted markiert ist, muss der Vereins-Admin das einmalig beim SumUp-Support freischalten lassen — in der Setup-Doku hinweisen.

### `GET /callback` (public, kein `authMiddleware`/`clubMiddleware`)

Fängt den SumUp-Redirect ab: `?code=...&state=...`.

1. `state`-Signatur verifizieren
2. `nonce` aus State gegen `/sumup_pending_auth/{nonce}` prüfen; bei Fehler → HTTP 400. Bei Erfolg: Doc löschen (Einmal-Verbrauch)
3. `clubId` aus verifiziertem State extrahieren
4. Token-Exchange gegen `https://api.sumup.com/token`:
   ```
   POST application/x-www-form-urlencoded
   grant_type=authorization_code
   client_id={sumup_client_id}
   client_secret={sumup_client_secret}
   code={code}
   redirect_uri={sumup_redirect_uri}
   ```
5. Antwort enthält `access_token`, `refresh_token`, `expires_in`
6. Refresh-Token speichern: `setSecret(sumupSecretName(clubId, "refresh_token"), refresh_token)`
7. Merchant-Info abrufen: `GET https://api.sumup.com/v0.1/me` mit dem frischen Access-Token → `merchant_profile.merchant_code`, `merchant_profile.company_name`
8. Verein-Doc updaten:
   ```ts
   sumupConnected: true,
   sumupMerchantCode: ...,
   sumupMerchantName: ...,
   sumupRefreshTokenUpdatedAt: FieldValue.serverTimestamp()
   ```
9. Response: einfache HTML-Bestätigungsseite („SumUp erfolgreich verbunden mit Konto X. Du kannst dieses Fenster schließen.")

### `DELETE /connect` (adminOnly)

- `deleteSecret(sumupSecretName(clubId, "refresh_token"))`
- Verein-Doc: `sumupConnected: false`, `sumupMerchantCode`/`sumupMerchantName` entfernen

### `GET /token` — wird vom Tablet aufgerufen

Middleware: `authMiddleware` → `clubMiddleware`. Role-Check: jede Rolle mit Verein-Zugriff reicht (auch Kassierer), also **nicht** `adminOnly`.

**Logik**:

1. In-Memory-Cache prüfen (Key: `${clubId}`, Value: `{accessToken, expiresAt}`); wenn noch ≥5 Minuten gültig → direkt zurückgeben
2. Sonst: Refresh-Token via `getSecret(sumupSecretName(clubId, "refresh_token"))` laden. Fehlt er → HTTP 409 `{error: "not_connected"}`; Tablet muss Admin benachrichtigen
3. Token-Refresh gegen SumUp:
   ```
   POST https://api.sumup.com/token
   grant_type=refresh_token
   client_id={sumup_client_id}
   client_secret={sumup_client_secret}
   refresh_token={stored}
   ```
4. **SumUp rotiert den Refresh-Token**. Wenn die Antwort ein neues `refresh_token`-Feld enthält → via `setSecret(...)` überschreiben **und** `sumupRefreshTokenUpdatedAt` im Verein-Doc updaten. Vor dem Schreiben eine Firestore-Transaction (Verein-Doc als Lock) nehmen, um Races bei parallelen Tablet-Anfragen auszuschließen
5. In-Memory-Cache setzen (`expiresAt = now + expires_in - 60s Puffer`)
6. Antwort an Tablet:
   ```json
   {
     "access_token": "...",
     "expires_in": 3543
   }
   ```

**Fehlerfall `invalid_grant`** (Refresh-Token tot):

- Verein-Doc: `sumupConnected: false`
- Log + Cloud Monitoring Alert
- HTTP 503 an Tablet mit `{error: "reauthorization_required"}`
- Admin muss neu durch `/connect-url` → Consent-Flow

### `GET /status` (adminOnly)

Diagnose-Endpoint für Admin-UI.

Antwort:
```json
{
  "connected": true,
  "merchantCode": "...",
  "merchantName": "...",
  "tokenUpdatedAt": "2026-04-24T10:00:00Z",
  "daysUntilRefreshExpiry": 52
}
```

## Scheduled Function — Keep-Alive

Neue Datei: `functions/src/sumup/keep-alive.ts`

```ts
export const sumupTokenKeepAlive = onSchedule(
  {
    schedule: "every monday 03:00",
    timeZone: "Europe/Berlin",
    region: "europe-west3",
  },
  async () => {
    // Iteriere alle /clubs mit sumupConnected == true
    // Für jeden Club: erzwinge Token-Refresh (Cache-Bypass), aktualisiere Refresh-Token wenn rotiert
    // Fehler pro Club loggen, Function nicht abbrechen
  }
);
```

Begründung: SumUps Refresh-Token hat ~60 Tage Lebensdauer und rotiert bei jedem Refresh. Ohne regelmäßige Nutzung (z. B. wenn ein Tablet lang ausgeschaltet ist) läuft er ab. Ein wöchentlicher Keep-Alive verhindert das zuverlässig.

## Firestore Rules

Ergänzen in `firestore.rules`:

- Neue Felder auf Verein-Doc (`sumupConnected`, `sumupMerchantCode`, `sumupMerchantName`, `sumupRefreshTokenUpdatedAt`) sind für Clients **read-only**; geschrieben werden sie nur vom Backend
- Keine Client-Reads/Writes auf `/sumup_pending_auth/*` (nur Backend)

## Admin-UI (Next.js)

Auf der **Verein-Settings-Seite** einen Block **"SumUp-Anbindung"** — analog zum PayPal-Block, aber auf Verein-Ebene (nicht Team-Ebene):

- Wenn `sumupConnected == false`:
  - Button "Mit SumUp verbinden" → ruft `GET /connect-url` → öffnet `authorizeUrl` in neuem Tab
  - Nach erfolgreichem Callback: Seite refreshen, um neuen Status zu zeigen
- Wenn `sumupConnected == true`:
  - Anzeige: `sumupMerchantName`, verbunden-seit-Datum (`sumupRefreshTokenUpdatedAt`)
  - Button "Verbindung trennen" → `DELETE /connect`
- Kein Client-Secret-Eingabefeld (anders als PayPal): Die OAuth-App gehört dem Plattform-Betreiber, die Credentials liegen im Secret Manager

## Deployment / Secret Manager Setup

`scripts/setup-gcp.sh` erweitern:

- Beim Deploy automatisch Secret-Stubs für `sumup_client_id`, `sumup_client_secret`, `sumup_redirect_uri`, `sumup_state_secret` anlegen, falls nicht existent
- Werte müssen vom Betreiber einmalig manuell mit `gcloud secrets versions add` befüllt werden — in der README/Deploy-Doku dokumentieren

## Tests

Minimal:

- Unit-Test für `sumupSecretName`
- Unit-Test für Token-Exchange-Logik (mit gemocktem `fetch`, happy path + `invalid_grant`)
- Unit-Test für State-Signatur-Verifikation
- Firestore-Rules-Test für den neuen `/sumup_pending_auth/*`-Pfad

## Naming-Konvention

Bestehendes Mischmuster befolgen:
- Deutsche Domain-Begriffe: `verein`, `geraet`, `aktivierungscode`
- Technische/integration-spezifische Begriffe englisch: `sumup`, `oauth`, `refresh_token`, `callback`

## Offene Punkte

- Welche Scopes hat der Plattform-Betreiber bei SumUp bereits freigeschaltet? Bauchansatz `user.app-settings user.profile_readonly transactions.history transactions.refund` reicht für Card-present-Payments via SDK + Refund via REST. **Nicht** `payments` oder `payment_instruments` anfordern — nicht nötig und restricted.
- Falls der Agent im bestehenden Code Abweichungen vom PayPal-Muster findet, die den Brief invalidieren: flaggen statt raten.

## Zusammenfassung der Änderungen gegenüber PayPal-Muster

| | PayPal (bestehend) | SumUp (neu) |
|---|---|---|
| Ebene | Team | Verein/Club |
| Flow | client_credentials | authorization_code |
| Client Credentials | pro Team (User-Input) | zentral (Plattform-Betreiber) |
| User-Consent | nein | ja, einmalig via Browser-Redirect |
| Token-Typ | kurzlebig, on-demand erzeugt | Refresh-Token persistent + Access-Token-Cache |
| Keep-Alive nötig | nein | ja (60-Tage-Refresh-Token-Limit) |
