# MCVerseRegister

A Paper plugin that lets players link their Minecraft account to the MCVerse website by registering an email address, and syncs server-wide plus per-player stats to the backend.

## Features

- `/register <email>` — link a Minecraft account to the MCVerse website
- Already-linked `/register` shows a clickable **Click to login** URL (no email resend)
- `/unregister` — unlink website email from the UUID (does not delete synced data)
- Registration status checked on login; unlinked players are shown a hint
- Username, balance, groups, clan, and claims sync on join for **every** UUID (website account optional)
- Vanilla Minecraft statistics snapshot on quit (~once per 24h) for every player
- Daily server-wide PLAN/LuckPerms/Vault/GriefPrevention snapshot at a configured wall-clock time
- `/mcvadmin syncstats` — force one server-stats snapshot without changing the daily slot
- Per-player cooldown to prevent spam
- All messages configurable via `config.yml`

## Requirements

- Paper 1.21.11+ (compiled against `paper-api 1.21.11-R0.1-SNAPSHOT`; vanilla collectors iterate registries so a later 26.2 bump does not require a payload-schema change)
- Java 21+
- MCVerse backend API accessible from the server
- Optional soft dependencies: Vault, LuckPerms, SimpleClans, GriefPrevention, Plan

## Installation

1. Build the jar: `mvn package` (output in `target/`)
2. Drop the jar into your server's `plugins/` folder
3. Start the server to generate `plugins/MCVerseRegister/config.yml`
4. Set `api-base-url` in `config.yml` and reload

## Commands

| Command | Description | Permission |
|---|---|---|
| `/register <email>` | Link your account to MCVerse | `mcverse.register` |
| `/unregister` | Unlink your account from MCVerse | `mcverse.unregister` |
| `/mcvadmin status [player]` | Show website-link status | `mcverse.admin` |
| `/mcvadmin remove <player>` | Unlink a player's website account | `mcverse.admin` |
| `/mcvadmin syncstats` | Force a server stats snapshot | `mcverse.admin` |

Aliases: `/register` → `webregister`, `link` · `/unregister` → `webunregister`, `unlink`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `mcverse.register` | Use `/register` | op |
| `mcverse.unregister` | Use `/unregister` | op |
| `mcverse.register.bypass-cooldown` | Skip the registration cooldown | op |
| `mcverse.admin` | MCVerse admin commands | op |

## Configuration

See `src/main/resources/config.yml` for the full file. Important keys:

```yaml
api-base-url: "https://api.mcverse.city"
login-url: "https://www.mcverse.city/login"
request-timeout: 5000

sync:
  server-stats:
    enabled: true
    run-at: "10:00"
    timezone: "America/Chicago"
    catch-up-on-startup: true
    main-world: world
    plan-active-playtime-ms: 1800000
    ranks:
      default: default
      member: member
      regular: regular
      citizen: citizen
  vanilla-stats:
    enabled: true
    min-interval-hours: 24
```

Last successful daily run is stored in `plugins/MCVerseRegister/server-stats-last-run.yml`. Vanilla-stats throttle timestamps are stored in `plugins/MCVerseRegister/vanilla-stats-last-sync.yml`.

## API Integration

The plugin communicates with the MCVerse backend using these endpoints:

| Method | Endpoint | Used for |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register / attach email |
| `DELETE` | `/api/v1/auth/player/{uuid}` | Unlink website email from UUID (must not delete the `players` row or synced stats) |
| `GET` | `/api/v1/auth/player/{uuid}` | Website-link status on login (`registered` means email linked) |
| `POST` | `/api/v1/sync/players/{uuid}/username` | Username upsert for linked and unlinked UUIDs |
| `POST` | `/api/v1/sync/players/{uuid}/balance` | Vault balance snapshot |
| `POST` | `/api/v1/sync/players/{uuid}/groups` | LuckPerms primary group + groups |
| `POST` | `/api/v1/sync/players/{uuid}/simpleclans` | SimpleClans clan/tag/role |
| `POST` | `/api/v1/sync/players/{uuid}/griefprevention-claims` | GriefPrevention claims summary/list |
| `POST` | `/api/v1/sync/players/{uuid}/vanilla-stats` | Mojang-shaped vanilla stats on quit |
| `POST` | `/api/v1/sync/server/stats` | Daily server-wide snapshot |

