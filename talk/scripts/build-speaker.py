#!/usr/bin/env python3
"""Export one speech source to the presenter, a readable script and printable HTML."""
import argparse
import hashlib
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TALK = ROOT / 'talk'
SOURCE = TALK / 'speaker/script.json'

def words(text):
    return len(text.split())

def stamp(seconds):
    n = round(seconds)
    return f'{n // 60:02d}:{n % 60:02d}'

def edn(value):
    if isinstance(value, dict):
        return '{' + ' '.join(':' + k.replace('_', '-') + ' ' + edn(v) for k, v in value.items()) + '}'
    if isinstance(value, list):
        return '[' + '\n'.join(edn(v) for v in value) + ']'
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, (int, float)):
        return str(value)
    raise TypeError(value)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = json.loads(SOURCE.read_text())
    slides = data['slides']
    titles = re.findall(r':section "[^"]+" :title "([^"]+)"', (TALK / 'src/talk/core.cljs').read_text())
    assert len(slides) == len(titles) and slides
    assert [s['slide'] for s in slides] == list(range(1, len(slides) + 1))
    for s, title in zip(slides, titles):
        assert s['title'] == title, (s['slide'], s['title'], title)
        for p in s['phases']:
            assert p['speech'].strip() and p['cue'].strip()
    rate = data['spoken_words_per_minute']
    total_words = sum(words(p['speech']) for s in slides for p in s['phases'])
    holds = sum(p['hold_seconds'] for s in slides for p in s['phases'])
    total = total_words * 60 / rate + holds
    intro = (f'{total_words:,} spoken words. Planning pace: {rate} words/minute, '
             f'plus {stamp(holds)} of pauses and visual holds. Speech and holds: {stamp(total)}; '
             f'with {stamp(data["question_seconds"])} for questions: {stamp(total + data["question_seconds"])}. '
             'This is an estimate, not an aloud rehearsal.')
    md = ['# Lambda World speaker script', '', intro, '', data['delivery_note'], '',
          'Use Right for a reveal and Down for the next slide. Space plays a numerical trace; '
          'End jumps to its final state. The N presenter window shows the matching speech. '
          'Keep the release open before the talk so its numerical runs have finished.', '',
          'Every rendered phase has a passage. Ranges such as “2–penultimate” cover continuous '
          'animation frames: speak that passage once over the range, not once per frame.', '']
    pages = []
    clock = 0
    timing = []
    for s in slides:
        count = sum(words(p['speech']) for p in s['phases'])
        hold = sum(p['hold_seconds'] for p in s['phases'])
        duration = count * 60 / rate + hold
        timing.append({'slide': s['slide'], 'words': count, 'hold_seconds': hold,
                       'start_seconds': round(clock), 'end_seconds': round(clock + duration)})
        heading = f'{s["slide"]:02d} {s["title"]}'
        times = f'{stamp(clock)}–{stamp(clock + duration)} · {count} words · {hold}s visual holds'
        md += [f'## {heading}', '', times, '']
        parts = [f'<section><h1>{html.escape(heading)}</h1><p class="timing">{times}</p>']
        for p in s['phases']:
            label = str(p['from']) if p['from'] == p['to'] else f'{p["from"]}–{p["to"]}'
            md += [f'### Phase {label}', '', f'[{p["cue"]}]', '', p['speech'], '']
            parts += [f'<article class="phase"><h2>Phase {label}</h2><p class="cue">{html.escape(p["cue"])}</p>']
            parts += [f'<p>{html.escape(t)}</p>' for t in p['speech'].split('\n\n')]
            parts.append('</article>')
        parts.append('</section>')
        pages.append('\n'.join(parts))
        clock += duration
    review = json.loads((TALK / 'speaker/revisions.json').read_text())
    assert len(review) >= 20 and [r['pass'] for r in review] == list(range(1, len(review) + 1))
    log = ['# Lambda World speech revision log', '',
           f'{len(review)} editorial passes followed the complete first draft. Each entry records the '
           'issue found and the actual revision. Before/after fingerprints of the script and deck '
           'are retained in revisions.json. Browser checks and integration follow these editorial passes.', '']
    for r in review:
        assert r['before_sha256'] != r['after_sha256']
        log += [f'## {r["pass"]:02d} {r["focus"]}', '', r['finding'], '', r['changes'], '']
    report = {'spoken_words': total_words, 'pace_wpm': rate, 'holds_seconds': holds,
              'questions_seconds': data['question_seconds'], 'slides': timing,
              'estimates_seconds_including_questions': {
                  str(pace): round(total_words * 60 / pace + holds + data['question_seconds'])
                  for pace in [110, 125, 140]},
              'script_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest()}
    generated = ';; Generated by scripts/build-speaker.py from speaker/script.json.\n(ns talk.script-data)\n\n(def slides\n' + edn(slides) + ')\n'
    files = {TALK / 'src/talk/script_data.cljs': generated,
             TALK / 'speaker/SCRIPT.md': '\n'.join(md),
             TALK / 'speaker/REVISIONS.md': '\n'.join(log),
             TALK / 'speaker/timing.json': json.dumps(report, ensure_ascii=False, indent=2) + '\n'}
    for path, content in files.items():
        if args.check:
            assert path.exists() and path.read_text() == content, f'Stale export: {path}; run npm run speaker:build'
        else:
            path.write_text(content)
    if not args.check:
        fonts = (TALK / 'public/fonts.css').read_text()
        printable = '<!doctype html><html lang="en"><meta charset="utf-8"><title>Lambda World speaker script</title><style>' + fonts + '''
body{font-family:Aeonik,sans-serif;max-width:850px;margin:40px auto;color:#17191b;background:#fff;font-size:18px;line-height:1.5;padding:0 28px}
h1{font-size:28px;line-height:1.2}h2{font-size:18px;margin-top:24px}.cue,.timing{color:#505968;font-size:15px}.cue{border-left:3px solid #3948e8;padding-left:12px}section{border-top:1px solid #ddd;margin-top:40px;padding-top:20px}
@page{size:A4;margin:18mm} @media print{body{margin:0;padding:0;max-width:none;font-size:11pt}section{break-before:page;border:0;padding:0;margin:0}h1{font-size:19pt}h2{font-size:12pt;break-after:avoid}.cue,.timing{font-size:10pt}.phase{break-inside:avoid}p{orphans:3;widows:3}}
</style><body><h1>Lambda World speaker script</h1><p>''' + html.escape(intro) + '</p><p>' + html.escape(data['delivery_note']) + '</p>' + '\n'.join(pages) + '</body></html>'
        dest = ROOT / 'artifacts/lambda-world-current-speaker-script.html'
        dest.parent.mkdir(exist_ok=True)
        dest.write_text(printable)
    print(('Checked' if args.check else 'Built') + f' {len(slides)} slides, {sum(len(s["phases"]) for s in slides)} speech cues, {total_words} words, {len(review)} revision passes.')
    print(intro)

if __name__ == '__main__':
    main()
