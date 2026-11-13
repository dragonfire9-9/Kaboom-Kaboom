# SoulBound Client

A Fabric client for Minecraft **1.21.11**. It includes a custom title screen, the SoulBound menu and these modules:

| Tab | Module |
| --- | --- |
| Combat | **Trigger Bot**, **Aim Assist** and **Auto Hit Crystal** |
| Visual | **Custom HUD** |
| Misc | **Auto MLG** |
| Bots, PvP group | **Sword PvP Bot**, **Crystal PvP Bot** and **UHC Bot** |
| Bots, Utility group | **Schematic Builder** |

## Title screen

![Title screen](docs/title-screen.png)

SoulBound replaces Minecraft's main menu with a full-screen scene:

- **Background:** two layers of silk ribbons in the accent colour drift slowly and shift with the mouse for depth. Soul sparkles and glowing motes float upward and twinkle.
- **Brand:** the SoulBound logo with a breathing glow, and the client and game version underneath.
- **Menu:** Singleplayer, Multiplayer, Realms, Minecraft Settings, Client (the SoulBound menu) and Quit Game, as plain centred text. Hovering lights an entry up with:
  - a glowing pill behind it
  - wider letter spacing
  - an underline that grows from the centre
  - arrows that slide in from both sides

  The arrow keys or Tab move between entries, and Enter or Space opens one. Items fade up one after another when the screen opens.
- **Quick Join:** a pill that joins your first saved server in one click.
- **Your character:** your skin stands in 3D on the right with a ground shadow and an accent ring. It turns toward the mouse, and you can drag it to spin it.
- **Corners:**
  - a clock and the date (top right)
  - your account (bottom left)
  - the version and a **Classic Menu** link (bottom right)

**Classic Menu** shows Minecraft's own title screen for the rest of the session, with a **SoulBound Menu** button in the corner to come back. To turn the custom title screen off permanently, use *Settings → Interface → Custom Title Screen*.

## Menu

![Menu](docs/gui-combat.png)

The menu has these parts:
- a header
- a category rail split into two labelled groups: **MODULES** (Combat, Visual, Util, Movement, Misc and Bots) and **GENERAL** (Settings and Theme). The selection highlight slides between the groups. The tiles size themselves to the window, so the rail always fits; in short windows they become icon-only, with a tooltip on hover.
- module tabs show a grid of module cards with search and an A-Z / Active filter. **Left-click** a card to turn it on or off; **right-click** it for quick settings.
- Settings and Theme keep a page list and a detail panel of setting cards, including dropdowns
- a footer

![Icons](docs/icons.png)

The icons are one consistent line-icon family, drawn by `tools/gen_icons.py`.

**Quick settings:** right-click any module card. The bar takes about one second to open:
1. **Ignite:** a crisp four-point star settles from 45°, with a soft bloom and a thin anamorphic lens streak.
2. **Dim:** it softly dims.
3. **Collapse and bloom:** it pulls into a bright point, then releases a fine shockwave ring and particles with motion trails.
4. **Morph:** the bar grows out of the burst, from a circle to a pill to the finished panel.
5. **Reveal:** the settings fade in row by row, and a glass highlight sweeps across once.

Closing reverses it: the bar folds back into a point of light that fades out. Two sounds accompany it: a glassy two-note chime, then an airy bloom chord. All timings follow the *Animation Speed* setting.

![Quick settings animation](docs/quick-settings-animation.png)
![Quick settings](docs/quick-settings.png)

Settings has the Interface and Audio pages. Theme has its own tile for the accent colour and glow.

**The Bots tab uses a different layout:**
- a bot icon strip, split into **PVP** and **UTIL** groups
- a 3D preview of the bot's own skin, which you can drag to rotate, on a glowing pedestal, with live hits, combo and CPS
- a flat settings panel with checkboxes, `‹ value ›` arrow selectors and sliders

![Bots tab](docs/gui-bots.png)

## Natural aiming (all bots, Aim Assist)

Rotation runs every rendered frame, not 20 times a second, and goes through the same path as real mouse input, so turning is smooth at any frame rate. Every aiming module has an **AIM** section:

| Setting | What it does |
| --- | --- |
| Aim Mode | *Human* (speed tapers as it nears the target, with limited acceleration), *Spring* (a springy follow) or *Linear* (constant speed) |
| Aim Speed | Top turning speed, in degrees per second |
| Smoothness | Higher values ease in more gently |
| Vertical Speed | Pitch speed relative to yaw |
| Jitter | Small layered hand-tremor noise, a little stronger while turning fast |
| Overshoot | Big flicks pass the target slightly, then settle |
| Reaction Time | Delay before following the target's movement |
| Sensitivity Fix | Moves in the step sizes your mouse sensitivity allows, like real mouse input |

