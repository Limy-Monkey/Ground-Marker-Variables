#!/usr/bin/env python3
# Builds the syntax reference site with MkDocs Material (pip install mkdocs-material):
#   1. build/site-src/ gets website/pages, stylesheets and javascripts, plus pages generated from the plugin's data:
#      variables.md from chatbox/completion/variables.json (the editor's tooltips read the same file), with any
#      website/sections/categories/<category>.md or sections/variables/<variable>.md write-up under its heading, and
#      symbols.md, every :name: emoji and unicode symbol with its image from the bundled zips.
#   2. mkdocs builds it (website/mkdocs.yml) into build/site/.
#   python3 scripts/build-site.py          generate and build
#   python3 scripts/build-site.py serve    generate, then preview at http://127.0.0.1:8000

import html
import json
import re
import shutil
import subprocess
import sys
import zipfile
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources/com/groundmarkervariables"
WEBSITE = ROOT / "website"
SRC = ROOT / "build/site-src"

# {...} with one level of nesting, e.g. {party {partyNum}\t{rsn}}.
TOKEN = re.compile(r"\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}")


# A description as HTML: escaped, with <br> kept and {tokens} as code.
def rich(text):
    text = html.escape(text).replace("&lt;br&gt;", "<br>")
    return TOKEN.sub(lambda m: f"<code>{m.group(0)}</code>", text)


def badges(v):
    out = []
    if v.get("returns"):
        out.append(f'<span class="badge">{html.escape(v["returns"])}</span>')
    if v.get("party"):
        out.append('<span class="badge party">Party</span>')
    if v.get("icon") == "yes":
        out.append('<span class="badge icon">Icon</span>')
    elif v.get("icon") == "items":
        out.append('<span class="badge icon">Icons</span>')
    return "".join(out)


# A variable's anchor, from its key: "lvl_" -> "lvl", "<item=" -> "item", "\n" -> "newline".
def anchor(name):
    return {"\\n": "newline", "\\t": "tab", "\\s": "space"}.get(name) or re.sub(r"[^a-z0-9]", "", name.lower())


# Its short form for the index: a {token}'s syntax up to its first space, e.g. "{showItems}"; others as written.
def short(v):
    token = re.match(r"\{[^\s}]*", v["syntax"])
    return token.group(0) + "}" if token else v["syntax"]


# A row per parameter with a description, and one per completion word with its own ({"word", "description"}).
def parameters_table(parameters):
    rows = []
    for p in parameters:
        if p.get("description"):
            rows.append((p["name"], p["description"]))
        rows += [(c["word"], c["description"]) for c in p.get("complete", []) if isinstance(c, dict) and c.get("description")]
    cells = "".join(f"<tr><td><code>{html.escape(name)}</code></td><td>{rich(description)}</td></tr>" for name, description in rows)
    return f"<table><thead><tr><th>Parameter</th><th>Description</th></tr></thead><tbody>{cells}</tbody></table>"


# The variables one entry covers, e.g. has<Spell>'s spells, each row its own anchor.
def variants_table(variants):
    rows = []
    for v in variants:
        names = " ".join(f"<code>{{{html.escape(n)}}}</code>" for n in [v["name"]] + v.get("aliases", []))
        rows.append(f'<tr id="{anchor(v["name"])}"><td>{names}</td><td>{rich(v["description"])}</td></tr>')
    return f"<table><thead><tr><th>Variable</th><th>Description</th></tr></thead><tbody>{''.join(rows)}</tbody></table>"


def variables_page():
    variables = json.loads((RES / "chatbox/completion/variables.json").read_text(encoding="utf-8"))
    by_category = OrderedDict()
    for name, v in variables.items():
        by_category.setdefault(v["category"], []).append((name, v))

    lines = ["# Variables", "", "Conditions, Or / And, math and date math: see [Conditions & math](conditions.md).", "",
        '<div class="var-index">']
    for entries in by_category.values():
        lines.append("<p>" + " ".join(f'<a href="#{anchor(name)}"><code>{html.escape(short(v))}</code></a>' for name, v in entries) + "</p>")
    lines += ["</div>", ""]

    for category, entries in by_category.items():
        lines += [f"## {category}", ""] + section("categories", anchor(category))
        for name, v in entries:
            lines += [f"### `{v['syntax']}` {{ #{anchor(name)} }}", ""]
            if badges(v):
                lines += [badges(v), ""]
            lines += [f"<p>{rich(v['description'])}</p>", ""]
            if v.get("aliases"):
                lines += ["<p>Also written " + ", ".join(f"<code>{html.escape(a)}</code>" for a in v["aliases"]) + "</p>", ""]
            if v.get("variants"):
                lines += [variants_table(v["variants"]), ""]
            if v.get("parameters"):
                lines += [parameters_table(v["parameters"]), ""]
            for example in v.get("examples", []):
                lines += ["```text", example, "```", ""]
            lines += section("variables", anchor(name))
    return "\n".join(lines)


