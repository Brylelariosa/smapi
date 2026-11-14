#!/usr/bin/env python3
"""Regenerate compat-db.json from a SMAPI source tree or archive.

Everything under "upstream*" is mined from the SMAPI sources themselves
(docs/release-notes*.md, src/SMAPI/Constants.cs, src/SMAPI/SMAPI.csproj,
build/common.targets). Nothing there is hand-edited, so re-running against a newer
SMAPI checkout refreshes the data.

The "androidPorts" section is different: SMAPI's own repository contains no
Android host, so those rows can only come from third-party documentation. They are
written with verification="documented-unverified" and must be promoted to
"verified-on-device" by a human after a real test. Nothing is auto-promoted.

usage:
  python3 tools/generate_compat_db.py SMAPI-develop.zip \
      > core/src/main/resources/com/universalsmapi/core/compat/compat-db.json
"""
import json
import re
import sys
import zipfile
from pathlib import Path


class SourceTree:
    """Read files from either a directory or a GitHub-style source zip."""

    def __init__(self, path: str):
        self.path = Path(path)
        self.zip = None
        self.prefix = ""
        if self.path.is_file():
            self.zip = zipfile.ZipFile(self.path)
            roots = {n.split("/", 1)[0] for n in self.zip.namelist() if "/" in n}
            if len(roots) == 1:
                self.prefix = roots.pop() + "/"

    def read(self, rel: str) -> str:
        if self.zip:
            return self.zip.read(self.prefix + rel).decode("utf-8-sig")
        return (self.path / rel).read_text(encoding="utf-8-sig")


# Mirrors src/SMAPI/Framework/GameVersion.cs VersionMap (old non-semver game versions).
GAME_VERSION_MAP = {"1.0": "1.0.0", "1.1": "1.1.0", "1.2": "1.2.0", "1.11": "1.1.1"}
MONTHS = {m: i for i, m in enumerate(
    ["January", "February", "March", "April", "May", "June", "July", "August",
     "September", "October", "November", "December"], 1)}


def norm_game(v: str) -> str:
    v = v.strip().strip("~")
    if v in GAME_VERSION_MAP:
        return GAME_VERSION_MAP[v]
    if re.fullmatch(r"\d+\.\d+", v):  # "1.4" -> "1.4.0"
        return v + ".0"
    return v


def parse_support(text: str):
    """Turn the 'Stardew Valley <text>' wording into (min, max-or-None)."""
    t = text.strip()
    m = re.fullmatch(r"(\S+) or later", t)
    if m:
        return norm_game(m.group(1)), None
    m = re.fullmatch(r"(?:~~)?([\d.]+)(?:~~)?[\u2013-]([\d.]+)", t)
    if m:
        lo, hi = m.group(1), m.group(2)
        if "." not in hi:  # "1.3.32-33" -> "1.3.33"
            hi = lo.rsplit(".", 1)[0] + "." + hi
        return norm_game(lo), norm_game(hi)
    m = re.fullmatch(r"([\d.]+[a-z]?)", t)
    if m:
        v = norm_game(m.group(1))
        return v, v
    return None, None


def parse_release_notes(text: str, evidence: str):
    rows = []
    lines = text.splitlines()
    for i, line in enumerate(lines):
        h = re.match(r"^## (\d+\.\d+(?:\.\d+)*)\s*$", line)
        if not h:
            continue
        j = i + 1
        while j < len(lines) and not lines[j].strip():
            j += 1
        info = lines[j].strip() if j < len(lines) else ""
        m = re.match(r"^Released (\d{1,2}) (\w+) (\d{4}) for Stardew Valley (.+?)(?:\.(?:\s|$))", info)
        if not m:
            continue
        day, month, year, support = int(m.group(1)), m.group(2), int(m.group(3)), m.group(4)
        lo, hi = parse_support(support)
        rows.append({
            "version": h.group(1),
            "released": f"{year:04d}-{MONTHS[month]:02d}-{day:02d}",
            "gameMin": lo,
            "gameMax": hi,
            "supportText": support,
            "evidence": evidence,
        })
    return rows