Bots and modules only act (swing, place or break) once the aim error is under their *Max Aim Error*. Long settings lists are divided into labelled sections.

## Sword PvP Bot (Bots tab)

![Bot HUD](docs/bot-hud.png)

The bot has its own skin, **Soulguard**: obsidian-navy plate armour with silver trim, a soul-blue T-visor, a soul-flame crest and a silver helmet fin (`textures/entity/sword_bot.png`, generated by `tools/gen_bot_skin.py`). It's shown in the Bots tab preview.

When enabled, the Sword PvP Bot takes over movement and combat:

- **Targeting:** players, hostile mobs or both within *Search Range*. Priority is closest, lowest health or nearest the crosshair. It also has a *FOV* cone, *Through Walls*, *Ignore Invisible* and *Target Lock*, and it skips teammates and creative players.
- **Aim:** the natural aim settings above, plus *Aim Point*: Closest, Head, Chest, Legs, or Dynamic (a point that drifts slowly over the upper body).
- **Attack:**
  - *Attack Range* and *Min Cooldown*: how far and how charged before it swings
  - *Hit Chance*: deliberate misses for realism
  - *Random Delay*: an extra random wait before each swing
  - *Max Aim Error*: only swings when aimed this closely
  - *Jump Crits*
  - *Break Shields*: swaps to an axe when the target blocks
  - *Auto Sword*
- **Movement:**
  - *Chase*, *Sprint* and *Jump Chase*
  - *Strafe*: Circle, Random, or Smart (jukes right after landing a hit)
  - *Sprint Reset*: W-Tap or S-Tap
  - *Keep Distance*
  - *Avoid Edges*: never walks off drops of more than three blocks, or into lava or fire
- **Survival:** *Retreat Health*, *Eat Gapples* with *Gapple Health*, and *Auto Totem*.
- **Pausing:** stops whenever a screen is open, such as chat, inventory or this menu. When it pauses or is disabled, it hands the movement keys back to your keyboard.

**HUD:** a status card that sits below the crosshair by default and can be moved to a corner. It shows:
- the bot's state: Searching, Chasing, Engaging, Retreating or Paused
- the target's head, name and distance
- an animated health bar, with a white "ghost" that trails behind damage, a red tint at low health and an absorption strip
- a red flash on the target slot when the target is hit
- combo (it pops on each hit and resets when you take damage), total hits, swings per second and your own health

## Crystal PvP Bot (Bots tab)

The bot fights crystal PvP for you. Its preview skin is **Prism Knight**: deep violet plate armour with rose-gold trim, magenta eye slits, a crystal set in the faceplate, and crystal growths on the crown and shoulders.

![Bot skins](docs/bot-skins.png)

- **Crystals:** places end crystals on obsidian or bedrock wherever they hurt the target most, and breaks crystals in reach that are worth it.
- **Damage maths:** it works out vanilla explosion damage, including exposure, difficulty, armour, armour toughness, Protection, Blast Protection and Resistance. A crystal is only used if it deals at least *Min Damage* to the target (or kills it) and at most *Max Self Damage* to you. *Anti Suicide* never lets a crystal kill you.
- **Build Obsidian:** when there's nowhere to place, it puts obsidian beside the target.
- **Survival:** *Auto Totem* keeps a totem in your offhand, and *Eat Gapples* eats golden apples at or below *Gapple Health*.
- **Playstyle:** Terminator fights from about 3.5 blocks and accepts lethal trades; Balanced stays at about 4.5; Safe stays at about 5.5 and takes less self-damage.
- **Placing:**
  - *Face Place* (with *Face Place Health*) and *Armor Breaker*: drop the minimum damage when the target is low, or any armour piece is below a set durability
  - *Predict*: accounts for where the target is moving
  - *Swap Mode*: Normal, or Swap Back to your previous slot
- **Breaking:**
  - *Anti Weakness*: swaps to a weapon when you have Weakness
  - *Inhibit*: won't re-hit the same crystal for a few ticks
- **Movement:** *Fight Distance* and *Avoid Edges*. Aiming uses the natural aim settings.
- **Testing:** in a test fight it killed a 60 HP husk with two crystals while I took about 5 damage in Blast Protection IV netherite. Without Blast Protection, nearly every crystal would exceed the self-damage limit, which matches real crystal PvP.

Only one bot runs at a time; turning one on turns the others off. The bot HUD follows whichever bot is running.

## UHC Bot (Bots tab)

