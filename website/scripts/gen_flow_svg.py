# -*- coding: utf-8 -*-
"""Generate website/docs/public/flow.svg — OryxOS architecture flow,
replicating the mq9.robustmq.com flow.svg box style (monochrome variant)."""
import io

OUT = r"d:\sortware\programSourceCode\ai_projects\oryxOS\website\docs\public\flow.svg"

MONO = "'JetBrains Mono', 'Fira Code', monospace"
SANS = "Inter, 'SF Pro Text', -apple-system, 'Segoe UI', system-ui, sans-serif"
STROKE = "#e5e5e5"
STROKE_STRONG = "#d4d4d4"
FILL_BOX = "#ffffff"
FILL_SOFT = "#f5f5f5"

parts = []
parts.append(f'''<?xml version="1.0" encoding="UTF-8"?>
<svg width="100%" viewBox="0 0 1400 760" role="img" xmlns="http://www.w3.org/2000/svg">
<title>OryxOS: private, auditable enterprise Agent OS</title>
<desc>A single Spring Boot base providing channels, a ReAct engine, capabilities and infrastructure for enterprise agents.</desc>
<defs>
<linearGradient id="engineGrad" x1="0%" y1="0%" x2="0%" y2="100%">
<stop offset="0%" stop-color="#FAFAFB"/>
<stop offset="100%" stop-color="#F4F4F8"/>
</linearGradient>
<marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
<path d="M 0 0 L 10 5 L 0 10 z" fill="#999999"/>
</marker>
</defs>''')


def col_header(x, label):
    parts.append(
        f'<text x="{x}" y="38" text-anchor="middle" style="fill:#6B6B73;font-family:{SANS};font-size:13px;font-weight:600">'
        f'{label}</text>')


def box(x, y, w, h, fill=FILL_BOX, stroke=STROKE, stroke_w=1.4, rx=8):
    parts.append(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{stroke_w}"/>')


def text(x, y, s, size=11, weight=400, fill="#555555", mono=False, anchor="start"):
    fam = MONO if mono else SANS
    parts.append(
        f'<text x="{x}" y="{y}" style="fill:{fill};font-family:{fam};font-size:{size}px;font-weight:{weight};text-anchor:{anchor}">{s}</text>')


def node(x, y, w, h, title, lines, title_fill="#111111", fill=FILL_BOX):
    box(x, y, w, h)
    text(x + 14, y + 24, title, size=12, weight=700, fill=title_fill)
    for i, ln in enumerate(lines):
        text(x + 14, y + 44 + i * 18, ln, size=11, fill="#555555", mono=True)


# ---- column headers ----
col_header(170, "CHANNELS")
col_header(700, "ORYXOS ENGINE")
col_header(1230, "INFRASTRUCTURE")

# ---- left column: channels ----
node(40, 90, 240, 88, "CLI", ["oryxos chat --profile", "oryxos gateway"])
node(40, 198, 240, 88, "WEB SERVICE", ["REST API · OpenAPI 3.0", "10 core endpoints"])
node(40, 306, 240, 100, "MULTI-CHANNEL", ["企微 · 飞书 · Slack · 邮件", "extension phase"])

# ---- center: engine ----
box(400, 70, 600, 620, fill="url(#engineGrad)", stroke=STROKE_STRONG, stroke_w=1.6, rx=10)

# band label
text(700, 104, "ENGINE", size=11, weight=700, fill="#555555", anchor="middle")

node(430, 116, 540, 84, "REACT LOOP", ["think → act → observe", "self-built · fully controlled"])
node(430, 214, 260, 64, "PROMPT BUILDER", ["system prompt assembly"])
node(710, 214, 260, 64, "CONTEXT LOADER", ["SKILL.md · SOUL.md · USER.md"])

text(700, 326, "CAPABILITIES", size=11, weight=700, fill="#555555", anchor="middle")

node(430, 338, 170, 126, "PROVIDER", ["name → ChatModel", "DeepSeek · Qwen", "Kimi · Anthropic", "hot-swap"])
node(610, 338, 170, 126, "MEMORY", ["session + long-term", "MEMORY.md", "recall / save tools"])
node(790, 338, 170, 126, "TOOL", ["built-in + MCP", "file · shell · http", "tool registry"])

text(700, 512, "GOVERNANCE", size=11, weight=700, fill="#555555", anchor="middle")

node(430, 524, 260, 92, "AUDIT", ["llm_calls · tool_invocations", "persisted from day one"])
node(710, 524, 260, 92, "SANDBOX", ["path / pattern whitelist", "app-layer · no SecurityManager"])

box(430, 636, 540, 32, fill="#000000", stroke="#000000", stroke_w=0, rx=8)
text(700, 656, "JDK 21 · SPRING BOOT 3 · JAVA-NATIVE · SELF-HOSTED",
     size=11, weight=600, fill="#ffffff", anchor="middle")

# ---- right column: infrastructure ----
node(1100, 90, 260, 88, "SQLITE", ["sessions · audit tables", "Spring Data JPA"])
node(1100, 198, 260, 88, "WORKSPACE", [".oryxos/ · profiles · skills", "MEMORY.md · mcp_servers.yaml"])
node(1100, 306, 260, 100, "EXTERNAL", ["LLM APIs · MCP servers", "deepseek · qwen · github…"])

# ---- arrows ----
for y in (134, 242, 356):
    parts.append(
        f'<line x1="280" y1="{y}" x2="394" y2="{y}" stroke="#999999" stroke-width="1.6" marker-end="url(#arrow)"/>')
    parts.append(
        f'<line x1="1006" y1="{y}" x2="1094" y2="{y}" stroke="#999999" stroke-width="1.6" marker-end="url(#arrow)"/>')

parts.append('</svg>')

with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(parts))

print("written:", OUT)
