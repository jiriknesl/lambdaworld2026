#!/usr/bin/env python3
"""Build a self-contained reading copy of the study guide; requires pandoc."""
from pathlib import Path
import subprocess
import tempfile

TALK = Path(__file__).resolve().parents[1]
ROOT = TALK.parent
SOURCE = TALK / 'speaker/VALIDATE-RAINBOW-STUDY.md'
OUTPUT = ROOT / 'artifacts/validate-rainbow-study.html'

css = (TALK / 'public/fonts.css').read_text() + '''
body { max-width: 880px; margin: 48px auto; padding: 0 28px; color: #1b1e20;
  background: #fcfaf6; font: 18px/1.6 Aeonik, sans-serif; }
h1,h2,h3 { line-height: 1.2; margin-top: 1.7em; }
h1 { font-size: 36px; } h2 { font-size: 28px; } h3 { font-size: 22px; }
pre,code { font-family: "Aeonik Mono", monospace; font-size: .86em; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #f0eee8; padding: 18px; }
table { width: 100%; border-collapse: collapse; margin: 24px 0; }
th,td { border-bottom: 1px solid #d9d5cc; text-align: left; padding: 10px; vertical-align: top; }
a { color: #343cdd; } nav { border-bottom: 1px solid #d9d5cc; padding-bottom: 24px; }
blockquote { border-left: 3px solid #343cdd; margin-left: 0; padding-left: 20px; }
@page { size: A4; margin: 17mm; }
@media print {
 body { margin: 0; padding: 0; background: white; font-size: 10.5pt; }
 h1 { font-size: 25pt; } h2 { font-size: 18pt; } h3 { font-size: 13pt; }
 h1,h2,h3 { break-after: avoid; } pre,tr { break-inside: avoid; }
 a { color: inherit; text-decoration: none; } nav { font-size: 10pt; }
}
'''

with tempfile.TemporaryDirectory(prefix='lambda-study-') as directory:
    header = Path(directory) / 'head.html'
    header.write_text('<style>' + css + '</style>')
    subprocess.run(['pandoc', str(SOURCE), '--standalone', '--toc', '--toc-depth=2',
                    '--metadata', 'pagetitle=Validate and Rainbow speaker study guide',
                    '--include-in-header', str(header), '--output', str(OUTPUT)], check=True)

# Source links are for a guide in talk/speaker; adjust them for the reading copy in artifacts.
html = OUTPUT.read_text()
html = html.replace('href="../src/talk/', 'href="../talk/src/talk/')
html = html.replace('href="../../resources/', 'href="../resources/')
for name in ['study-results.json', 'plant-case-evidence.json']:
    html = html.replace('href="' + name + '"', 'href="../talk/speaker/' + name + '"')
OUTPUT.write_text(html)
print('Built ' + str(OUTPUT))