# A category's or variable's longer write-up, from website/sections/<kind>/<anchor>.md, if it has one.
def section(kind, name):
    path = WEBSITE / "sections" / kind / (name + ".md")
    return [path.read_text(encoding="utf-8"), ""] if path.exists() else []


# codepoint -> names, the set's own names first, then aliases.
def names_by_codepoint(*maps):
    out = OrderedDict()
    for names in maps:
        for name, codepoint in names.items():
            out.setdefault(codepoint, []).append(name)
    return out


# The set's images copied beside symbols.md's page (served as symbols/index.html); the cells of those it has.
def symbol_cells(by_codepoint, zip_path, folder, css_class):
    (SRC / "symbols" / folder).mkdir(parents=True, exist_ok=True)
    cells = []
    with zipfile.ZipFile(zip_path) as z:
        available = set(z.namelist())
        for codepoint, names in by_codepoint.items():
            entry = codepoint + ".png"
            if entry not in available:
                continue
            (SRC / "symbols" / folder / entry).write_bytes(z.read(entry))
            label = f"<b>{html.escape(names[0])}</b>" + "".join(f'<br><span class="alias">{html.escape(n)}</span>' for n in names[1:])
            search = html.escape(" ".join(names + [codepoint]).lower(), quote=True)
            copy = html.escape(f":{names[0]}:", quote=True)
            cells.append(f'<div class="sym {css_class}" data-copy="{copy}" data-search="{search}" title="{copy}">'
                f'<img loading="lazy" src="{folder}/{entry}" alt=""><div>{label}</div></div>')
    return cells


def symbols_page():
    emoji = json.loads((RES / "emoji/index.json").read_text(encoding="utf-8"))["names"]
    aliases = json.loads((RES / "emoji/aliases.json").read_text(encoding="utf-8"))["aliases"]
    unicode = json.loads((RES / "unicode/names.json").read_text(encoding="utf-8"))["names"]
    emoji_cells = symbol_cells(names_by_codepoint(emoji, aliases), RES / "emoji/assets.zip", "img/emoji", "emoji")
    unicode_cells = symbol_cells(names_by_codepoint(unicode), RES / "unicode/images.zip", "img/unicode", "unicode")

    def section(title, cells):
        return f'<div class="sym-section" markdown>\n\n## {title} ({len(cells)})\n\n<div class="sym-grid">{"".join(cells)}</div>\n\n</div>'

    return "\n".join([
        "# Symbols", "",
        "Use any name as `:name:` in a label, or `:name@<col>:` to tint it. Click one to copy it.", "",
        f'<input class="sym-filter" type="search" placeholder="Filter {len(emoji_cells) + len(unicode_cells)} symbols…">', "",
        section("Emoji", emoji_cells), "",
        section("Unicode symbols", unicode_cells), "",
        "Emoji images: RuneLite's emoji set (static.runelite.net/emoji), licensed CC BY 4.0. Unicode symbol images: rendered "
        "from the DejaVu fonts (Bitstream Vera license, DejaVu changes public domain).", ""])


def generate():
    if SRC.exists():
        shutil.rmtree(SRC)
    shutil.copytree(WEBSITE / "pages", SRC)
    for folder in ("stylesheets", "javascripts"):
        shutil.copytree(WEBSITE / folder, SRC / folder)
    (SRC / "assets").mkdir()
    shutil.copy(ROOT / "icon.png", SRC / "assets/icon.png")
    (SRC / "variables.md").write_text(variables_page(), encoding="utf-8")
    (SRC / "symbols.md").write_text(symbols_page(), encoding="utf-8")


def main():
    generate()
    command = "serve" if sys.argv[1:] == ["serve"] else "build"
    try:
        subprocess.run([sys.executable, "-m", "mkdocs", command, "-f", str(WEBSITE / "mkdocs.yml")]
            + (["--strict"] if command == "build" else []), check=True)
    except subprocess.CalledProcessError as e:
        sys.exit(e.returncode)
    if command == "build":
        print(f"Built {ROOT / 'build/site'}")


if __name__ == "__main__":
    main()
