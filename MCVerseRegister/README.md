# MCVerseRegister

A Paper plugin that lets players link their Minecraft account to the MCVerse website by registering an email address.

## Features

- `/register <email>` — link a Minecraft account to the MCVerse website
- `/unregister` — unlink a Minecraft account
- Registration status checked on login; unregistered players are shown a hint
- Username drift is reconciled on login (backend name check + sync when mismatch is found)
- Admin diagnostics sync on login: balance, groups, clan info, and claims summary/list
- Per-player cooldown to prevent spam
- All messages configurable via `config.yml`

## Requirements

- Paper 1.21.1+
- Java 21+
- MCVerse backend API accessible from the server

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

Aliases: `/register` → `webregister`, `link` · `/unregister` → `webunregister`, `unlink`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `mcverse.register` | Use `/register` | op |
| `mcverse.unregister` | Use `/unregister` | op |
| `mcverse.register.bypass-cooldown` | Skip the registration cooldown | op |
| `mcverse.admin` | MCVerse admin commands | op |

## Configuration

```yaml
# Base URL of the MCVerse backend API (no trailing slash)
api-base-url: "https://api.mcverse.city"

# Cooldown between /register attempts per player (seconds)
register-cooldown: 60

# HTTP request timeout (milliseconds)
request-timeout: 5000

# Messages (supports & color codes)
messages:
  prefix: "&8[&bMCVerse&8] &r"
  # ... (see config.yml for all keys)
```

## API Integration

The plugin communicates with the MCVerse backend using these endpoints:

| Method | Endpoint | Used for |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a player |
| `DELETE` | `/api/v1/auth/player/{uuid}` | Unregister a player |
| `GET` | `/api/v1/auth/player/{uuid}` | Check registration status on login |
| `POST` | `/api/v1/sync/players/{uuid}/username` | Sync backend username when player name changed |
| `POST` | `/api/v1/sync/players/{uuid}/balance` | Sync Vault balance snapshot |
| `POST` | `/api/v1/sync/players/{uuid}/groups` | Sync LuckPerms primary group + groups |
| `POST` | `/api/v1/sync/players/{uuid}/simpleclans` | Sync SimpleClans clan/tag/role |
| `POST` | `/api/v1/sync/players/{uuid}/griefprevention-claims` | Sync GriefPrevention claims summary/list |

The `User-Agent` header is set to `MCVerseRegister/1.0.0` on all requests. All HTTP calls are made asynchronously to avoid blocking the main thread.

On player join, the plugin performs a case-sensitive compare between backend `minecraftUsername` and the current in-game username. If they differ, it calls the username sync endpoint with `minecraftUsername` and `observedAt` (ISO-8601). The sync is idempotent (`changed=false` means no-op), and transient transport failures use a fixed retry policy of up to 3 attempts with exponential backoff plus small jitter.

For diagnostic sync fanout, the plugin gathers snapshots from soft dependencies (Vault, LuckPerms, SimpleClans, GriefPrevention) when available, then posts each category asynchronously. Category syncs include debounce (`sync.min-sync-interval-ms`), retries on network/5xx responses, and a temporary unlinked cache after 404 responses (`sync.unlinked-cache-ttl-ms`) to reduce repeated failed calls.

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
| 200 | Email updated |
| 201 | Account created |
| 403 | Forbidden |
| 404 | Player not found (unregister) |
| 409 | Email already in use |
| 422 | Invalid input |
| 429 | Rate limited |

## Project Structure

```
src/main/java/net/mcverse/register/
├── MCVerseRegister.java          # Plugin entry point
├── api/
│   ├── ApiResponse.java          # HTTP response wrapper
│   └── MCVerseApiClient.java     # Backend API calls
├── commands/
│   ├── RegisterCommand.java      # /register handler
│   └── UnregisterCommand.java    # /unregister handler
├── listeners/
│   └── PlayerListener.java       # Join (status check) / quit (cleanup)
└── util/
    ├── CooldownManager.java      # Per-player cooldown tracking
    ├── RegistrationCache.java    # In-memory registration status cache
    └── MessageUtil.java          # Color code formatting
```
