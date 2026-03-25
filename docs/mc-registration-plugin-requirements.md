# MCVerse Registration Plugin — Requirements Specification

> Standalone Spigot/Paper plugin for Minecraft Java Edition 1.21.1
> Bridges in-game player identity to the MCVerse website auth system

---

## Overview

The MCVerse Registration Plugin allows players to link their Minecraft account to the MCVerse website by registering an email address in-game. The plugin sends the player's UUID, username, and email to the MCVerse backend API, which creates their web account and sends a magic-link login email.

This is a **standalone plugin** with no dependencies on other plugins.

---

## Target Environment

| Property | Value |
|----------|-------|
| Server software | Spigot or Paper (Paper preferred) |
| Minecraft version | 1.21.1 |
| Java version | 21+ |
| API version | 1.21 |
| Backend API | MCVerse Express API (`/api/v1/auth/register`) |

---

## Commands

### `/register <email>`

| Property | Value |
|----------|-------|
| Permission | `mcverse.register` (default: `true` — all players) |
| Aliases | `/webregister`, `/link` |
| Usage | `/register player@example.com` |
| Cooldown | 60 seconds per player |

**Behavior:**

1. Validate email format client-side (basic regex check)
2. Send HTTP POST to the backend API:
   ```
   POST {api-base-url}/api/v1/auth/register
   Content-Type: application/json

   {
     "minecraftUuid": "player-uuid-with-dashes",
     "minecraftUsername": "PlayerName",
     "email": "player@example.com"
   }
   ```
3. Display result message to the player in chat:
   - **Success (201):** "Your account has been created! Check your email for a login link."
   - **Success (200):** "Your email has been updated! Check your email for a new login link."
   - **Conflict (409):** "That email is already registered to another player."
   - **Rate limited (429):** "Too many attempts. Please wait a moment and try again."
   - **Network error:** "Could not reach the MCVerse website. Please try again later."
4. Log the registration attempt (player name, success/failure) to console

**Validation rules (client-side):**
- Email must contain `@` and a `.` after the `@`
- Email length must be between 5 and 255 characters
- If invalid: "Please enter a valid email address. Usage: /register your@email.com"

### `/unregister`

| Property | Value |
|----------|-------|
| Permission | `mcverse.register` |
| Aliases | `/webunregister`, `/unlink` |
| Usage | `/unregister` |

**Behavior:**
- Reserved for future implementation
- Currently returns: "This command is not yet available. Contact an admin to unlink your account."

---

## Configuration

### `config.yml`

```yaml
# MCVerse Registration Plugin Configuration

# Base URL of the MCVerse backend API (no trailing slash)
api-base-url: "http://127.0.0.1:3001/api/v1"

# Cooldown between /register attempts per player (seconds)
register-cooldown: 60

# HTTP request timeout (milliseconds)
request-timeout: 5000

# Messages (supports & color codes and MiniMessage on Paper)
messages:
  prefix: "&8[&bMCVerse&8] &r"
  register-success-new: "&aYour account has been created! Check your email for a login link."
  register-success-update: "&aYour email has been updated! Check your email for a new login link."
  register-conflict: "&cThat email is already registered to another player."
  register-rate-limited: "&eToo many attempts. Please wait and try again."
  register-error: "&cCould not reach the MCVerse website. Please try again later."
  register-invalid-email: "&cPlease enter a valid email address. Usage: /register your@email.com"
  register-cooldown: "&eYou must wait {seconds} seconds before registering again."
  register-usage: "&eUsage: /register your@email.com"
  unregister-unavailable: "&eThis command is not yet available. Contact an admin to unlink your account."
```

### `plugin.yml`

```yaml
name: MCVerseRegister
version: 1.0.0
main: net.mcverse.register.MCVerseRegister
api-version: "1.21"
description: Link your Minecraft account to the MCVerse website
authors: [MCVerse]
website: https://mcverse.net

commands:
  register:
    description: Register your email for website access
    usage: /register <email>
    aliases: [webregister, link]
  unregister:
    description: Unlink your email from website access
    usage: /unregister
    aliases: [webunregister, unlink]

permissions:
  mcverse.register:
    description: Allows players to register for the website
    default: true
  mcverse.register.bypass-cooldown:
    description: Bypass the registration cooldown
    default: op
  mcverse.admin:
    description: MCVerse admin commands
    default: op
```

---

## Technical Requirements

### HTTP Client

- Use Java's built-in `java.net.http.HttpClient` (Java 11+, no external dependencies)
- All HTTP requests **must** run asynchronously (never block the main server thread)
- Use `Bukkit.getScheduler().runTaskAsynchronously()` for the HTTP call
- Use `Bukkit.getScheduler().runTask()` to send chat messages back on the main thread
- Set a configurable timeout (default: 5 seconds)
- Set `User-Agent: MCVerseRegister/1.0.0`

### Async Pattern

