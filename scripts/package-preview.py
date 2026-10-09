"""Package the exported replay and speaker PDF using only Python's standard library."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json
from hashlib import sha256

root = Path(__file__).resolve().parent.parent
preview = root / ".preview"
manifest = json.loads((preview / "api/scenes").read_text())
assert manifest["mode"] == "replay" and "token" not in manifest
assert len(manifest["scenes"]) == 9
assert all(result["ok"] for result in manifest["results"].values())
assert len(manifest["experiments"]) == 5
assert all(result["ok"] for result in manifest["experiments"].values())
pdf = root / "artifacts/lambda-world-speaker-script.pdf"
if not pdf.exists():
    raise SystemExit("Run scripts/stage-smoke.cjs to generate the speaker PDF first.")
audit = root / "artifacts/rehearsal-audit.json"
worksheet = root / "artifacts/rehearsal-timings.csv"
if not audit.exists() or not worksheet.exists():
    raise SystemExit("Run python3 scripts/rehearsal-audit.py before packaging.")
assert json.loads(audit.read_text())["source_sha256"] == sha256((preview / "api/scenes").read_bytes()).hexdigest(), "Rehearsal audit must match the current export"

instructions = """LAMBDA WORLD — ONE RESCUE

This is the portable replay of the Clojure presentation, with recorded results.
It includes nine chapters, thirty explanation cues, and the timed speaker script.
No internet access, npm packages, or Java runtime are needed for this replay.

1. Extract this ZIP into a directory.
2. Open a terminal in that directory and run:

   python3 -m http.server 7342 --bind 127.0.0.1

3. Open http://127.0.0.1:7342 in your browser.
4. Choose Present on stage. Press P to open the separate presenter window.
5. Put the audience window on the projector; keep notes on the laptop.

Left/right changes chapter. Up/down changes explanation cue. Space plays/pauses.
S toggles stage view. T shows the current calculation. F toggles fullscreen. Start the timer in Presenter manually.
Change one thing compares five Clojure parameter changes at the same step.
Choose Talk default before returning to the scripted explanation.
If Java, the server, or the interactive browser view fails, open contingency.html
directly from disk, or show lambda-world-contingency.pdf. Both include every demo
and its recorded fallback values; neither requires an internet connection.
The speaker script is also available as speaker-script.pdf and /presenter.html.

Use the local Clojure project (clojure -M:present) for live code editing.
This replay does not execute arbitrary code. It displays recorded JVM and kernel
results. The proof boundaries are explained in the Rainbow chapter and script.

Timings are a 40-minute rehearsal plan, not a measured performance duration.
rehearsal-audit.json estimates speech and demo allowances using explicit rates.
Copy rehearsal-timings.csv and record actual cue times during the spoken rehearsal.
Rehearse on the intended two-screen setup and check the actual projector.
"""

destination = root / "artifacts/lambda-world-rehearsal.zip"
with ZipFile(destination, "w", ZIP_DEFLATED) as archive:
    for file in sorted(preview.rglob("*")):
        if file.is_file():
            archive.write(file, file.relative_to(preview))
    archive.write(pdf, "speaker-script.pdf")
    archive.write(root / "artifacts/lambda-world-contingency.pdf", "lambda-world-contingency.pdf")
    archive.write(audit, "rehearsal-audit.json")
    archive.write(worksheet, "rehearsal-timings.csv")
    archive.writestr("START-HERE.txt", instructions)
with ZipFile(destination) as archive:
    assert archive.testzip() is None
print(f"Packaged {destination.name}: {destination.stat().st_size:,} bytes; archive verified.")