The Sword PvP Bot's melee, plus everything a UHC fight needs. Its skin is **Vanguard**: iron plate with gold trim and a crimson visor and tabard.
- **Bow:** at range (*Bow Min Range* to *Bow Max Range*) it draws to *Bow Charge*. It aims on the arrow's arc, with *Lead Shots* to aim ahead of moving targets, and strafes while drawing.
- **Rod:** in the mid range it hooks the target with a fishing rod for knockback, reels in, and swaps back to the sword.
- **Water Clutch:** uses Auto MLG's clutch on big falls, even when Auto MLG itself is off.
- **Survival:** golden apples and Auto Totem are on by default.

## Schematic Builder (Bots tab, Utility group)

![Schematic screen](docs/schematic-screen.png)

The bot builds schematics from its own folder, `config/soulbound/schematics/`. It reads Sponge `.schem` (v2 and v3), Litematica `.litematic` and vanilla structure `.nbt` files. On first run the folder is created with an example, **starter_hut.schem**. Its skin is **Architect**: a hard hat, a teal-glass visor, copper trim and a hi-vis tabard.

**Choose Schematic** opens the picker with a transition:
1. The menu shifts while a star swirls around it on a tilted 3D orbit, passing in front of and behind the window with a comet trail.
2. The star pulls to the centre and shines until the whole screen turns white.
3. The picker appears as the white dims, and the star floats away with a soft glow.

![Portal transition](docs/schematic-portal.png)

**The picker:**
- **Left:** a searchable list of your files, with *Open Folder* and *Refresh*.
- **Centre:** a shaded top-down map with a layer slider.
- **Right:**
  - format, size, block count, origin and rotation
  - a materials list showing each item's icon and how many you have against how many you need (green, amber or red)
  - *Set Origin Here* and *Build*

**Leaving the picker:** the UI crumbles into falling tiles, and the menu rebuilds itself from tiles that fly up into place.

![Crumble and rebuild](docs/schematic-crumble.png)

**Building:**
- **Order:** layer by layer from the bottom, nearest block first, always against a supporting face.
- **Materials:** it moves the right block into your hotbar automatically.
- **Walking:** A* pathfinding with one-block step-ups, drops of up to three blocks, no corner-cutting, and avoidance of lava, fire, water, cactus, berry bushes, powder snow and cobwebs.
- **Natural movement:** the head looks along the path, glances around occasionally, steers the body, and sprints on longer straights.
- **Settings:** *Reach*, *Place Delay*, *Exact States*, *Skip Missing*, *Sprint* and *Rotation*.
- **Saving:** the chosen schematic, origin and rotation are saved and restored next launch.

**Greeting:** when another player comes within *Greet Range*, it stops completely, turns to look at them, says hi in chat with a little wave, then goes back to building. You can choose the message, and *Greet Cooldown* limits how often it greets the same player.

## Combat tab

- **Trigger Bot:** attacks the valid target under your crosshair once your weapon is charged to *Min Cooldown*.
  - Timing: a random delay between *Min Delay* and *Max Delay*, plus a *Hit Chance*
  - Conditions: *Only With Weapon*, *Only Crits*, *Ignore Invisible* and *Ignore Blocking*
- **Aim Assist:** gently pulls your aim toward the nearest target inside its *FOV* and *Distance*, using the natural aim settings.
  - Options: *Aim Point*, *Sticky Target*, *Through Walls*, *Only While Clicking* and *Only With Weapon*
  - A running bot takes priority over the assist.
- **Auto Hit Crystal:** a hit-crystal macro. Right-click with obsidian to place it; it then swaps to a crystal, places it on the obsidian and hits it.
  - Modes: *Full*, or *Crystal Only* (right-click existing obsidian)
  - Options: *Auto Break* for crystals you look at, *Swap Back*, and place and break delays

## Auto MLG (Misc tab)

When you're falling at least *Min Fall* blocks, Auto MLG looks down just before you land, switches to a clutch item and places it.
- **Items:** water bucket, powder snow, cobweb, slime block or hay bale. *Auto* picks the first one you have and skips water in the Nether.
- **After landing:** it scoops the water back up, then returns your hotbar slot and view.
- **Smart Timing:** a bucket only reaches about 2.9 blocks below your feet, and a fast fall can skip that range in one tick. Smart Timing eases that one tick's fall so you always pass through the reachable range.
- **Testing:** I survived 31- and 36-block drops at full health, with the water picked back up.

## Custom HUD (Visual tab)

![Custom HUD](docs/custom-hud.png)

Each element can be switched on or off in the module's settings:

