# -*- coding: utf-8 -*-
"""从整合包 KubeJS 的 gte_component_assembly.js 生成力场压模零件工厂/奈亚机
「零部件加工」(gtlmodular:component_parts) 的数据包配方。

用法：python scripts/gen_component_parts_recipes.py [源 js 路径]
默认读本机 Prism 实例；产物写到 src/main/resources/data/gtlmodular/recipes/component_parts/。
"""
import json
import os
import re
import sys

DEFAULT = r"D:/Prism Launcher/sl/GregTech Leisure-猪咪增强版 2.9.2/minecraft/kubejs/server_scripts/gte_component_assembly.js"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "data", "gtlmodular", "recipes", "component_parts")


def parse_array(s, i):
    if s[i] != '[':
        raise SystemExit("expected '[' at %d" % i)
    out = []
    i += 1
    while True:
        while i < len(s) and s[i] in ' \t\r\n,':
            i += 1
        if s[i] == ']':
            return out, i + 1
        if s[i] == '[':
            value, i = parse_array(s, i)
            out.append(value)
        elif s[i] == '"':
            j = i + 1
            buf = []
            while s[j] != '"':
                if s[j] == '\\':
                    buf.append(s[j + 1])
                    j += 2
                else:
                    buf.append(s[j])
                    j += 1
            out.append(''.join(buf))
            i = j + 1
        else:
            j = i
            while j < len(s) and s[j] not in ',[] \t\r\n':
                j += 1
            token = s[i:j]
            out.append(int(token) if token.lstrip('-').isdigit() else token)
            i = j


def item_content(spec):
    m = re.match(r'^(\d+)x\s+(.+)$', spec)
    if m:
        count, item = int(m.group(1)), m.group(2)
    else:
        count, item = 1, spec
    return {"content": {"type": "gtceu:sized", "count": count, "ingredient": {"item": item}}}


def fluid_content(spec):
    m = re.match(r'^(.+?)\s+(\d+)$', spec)
    if not m:
        raise SystemExit("bad fluid spec: " + spec)
    return {"content": {"amount": int(m.group(2)), "value": {"fluid": m.group(1)}}}


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    source = open(src, "r", encoding="utf-8").read()
    marker = 'batch(event.recipes.gtlmodular, "component_parts",'
    pos = 0
    batches = []
    while True:
        idx = source.find(marker, pos)
        if idx < 0:
            break
        i = idx + len(marker)
        while source[i] in ' \t\r\n':
            i += 1
        columns, i = parse_array(source, i)
        while source[i] in ' \t\r\n,':
            i += 1
        rows, i = parse_array(source, i)
        batches.append((columns, rows))
        pos = i
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for columns, rows in batches:
        fluid_first = columns and columns[0] == "inputFluids"
        for row in rows:
            rid, raw_in, raw_out = row[0], row[1], row[2]
            inputs = {}
            if fluid_first:
                inputs["fluid"] = [fluid_content(x) for x in raw_in]
            else:
                inputs["item"] = [item_content(x) for x in raw_in]
            recipe = {
                "type": "gtlmodular:component_parts",
                "conditions": [{"type": "forge:mod_loaded", "modid": "gtceu"}],
                "duration": 800,
                "inputs": inputs,
                "outputs": {"item": [item_content(x) for x in raw_out]},
                "tickInputs": {"eu": [{"content": 491520}]},
            }
            with open(os.path.join(OUT, rid + ".json"), "w", encoding="utf-8", newline="\n") as f:
                json.dump(recipe, f, ensure_ascii=False, indent=2)
                f.write("\n")
            n += 1
    print("wrote", n, "recipes ->", OUT)


if __name__ == "__main__":
    main()
