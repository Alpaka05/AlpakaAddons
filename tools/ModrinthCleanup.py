"""Drops the Modrinth App's records of alpaka jars that no longer exist in a profile.

The Modrinth App keeps every mod file of an instance in its SQLite database (app.db) and, once it
has launched with a file, also links it to its blob store. Deleting such a file from the mods folder
behind the app's back leaves that record dangling, and the app then refuses to launch the instance:
"mods/alpaka-1.1.xx.jar needs repair or re-import". The deploy replaces the jar on every build,
so this runs right after it and removes the dead records. Only alpaka rows of the named profile are
touched, and only those whose file is gone.

Usage: python ModrinthCleanup.py "<profile folder name>"
"""
import os
import sqlite3
import sys

APPDATA = os.environ.get("APPDATA") or os.path.expanduser(r"~\AppData\Roaming")
ROOT = os.path.join(APPDATA, "ModrinthApp")
DB = os.path.join(ROOT, "app.db")


def main(profile: str) -> int:
    if not os.path.exists(DB):
        print("ModrinthCleanup: no app.db found, nothing to do")
        return 0
    mods_dir = os.path.join(ROOT, "profiles", profile, "mods")
    con = sqlite3.connect(DB, timeout=15)
    cur = con.cursor()
    # An instance's id is not its folder name; the folder is found through the path the app stores.
    instance_ids = [r[0] for r in cur.execute(
        "select id from instances where path = ? or path like ?", (profile, "%/" + profile)).fetchall()]
    if not instance_ids:
        # Fall back to every instance that ever held an alpaka jar under that folder name.
        instance_ids = [r[0] for r in cur.execute(
            "select distinct instance_id from instance_files where file_name like 'alpaka%'").fetchall()]
    removed = 0
    for instance_id in instance_ids:
        rows = cur.execute(
            "select id, relative_path from instance_files where instance_id = ? and file_name like 'alpaka%'",
            (instance_id,)).fetchall()
        for file_id, relative_path in rows:
            on_disk = os.path.join(ROOT, "profiles", profile, relative_path.replace("/", os.sep))
            if os.path.exists(on_disk) or os.path.exists(on_disk + ".disabled"):
                # Present after all: clear a stale "missing" flag the app may have set mid-copy.
                cur.execute("update instance_files set missing = 0 where id = ? and missing = 1", (file_id,))
                continue
            cur.execute("delete from store_instance_files where file_id = ?", (file_id,))
            cur.execute("delete from instance_files where id = ?", (file_id,))
            removed += 1
            print(f"ModrinthCleanup: dropped stale record {relative_path}")
    con.commit()
    con.close()
    if removed == 0:
        print("ModrinthCleanup: no stale alpaka records")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "Fabric 26.2"))
