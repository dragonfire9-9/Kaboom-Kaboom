# DeadCraft

<img src="icon.png" width="128" align="right" alt="DeadCraft icon"/>

*There is no morning here.*

DeadCraft is a horror overhaul for **Minecraft 1.21.1 (NeoForge 21.1)**. Fifteen horrors hunt
the dark, five bosses wait in tombs, castles and gateways, and destroyed cities are overrun by
zombies. It adds four dimensions with their own terrain and skies and eight biomes, plus guns, magic, vehicles and a dodge/combo
combat system. The client side is reworked with a blood-soaked title screen, a new HUD, a
cinematic camera, walk/run animations and a full soundscape.

![Title screen](docs/title_screen.jpg)

| | |
|---|---|
| ![Creatures at dusk](docs/creatures.jpg) | ![A boss, the vial HUD and the 3D Bloodreaver](docs/boss_and_hud.jpg) |

*Screenshots from the dev client: the parallax graveyard title screen; Chronos and Fracture at
noon; the Dim Light Demon's boss bar over the vial HUD (half health, so the skull's eyes are lit),
with the 3D Bloodreaver in hand.*

**Bestiary** (rendered by `tools/preview.py` straight from the model geometry and textures):

![Every custom-modelled creature](docs/bestiary.png)
![Bandits, the Rotwalker and both vehicles](docs/humanoids_and_vehicles.png)
![The Skeleton](docs/skeleton_king.jpg)

## Build and run

Requirements: JDK 21. Gradle downloads everything else.

```sh
cd deadcraft
./gradlew build          # → build/libs/deadcraft-0.1.0.jar (drop into your mods folder)
./gradlew runClient      # dev client
./gradlew runServer      # dev server (accept the EULA in run/eula.txt first)
```

When you first open a world, Minecraft warns that it uses experimental settings. Vanilla shows
this warning for any world with custom dimensions, and it only appears once per world.

Optional: install **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** for
proximity voice chat. DeadCraft detects it automatically, and creatures can then hear you speak.

## Controls

| Action | Default key |
|---|---|
| Dodge (i-frames, direction follows WASD) | Left Alt |
| Reload gun | R |
| Fire gun | Right-click (hold for the SMG) |
| Cast spell | Right-click with a staff, tome, rod or chalice |
| Drive vehicle | Right-click to mount. W/S throttle, A/D steer (Ruin Runner), look to fly (Sky Skiff) |
| Pick up vehicle | Sneak + punch |

## Creatures

**Hostile (15):** The Hollow (only moves when nobody is looking at it), Severed (leaps, causes
bleeding), Cracked (fires shards when struck), Depart (teleports behind you, brings darkness),
Scorched (sets everything nearby on fire), Parasite (fast leaps, infests), Illusion (spawns decoy
copies), Bandit (shoots revolvers), Bandit Captain (shotgun, calls reinforcements), The Forgotten
(hears twice as far, inflicts Dread), Wretched (spits acid), Ghosts (pass through walls, immune to
arrows), Blood Demon (lifesteal, frenzies when hurt), Star Fall (calls down falling stars),
Collapse (ground slam, explodes on death).

**Destroyed-city zombie:** Rotwalker. Unlike vanilla zombies, it doesn't burn in daylight.

**Bosses (5)**, each with a boss bar, a second phase at half health and its own trophy:
- **Fracture:** shard novas, ground slams; summons Cracked.
- **Chronos:** a slowing aura, homing time bolts and teleports. Rewinds its own health once.
- **The Skeleton:** a crowned skeleton king with a hollow ribcage around a soul flame, bone wings, four arms, a vertebral tail, a soulfire greatsword and three skulls circling its head. Bone volleys, raises skeleton guards, leaps at you in phase two.
- **Dim Light Demon:** darkness pulses, rains hellfire, summons Blood Demons.
- **Petrified:** looking at it slows you to a crawl. Throws boulders, slams the ground, shrugs off half of all projectile damage.

Bosses spawn in structures (the castle always has the Skeleton). You can also summon them with
**Sigils** crafted from their materials.