The `User-Agent` header is set to `MCVerseRegister/1.0.0` on all requests. HTTP calls run off the main thread (vanilla stats are snapshotted on the main thread during quit, then POSTed async).

On player join, username + diagnostic fanout run for every UUID. `GET /auth/player/{uuid}` 404 or `registered: false` means **no website account**, not “no Minecraft player.” The plugin still POSTs username and diagnostics so the backend can get-or-create a stub `players` row. 404 on a sync route is logged and retried next join; it does not cache the player as “never sync.”

`/unregister` and `/mcvadmin remove` still send `DELETE /api/v1/auth/player/{uuid}`. The backend must treat that as an **UPDATE** that clears email / website user id only. CASCADE on `players` delete can remain for a future GDPR hard-delete path.

### Register request body

```json
{
  "minecraftUuid": "uuid-here",
  "minecraftUsername": "PlayerName",
  "email": "player@example.com"
}
```

### Handled response codes

| Code | Meaning |
|---|---|
| 200 | Email updated / sync applied / unlink succeeded |
| 201 | Account created |
| 403 | Forbidden |
| 404 | Route or player missing (retry sync later; unlink “not registered”) |
| 409 | Email already in use |
| 422 | Invalid input |
| 429 | Rate limited |
| 5xx | Retryable |

## Backend ingest guide (MariaDB + Drizzle)

Backend lives outside this plugin repo (`backend/src/db/index.js`, `backend/src/db/schema.js`). **Do not use Postgres types.** Stack is MariaDB + Drizzle.

Type map:

- `timestamptz` → `TIMESTAMP`
- `uuid` → `VARCHAR(36)` (same as `players.minecraft_uuid`)
- `numeric` (server economy total) → `DECIMAL(20,2)` (wider than per-player `DECIMAL(14,2)`)
- `double precision` → `DOUBLE`
- `ON CONFLICT ... DO UPDATE` → `INSERT ... ON DUPLICATE KEY UPDATE` with `col = COALESCE(VALUES(col), col)` so JSON `null` does not blank a column
- Index `value` **without** `DESC` (MariaDB). `CHECK (value >= 0)` optional; enforce in Zod.

Migration: `backend/sql/036_server_and_vanilla_stats_sync.sql` (next after `035_ban_appeal_attachments.sql`). Ignore `2026-04-30-submitted-minecraft-username.sql` as “latest”. Mirror tables in `schema.js` and export them.

`stat_date`: calendar date of `observedAt` in **America/Chicago**. Add a small America/Chicago date helper for `stat_date` (tournament-only helper lives in `backend/src/utils/tournamentTime.js` today).

Auth / 422 / 5xx match existing player sync. User-Agent `MCVerseRegister/1.0.0`.

Vanilla identity: **FK to `players.id`**, plus stored `minecraft_uuid`. Get-or-create stub on ingest. `/register` on an existing UUID attaches email to that stub.

Join diagnostic sync must accept unlinked UUIDs the same way (get-or-create stub, 200, `registered: false` if no email). Do not 404.

### 1. Server-wide snapshot (daily history)

`POST /api/v1/sync/server/stats`

