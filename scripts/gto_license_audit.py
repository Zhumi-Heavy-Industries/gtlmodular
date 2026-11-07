# -*- coding: utf-8 -*-
"""GTO 移植素材 · 许可合规自检（发版 / 部署前必跑）。

检查五件事：
  1. NOTICE 顶部有 Required Notice 原文；
  2. NOTICE 里有 PolyForm Noncommercial 1.0.0 / CC BY-NC-SA 4.0 的许可 URL；
  3. 许可全文随包：resources/licenses/ 两份（非空、内容对得上）；
  4. jar（若已构建）内 META-INF/NOTICE_GTO.md 与 META-INF/licenses/ 两份全文；
  5. **本模组里与 GTO jar 逐字节相同的每一张贴图，都必须在 NOTICE 里被点名**（按文件名匹配）。

用法（在 gtl-modular 目录或任意目录）：
    python scripts/gto_license_audit.py
    python scripts/gto_license_audit.py --gto <GTO jar 或解包目录> --notice <NOTICE 路径>

退出码：0 = 全部通过；1 = 有缺项（逐条打印）。
"""
import argparse
import hashlib
import os
import sys
import zipfile

MOD = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(MOD, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'gtlmodular')
NOTICE_WORKSPACE = os.path.join(MOD, 'NOTICE_第三方素材_GTO.md')
NOTICE_JAR = os.path.join(RES, 'META-INF', 'NOTICE_GTO.md')
LIC_DIR = os.path.join(RES, 'META-INF', 'licenses')
BUILT_JAR = None

REQUIRED_NOTICE = 'Required Notice: Copyright GregTech Odyssey'
URLS = ('polyformproject.org/licenses/noncommercial/1.0.0',
        'creativecommons.org/licenses/by-nc-sa/4.0')


def sha_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def collect_gto(gto_path):
    """返回 {sha256: [GTO 内相对路径,...]}，支持 jar 或解包目录。"""
    index = {}
    if os.path.isdir(gto_path):
        root = gto_path
        # 若给的是解包后的 gtocore 目录（含 assets/gtocore），上溯到能包住 assets 的根
        if os.path.isdir(os.path.join(gto_path, 'assets')):
            root = gto_path
        for base, _, files in os.walk(root):
            for f in files:
                if f.lower().endswith('.png'):
                    p = os.path.join(base, f)
                    index.setdefault(sha_file(p), []).append(
                        os.path.relpath(p, root).replace(os.sep, '/'))
        return index
    with zipfile.ZipFile(gto_path) as z:
        for n in z.namelist():
            if n.lower().endswith('.png') and not n.endswith('/'):
                index.setdefault(hashlib.sha256(z.read(n)).hexdigest(), []).append(n)
    return index


def find_built_jar():
    libs = os.path.join(MOD, 'build', 'libs')
    if not os.path.isdir(libs):
        return None
    jars = [os.path.join(libs, n) for n in os.listdir(libs)
            if n.startswith('gtlmodular-') and n.endswith('.jar')]
    if not jars:
        return None
    return max(jars, key=os.path.getmtime)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--gto', default=None, help='GTO jar 或解包目录；默认用工作区 _tentacle_gto')
    ap.add_argument('--notice', default=NOTICE_WORKSPACE)
    args = ap.parse_args()

    gto = args.gto
    if gto is None:
        gto = os.path.join(os.path.dirname(MOD), '_tentacle_gto')
    failures = []

    def ok(cond, msg):
        print(('  OK   ' if cond else ' FAIL  ') + msg)
        if not cond:
            failures.append(msg)

    print('== 1/2. NOTICE 头部与 URL')
    for path in (args.notice, NOTICE_JAR):
        if not os.path.exists(path):
            ok(False, 'NOTICE 缺失: ' + path)
            continue
        text = open(path, encoding='utf-8').read()
        ok(REQUIRED_NOTICE in text, 'Required Notice 在 ' + os.path.basename(path))
        for url in URLS:
            ok(url in text, '%s 含 URL %s' % (os.path.basename(path), url))

    print('== 3. 许可全文（源码资源）')
    for name, must in (('GTOCore-LICENSE.txt', 'PolyForm Noncommercial'),
                       ('CC-BY-NC-SA-4.0.txt', 'Creative Commons')):
        p = os.path.join(LIC_DIR, name)
        if not os.path.exists(p):
            ok(False, '许可全文缺失: ' + p)
            continue
        body = open(p, encoding='utf-8', errors='ignore').read()
        ok(len(body) > 1000 and must.lower() in body.lower(),
           '%s 非空且含 %s（%d B）' % (name, must, len(body)))

    print('== 4. jar 内许可包（若已构建）')
    jar = find_built_jar()
    if jar is None:
        print('  SKIP  没有构建产物（先 gradle build）')
    else:
        print('  jar =', os.path.basename(jar))
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            ok('META-INF/NOTICE_GTO.md' in names, 'jar 含 META-INF/NOTICE_GTO.md')
            if 'META-INF/NOTICE_GTO.md' in names:
                ok(REQUIRED_NOTICE in z.read('META-INF/NOTICE_GTO.md').decode('utf-8', 'ignore'),
                   'jar 内 NOTICE 含 Required Notice')
            for n in ('GTOCore-LICENSE.txt', 'CC-BY-NC-SA-4.0.txt'):
                key = 'META-INF/licenses/' + n
                ok(key in names and len(z.read(key)) > 1000, 'jar 含 ' + key)

    print('== 5. 逐字节同源贴图是否都被 NOTICE 点名')
    if not os.path.exists(gto):
        ok(False, '找不到 GTO 参照（--gto 指定 jar 或解包目录）: ' + gto)
    else:
        gto_index = collect_gto(gto)
        notice_text = open(args.notice, encoding='utf-8').read() if os.path.exists(args.notice) else ''
        hits = []
        for base, _, files in os.walk(ASSETS):
            for f in files:
                if not f.lower().endswith('.png'):
                    continue
                p = os.path.join(base, f)
                h = sha_file(p)
                if h in gto_index:
                    hits.append((os.path.relpath(p, ASSETS).replace(os.sep, '/'), sorted(gto_index[h])[0]))
        def documented(rel):
            rel2 = rel[len('textures/'):] if rel.startswith('textures/') else rel
            return rel in notice_text or rel2 in notice_text

        missing = [(a, b) for a, b in hits if not documented(a)]
        print('  同源贴图 %d 张，NOTICE 未点名 %d 张' % (len(hits), len(missing)))
        ok(not missing, '所有同源贴图都在 NOTICE 清单里')
        for a, b in missing[:20]:
            print('      缺: %s  <=  %s' % (a, b))

    print()
    if failures:
        print('RESULT: %d 项不通过（发版前必须补齐 NOTICE / licenses）' % len(failures))
        return 1
    print('RESULT: ALL CHECKS PASSED（GTO 许可包完整）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
