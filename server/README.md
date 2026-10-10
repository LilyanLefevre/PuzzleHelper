# PuzzleIt server

The optional backend of the app: accounts, and a copy of your puzzles, scans and progress photos that follows you from phone to phone.
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

The server listens on port 8090. The dashboard is at `http://<address>:8090/_/`. In the app, open the account button (top right of the puzzle list),
type `http://<address>:8090` and create your account.

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
- The app talks to no server but the one you type.

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

The list of puzzles syncs by itself each time it shows (at most once every 30 seconds, only when signed in; offline it is silently skipped), and the account
screen has a "Sync now" button. A puzzle is matched with its copy by the id it was created with, a scan or a photo by its date. Missing items are copied to the side that lacks them, a verdict
given on one phone reaches the others, and a deletion made on a phone is replayed on the server. Known limits: a puzzle renamed on two phones keeps the name of the
last phone that syncs, and a deletion is not yet propagated to the *other* phones (they would send the item again). There is no background sync while the app is closed.
