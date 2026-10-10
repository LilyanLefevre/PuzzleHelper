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

A puzzle is matched with its copy by the id it was created with, a scan or a photo by its date. Missing items are copied to the side that lacks them, a verdict
given on one phone reaches the others, and a deletion made on a phone is replayed on the server. Known limits: a puzzle renamed on two phones keeps the name of the
last phone that syncs, and a deletion is not yet propagated to the *other* phones (they would send the item again).
