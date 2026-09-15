"""Removes stale "missing" mod entries for alpaka jars from the Modrinth App database.

The Modrinth App records every file in a profile's mods folder in app.db. A jar that was deleted
behind its back stays listed as missing, and the app refuses to launch the profile until the entry
is gone; its own Repair leaves such rows in place. This drops exactly those rows - alpaka jars with
missing=1 - after writing a backup of the database next to this script's output folder.

Run it while the Modrinth App is closed:  python tools/FixModrinthMissingMods.py
"""
import os
import shutil
import sqlite3
import time

DB = os.path.join(os.environ["APPDATA"], "ModrinthApp", "app.db")
BACKUP = DB + ".backup-" + time.strftime("%Y%m%d-%H%M%S")

src = sqlite3.connect(DB, timeout=15)
bak = sqlite3.connect(BACKUP)
src.backup(bak)
bak.close()
print("backup:", BACKUP)

rows = src.execute(
    "select instance_id, relative_path from instance_files where file_name like 'alpaka%' and missing=1"
).fetchall()
print("removing:", rows)
cur = src.execute("delete from instance_files where file_name like 'alpaka%' and missing=1")
src.commit()
print("deleted rows:", cur.rowcount)
print("alpaka entries left:",
      src.execute("select instance_id, relative_path, missing from instance_files where file_name like 'alpaka%'").fetchall())
