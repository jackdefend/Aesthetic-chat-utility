# Aesthetic Chat Utility

A **server-side** Fabric mod for Minecraft **26.3** that gives every player a name color and a nickname, and
makes that color show up consistently in chat, the tab list, the locator bar and above their head. It can also
color death and advancement messages.

Players do **not** need to install anything. Everything is done on the server with vanilla-compatible
messages, teams and waypoint data.

---

## Features

- **One name color per player**, used everywhere: chat, tab list, locator bar dot and the name above the head.
- **Separate nickname color**, so a player can have, for example, a green username and a gold nickname.
- **Nicknames** shown as `<Player (nickname) > message` (the format is configurable).
- **Locator bar dot colors**, including keeping the vanilla color when you want to, and optionally replacing colors
  set with `/waypoint modify`.
- **Name above the head** colored through 16 hidden teams (the closest of Minecraft's 16 base colors).
- **Death and advancement message colors**, with a separate option for the `[advancement name]`.
- **Admin controls**: ops can change anyone's color or nickname, and commands can be hidden from non-admins.
- **Everything is configurable** in a commented `.json5` file, and reloadable with a command.

---

## Requirements

| What | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | the build for 26.3 |
| Java | 25 |

Clients need nothing installed. The mod also loads in singleplayer, where the built-in server uses it.

---

## Installation (server)

1. Stop the server.
2. Put `aesthetic-chat-utility-<version>.jar` and the **Fabric API** jar for 26.3 in the server's `mods` folder.
3. Start the server. The config file `config/aesthetic-chat-utility.json5` is created on first start.
4. Edit the config if you want, then run `/aestheticchat reload` (or restart).

If you replace the mod with a newer version, delete the old jar first so two copies aren't loaded.

---

## Commands

"Admin" means anyone with op (any op level).

| Command | Who | What it does |
|---|---|---|
| `/namecolor` | see config | Shows your current name color and how to use the command. |
| `/namecolor <#hex \| color name \| reset>` | `color.who` | Sets your name color. `reset` returns to the default color. |
| `/namecolor <color> <player>` | admins | Sets another player's name color. |
| `/nickcolor <#hex \| color name \| reset>` | `nickname.colorWho` | Sets your nickname color. |
| `/nickcolor <color> <player>` | admins | Sets another player's nickname color. |
| `/nickname <nickname \| reset>` | `nickname.who` | Sets or removes your nickname. Use quotes for spaces: `/nickname "my nick"`. |
| `/nickname <nickname \| reset> <player>` | admins | Sets or removes another player's nickname. |
| `/aestheticchat reload` | admins | Reloads the config and resends the command list. |

- **Colors** can be a hex code (`#129f30` or `129f30`) or one of the 16 base color names:
  `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`, `dark_purple`, `gold`, `gray`, `dark_gray`,
  `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow`, `white`.
  Which of the two are accepted is set by `color.accepts` / `nickname.colorAccepts`. `reset` always works.
- When a command is limited to admins in the config, it is **hidden** from everyone else (it won't tab-complete).
  It still checks permission when run.
- The `[player]` argument only works for admins and only for players who are online.

---

## How colors work

Each player has **one name color**: the color they chose, or the **default** color if they haven't chosen one.
That single color is what every place below uses. Each place can show it exactly, snapped to the nearest base
color, or not at all.

| Place | Setting | Notes |
|---|---|---|
| Chat name | `show.chat` | `OFF`, `EXACT` or `NEAREST` |
| Tab list name | `show.tabList` | `OFF`, `EXACT` or `NEAREST` |
| Locator bar dot | `show.locatorBar` | `OFF`, `EXACT` or `NEAREST` |
| Name above the head | `show.nametag` | `OFF` or `NEAREST` only (see below) |

- **`OFF`** leaves that place vanilla.
- **`EXACT`** uses the exact color, including any hex code.
- **`NEAREST`** snaps the color to the closest of the 16 base colors (compared by perceived color difference).

### The default color

`color.default` is the color a player has until they choose one, and what `reset` returns them to:

- `LOCATOR`: the player's normal locator bar color, which the game derives from their account UUID.
- `VANILLA`: no coloring at all.
- a base color name such as `gold`.

Set `color.forceDefault` to `true` to make **everyone** always use the default. Chosen colors are kept but ignored,
and `/namecolor` is hidden for everyone.

### Name above the head (teams)

Minecraft draws the name above a player from their scoreboard team, and teams only have the 16 base colors.
The mod creates 16 teams named `acu_<color>` (for example `acu_dark_aqua`) and puts each player in the one nearest
their color. Players never see these teams, but admins will see them in `/team list`.

- Nicknames do **not** show above the head, only the real username.
- A player already in a real team keeps it if `overrides.realTeams` is `false`.

### Locator bar

The mod sets the player's locator dot color directly. With `show.locatorBar: "OFF"` it keeps the dot at its
normal vanilla color even though the player is in one of the mod's teams. A color set with `/waypoint modify`
is replaced when `overrides.waypointColor` is `true` (checked about every two seconds) and left alone when it's
`false`.

---

## Nicknames

A nickname is shown after the username using the template in `nickname.format`:

- `{name}` is the username and `{nick}` is the nickname.
- `"{name} ({nick}) "` gives `<Player (nickname) > message` (the trailing space puts it before the `>`).
- `"{nick}"` shows only the nickname.

The nickname can have its own color (`/nickcolor`). Until a player chooses one, `nickname.defaultColor` applies:

- `VANILLA`: plain, uncolored text (the default).
- `NAME`: the same as the player's name color.
- a base color name such as `gray`.

Rules for nicknames are `nickname.maxLength`, `nickname.allowedPattern` (a regular expression) and
`nickname.blockImpersonation`, which stops anyone using another **online** player's username as a nickname.

Because the mod changes the player's display name, a nickname also shows anywhere the name appears: join and
leave messages, deaths, advancements, `/msg`, and so on.

---

## Death and advancement colors

`messages.colorDeaths` and `messages.colorAdvancements` (both on by default) color those announcements with
`deathColor` (default `red`) and `advancementColor` (default `blue`). Both accept a base color name or a hex code.

- Only the message's own text is colored. **Player names keep their own color**, and uncolored names and
  nicknames are pinned to the vanilla chat white so they don't turn red or blue.
- The `[advancement name]` keeps the game's own color (green, or purple for challenges) and its hover tooltip.
  Set `messages.advancementTitleColor` to a base color or hex code to change it.
- The death screen text shown to the player who died is not changed.

---

## Configuration

`config/aesthetic-chat-utility.json5` is JSON with `//` comments. The mod rewrites it from a template whenever
it loads, so the comments stay and any new options are added with their defaults. Don't add trailing commas.

<details>
<summary>Default config (as generated on first start)</summary>

```json5
{
  // ===== Name color =====
  "color": {
    // Who can use /namecolor. ADMINS hides it from non-ops. Ops can always change anyone's
    // color by adding a username at the end: /namecolor gold jackdefend      ADMINS | PLAYERS
    "who": "ADMINS",
    // What /namecolor accepts. "reset" always works.      BASE | HEX | BOTH
    "accepts": "BOTH",
    // The color players use until they pick one, and what /namecolor reset returns to.
    // VANILLA = no coloring at all, LOCATOR = their normal locator bar color (from their account),
    // or a base color name such as "gold".
    "default": "LOCATOR",
    // true = everyone always uses the default. Chosen colors are kept but ignored,
    // and /namecolor is disabled for everyone.
    "forceDefault": false
  },

  // ===== Where the color shows =====      OFF | EXACT | NEAREST (nearest of the 16 base colors)
  "show": {
    "chat": "EXACT",
    "tabList": "EXACT",
    // OFF keeps the dot at its normal vanilla color even when the player is on a team.
    "locatorBar": "EXACT",
    // The name above the head comes from teams, which only have the 16 base colors.
    // OFF | NEAREST (EXACT is treated as NEAREST)
    "nametag": "NEAREST"
  },

  // ===== Nicknames =====
  "nickname": {
    // Who can use /nickname. ADMINS hides it from non-ops. Ops can always change anyone's
    // by adding a username at the end.      ADMINS | PLAYERS
    "who": "PLAYERS",
    // {name} is the username, {nick} the nickname. A trailing space puts it before the closing >.
    // "{nick}" alone shows only the nickname.
    "format": "{name} ({nick}) ",

    // ---- Nickname color: a second color, separate from the name color (/nickcolor) ----
    // Who can use /nickcolor. ADMINS hides it from non-ops. Ops can always change anyone's
    // by adding a username at the end.      ADMINS | PLAYERS
    "colorWho": "PLAYERS",
    // What /nickcolor accepts. "reset" always works.      BASE | HEX | BOTH
    "colorAccepts": "BOTH",
    // The nickname color until a player picks one, and what /nickcolor reset returns to.
    // VANILLA = plain (no color), NAME = same as the player's name color, or a base color such as "gray".
    "defaultColor": "VANILLA",

    // true = the tab list shows the nickname as well.
    "inTabList": true,
    "maxLength": 16,
    // Letters, numbers, underscore, space, dot, dash.
    "allowedPattern": "[A-Za-z0-9_ .-]+",
    // true = nobody can use another online player's username as a nickname.
    "blockImpersonation": true
  },

  // ===== Overrides =====
  "overrides": {
    // true = our team replaces a real team the player is already in. false = real teams are left alone.
    "realTeams": true,
    // true = the mod replaces colors set with /waypoint modify (re-checked every couple of seconds).
    "waypointColor": true
  },

  // ===== Death and advancement message colors =====
  "messages": {
    // true = death messages are colored. Player names inside them keep their own color.
    "colorDeaths": true,
    // A base color name (red, gold, ...) or a hex code such as "#ff5555".
    "deathColor": "red",
    // true = advancement messages are colored. The [advancement name] keeps its own color.
    "colorAdvancements": true,
    // A base color name (blue, aqua, ...) or a hex code such as "#5555ff".
    "advancementColor": "blue",
    // The color of the [advancement name] inside the message. Its hover tooltip is unchanged.
    // VANILLA = the game's own color (green, or purple for challenges), or a base color name / hex code.
    // Only used when colorAdvancements is true.
    "advancementTitleColor": "VANILLA"
  }
}
```

</details>

### Reference

| Key | Values | Default | Description |
|---|---|---|---|
| `color.who` | `ADMINS` / `PLAYERS` | `ADMINS` | Who can use `/namecolor`. Admins can always change anyone's. |
| `color.accepts` | `BASE` / `HEX` / `BOTH` | `BOTH` | What `/namecolor` accepts. `reset` always works. |
| `color.default` | `VANILLA` / `LOCATOR` / base color | `LOCATOR` | The default name color and what `reset` returns to. |
| `color.forceDefault` | `true` / `false` | `false` | Everyone always uses the default; `/namecolor` is hidden. |
| `show.chat` | `OFF` / `EXACT` / `NEAREST` | `EXACT` | Name color in chat. |
| `show.tabList` | `OFF` / `EXACT` / `NEAREST` | `EXACT` | Name color in the tab list. |
| `show.locatorBar` | `OFF` / `EXACT` / `NEAREST` | `EXACT` | Locator dot color. `OFF` keeps the vanilla color. |
| `show.nametag` | `OFF` / `NEAREST` | `NEAREST` | Name above the head, through teams (`EXACT` counts as `NEAREST`). |
| `nickname.who` | `ADMINS` / `PLAYERS` | `PLAYERS` | Who can use `/nickname`. |
| `nickname.format` | text with `{nick}` (and optionally `{name}`) | `"{name} ({nick}) "` | How the nickname is shown. |
| `nickname.colorWho` | `ADMINS` / `PLAYERS` | `PLAYERS` | Who can use `/nickcolor`. |
| `nickname.colorAccepts` | `BASE` / `HEX` / `BOTH` | `BOTH` | What `/nickcolor` accepts. |
| `nickname.defaultColor` | `VANILLA` / `NAME` / base color | `VANILLA` | The nickname color until a player picks one. |
| `nickname.inTabList` | `true` / `false` | `true` | Show the nickname in the tab list. |
| `nickname.maxLength` | 1 to 64 | `16` | Longest allowed nickname. |
| `nickname.allowedPattern` | regular expression | `[A-Za-z0-9_ .-]+` | Which characters a nickname may use. |
| `nickname.blockImpersonation` | `true` / `false` | `true` | Block nicknames equal to an online player's username. |
| `overrides.realTeams` | `true` / `false` | `true` | The mod's team replaces a real team the player is in. |
| `overrides.waypointColor` | `true` / `false` | `true` | Replace colors set with `/waypoint modify`. |
| `messages.colorDeaths` | `true` / `false` | `true` | Color death messages. |
| `messages.deathColor` | base color or hex | `red` | Death message color. |
| `messages.colorAdvancements` | `true` / `false` | `true` | Color advancement messages. |
| `messages.advancementColor` | base color or hex | `blue` | Advancement message color. |
| `messages.advancementTitleColor` | `VANILLA` / base color / hex | `VANILLA` | Color of the `[advancement name]`. |

An invalid value is logged as a warning and replaced with its default.

---

## Data files

| File | Contents |
|---|---|
| `config/aesthetic-chat-utility.json5` | The settings above. |
| `config/aesthetic-chat-utility-players.json` | Each player's chosen colors and nickname, keyed by UUID. Stored with the server, not with any one world. |

The players file contains player UUIDs, so keep it out of public repositories.

---

## Building from source

You need **Java 25** (a full JDK, not just a JRE).

```bash
git clone <this repository>
cd aesthetic-chat-utility
./gradlew build
```

The mod jar is `build/libs/aesthetic-chat-utility-<version>.jar` (use the one **without** `-sources`).
The project ships its own Gradle wrapper, so use `./gradlew` and not a system-wide `gradle`.

To try it in a development client:

```bash
./gradlew runClient
```

Create a singleplayer world with cheats on so you are an op.

### Project layout

```
src/main/java/dev/jackdefend/aestheticchat/
  AestheticChatUtility.java   entry point, events, periodic locator check
  AcuConfig.java              the .json5 config (template, parsing, defaults)
  AcuCommands.java            /namecolor, /nickcolor, /nickname, /aestheticchat
  NameStyler.java             builds the chat and tab list names
  ColorSync.java              teams, locator bar dot and tab list updates
  ColorMath.java              nearest base color
  LocatorColors.java          the vanilla UUID-based locator color
  MessageColors.java          death and advancement colors
  PlayerStore.java            saved colors and nicknames
  mixin/PlayerMixin.java          Player#getDisplayName (chat names)
  mixin/ServerPlayerMixin.java    ServerPlayer#getTabListDisplayName
  mixin/PlayerListMixin.java      PlayerList#broadcastSystemMessage
```

---

## Compatibility and limitations

- The mod uses mixins on `Player`, `ServerPlayer` and `PlayerList`. A Minecraft update that changes those
  methods can break it until it is updated.
- Other mods that change display names, tab list names, chat formatting or scoreboard teams can conflict.
- The name above the head can only be one of the 16 base colors, and never shows nicknames.
- `/nickname`, `/namecolor` and `/nickcolor` target **online** players only.
- Bedrock players connecting through Geyser have not been tested.

---

## License

MIT. See `LICENSE`.
