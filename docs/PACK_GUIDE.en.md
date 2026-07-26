# SoundCIT — pack author's guide

Everything you need to build a sound pack: the rule format, the full list of sounds that can be
replaced, and an honest account of how reliable each category is.

In a hurry? Read [Five-minute pack](#five-minute-pack) and come back for the rest.

---

## Contents

- [How it works, briefly](#how-it-works-briefly)
- [Five-minute pack](#five-minute-pack)
- [Pack layout](#pack-layout)
- [Rule format](#rule-format)
- [Every trigger](#every-trigger)
- [Replacing a sound by its id](#replacing-a-sound-by-its-id)
- [Sound file requirements](#sound-file-requirements)
- [Debugging](#debugging)
- [How reliable is each category](#how-reliable-is-each-category)
- [Installing on a server](#installing-on-a-server)
- [Common mistakes](#common-mistakes)

---

## How it works, briefly

The mod listens to **every** sound the client is about to play and works out which item caused it.
If it can name the item and that item's custom name matches one of your rules, the sound is swapped
for yours.

Working out the item is the hard part: most sounds arrive from the server as "play this sound at
this position", with nothing about the item involved. So the mod uses several independent routes,
from the most trustworthy to the least. Which route succeeded decides how reliable the replacement
is — see [How reliable is each category](#how-reliable-is-each-category).

---

## Five-minute pack

Goal: a mace named **Frying Pan** that clangs like one.

**1.** Create a pack folder with `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 88,
    "description": "My sounds"
  }
}
```

> The format number depends on the game version: **88** for 26.2, **34** for 1.21.1.

**2.** Put your sound at `assets/mypack/sounds/frying_pan.ogg`.

**3.** Declare it in `assets/mypack/sounds.json`:

```json
{
  "frying_pan": {
    "sounds": ["mypack:frying_pan"]
  }
}
```

**4.** Add a rule at `assets/mypack/soundcit/frying_pan.json`:

```json
{
  "item": "minecraft:mace",
  "pattern": "Frying Pan",
  "sounds": {
    "hit": "mypack:frying_pan"
  }
}
```

**5.** Enable the pack, rename a mace to "Frying Pan" on an anvil, hit something.

**F3+T** reloads rules and sounds without restarting the game.

---

## Pack layout

```
MyPack/
├── pack.mcmeta
└── assets/
    └── mypack/                      ← your namespace, any lowercase name
        ├── sounds.json              ← sound declarations for Minecraft
        ├── sounds/
        │   ├── frying_pan.ogg
        │   └── mjolnir_thunder.ogg
        └── soundcit/                ← SoundCIT rules
            ├── frying_pan.json
            └── mjolnir.json
```

The mod reads **every** `.json` under `soundcit/` in **every** namespace of **every** enabled pack.
File names mean nothing — organise them however you like, including subfolders.

Rules from different packs add up. When two packs describe the same item, the one with the higher
[`priority`](#priority--who-wins) wins.

---

## Rule format

```json
{
  "item": "minecraft:mace",
  "match": "custom_name",
  "pattern": "Frying Pan",
  "priority": 0,
  "sounds": {
    "hit": "mypack:frying_pan_hit",
    "attack": "mypack:frying_pan_swing"
  }
}
```

| Field | Required | Meaning |
|---|---|---|
| `item` | no | Which items. A string or an array. Omit it and the rule applies to any item whose name matches. |
| `match` | no | Only `custom_name` is supported so far (the default). |
| `pattern` | **yes** | How to compare the item's custom name. |
| `priority` | no | Integer, default `0`. Decides between rules that both match. |
| `sounds` | **yes** | What to replace with what. An empty object is an error. |

### `pattern` — matching the name

The **visible name** is compared — what you type on an anvil or set with the
`minecraft:custom_name` component. Formatting (colour, italics) is ignored; plain text is compared.

| Syntax | Behaviour |
|---|---|
| `Frying Pan` | Exact match, **case-insensitive** |
| `pattern:Frying*` | Wildcard: `*` any run, `?` one character. Case-sensitive |
| `ipattern:frying*` | Same, case-insensitive |
| `regex:^Sword .*$` | Regular expression, case-sensitive |
| `iregex:^sword .*$` | Regular expression, case-insensitive |

Wildcards and regular expressions must match the **whole** name, not a part of it. To find a word
inside a name, wrap it in asterisks: `ipattern:*hammer*`.

### `priority` — who wins

When several rules match the same item, the **highest** `priority` wins. Ties are broken by file
path, which is deterministic but not something to rely on — give competing rules explicit
priorities.

```json
{ "pattern": "*", "priority": -10, "sounds": { "hit": "mypack:generic" } }
```

A catch-all like this only applies where nothing more specific did.

---

## Every trigger

What each trigger actually replaces. The right-hand column lists the exact vanilla ids it reacts to.

### Melee

| Trigger | When | Vanilla sounds |
|---|---|---|
| `attack` | Swing, miss, sweep | `entity.player.attack.weak`, `.nodamage`, `.sweep` |
| `hit` | A landed hit, including a mace smash | `entity.player.attack.strong`, `.crit`, `.knockback`, `item.mace.smash_ground`, `.smash_ground_heavy`, `.smash_air` |

### Eating and drinking

| Trigger | When | Vanilla sounds |
|---|---|---|
| `eat` | Chewing and the burp | `entity.generic.eat`, `entity.player.burp` |
| `drink` | Drinking, honey included | `entity.generic.drink`, `item.honey_bottle.drink` |
| `chorus_teleport` | Chorus fruit teleport | `item.chorus_fruit.teleport` |

### Mining and building

| Trigger | When | Vanilla sounds |
|---|---|---|
| `mine` | Each hit while breaking a block | every `block.*.hit` |
| `break` | Block broken | every `block.*.break` |
| `place` | Block placed | every `block.*.place` |

### Shooting and throwing

| Trigger | When | Vanilla sounds |
|---|---|---|
| `shoot` | Bow or crossbow shot | `entity.arrow.shoot`, `item.crossbow.shoot` |
| `crossbow_load` | Crossbow charging stages | `item.crossbow.loading_start`, `.loading_middle`, `.quick_charge_1..3` |
| `crossbow_load_end` | Crossbow charged | `item.crossbow.loading_end` |
| `arrow_hit` | Arrow lands | `entity.arrow.hit`, `.hit_player`, `item.crossbow.hit` |
| `throw` | Snowball, egg, pearl, potion, eye, wind charge | `entity.snowball.throw`, `entity.egg.throw`, `entity.ender_pearl.throw`, `entity.experience_bottle.throw`, `entity.splash_potion.throw`, `entity.lingering_potion.throw`, `entity.wind_charge.throw`, `entity.ender_eye.launch` |

### Trident

| Trigger | When | Vanilla sounds |
|---|---|---|
| `trident_throw` | Thrown | `item.trident.throw` |
| `trident_return` | Loyalty return | `item.trident.return` |
| `trident_hit` | Hits a creature | `item.trident.hit` |
| `trident_hit_ground` | Sticks into a block | `item.trident.hit_ground` |
| `riptide` | Riptide dash | `item.trident.riptide_1..3` |
| `thunder` | Channeling lightning | `item.trident.thunder` |

### Defence and survival

| Trigger | When | Vanilla sounds |
|---|---|---|
| `shield_block` | A hit blocked by a shield | `item.shield.block` |
| `shield_break` | Shield broken | `item.shield.break` |
| `totem_use` | Totem of undying fires | `item.totem.use` |

### Item state

| Trigger | When | Vanilla sounds |
|---|---|---|
| `equip` | Armour and elytra put on | every `item.armor.equip*` |
| `item_break` | An item wears out and breaks | `entity.item.break`, `item.wolf_armor.break` |
| `elytra` | The looping flight sound | `item.elytra.flying` |

### Fishing

| Trigger | When | Vanilla sounds |
|---|---|---|
| `fish_cast` | Cast | `entity.fishing_bobber.throw` |
| `fish_retrieve` | Reel in | `entity.fishing_bobber.retrieve` |
| `fish_splash` | A bite | `entity.fishing_bobber.splash` |

### Containers and fluids

| Trigger | When | Vanilla sounds |
|---|---|---|
| `bucket_fill` | Filling a bucket | every `item.bucket.fill*` |
| `bucket_empty` | Emptying a bucket | every `item.bucket.empty*` |
| `bottle_fill` | Filling a bottle | `item.bottle.fill`, `item.bottle.fill_dragonbreath` |
| `bottle_empty` | Emptying a bottle | `item.bottle.empty` |

### Tools on blocks and creatures

| Trigger | When | Vanilla sounds |
|---|---|---|
| `till` | Hoe tills soil | `item.hoe.till` |
| `strip` | Axe strips bark | `item.axe.strip` |
| `scrape` | Axe scrapes oxidation | `item.axe.scrape` |
| `wax_off` | Axe removes wax | `item.axe.wax_off` |
| `wax_on` | Honeycomb waxes a block | `item.honeycomb.wax_on` |
| `flatten` | Shovel makes a path | `item.shovel.flatten` |
| `brush` | Brushing | every `item.brush.brushing*` |
| `ignite` | Flint and steel, fire charge | `item.flintandsteel.use`, `item.firecharge.use` |
| `shear` | Shearing | `entity.sheep.shear`, `block.beehive.shear`, `entity.snow_golem.shear`, `entity.mooshroom.shear`, `entity.bogged.shear` |
| `bone_meal` | Bone meal | `item.bone_meal.use` |
| `dye` | Dye, ink sacs | `item.dye.use`, `item.ink_sac.use`, `item.glow_ink_sac.use` |

### Other items

| Trigger | When | Vanilla sounds |
|---|---|---|
| `spyglass` | Spyglass | `item.spyglass.use`, `item.spyglass.stop_using` |
| `instrument` | Goat horn | every `item.goat_horn.*` (`item.goat_horn.play*` on 1.21.x, `item.goat_horn.sound.N` on 26.2, where instruments became data-driven) |
| `bundle` | Bundle | `item.bundle.insert`, `.remove_one`, `.drop_contents` |
| `use` | A few leftovers | `item.lodestone_compass.lock`, `entity.armor_stand.place`, `item.ominous_bottle.dispose` |

> **About `use`.** It is deliberately narrow. "Any `item.*` sound while using something" would steal
> chest opens, note blocks and every bucket sound that happened to coincide. For a specific sound,
> [replace it by id](#replacing-a-sound-by-its-id).

### Workstations

| Trigger | When | Vanilla sounds |
|---|---|---|
| `anvil` | Anvil | `block.anvil.use`, `block.anvil.destroy` |
| `grindstone` | Grindstone | `block.grindstone.use` |
| `smithing` | Smithing table | `block.smithing_table.use` |
| `enchant` | Enchanting table | `block.enchantment_table.use` |

---

## Replacing a sound by its id

Any key containing a colon is treated as a **full vanilla sound id** rather than a trigger:

```json
"sounds": {
  "minecraft:item.mace.smash_ground_heavy": "mypack:earthquake",
  "minecraft:item.trident.thunder": "mypack:thor"
}
```

A direct id **beats** a semantic trigger, so you can set general behaviour with a trigger and
override one special case:

```json
"sounds": {
  "hit": "mypack:generic_hit",
  "minecraft:item.mace.smash_ground_heavy": "mypack:heavy_smash"
}
```

This works for modded sounds too: `"othermod:item.hammer.smash": "mypack:clang"`.

---

## Sound file requirements

- **Ogg Vorbis** (`.ogg`). Minecraft reads nothing else.
- **Mono** for positional sounds, which is almost every item sound. A stereo file plays at the same
  volume from every direction, with no sense of place.
- 44100 Hz is the usual sample rate.
- Files live in `assets/<namespace>/sounds/`; `sounds.json` refers to them without the `sounds/`
  prefix and without the extension.

Several variants, picked at random:

```json
{
  "frying_pan_hit": {
    "sounds": [
      "mypack:frying_pan_hit1",
      "mypack:frying_pan_hit2",
      "mypack:frying_pan_hit3"
    ],
    "subtitle": "mypack.subtitle.frying_pan"
  }
}
```

The subtitle is optional, but without one the subtitle line stays empty for players who use them.
Add the text to `assets/<namespace>/lang/en_us.json`.

You do **not** need to register a `SoundEvent` in code — `sounds.json` is enough.

---

## Debugging

| Command | What it does |
|---|---|
| `/soundcit list` | Loaded rules, and whether the server is helping |
| `/soundcit debug` | Verbose log: which candidates were considered and why they were rejected |
| `/soundcit why` | Recent replacements and which layer resolved each |

In `/soundcit why`, the layer tells you how much to trust the result:

| Layer | Meaning | Trust |
|---|---|---|
| `server` | The server named the item outright | Fact |
| `entity` | Taken from the entity the sound was bound to | Fact |
| `context` | The client predicted the action beforehand | Usually right |
| `proximity` | A matching entity was found near the sound | Guess |

---

## How reliable is each category

**Always works, even on a vanilla server:** melee, eating and drinking, mining and placing, tools
used on blocks (hoe, axe, shovel, flint and steel, wax, bone meal), buckets and bottles, spyglass,
goat horn, bundle, item breaking.

**Yours works, other people's needs the mod on the server:** totem, shield block, armour equipped
from an inventory, shearing. For your own actions the client knows enough; for other entities it
does not.

**Only with the mod on the server:** an arrow hit that depends on **which bow** fired it. The
projectile's weapon is never synced to clients.

**May not fire:**

- `trident_throw` — the sound is bound to a projectile the client may not know about yet; vanilla
  drops such sounds silently, so there is nothing to replace. Return and hit sounds are reliable.
- On the 2026 releases, sounds of **flying tridents and arrows** rely on the mod having remembered
  your throw: the projectile no longer tells the client which item it was (1.21.x worked
  differently — the projectile inherited the item's name). Other players' projectiles cannot be
  identified without the mod on the server.
- Workstations (`anvil`, `grindstone`, `smithing`, `enchant`) and `bone_meal`/`wax_on` travel by a
  different route with no item at the playback site; they are matched by block position.
- Under heavy lag a predicted action may expire: contexts live about a second.

**Deliberately never replaced:** music, records, ambient and voice categories. And when two players
nearby both hold matching items and the mod cannot tell them apart, it replaces **nothing** —
stealing someone else's sound is worse than missing your own.

---

## Installing on a server

You do **not** have to install SoundCIT on the server — it works on a vanilla one.

Installing it there improves accuracy: the server tells the client which item caused each sound, and
guesses become facts. Your **rules stay yours** — the server never sees them, so every player can
run a different pack.

Compatibility holds in both directions: a vanilla client can join a server with the mod, and a
client with the mod can join a vanilla server.

`/soundcit list` reports `server-assisted` or `client-only`.

---

## Common mistakes

**Nothing changes and the log is silent.** The rule did not load. Check `/soundcit list`; if it is
missing, the JSON is malformed (the mod logs why on load) or the file is not under
`assets/<namespace>/soundcit/`.

**The rule loaded but the sound is vanilla.** The name probably did not match. Case does not matter
for a plain `pattern`, but stray spaces do. Turn on `/soundcit debug` and see whether the item is
identified at all.

**The sound plays at full volume from everywhere.** The file is stereo. Re-export it as mono.

**The wrong sound got replaced.** Triggers overlap — `hit` covers both an ordinary hit and a mace
smash. Use a [direct id](#replacing-a-sound-by-its-id) instead.

**It works for you but not for others.** That is by design: the pack is installed on your client
only. Everyone who should hear it needs the pack.

**Edits do nothing.** Press F3+T. If that does not help, the pack is not enabled, or the game is
reading an older copy from `resourcepacks/`.