**Peaceful (20):** Ashgrazer, Gloomhog, Cinderhen, Marrow Ox and Duskhare drop meat you can
cook (smelt it, or kill them with fire). Also: Lantern Moth, Hush Owl, Gravecat, Wisplet,
Shellmourn, Candlewick Toad, Mossback Tortoise, Ember Fox, Veilfawn, Grimfinch, Sorrowdove,
Stormstag, Cragstrider, Lumen Beetle and Hollowfleece. Breed them with wheat, seeds, carrots or
berries.

### Hearing and proximity chat
Creatures react to sound through one system (`world/Hearing.java`):
- **Voice** (with Simple Voice Chat): the louder you speak, the further it carries. Whispering is near-silent.
- **Text chat** carries 16 blocks. Sprinting is louder than walking, and sneaking halves every noise you make.
- **Gunshots, engines, explosions and spells** are loud.

A quiet noise sends creatures to investigate. A loud or close one makes them hunt you. The Forgotten hears twice as far.

## World

![The four dimensions](docs/dimensions.jpg)

Every DeadCraft dimension has its own terrain generator, blocks, sky and biomes. None of them reuse
vanilla Nether, End or Overworld terrain, and no biome appears in more than one dimension.

| Dimension | Terrain | Sky | Biomes |
|---|---|---|---|
| **Hell's Paradise** | A gutted cavern world of charnel stone: a floor and a roof torn open by vast caverns, column pillars holding the roof up, stalactites, blood falls, and a sea of **blood** | No sky, red murk | **Scorched Forest** (ash soil, charred trees, cinder sprouts, ember storms), **Bleeding Hollows** (sinew floors, ribcages of giant beasts) |
| **Skyfall Torn** | Pale skystone islands adrift between y 70 and 200, with stalactites hanging beneath them | Overcast, a shattered moon shedding fragments, clouds above the islands | **Floating Islands** (pale moss, ghostwood trees, pale blooms), **Shroud Reach** (shroud mold, ash fog, ruins) |
| **Light's Descent** | Terraced mesas of umbral stone and dustrock strata separated by sheer cliffs | Eternal dusk under an enormous blood moon resting on the horizon | **Dark Plains** (gloom grass over grave soil, gloom ferns, dead trees, graves), **Deserted Cliffs** (bone sand, banded dustrock, buried ribcages) |
| **Disposed Skies** | The wreckage of a world: thin shattered plates of voidrock and needle spires adrift in the void | Black void, swarming stars, a slowly turning void eye, flickering static storms | **Storm Field** (static crust, static thorns), **Glass Wastes** (void crystal floors and spikes) |

Reach them with a **Rift Key** or a **Gateway**. Every DeadCraft dimension has **Portal shrines** that
lead home, and a Rift Key used inside a DeadCraft dimension takes you back to where you left the Overworld.

**New blocks:** charnel stone, ash soil, sinew, skystone, pale moss, shroud mold, ghostwood logs and
leaves, umbral stone, grave soil, gloom grass, bone sand, dustrock, voidrock, static crust, void
crystal, four plants (cinder sprout, gloom fern, pale bloom, static thorn) and **blood**, a thick,
slow fluid you can carry in a bucket.

**Structures:** Tombs (a mausoleum over a buried crypt), Graveyards, ruined Castles, Gateways
(grand arches holding a rift to one of the dimensions), Portal shrines, and **Destroyed Cities**.
Cities are street grids of wrecked buildings in one of five styles: *burned*, *collapsed*,
*blighted*, *butchered* or *meteor-struck*. They're full of Rotwalkers, loot and wrecked cars.

**Ores:** Necrite, Bloodstone, Voidsteel and Stardust, each with an armour set and set bonus.
Full Necrite gives night vision, full Bloodstone gives lifesteal, full Voidsteel gives
resistance, and full Stardust gives extra mana and faster regeneration.

