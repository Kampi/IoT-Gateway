import os

from pathlib import Path

for Path in Path(".").rglob("desktop.ini"):
    os.remove(Path)