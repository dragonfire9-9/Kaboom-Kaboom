#!/usr/bin/env python3
"""Uploads build/libs/deadcraft-<version>.jar to a CurseForge project as a beta file.

    CF_TOKEN=... python3 curseforge/upload.py <project id>

Get a token at https://legacy.curseforge.com/account/api-tokens. The project itself must be
created on the CurseForge website first (the API cannot create projects).
"""
import json
import os
import sys
import urllib.request
import uuid

API = "https://minecraft.curseforge.com/api"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def req(url, token, data=None, ctype=None):
    r = urllib.request.Request(url, data=data, headers={"X-Api-Token": token, "User-Agent": "deadcraft-uploader"})
    if ctype:
        r.add_header("Content-Type", ctype)
    with urllib.request.urlopen(r) as resp:
        return json.loads(resp.read())


def main():
    project, token = sys.argv[1], os.environ["CF_TOKEN"]
    props = dict(l.strip().split("=", 1) for l in open(os.path.join(ROOT, "gradle.properties")) if "=" in l and not l.startswith("#"))
    version, mc = props["mod_version"], props["minecraft_version"]
    jar = os.path.join(ROOT, "build/libs", f"deadcraft-{version}.jar")

    types = {t["id"]: t["slug"] for t in req(f"{API}/game/version-types", token)}
    wanted = {(mc, "minecraft-1-21"), ("NeoForge", "modloader"), ("Java 21", "java"), ("Client", "environment"), ("Server", "environment")}
    ids = [v["id"] for v in req(f"{API}/game/versions", token)
           if any(v["name"] == n and types.get(v["gameVersionTypeID"], "").startswith(s) for n, s in wanted)]
    print("game versions:", ids)

    meta = {"changelog": open(os.path.join(HERE, "changelog.md")).read(), "changelogType": "markdown",
            "displayName": f"Dead Craft [BETA] {version}", "gameVersions": ids, "releaseType": "beta",
            "relations": {"projects": [{"slug": "simple-voice-chat", "type": "optionalDependency"}]}}
    b = uuid.uuid4().hex
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"metadata\"\r\n\r\n{json.dumps(meta)}\r\n"
            f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{os.path.basename(jar)}\"\r\n"
            f"Content-Type: application/java-archive\r\n\r\n").encode() + open(jar, "rb").read() + f"\r\n--{b}--\r\n".encode()
    print(req(f"{API}/projects/{project}/upload-file", token, body, f"multipart/form-data; boundary={b}"))


if __name__ == "__main__":
    main()
