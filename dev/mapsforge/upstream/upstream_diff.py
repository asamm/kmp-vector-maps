# Copyright 2026 Asamm Software
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Lists what changed in upstream Mapsforge between two tags and how it touches this repo, so a sync
misses nothing (docs/upstream.md, "Following upstream").

Every changed upstream file lands in exactly one group:
  VENDORED   the upstream file of a vendored Kotlin file, also before a rename: port the change
  BUILD      Gradle files of the vendored modules and the root: dependency versions
  THEME      the render theme schema and resources the theme format depends on
  MODULE     other files of the vendored modules (layers, overlays, views, tests): read, usually skip
  OUT        modules outside the vendored subset (POI, writer, samples, themes, docs)

Read-only: it calls the GitHub API through `gh` and never touches a local Mapsforge clone.

Usage: python dev/mapsforge/upstream/upstream_diff.py <fromTag> <toTag>
"""

import json
import pathlib
import subprocess
import sys

REPO = "mapsforge/mapsforge"
ROOT = pathlib.Path(__file__).resolve().parents[3]
VENDORED_DIRS = [
    "formats/mapsforge/engine/src/main/kotlin",
    "formats/mapsforge/android/src/main/kotlin",
    "formats/mapsforge/awt/src/main/kotlin",
]
OUR_PACKAGE = "com/asamm/vectormaps/mapsforge/"
UPSTREAM_PACKAGE = "org/mapsforge/"
VENDORED_MODULES = [
    "mapsforge-core/",
    "mapsforge-map/",
    "mapsforge-map-reader/",
    "mapsforge-map-android/",
    "mapsforge-map-awt/",
]
THEME_FILES = ["resources/renderTheme.xsd"]
BUILD_SUFFIXES = (".gradle", ".gradle.kts", "gradle.properties", ".toml")
# the compare API lists at most 250 commits and 300 files, a commit at most 300 files; past the
# file limit the commits are read one by one, past the others the script stops
COMMIT_LIMIT = 250
FILE_LIMIT = 300


def gh(path):
    # stderr stays on the console, so gh's own message (not found, auth) shows
    result = subprocess.run(["gh", "api", path], stdout=subprocess.PIPE, text=True, encoding="utf-8")
    if result.returncode != 0:
        sys.exit(f"upstream_diff.py: gh api {path} failed")
    return json.loads(result.stdout)


def vendored_upstream_paths():
    paths = set()
    for d in VENDORED_DIRS:
        for kt in (ROOT / d).rglob("*.kt"):
            relative = kt.relative_to(ROOT / d).as_posix()
            if relative.startswith(OUR_PACKAGE):
                paths.add(UPSTREAM_PACKAGE + relative[len(OUR_PACKAGE):-3] + ".java")
    return paths


def entry(f, added, deleted):
    return f["status"], added, deleted, f.get("previous_filename")


def changed_files(from_tag, to_tag):
    compare = gh(f"repos/{REPO}/compare/{from_tag}...{to_tag}")
    commits = [(c["sha"][:8], c["commit"]["message"].split("\n")[0]) for c in compare["commits"]]
    if compare["total_commits"] > len(commits) or len(commits) >= COMMIT_LIMIT:
        sys.exit(f"upstream_diff.py: {compare['total_commits']} commits exceed one compare page; "
                 "split the range at an intermediate tag")
    files = {f["filename"]: entry(f, f["additions"], f["deletions"]) for f in compare.get("files", [])}
    if len(files) >= FILE_LIMIT:
        files = {}
        for sha, _ in commits:
            commit_files = gh(f"repos/{REPO}/commits/{sha}").get("files", [])
            if len(commit_files) >= FILE_LIMIT:
                sys.exit(f"upstream_diff.py: commit {sha} lists {FILE_LIMIT}+ files; read it on GitHub")
            for f in commit_files:
                files.setdefault(f["filename"], entry(f, 0, 0))
    return commits, files


def group(path, previous, vendored):
    for p in filter(None, [path, previous]):
        if any(p.endswith("/src/main/java/" + v) for v in vendored):
            return "VENDORED"
    if path in THEME_FILES:
        return "THEME"
    if path.endswith(BUILD_SUFFIXES):
        if "/" not in path or path.startswith("gradle/") or any(path.startswith(m) for m in VENDORED_MODULES):
            return "BUILD"
    if any(path.startswith(m) for m in VENDORED_MODULES):
        return "MODULE"
    return "OUT"


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    from_tag, to_tag = sys.argv[1], sys.argv[2]
    vendored = vendored_upstream_paths()
    commits, files = changed_files(from_tag, to_tag)
    print(f"{REPO} {from_tag}...{to_tag}: {len(commits)} commits, {len(files)} files; {len(vendored)} vendored files")
    for sha, title in commits:
        print(f"  {sha} {title}")
    groups = {}
    for path, info in sorted(files.items()):
        groups.setdefault(group(path, info[3], vendored), []).append((path, info))
    for name in ["VENDORED", "BUILD", "THEME", "MODULE", "OUT"]:
        entries = groups.get(name, [])
        print(f"\n{name}: {len(entries)}")
        for path, (status, added, deleted, previous) in entries:
            moved = f"{previous} -> " if previous else ""
            print(f"  {status:<9} +{added:<5} -{deleted:<5} {moved}{path}")


if __name__ == "__main__":
    main()
