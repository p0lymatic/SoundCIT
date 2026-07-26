#!/usr/bin/env python3
"""
SoundCIT pack builder — a terminal tool for making and checking sound packs.

Creating a pack by hand means writing pack.mcmeta, sounds.json and a rule file that all have to
agree with each other; a single typo in a namespace or a missing .ogg produces silence with no
explanation. This does the bookkeeping and, more importantly, tells you when the pieces do not
line up.

    soundcit-pack.py new MyPack --namespace mypack
    soundcit-pack.py add MyPack --item minecraft:mace --name "Frying Pan" \
                                --sound hit=pan_hit --file ~/pan.wav
    soundcit-pack.py check MyPack
    soundcit-pack.py build MyPack

Converting audio needs ffmpeg; everything else is pure Python.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

# Resource pack format numbers, by game version. Taken from SharedConstants in the game itself.
PACK_FORMATS = {
    "26.2": 88,
    "1.21.1": 34,
}
DEFAULT_VERSION = "26.2"

# Semantic triggers the mod understands, grouped the way the guide presents them.
TRIGGERS = {
    "melee": ["attack", "hit"],
    "use": ["use", "eat", "drink", "chorus_teleport"],
    "mining": ["mine", "break", "place"],
    "shooting": ["shoot", "throw", "crossbow_load", "crossbow_load_end", "arrow_hit"],
    "trident": ["trident_throw", "trident_return", "trident_hit", "trident_hit_ground",
                "riptide", "thunder"],
    "defence": ["shield_block", "shield_break", "totem_use"],
    "item state": ["equip", "item_break", "elytra"],
    "fishing": ["fish_cast", "fish_retrieve", "fish_splash"],
    "containers": ["bucket_fill", "bucket_empty", "bottle_fill", "bottle_empty"],
    "tools": ["till", "strip", "scrape", "wax_on", "wax_off", "flatten", "brush", "ignite",
              "shear", "bone_meal", "dye"],
    "misc": ["spyglass", "instrument", "bundle"],
    "workstations": ["anvil", "grindstone", "smithing", "enchant"],
}
ALL_TRIGGERS = {t for group in TRIGGERS.values() for t in group}


def fail(message: str) -> "NoReturn":
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        fail(f"{path} is not valid JSON: {e}")


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def find_namespace(pack: Path) -> str:
    assets = pack / "assets"
    if not assets.is_dir():
        fail(f"{pack} has no assets/ directory — is it a resource pack?")
    namespaces = [d.name for d in assets.iterdir() if d.is_dir()]
    if not namespaces:
        fail(f"{assets} contains no namespace directory")
    if len(namespaces) > 1:
        fail(f"{assets} has several namespaces ({', '.join(namespaces)}); pass --namespace")
    return namespaces[0]


# --------------------------------------------------------------------------------------- commands

def cmd_new(args: argparse.Namespace) -> None:
    pack = Path(args.pack)
    if pack.exists() and any(pack.iterdir()):
        fail(f"{pack} already exists and is not empty")
    ns = args.namespace or pack.name.lower().replace(" ", "_").replace("-", "_")
    fmt = PACK_FORMATS.get(args.mc_version)
    if fmt is None:
        fail(f"unknown Minecraft version {args.mc_version}; known: {', '.join(PACK_FORMATS)}")

    write_json(pack / "pack.mcmeta", {
        "pack": {"pack_format": fmt, "description": args.description or f"{pack.name} — SoundCIT pack"}
    })
    write_json(pack / "assets" / ns / "sounds.json", {})
    (pack / "assets" / ns / "sounds").mkdir(parents=True, exist_ok=True)
    (pack / "assets" / ns / "soundcit").mkdir(parents=True, exist_ok=True)
    write_json(pack / "assets" / ns / "lang" / "en_us.json", {})

    print(f"created {pack} (namespace '{ns}', pack_format {fmt} for Minecraft {args.mc_version})")
    print("next: soundcit-pack.py add", pack, "--item minecraft:mace --name \"Frying Pan\""
          " --sound hit=pan_hit --file /path/to/sound.wav")


def convert_audio(source: Path, target: Path) -> None:
    """Convert to mono Ogg Vorbis — positional sounds must be mono or they play from everywhere."""
    if shutil.which("ffmpeg") is None:
        fail("ffmpeg is needed to convert audio; install it or pass an .ogg file directly")
    target.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", str(source),
         "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", str(target)],
        capture_output=True, text=True)
    if result.returncode != 0:
        fail(f"ffmpeg failed to convert {source}:\n{result.stderr.strip()}")


def cmd_add(args: argparse.Namespace) -> None:
    pack = Path(args.pack)
    ns = args.namespace or find_namespace(pack)
    base = pack / "assets" / ns

    sounds: dict[str, str] = {}
    for pair in args.sound:
        if "=" not in pair:
            fail(f"--sound expects trigger=soundname, got '{pair}'")
        trigger, name = pair.split("=", 1)
        if trigger not in ALL_TRIGGERS and ":" not in trigger:
            fail(f"unknown trigger '{trigger}'. Use a full vanilla sound id "
                 f"(minecraft:item.mace.smash_ground) or one of: {', '.join(sorted(ALL_TRIGGERS))}")
        sounds[trigger] = name

    if not sounds:
        fail("give at least one --sound trigger=soundname")

    # Register each sound name in sounds.json, and copy/convert the audio if one was given.
    sounds_json_path = base / "sounds.json"
    sounds_json = read_json(sounds_json_path) if sounds_json_path.exists() else {}
    for trigger, name in sounds.items():
        sounds_json.setdefault(name, {"sounds": [f"{ns}:{name}"]})
    write_json(sounds_json_path, sounds_json)

    if args.file:
        source = Path(args.file).expanduser()
        if not source.is_file():
            fail(f"{source} does not exist")
        first_name = next(iter(sounds.values()))
        target = base / "sounds" / f"{first_name}.ogg"
        if source.suffix.lower() == ".ogg":
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(source, target)
            print(f"copied {source} -> {target} (not converted; make sure it is mono)")
        else:
            convert_audio(source, target)
            print(f"converted {source} -> {target} (mono, 44.1 kHz, vorbis)")

    rule = {"pattern": args.name, "sounds": {t: f"{ns}:{n}" for t, n in sounds.items()}}
    if args.item:
        rule = {"item": args.item if len(args.item) > 1 else args.item[0], **rule}
    if args.priority:
        rule["priority"] = args.priority

    slug = args.rule_name or "".join(c if c.isalnum() else "_" for c in args.name).strip("_").lower()
    rule_path = base / "soundcit" / f"{slug}.json"
    if rule_path.exists() and not args.force:
        fail(f"{rule_path} already exists; pass --force to overwrite")
    write_json(rule_path, rule)
    print(f"wrote rule {rule_path}")

    missing = [n for n in sounds.values() if not (base / "sounds" / f"{n}.ogg").exists()]
    if missing:
        print(f"note: no audio yet for {', '.join(missing)} —"
              f" put {', '.join(n + '.ogg' for n in missing)} in {base / 'sounds'}")


def cmd_check(args: argparse.Namespace) -> None:
    """Report every way the pack's pieces fail to line up. This is the useful part."""
    pack = Path(args.pack)
    problems: list[str] = []
    notes: list[str] = []

    meta_path = pack / "pack.mcmeta"
    if not meta_path.exists():
        problems.append("pack.mcmeta is missing — the game will not see this as a resource pack")
    else:
        meta = read_json(meta_path)
        fmt = meta.get("pack", {}).get("pack_format")
        if fmt not in PACK_FORMATS.values():
            notes.append(f"pack_format {fmt} matches no version this tool knows "
                         f"({', '.join(f'{v}={f}' for v, f in PACK_FORMATS.items())})")

    ns = args.namespace or find_namespace(pack)
    base = pack / "assets" / ns
    sounds_json_path = base / "sounds.json"
    sounds_json = read_json(sounds_json_path) if sounds_json_path.exists() else {}

    declared: set[str] = set()
    for name, entry in sounds_json.items():
        declared.add(f"{ns}:{name}")
        for sound in entry.get("sounds", []):
            path_part = sound["name"] if isinstance(sound, dict) else sound
            sound_ns, _, sound_path = path_part.partition(":")
            if not sound_path:
                sound_ns, sound_path = ns, path_part
            ogg = pack / "assets" / sound_ns / "sounds" / f"{sound_path}.ogg"
            if not ogg.exists():
                problems.append(f"sounds.json entry '{name}' points at {path_part}, "
                                f"but {ogg} does not exist")

    rules_dir = base / "soundcit"
    rule_files = sorted(rules_dir.rglob("*.json")) if rules_dir.is_dir() else []
    if not rule_files:
        problems.append(f"no rules found in {rules_dir}")

    for rule_path in rule_files:
        rule = read_json(rule_path)
        rel = rule_path.relative_to(pack)
        if "pattern" not in rule:
            problems.append(f"{rel}: no \"pattern\" — the rule can never match")
        if "sounds" not in rule or not rule["sounds"]:
            problems.append(f"{rel}: \"sounds\" is missing or empty")
            continue
        for trigger, replacement in rule["sounds"].items():
            if ":" not in trigger and trigger not in ALL_TRIGGERS:
                problems.append(f"{rel}: unknown trigger '{trigger}'")
            if replacement.lower() == "none":
                continue
            if ":" not in replacement:
                problems.append(f"{rel}: replacement '{replacement}' has no namespace "
                                f"(write {ns}:{replacement})")
            elif replacement not in declared and replacement.split(":")[0] == ns:
                problems.append(f"{rel}: replacement '{replacement}' is not declared in sounds.json"
                                f" — the mod will keep the vanilla sound")

    for problem in problems:
        print(f"  problem: {problem}")
    for note in notes:
        print(f"  note:    {note}")
    if not problems:
        print(f"{pack}: {len(rule_files)} rule(s), {len(sounds_json)} sound(s) — all references resolve")
    sys.exit(1 if problems else 0)