| Element | Shows |
| --- | --- |
| Watermark | SoulBound logo, FPS, ping and time |
| Module List | Active modules, longest first, sliding in and out with accent-gradient bars |
| Coordinates | XYZ, facing, and the matching Nether or Overworld coordinates |
| Keybinds | Every module with a key bound, its key, and whether it's on |
| Potions | Effect icon, level and time left (blinks in the last 10 seconds) |
| Armor | Worn armour with colour-coded durability |
| Keystrokes | W, A, S and D, left and right mouse buttons with clicks per second, and the space bar. Pressed keys light up with the accent colour. |

**Edit Layout** opens a drag-and-drop editor. Elements snap to the screen edges and the centre lines, and right-clicking an element hides or shows it. Positions are saved as fractions of the screen, so an element pushed against an edge stays there at any window size. Other settings:
- **Background** sets the opacity of the element backgrounds.
- **Hide Vanilla Effects** hides Minecraft's own effect icons while Potions is on.

![HUD layout editor](docs/hud-editor.png)

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.17 or newer (built against 0.20.1)
- Fabric API (built against 0.141.6+1.21.11)
- Java 21

## Build

```bash
./gradlew build          # jar is written to build/libs/soulbound-1.0.0.jar
./gradlew runClient      # launch a dev client
```

## Using the menu

| Action | Input |
| --- | --- |
| Open or close the menu | `Right Shift` (change it in Settings → Interface → Menu Key) |
| Change accent colour or glow | Theme (GENERAL) |
| Move HUD elements | Visual → Custom HUD → Edit Layout |
| Search modules | Click the search box, or press `/` |
| Toggle a module | Left-click its card |
| Quick settings for a module | Right-click its card |
| Change a `‹ value ›` setting | Click the left half to go back, the right half to go forward |
| Rebind a key | Click the keybind chip, then press a key (`Esc` or `Backspace` clears it) |

Settings are saved to `config/soulbound.json`.

## How the menu is drawn

- **Perfectly round edges.** `Render2D` converts GUI coordinates to real framebuffer pixels. It draws corners from anti-aliased disc, ring and glow textures generated by `ShapeCache`.
- **Texture sizes:** sizes snap to a small set of steps and are drawn slightly scaled, so animated shapes reuse a handful of textures instead of creating one per frame. Textures unused for 15 seconds are released.
- **Soft outer glow.** A procedurally generated falloff texture is 9-sliced around any rounded rectangle. The window glow breathes slowly and follows the accent colour.
- **Custom fonts.** Inter is used for body text and labels, and Outfit for the brand and titles. They load through Minecraft's TTF font providers in `assets/soulbound/font/`.
- **Sound effects.** There are separate sounds for hover, click, toggle on and off, open, close, slider ticks and typing. `tools/gen_assets.py` synthesises them. They are not added to the sound registry, so the client still works on servers that don't have the mod.
- **Animation.** All motion is frame-rate independent:
  - the selection tile slides and its edge marker glows
  - the A-Z / Active control slides
  - switches use an eased knob
  - clicks ripple
  - the selected row has a shimmer sweep
  - content fades in when you switch pages
  - the window opens with a scale-in and closes with a scale-out
- **Accent themes.** Soul, Violet, Frost and Ember.

## Adding a module

```java
public final class Example extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast", 5, 0, 10, 0.1));
    private final ModeSetting mode = add(new ModeSetting("Mode", "Behaviour", "Normal", "Normal", "Fast"));
    private final BoolSetting flag = add(new BoolSetting("Flag", "Something", true));

    public Example() { super("Example", "What it does.", Category.UTIL); }

    @Override public void onTick() { /* runs every client tick while enabled */ }
}
```

Then call `register(new Example());` in `ModuleManager.init()`. The menu creates the list row, the switch, the keybind field and all the parameter controls automatically. Override `getTag()`, `getBadge()` and `getIcon()` to customise the header and the bots strip; any module in `Category.BOTS` appears in the Bots view.

## Assets

- `tools/gen_icons.py` regenerates the interface icons.
- `tools/gen_assets.py [logo] [--sounds]` rebuilds the mod icon from a logo image, and the interface sounds with `--sounds`.
- `tools/gen_example_schem.py` regenerates the bundled starter hut schematic.
- `tools/gen_bot_skin.py` regenerates all four bot skins. Each face is a hand-designed pixel map. Side and back faces are shaded one tone darker and top faces one tone lighter, for consistent top-down lighting.
- `tools/gen_fx_sounds.py` regenerates the shine and burst sounds used by quick settings.
- `tools/gen_title_assets.py` regenerates the title screen's silk background and sparkle.
- The Inter and Outfit fonts use the SIL Open Font License. The licence texts are in `src/main/resources/licenses/`.
