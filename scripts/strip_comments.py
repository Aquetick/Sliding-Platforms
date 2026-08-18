#!/usr/bin/env python3
"""Strip // and /* */ comments from Java sources, preserving string/char literals."""
import pathlib
import sys

def strip(text: str) -> str:
    out = []
    i, n = 0, len(text)
    in_str = in_chr = in_lcomment = in_bcomment = in_textblock = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if in_lcomment:
            if c == '\n':
                in_lcomment = False
                out.append(c)
            i += 1
            continue
        if in_bcomment:
            if c == '*' and nxt == '/':
                in_bcomment = False
                i += 2
            else:
                if c == '\n':
                    out.append(c)
                i += 1
            continue
        if in_str:
            out.append(c)
            if c == '\\':
                if i + 1 < n:
                    out.append(text[i + 1])
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if in_chr:
            out.append(c)
            if c == '\\':
                if i + 1 < n:
                    out.append(text[i + 1])
                i += 2
                continue
            if c == "'":
                in_chr = False
            i += 1
            continue
        if in_textblock:
            out.append(c)
            if c == '"' and nxt == '"' and i + 2 < n and text[i + 2] == '"':
                out.append('""')
                i += 3
                in_textblock = False
                continue
            i += 1
            continue
        # not in any literal/comment
        if c == '/' and nxt == '/':
            in_lcomment = True
            i += 2
            continue
        if c == '/' and nxt == '*':
            in_bcomment = True
            i += 2
            continue
        if c == '"':
            if nxt == '"' and i + 2 < n and text[i + 2] == '"':
                in_textblock = True
                out.append('"""')
                i += 3
                continue
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == "'":
            in_chr = True
            out.append(c)
            i += 1
            continue
        out.append(c)
        i += 1
    return ''.join(out)

def clean(text: str) -> str:
    """Drop now-empty (formerly comment-only) lines and trailing whitespace."""
    lines = [ln.rstrip() for ln in strip(text).split('\n')]
    out, blank_run = [], 0
    for ln in lines:
        if ln.strip() == '':
            blank_run += 1
            if blank_run <= 1:
                out.append('')
        else:
            blank_run = 0
            out.append(ln)
    while out and out[-1] == '':
        out.pop()
    return '\n'.join(out) + '\n'

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'src/main/java')
changed = 0
for f in sorted(root.rglob('*.java')):
    src = f.read_text(encoding='utf-8')
    stripped = clean(src)
    if stripped != src:
        f.write_text(stripped, encoding='utf-8')
        changed += 1
print(f'stripped {changed} files')