def cmd_build(args: argparse.Namespace) -> None:
    pack = Path(args.pack)
    if not (pack / "pack.mcmeta").exists():
        fail(f"{pack}/pack.mcmeta not found")
    out = Path(args.output) if args.output else pack.with_suffix(".zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(pack.rglob("*")):
            if path.is_file():
                z.write(path, path.relative_to(pack))
    print(f"built {out} ({out.stat().st_size // 1024} KiB)")


def cmd_triggers(args: argparse.Namespace) -> None:
    for group, triggers in TRIGGERS.items():
        print(f"{group}:")
        print("   ", ", ".join(triggers))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    p_new = sub.add_parser("new", help="create an empty pack")
    p_new.add_argument("pack")
    p_new.add_argument("--namespace")
    p_new.add_argument("--description")
    p_new.add_argument("--mc-version", default=DEFAULT_VERSION)
    p_new.set_defaults(func=cmd_new)

    p_add = sub.add_parser("add", help="add a rule, and optionally import an audio file")
    p_add.add_argument("pack")
    p_add.add_argument("--name", required=True, help="item name to match, e.g. \"Frying Pan\" or ipattern:*pan*")
    p_add.add_argument("--item", action="append", help="item id; repeat for several")
    p_add.add_argument("--sound", action="append", default=[], metavar="TRIGGER=NAME")
    p_add.add_argument("--file", help="audio file to convert into the pack")
    p_add.add_argument("--priority", type=int)
    p_add.add_argument("--rule-name")
    p_add.add_argument("--namespace")
    p_add.add_argument("--force", action="store_true")
    p_add.set_defaults(func=cmd_add)

    p_check = sub.add_parser("check", help="verify every reference in the pack resolves")
    p_check.add_argument("pack")
    p_check.add_argument("--namespace")
    p_check.set_defaults(func=cmd_check)

    p_build = sub.add_parser("build", help="zip the pack")
    p_build.add_argument("pack")
    p_build.add_argument("--output")
    p_build.set_defaults(func=cmd_build)

    p_triggers = sub.add_parser("triggers", help="list every trigger the mod understands")
    p_triggers.set_defaults(func=cmd_triggers)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
