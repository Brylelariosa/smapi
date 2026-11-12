# Notices

## SMAPI-derived code

`core/.../version/SemVer.java` and `core/.../version/GameVersion.java` are Java ports of SMAPI's
`SemanticVersion`, `SemanticVersionReader` and `GameVersion` (SMAPI 4.5.2, Pathoschild and contributors),
licensed under the **GNU Lesser General Public License v3.0** (`third_party/SMAPI-LICENSE-LGPL-3.0.txt`).
Their test cases in `SemVerTest` are ported from SMAPI's own `SemanticVersionTests.cs`.

Because those files are derivative works, the project as a whole should be distributed under
LGPL-3.0-or-later (or a license that LGPL-3.0 permits, such as GPL-3.0). **Choose the project license before
publishing.** None has been applied yet.

`core/src/main/resources/.../compat-db.json` is generated from facts in SMAPI's release notes and source
(`tools/generate_compat_db.py`).

## Not included

No Stardew Valley files, no SMAPI binaries and no third-party launcher code are bundled or redistributed.