```sql
CREATE TABLE server_stats_daily (
  stat_date DATE NOT NULL PRIMARY KEY,
  observed_at TIMESTAMP NOT NULL,
  week_start TIMESTAMP NULL,
  players_joined BIGINT NULL,
  rank_default INT NULL,
  rank_member INT NULL,
  rank_regular INT NULL,
  rank_citizen INT NULL,
  citizen_all INT NULL,
  economy_total DECIMAL(20,2) NULL,
  plan_regular_players INT NULL,
  total_playtime_ms BIGINT NULL,
  minecraft_day BIGINT NULL,
  average_tps DOUBLE NULL,
  player_kills_all_time BIGINT NULL,
  deaths_all_time BIGINT NULL,
  mob_kills_all_time BIGINT NULL,
  claimed_area BIGINT NULL,
  player_kills_this_week BIGINT NULL,
  deaths_this_week BIGINT NULL,
  mob_kills_this_week BIGINT NULL
);
```

Upsert with `INSERT ... ON DUPLICATE KEY UPDATE` and `col = COALESCE(VALUES(col), col)` for every metric column.

Website latest: `ORDER BY stat_date DESC LIMIT 1`. History: `WHERE stat_date >= ...`. Return `{ "success": true, "updated": true }`.

`rank_citizen` is LuckPerms **primary** group `citizen`. `citizen_all` counts anyone who has/inherits `citizen` (including `mod` / `supporter` primaries).

### 2. Per-player vanilla stats

`POST /api/v1/sync/players/{uuid}/vanilla-stats`

Lookup `players` by `minecraft_uuid`. If missing, insert a stub player (new `id`, `minecraft_uuid`, username from payload, not linked). Then upsert `player_vanilla_sync` + flatten `stats`. If `observedAt` is older than `last_synced_at`, 200 `updated: false`. Delete omitted keys. Do not increment. Body limit ~1 MB. Zod: `value >= 0`. Category and `stat_key` are open `text`, not enums.

```sql
CREATE TABLE player_vanilla_sync (
  player_id VARCHAR(36) NOT NULL PRIMARY KEY,
  minecraft_uuid VARCHAR(36) NOT NULL,
  last_synced_at TIMESTAMP NOT NULL,
  minecraft_version VARCHAR(64) NULL,
  UNIQUE KEY uniq_player_vanilla_sync_uuid (minecraft_uuid),
  CONSTRAINT player_vanilla_sync_player_fk
    FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE
);

CREATE TABLE player_vanilla_stat (
  player_id VARCHAR(36) NOT NULL,
  minecraft_uuid VARCHAR(36) NOT NULL,
  category VARCHAR(128) NOT NULL,
  stat_key VARCHAR(128) NOT NULL,
  value BIGINT NOT NULL,
  PRIMARY KEY (player_id, category, stat_key),
  KEY player_vanilla_stat_category_idx (player_id, category, value),
  CONSTRAINT player_vanilla_stat_sync_fk
    FOREIGN KEY (player_id) REFERENCES player_vanilla_sync (player_id) ON DELETE CASCADE
);
```

Profile fields (`minecraftUsername`, `firstPlayed`, `lastSeen`, `balance`, `primaryGroup`) update existing player/balance/groups tables. Optional: denormalize `play_time` / `deaths` / `player_kills` / `mob_kills` onto `player_vanilla_sync` for cheap listing.

### 3. Admin UI (website)

**Overview → Server Stats** — table of `server_stats_daily`, newest first. No charts in v1.

**Overview → Player Stats** — searchable by `players.minecraft_username`. Join `players`, `player_balances_sync`, groups sync, `player_vanilla_sync` / `player_vanilla_stat`. Include stub (unlinked) players. Columns: name, first joined, last seen, balance, website linked?, rank, play time / deaths / player kills / mob kills. No mined-by-block UI in v1.

## Project Structure

```
src/main/java/net/mcverse/register/
├── MCVerseRegister.java
├── api/                 # HTTP client + sync DTOs
├── commands/            # register / unregister / mcvadmin
├── integration/         # Soft-dep adapters + server-stats collectors
├── listeners/           # Join (sync) / quit (vanilla snapshot)
├── service/             # Username, diagnostics, server stats, vanilla stats
└── util/
```