## Combat, weapons and magic
- **Dodge** with invulnerability frames. The Phantom Step enchantment shortens the cooldown.
- **Combo chains** with DeadCraft melee weapons: each hit within 1.5 s adds 8 % damage, and the
  fifth hit is a **finisher** whose effect depends on the weapon's trait: cleave, lifesteal,
  void, starfall, reap, time or shatter.
- **Weapons:** Bone Cleaver, Bloodreaver, Voidsteel Longsword, Star Edge, Hollow Scythe, Chrono Blade, Fracture Greatsword.
- **Guns:** Rusted Revolver, Doomsday Shotgun, Marksman Rifle, Scrap SMG. They have 3D models, use real ammo and reload, and their recoil kicks the camera.
- **Magic:** read a **Grimoire of Awakening** (bosses drop it; you can also craft it) to unlock
  mana. Then: Soul Staff (homing bolt), Ember Staff (fireball), Frost Tome (freezing nova),
  Void Tome (blink), Storm Rod (lightning), Blood Chalice (heal). Mana Tonics refill mana.
- **Enchantments:** Bloodthirst, Soul Reaper, Exorcism, Hollow Point, Quick Reload, Phantom Step, Static Ward.

## Presentation
- **Title screen:** a parallax graveyard under a blood moon. The sky, a ruined castle with lit
  windows, two drifting fog banks, a hill of crosses and dead trees, and a wall of skeletons each
  move at their own depth with the mouse. Embers rise out of the graves, and lightning strobes
  the sky with far thunder a moment later. Over that: a bleeding DEADCRAFT logo with chromatic
  glitches, blood running down the screen, rolling static and scanlines, a dark ambient score and a static hiss.
- **Menus and GUIs:** the panorama behind every menu is the same blood-moon graveyard, painted as a
  1024px cube map. All containers (inventory, chests, furnaces, crafting, anvil, enchanting,
  brewing, villagers, hoppers, ...) are re-painted as stained, cracked bone with dark slots and
  blood drips. The menu backgrounds, separators, tabs, sliders, text fields, checkboxes, scrollers,
  hotbar, offhand slots, XP bar, crosshair and every boss bar colour are re-drawn too, and every button is re-skinned.
- **HUD:** health is a glass vial of sloshing blood and hunger a vial of bile, with a skull medallion
  between them whose eyes burn as you near death. Lost health leaves a pale "damage ghost" in the
  vial, the glass cracks under 30%, and the vial shakes when you are hit. Armour is a row of shield
  pips and mana a vial of souls. There is also a vignette, film grain, a red heartbeat pulse at low health, and bleeding edges.
- **Camera:** a slow, heavy sway when walking and a faster, tighter one when running. It leans into strafes, dips on a spring when you land, rolls when you dodge, and handles recoil and screen shake (bosses shake the screen). FOV eases in when sprinting.
- **First-person view model:** inertia sway, bob, a lowered sprint pose and recoil kick.
- **Third-person animations:** the torso counter-rotates while walking, the arms pump when
  sprinting, and every stride bounces.
- **Sound:** 58 sound events (88 files): gear rustle per stride, running steps, breathing after long
  sprints, a low-health heartbeat, landing thuds, and every weapon, spell, creature, boss, portal and vehicle.
- **VFX:** 11 custom particles (blood, embers, souls, sparks, stars, runes, sword slashes, ash,
  frost, muzzle flash, ground shockwaves). Creatures have glowing eyes and lava cracks, and projectiles have emissive halos.

Every option is in `config/deadcraft-client.toml` (camera sway strength, film grain, HUD, title
screen, animations, movement sounds) and `deadcraft-server.toml` (voice hearing range).

## Editing the art

All art is generated from code so the project rebuilds from scratch, and every piece can be replaced:

```sh
python3 tools/gen_assets.py   # models, textures, item/block models, lang, sounds.json
python3 tools/gen_sounds.py   # 88 .ogg files (numpy + ffmpeg)
python3 tools/gen_data.py     # worldgen, structures, loot, recipes, tags, enchantments
python3 tools/validate.py     # cross-checks every reference (items, features, textures, lang)
python3 tools/preview.py out.png   # renders every creature in 3D to a contact sheet for review
```

