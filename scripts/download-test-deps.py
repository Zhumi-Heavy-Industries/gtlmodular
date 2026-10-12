#!/usr/bin/env python3
"""直接读取 docs/依赖.md 的测试依赖表，避免脚本和文档的版本、哈希分叉。"""

import argparse
import hashlib
import http.client
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request


START = "<!-- test-dependencies:start -->"
END = "<!-- test-dependencies:end -->"
COLUMNS = (
    "文件名", "modId", "内部版本", "启用", "用途与选择依据", "项目/发布页",
    "下载 URL", "SHA-1", "SHA-256", "已有文件/复现前提",
)


def allowed_url(url):
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != "https" or parsed.username or parsed.password
            or parsed.port not in (None, 443) or parsed.query or parsed.fragment):
        raise ValueError(f"不允许的下载地址：{url}")
    prefixes = {
        "cdn.modrinth.com": "/data/",
        "edge.forgecdn.net": "/files/",
        "mediafilez.forgecdn.net": "/files/",
        "media.forgecdn.net": "/files/",
        "maven.tterrag.com": "/com/tterrag/registrate/Registrate/",
    }
    prefix = prefixes.get(parsed.hostname)
    path = urllib.parse.unquote(parsed.path)
    if (not prefix or not path.startswith(prefix) or not path.endswith(".jar")
            or ".." in path.split("/") or "\\" in path):
        raise ValueError(f"不在已核实的官方下载范围内：{url}")
    return url


class OfficialRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # 校验每一跳，避免官方地址重定向后落到未核实的镜像。
        allowed_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def read_dependencies(readme):
    text = readme.read_text(encoding="utf-8")
    if text.count(START) != 1 or text.count(END) != 1:
        raise ValueError("docs/依赖.md 必须且只能包含一份测试依赖下载表")
    before, rest = text.split(START)
    if END in before or END not in rest:
        raise ValueError("docs/依赖.md 测试依赖表标记顺序错误")
    lines = [line.strip() for line in rest.split(END)[0].splitlines() if line.strip()]
    rows = []
    for line in lines:
        if not line.startswith("|") or not line.endswith("|"):
            raise ValueError(f"测试依赖表中不是表格行：{line}")
        cells = [cell.strip() for cell in line[1:-1].split("|")]
        if len(cells) != len(COLUMNS):
            raise ValueError(f"测试依赖表列数错误：{line}")
        rows.append(cells)
    if (len(rows) < 3 or tuple(rows[0]) != COLUMNS
            or any(not re.fullmatch(r":?-{3,}:?", cell) for cell in rows[1])):
        raise ValueError("测试依赖表表头或分隔行错误")
    selected = []
    filenames, modids = set(), set()
    for cells in rows[2:]:
        entry = dict(zip(COLUMNS, cells))
        name, enabled = entry["文件名"], entry["启用"]
        if (not name.endswith(".jar") or "/" in name or "\\" in name
                or any(ord(c) < 32 for c in name) or name in filenames):
            raise ValueError(f"重复或非法文件名：{name}")
        filenames.add(name)
        if enabled not in ("是", "仅完整版", "否"):
            raise ValueError(f"{name}：启用列必须为 是/仅完整版/否")
        if enabled == "否":
            continue
        mid = entry["modId"]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", mid) or mid in modids:
            raise ValueError(f"{name}：重复或非法启用 modId：{mid}")
        modids.add(mid)
        for column, size in (("SHA-1", 40), ("SHA-256", 64)):
            if not re.fullmatch(rf"[0-9a-f]{{{size}}}", entry[column]):
                raise ValueError(f"{name}：缺少有效的 {column}")
        if entry["下载 URL"] != "-":
            allowed_url(entry["下载 URL"])
        origin = entry["已有文件/复现前提"]
        if origin.startswith("libs/"):
            source = PurePosixPath(origin)
            if (".." in source.parts or "\\" in origin or source.name != name
                    or len(source.parts) not in (2, 3)
                    or (len(source.parts) == 3 and source.parts[1] != "runtime")):
                raise ValueError(f"{name}：非法本地复用路径：{origin}")
        selected.append(entry)
    if not selected:
        raise ValueError("测试依赖表没有启用项")
    return selected


def verify(path, entry):
    sha1, sha256 = hashlib.sha1(), hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            sha1.update(block)
            sha256.update(block)
    actual = (sha1.hexdigest(), sha256.hexdigest())
    expected = (entry["SHA-1"], entry["SHA-256"])
    if actual != expected:
        raise ValueError(
            f"哈希不符 {path}：SHA-1={actual[0]}，SHA-256={actual[1]}；"
            f"预期 SHA-1={expected[0]}，SHA-256={expected[1]}"
        )


def install(entry, root, mode, opener):
    name = entry["文件名"]
    destination = root / "libs" / "testrun" / name
    if destination.is_symlink():
        raise ValueError(f"目标不能是符号链接：{destination}")
    if destination.exists():
        verify(destination, entry)
        return "已校验"
    if mode == "check":
        raise ValueError(f"缺件：{destination}")
    origin = entry["已有文件/复现前提"]
    source = root / origin if origin.startswith("libs/") else root / "libs" / name
    if source.exists() or source.is_symlink():
        verify(source, entry)
    else:
        source = None
    url = entry["下载 URL"]
    if source is None and (mode == "reuse" or url == "-"):
        requirement = f"负责人提供的 {origin}" if url == "-" else url
        raise ValueError(f"缺件：{name}；需补齐 {requirement}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            dir=destination.parent, prefix=f".{name}.", suffix=".part", delete=False
        ) as output:
            temporary = Path(output.name)
            if source is not None:
                with source.open("rb") as stream:
                    shutil.copyfileobj(stream, output)
            else:
                request = urllib.request.Request(
                    allowed_url(url), headers={"User-Agent": "gtlmodular-test-deps/1"}
                )
                with opener.open(request, timeout=60) as stream:
                    allowed_url(stream.geturl())
                    shutil.copyfileobj(stream, output)
        verify(temporary, entry)
        temporary.replace(destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    return "已复用" if source is not None else "已下载"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="只读、离线校验目标目录")
    mode.add_argument("--reuse-only", action="store_true", help="仅复用本地文件，不联网")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        entries = read_dependencies(root / "docs" / "依赖.md")
    except (OSError, ValueError) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1
    mode = "check" if args.check else "reuse" if args.reuse_only else "download"
    opener = urllib.request.build_opener(OfficialRedirects())
    failures = 0
    for entry in entries:
        try:
            result = install(entry, root, mode, opener)
            print(f"{result}：{entry['文件名']}", flush=True)
        except (OSError, ValueError, urllib.error.URLError, http.client.HTTPException) as error:
            failures += 1
            print(f"失败：{entry['文件名']}：{error}", file=sys.stderr, flush=True)
    print(f"启用 {len(entries)} 项；成功 {len(entries) - failures} 项；失败 {failures} 项。")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
