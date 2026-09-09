#!/usr/bin/env python3
"""Comprueba, o regenera explícitamente, el manifiesto de recursos LC."""
import argparse
import hashlib
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--write", action="store_true")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / "vendor/lc"
files = sorted(path for path in (root / "xsl").rglob("*") if path.is_file())
text = "".join(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.relative_to(root).as_posix() + "\n" for path in files)
manifest = root / "SHA256SUMS"
if args.write:
    manifest.write_text(text)
    print(f"Manifiesto escrito: {len(files)} archivos.")
elif manifest.read_text() != text:
    raise SystemExit("ERROR: recursos LC modificados, añadidos o ausentes respecto al manifiesto.")
else:
    print(f"OK: {len(files)} recursos LC coinciden con SHA256SUMS.")