How the textures are made:
- `tools/tex.py` holds the material library: coherent value noise, Voronoi stone and crystal, fur,
  wool, feathers, chitin, cloth (with tattered hems), leather, bone, metal, void, starfield and
  ethereal materials. Its shading shifts shadows toward violet and highlights toward warm yellow,
  and adds soft ambient occlusion down each face. Materials use absolute texture coordinates, so
  patterns continue across neighbouring faces.
- `tools/rigs.py` builds every creature's skeleton: a base rig per body type (gaunt, crawler,
  wraith, brute, quadruped, critter, flyer) with jointed limbs (shin and foot, forearm and hand,
  neck and jaw), plus extras unique to each creature (back arms, rock spikes, orbiting shards,
  lanterns, flames, halos, clock hands, pendulums, masks, wings, tails, tentacle chains, ...).
- `tools/anims.py` writes the keyframe animations (walk, idle, attack, cast, fly) for each rig to
  `assets/deadcraft/neoforge/animations/entity/<id>/`, the same keyframes go into the
  `.bbmodel` files, and secondary motion (chains sway, orbits spin, flames flicker) is added per bone.
- `tools/terrain.py` paints the seamless terrain textures, the plants, the animated blood and the
  sky bodies (blood moon, shattered moon, void eye). `tools/gen_data.py` writes each dimension's
  noises, density functions (`terrain()`), surface rules, biomes and features.
- `tools/skins.py` paints each creature at twice the model's texel density: which material each body part uses, its face style
  (skull, demon, void, mask, beast, critter, bird, insect, ...) and detail decorators (stitches,
  bandages, ribs, the Hollow's empty chest, Chronos's clock, pustules, runes, glowing stripes).
  Anything that should glow goes into the `_eyes.png` emissive layer.
- `tools/icons.py` upscales each item design to 32x32, adds material detail, lights it from the
  top-left with a bevel and glints, and colours its outline from the pixels it borders.
  `tools/items3d.py` builds the 3D held models for the melee weapons and staves. `tools/art.py`
  makes blocks, gun atlases, particles and projectiles. `tools/gui.py` makes the buttons, logo and
  mod icon, and `tools/gui2.py` makes the HUD vials, panorama, title scene layers, container
  re-skins and widget sprites.

- **Entity models:** `blockbench/*.bbmodel` holds one Blockbench project per creature and vehicle,
  with its texture embedded. Open one in Blockbench ("Modded Entity" format), refine it, then export
  the Java model and texture. The project includes every animation, so you can scrub and edit them in
  the Animate tab. The game reads the NeoForge keyframe JSON written by `tools/anims.py`, so carry
  edits back there (or swap the JSON for Blockbench's exported Java keyframes). The geometry the game loads comes from
  `tools/models.py` → `ModelGeometry.java`. Keep bone names the same so the animations still apply.
- **Gun models:** `src/main/resources/assets/deadcraft/models/item/<gun>.json` are Blockbench
  "Java Block/Item" models. The Display tab tunes the first-person view model.
- **Sounds:** replace any `.ogg` under `assets/deadcraft/sounds/` with a recording of the same name.

## Project layout

```
deadcraft/
├── blockbench/            # .bbmodel projects for every creature and vehicle
├── tools/                 # asset / data / sound generators and the validator
└── src/main/java/com/deadcraft/
    ├── entity/            # DeadMob (the creature table), DeadMonster, DeadAnimal, Rotwalker, projectiles, vehicles
    ├── item/              # guns, spells, weapons, rift keys, sigils, vehicles
    ├── combat/            # dodge, combos, enchantment effects, set bonuses, status effects
    ├── world/             # dimensions, travel, hearing, structures (DeadStructure / DeadPiece), features
    ├── compat/voicechat/  # Simple Voice Chat plugin
    ├── client/            # title screen, HUD, camera, models, renderers, particles, sounds
    └── mixin/             # third-person walk/run animation
```
