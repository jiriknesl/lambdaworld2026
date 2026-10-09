"""Prepare a timing worksheet. This does not simulate or certify a spoken rehearsal."""
import csv
import json
import re
from hashlib import sha256
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
manifest = json.loads((ROOT / ".preview/api/scenes").read_text())
rates = [120, 130, 160]  # Planning scenarios, not measured speaker speeds.
worksheet = ROOT / "artifacts/rehearsal-timings.csv"
if worksheet.exists():
    with worksheet.open(newline="") as file:
        if any(any(row.get(key, "").strip() for key in ["actual_start", "actual_finish", "observations"])
               for row in csv.DictReader(file)):
            raise SystemExit("Existing worksheet contains rehearsal observations. Archive it before generating a fresh template.")


def words(text):
    return len(re.findall(r"\b[\w’'-]+\b", text))


def clock(seconds):
    seconds = round(seconds)
    return f"{seconds // 60:02d}:{seconds % 60:02d}"


rows, chapters = [], []
for scene in manifest["scenes"]:
    speaker = scene["speaker"]
    beats = speaker["beats"]
    chapter_words = sum(words(beat["say"]) for beat in beats)
    budget = speaker["minutes"] * 60
    chapters.append({
        "chapter": scene["id"], "planned_seconds": budget,
        "spoken_words": chapter_words,
        "estimated_speech_seconds": {str(rate): round(chapter_words * 60 / rate, 1) for rate in rates},
        "demo_discussion_allowance_at_130_wpm": round(budget - chapter_words * 60 / 130, 1),
        "cut": speaker["cut"], "recovery": speaker["fallback"],
        "measured_seconds": None,
    })
    for i, beat in enumerate(beats):
        end = beats[i + 1]["at"] if i + 1 < len(beats) else budget
        word_count = words(beat["say"])
        start = speaker["start-seconds"] + beat["at"]
        rows.append({
            "chapter": scene["id"], "cue": i + 1, "title": beat["title"],
            "target_start": clock(start), "target_finish": clock(speaker["start-seconds"] + end),
            "slot_seconds": end - beat["at"], "spoken_words": word_count,
            "estimated_speech_seconds_at_130_wpm": round(word_count * 60 / 130, 1),
            "demo_discussion_allowance_seconds_at_130_wpm": round(end - beat["at"] - word_count * 60 / 130, 1),
            "demo": beat["demo"], "actual_start": "", "actual_finish": "", "observations": "",
        })

total_words = sum(chapter["spoken_words"] for chapter in chapters)
total_seconds = sum(chapter["planned_seconds"] for chapter in chapters)
assert total_seconds == 2400
assert len(rows) == 30
assert all(row["slot_seconds"] > 0 for row in rows)

report = {
    "status": "desk audit complete; spoken rehearsal NOT performed",
    "source": ".preview/api/scenes — export resources/presentation.edn before regenerating",
    "source_sha256": sha256((ROOT / ".preview/api/scenes").read_bytes()).hexdigest(),
    "planning_rates_wpm": rates,
    "counting_method": "Words in :say only; excludes live code narration, demos, handoffs, audience responses, and pauses.",
    "total_spoken_words": total_words,
    "planned_total_seconds": total_seconds,
    "estimated_speech_seconds": {str(rate): round(total_words * 60 / rate, 1) for rate in rates},
    "demo_discussion_allowance_at_130_wpm": round(total_seconds - total_words * 60 / 130, 1),
    "measured_total_seconds": None,
    "findings": [
        "The script's 40-minute allocation is not a measured performance duration.",
        "More than half the allocation is available for demos, live narration, discussion and pauses at 130 words/minute; validate this aloud.",
        "Rainbow rejection and closing invitation have the least demo allowance; keep both demonstrations short.",
        "Use the notebook for proof metadata and all six Shapley marginal gains; the large calculation card omits those details.",
        "Do not wait out unused slots. Record the actual duration, then revise the schedule or substantive explanations.",
    ],
    "chapters": chapters,
    "cues": rows,
}
out = ROOT / "artifacts"
out.mkdir(exist_ok=True)
(out / "rehearsal-audit.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
with (out / "rehearsal-timings.csv").open("w", newline="") as file:
    writer = csv.DictWriter(file, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
print(f"Desk audit: {total_words} scripted words; {clock(total_words * 60 / 130)} estimated speech at 130 wpm;")
print(f"{clock(total_seconds - total_words * 60 / 130)} available for demos/discussion in the 40-minute plan.")
print("Wrote rehearsal-audit.json and rehearsal-timings.csv. Actual timing fields remain blank.")