def parse_exact_map(constants: str):
    body = constants.split("GetCompatibleApiVersion", 1)[1]
    pending, out = [], []
    for line in body.splitlines():
        c = re.search(r'case "([^"]+)":', line)
        if c:
            pending.append(c.group(1))
            continue
        r = re.search(r'return new SemanticVersion\("([^"]+)"\);', line)
        if r and pending:
            for g in pending:
                out.append({"game": g, "smapi": r.group(1),
                            "evidence": "src/SMAPI/Constants.cs GetCompatibleApiVersion"})
            pending = []
        if line.strip().startswith("default:"):
            break
    return out


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = SourceTree(sys.argv[1])
    constants = src.read("src/SMAPI/Constants.cs")
    csproj = src.read("src/SMAPI/SMAPI.csproj")
    targets = src.read("build/common.targets")

    api = re.search(r'RawApiVersion = "([^"]+)"', constants).group(1)
    min_game = re.search(r'MinimumGameVersion \{ get; \} = new GameVersion\("([^"]+)"\)', constants).group(1)
    max_m = re.search(r'MaximumGameVersion \{ get; \} = (null|new GameVersion\("([^"]+)"\))', constants)
    max_game = max_m.group(2) if max_m and max_m.group(2) else None
    refs = re.findall(r'<Reference Include="([^"]+)" HintPath="\$\(GamePath\)', csproj)
    aliases = re.search(r'AddWithExplicitNames\([^,]+,\s*((?:"[^"]+"(?:,\s*)?)+)\)', constants)
    alias_list = re.findall(r'"([^"]+)"', aliases.group(1)) if aliases else []

    releases = parse_release_notes(src.read("docs/release-notes.md"), "docs/release-notes.md")
    releases += parse_release_notes(src.read("docs/release-notes-archived.md"), "docs/release-notes-archived.md")

    db = {
        "schemaVersion": 1,
        "generator": "tools/generate_compat_db.py",
        "source": {
            "archive": Path(sys.argv[1]).name,
            "apiVersion": api,
            "buildVersionProperty": re.search(r"<Version>([^<]+)</Version>", targets).group(1),
            "minimumGameVersion": min_game,
            "maximumGameVersion": max_game,
            "targetFramework": re.search(r"<TargetFramework>([^<]+)</TargetFramework>", csproj).group(1),
            "platformTarget": re.search(r"<PlatformTarget>([^<]+)</PlatformTarget>", csproj).group(1),
        },
        "notes": [
            "upstream* sections describe desktop SMAPI releases (Windows/Linux/macOS). They are declared by the SMAPI project and were not tested on Android.",
            "SMAPI's Constants.cs says exact versions are safer than ranges: a release may not work with unlisted intermediate game builds.",
            "Android game versions carry a fourth number (platform release), e.g. 1.6.15.1; match on the first three parts and keep the fourth for diagnostics.",
            "No entry in this file is marked verified-on-device. Promote entries only after a real installation test.",
        ],
        "upstreamReferencedAssemblies": refs,
        "upstreamGameAssemblyAliases": alias_list,
        "upstreamReleases": releases,
        "upstreamExactRecommendations": parse_exact_map(constants),
        "androidPorts": {
            "status": "documented-unverified",
            "retrieved": "2026-11-12",
            "summary": "Official SMAPI cannot run on Android as-is; community ports exist (stardewvalleywiki.com Modding:Installing_SMAPI_on_Android). Android rows below come from third-party documentation and stay documented-unverified until tested on a real installation.",
            "documentedRequirements": [
                {
                    "launcher": "NRTnarathip/SMAPILoader",
                    "launcherPackage": "abc.smapi.gameloader",
                    "minGame": "1.6.15.1",
                    "stores": ["Play Store", "Galaxy Store"],
                    "requires64BitOs": True,
                    "zipNaming": "SMAPI.4.x.x-xxxx.zip",
                    "evidence": "https://stardewvalleywiki.com/Modding:Installing_SMAPI_on_Android ; https://github.com/NRTnarathip/SMAPILoader",
                    "verification": "documented-unverified",
                }
            ],
            "releases": [
                {
                    "name": "Community Android port, as used in a December 2025 tutorial",
                    "smapiVersion": "4.1.10.3",
                    "gameMin": "1.6.15.0",
                    "gameMax": "1.6.15.0",
                    "evidence": "https://www.bilibili.com/opus/1146898074988183554 (edited 16 Dec 2025): game 1.6.15.0 installed from an apks bundle, SMAPI Launcher app, SMAPI-4.1.10.3 zip; the in-game overlay shows SMAPI v4.1.10.3 and game 1.6.15",
                    "verification": "documented-unverified",
                }
            ],
        },
    }
    json.dump(db, sys.stdout, indent=2, ensure_ascii=False)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