```java
// Pseudocode for the /register command
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("This command can only be used by players.");
        return true;
    }

    if (args.length != 1) {
        player.sendMessage(prefix + messages.get("register-usage"));
        return true;
    }

    String email = args[0];

    // Validate email format (sync, fast)
    if (!isValidEmail(email)) {
        player.sendMessage(prefix + messages.get("register-invalid-email"));
        return true;
    }

    // Check cooldown (sync, fast)
    if (isOnCooldown(player) && !player.hasPermission("mcverse.register.bypass-cooldown")) {
        player.sendMessage(prefix + messages.get("register-cooldown")
            .replace("{seconds}", getRemainingCooldown(player)));
        return true;
    }

    // Set cooldown
    setCooldown(player);

    // Run HTTP request async
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        try {
            ApiResponse response = callRegisterApi(player.getUniqueId(), player.getName(), email);

            // Send response message on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(prefix + getMessageForResponse(response));
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Registration failed for " + player.getName() + ": " + e.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(prefix + messages.get("register-error"));
            });
        }
    });

    return true;
}
```

### UUID Format

Minecraft provides UUIDs without dashes internally. The plugin must convert to dashed format before sending to the API:

```java
// Input:  550e8400e29b41d4a716446655440000
// Output: 550e8400-e29b-41d4-a716-446655440000
String uuid = player.getUniqueId().toString(); // Already dashed in Bukkit API
```

Note: `Player.getUniqueId().toString()` in Bukkit already returns dashed format.

### Cooldown Tracking

- Store cooldowns in a `HashMap<UUID, Long>` (player UUID → expiry timestamp)
- Clean up on player quit to prevent memory leaks
- Cooldowns do not persist across server restarts (intentional)

---

## Project Structure

```
MCVerseRegister/
├── src/main/java/net/mcverse/register/
│   ├── MCVerseRegister.java        # Main plugin class (onEnable/onDisable)
│   ├── commands/
│   │   ├── RegisterCommand.java    # /register command handler
│   │   └── UnregisterCommand.java  # /unregister placeholder
│   ├── api/
│   │   └── MCVerseApiClient.java   # HTTP client for backend API calls
│   └── util/
│       ├── CooldownManager.java    # Per-player cooldown tracking
│       └── MessageUtil.java        # Color code translation + message formatting
├── src/main/resources/
│   ├── plugin.yml
│   └── config.yml
├── pom.xml                         # Maven build file
└── README.md
```

---

## Build Configuration

### Maven (`pom.xml`)

```xml
<project>
    <groupId>net.mcverse</groupId>
    <artifactId>MCVerseRegister</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.21.1-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## API Contract

The plugin communicates with a single backend endpoint:

### `POST /api/v1/auth/register`

**Request:**
```json
{
  "minecraftUuid": "550e8400-e29b-41d4-a716-446655440000",
  "minecraftUsername": "Steve",
  "email": "steve@example.com"
}
```

**Response codes:**
| Status | Meaning | `success` | `message` / `error` |
|--------|---------|-----------|---------------------|
| 201 | New player created | `true` | "Player registered. Magic link sent to email." |
| 200 | Existing player updated | `true` | "Email updated. Magic link sent to new email." |
| 409 | Email belongs to another player | `false` | "Email already registered to another player" |
| 422 | Invalid input (bad UUID/email/username) | `false` | Validation error message |
| 429 | Rate limited | `false` | "Too many registration attempts" |
| 403 | IP not whitelisted | `false` | "Forbidden" |

**The plugin must handle all of these status codes gracefully.**

---

## Security Considerations

1. **API URL should be localhost** — The `/register` endpoint is IP-whitelisted. The plugin runs on the same machine as the backend, so `api-base-url` should be `http://127.0.0.1:3001/api/v1`. Never expose this endpoint to the public internet.

2. **Email privacy** — The email address is sent over HTTP to localhost, which is acceptable. If the backend runs on a separate host, use HTTPS.

3. **No email stored in plugin** — The plugin does not persist email addresses. It only forwards them to the API in the HTTP request body.

4. **Rate limiting** — The plugin enforces its own cooldown (60s) independently of the backend's rate limit (3/min). Both layers protect against abuse.

5. **Async HTTP** — All network I/O is off the main thread. A slow or unreachable API will not lag the server.

---

## Testing Checklist

- [ ] `/register` with valid email → success message, email received
- [ ] `/register` with invalid email → validation error in chat
- [ ] `/register` twice within cooldown → cooldown message with remaining seconds
- [ ] `/register` with email belonging to another UUID → conflict message
- [ ] `/register` when API is unreachable → error message, no server lag
- [ ] `/register` from console → "players only" message
- [ ] `/register` without args → usage message
- [ ] `/register` with multiple args → usage message (only first arg used, or reject)
- [ ] `/unregister` → "not yet available" message
- [ ] Player with `mcverse.register.bypass-cooldown` → can register without cooldown
- [ ] Plugin reload → config values updated, cooldowns reset
- [ ] Server has 50+ players online → no TPS impact from registration requests

---

## Future Enhancements (Not in Scope for v1)

- `/unregister` implementation (API endpoint + DB cleanup)
- `/webstatus` — check if your account is linked
- Tab completion for commands
- PlaceholderAPI expansion: `%mcverse_linked%`, `%mcverse_role%`
- BungeeCord/Velocity proxy support
- Webhook notification to Discord on new registration
