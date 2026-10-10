# PuzzleIt server

The backend of the app: accounts (required to use it), and a copy of your puzzles, scans and progress photos that follows you from phone to phone.
It is a [PocketBase](https://pocketbase.io) (one small binary, SQLite inside, free, runs on a Raspberry Pi) with the three collections
of [`pb_migrations/1_puzzleit.js`](pb_migrations/1_puzzleit.js):

| Collection | Holds | Rule |
|---|---|---|
| `puzzles` | name, piece count, box photos | each record belongs to its `owner`, nobody else can list, read, change or delete it |
| `scans` | a scanned piece, its leads, your verdict | same |
| `progress_photos` | dated photos of the puzzle's progress | same |

Photos are protected files: they are fetched with a short-lived token, never with a public link.

## Run it

On a Raspberry Pi (64-bit OS) or any machine with Docker:

```bash
cd server
docker compose up -d --build
docker compose exec puzzleit /pb/pocketbase superuser upsert you@example.com a-long-password --dir /pb/pb_data   # admin of the dashboard
```

The server listens on port 8090. The dashboard is at `http://<address>:8090/_/`. The app signs in to **one** server, the one built into it
(`https://puzzleit.lilyan.app`, `PUZZLEIT_SERVER` in `app/build.gradle.kts`). To try a local build against another one:
`./gradlew installDebug -PpuzzleitServer=http://192.168.1.20:8090`.

Without Docker: download the binary for your machine from the PocketBase releases, then
`./pocketbase serve --http=0.0.0.0:8090 --migrationsDir=pb_migrations`.

## What protects your data (nothing here is home-made)

- **Accounts, password hashing, tokens, file tokens and per-record access rules are PocketBase's own.** The only things written for PuzzleIt are
  the collections and their rules (`pb_migrations/1_puzzleit.js`). Checked by hand against a running server: a user cannot list or read another's records
  (404), cannot create a record in another's name, and a photo is only served with a token.
- **On the phone**, the token is kept in Jetpack Security's `EncryptedSharedPreferences` (AES-256-GCM, key in the Android Keystore) and is excluded from
  cloud backups. The password is never stored.
- **In transit**, the app refuses to send a password over plain `http://` unless the server is on a home network (192.168.x.x, 10.x.x.x, 172.16-31.x.x,
  100.64-127.x.x for Tailscale, `*.local`, `localhost`); anywhere else it requires `https://`.
- The app talks to no server but the one built into it.

## Reach it from anywhere: a free Cloudflare Tunnel

A tunnel gives the server an `https://` address on your own domain without opening a port on your router: the Raspberry calls out to Cloudflare, nothing calls in.
Free, but it needs **a domain whose DNS is managed by Cloudflare** (a Cloudflare account is free; the domain itself is not). Without a domain, use Tailscale instead
(the app already accepts its `100.x.x.x` addresses over `http://`).

1. Cloudflare dashboard, Zero Trust, Networks, Tunnels, *Create a tunnel* (Cloudflared). Name it `puzzleit` and copy the **token** it shows.
2. In that tunnel, *Public hostname*: `puzzleit.your-domain.com`, service type `HTTP`, URL `puzzleit:8090` (the compose service name).
3. On the Raspberry, in `server/`:
   ```bash
   printf 'CLOUDFLARED_TOKEN=<the token>\nPUZZLEIT_BIND=127.0.0.1\n' > .env && chmod 600 .env
   docker compose --profile tunnel up -d --build
   ```
4. In the app, the server is `https://puzzleit.your-domain.com`.

Cloudflare terminates the HTTPS connection, so it can technically see the traffic: that is the price of the free tunnel. Lock the sign-up (below) once your account exists.

## Sign in with Google (or GitHub, Microsoft...)

The login screen shows one "Continue with ..." button for every provider switched on in the server, so adding one needs no new app version.
The sign-in itself is PocketBase's OAuth2 flow (the app opens the provider's page in the browser and the server pushes the result back over `/api/realtime`).

1. [Google Cloud console](https://console.cloud.google.com) > APIs & Services > OAuth consent screen: configure it (external, your own email as test user is enough while it
   is not published). Credentials > *Create credentials* > *OAuth client ID* > type **Web application**, with the authorized redirect URI
   `https://puzzleit.your-domain.com/api/oauth2-redirect`.
2. PocketBase dashboard > Collections > `users` > settings (cog) > Authentication > **OAuth2** on, *Add provider* > Google, paste the client ID and secret.
3. Reopen the login screen of the app: the Google button appears. After agreeing in the browser, come back to the app (the browser stays on a "you can close this page" page).

A person who signs in with Google gets a normal account on the server; a provider's account is matched with an existing one by email. Keep the sign-up by password
locked (below) if only the people you invited should get in: OAuth sign-ups are governed by the same `users` create rule.

`POCKETBASE_OAUTH_URL=http://127.0.0.1:8090 ./gradlew testDebugUnitTest --tests "*OAuthIntegrationTest*"` runs that flow against a local server that has a **fake**
Google provider (client ID `dummy`): it checks the list of providers, the realtime handshake and that the code reaches the server, which then fails to trade it with Google.

## Keep it safe

- **Back up `pb_data/`** (accounts, records and photos are all in it).
- On your home network `http://` is enough. Before opening the server to the internet, put HTTPS in front of it. The simplest way is
  [Caddy](https://caddyserver.com) with a domain name: `puzzleit.example.com { reverse_proxy localhost:8090 }`, then use `https://puzzleit.example.com` in the app.
- Anyone who can reach the server can create an account. On the internet, disable sign-up in the dashboard (Collections, users, API rules, create rule: locked)
  once your accounts exist.

## Develop against it

`POCKETBASE_URL=http://127.0.0.1:8090 ./gradlew testDebugUnitTest --tests "*SyncIntegrationTest*"` runs the app's sync against a local server (start one with
`pocketbase serve --dir ./tmp_data --migrationsDir server/pb_migrations`).

## How the sync behaves

The list of puzzles syncs by itself each time it shows (at most once every 30 seconds, only when signed in; offline it is silently skipped); there is no sync button. A puzzle is matched with its copy by the id it was created with, a scan or a photo by its date. Missing items are copied to the side that lacks them, a verdict
given on one phone reaches the others, and a deletion made on a phone is replayed on the server and recorded in the `deletions` collection, so the other phones delete the same
thing at their next sync. A retaken box photo is noticed through the date in its file name (`puzzles.photoId`): the newest photo wins on every phone. The app also syncs when it goes
to the background. The name, piece count and grid of a puzzle carry the date of their last edit (`puzzles.updatedAt`): the latest edit wins on every phone. Known limits: a puzzle edited on two
phones before they sync keeps only the later edit as a whole (the other phone's changes are lost), and nothing syncs while the app is closed (no WorkManager): a phone catches up the
next time its app is opened.
