#!/usr/bin/env python3
"""Extract product data + image-to-product mapping from 乾程五金锁具价格表(1).xlsx"""
import re, json, os, zipfile
import xml.etree.ElementTree as ET

BASE = os.path.dirname(os.path.abspath(__file__))
XLSX = os.path.join(os.path.dirname(os.path.dirname(BASE)), "乾程五金锁具价格表(1).xlsx")
OUT = BASE
NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}

SHEET_NAMES = {1: '合页', 2: '门吸', 3: '胶类', 4: '各类锁具', 5: '木门衣橱拉手', 6: '隐形锁合页暗插', 7: '滑道反弹器等'}

CAT_MAP = {
    '合页': '合页',
    '门吸': '门吸',
    '胶类': '胶类',
    '各类锁具': '锁具',
    '木门衣橱拉手': '拉手',
    '隐形锁合页暗插': '隐形锁合页暗插',
    '滑道反弹器等': '轨道',
}

COLORS = ['亮咖啡古铜', '亮烙黑胡桃', '灰拉丝黑胡桃', '红古铜', '黄古铜', '青古铜', '古铜拉丝', '紫金拉丝',
          '氧化灰', '咖啡古铜', '灰拉丝', '绅士灰', '玫瑰金', '亚金拉丝', '亚镍拉丝', '亮光', '亮烙',
          '仿金', '古铜', '拉丝', '氧化黑', '灰色', '砂光', '红古', '银色', '白', '金', '金色',
          '铜拉丝', '银', '青古', '黄古', '黑色', '亚金', '亚黑', '黑拉丝', '黑钛', '金钛', '钛金',
          '星点', '玫金', '雅丝灰', '亮灰', '磷化黑', '纯铜', '胡桃', '电镀', 'PVD', '真皮', '绿',
          '黑', '灰']
COLORS = sorted(set(COLORS), key=len, reverse=True)


def col_num(letter):
    n = 0
    for ch in letter:
        n = n * 26 + (ord(ch) - 64)
    return n


def col_letter(n):
    s = ''
    while n:
        n, r = divmod(n - 1, 26)
        s = chr(65 + r) + s
    return s


def parse_shared_strings(z):
    root = ET.fromstring(z.read('xl/sharedStrings.xml'))
    out = []
    for si in root.findall('m:si', NS):
        txt = ''.join(t.text or '' for t in si.iter('{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t'))
        out.append(txt)
    return out


def read_sheet(z, i, ss):
    root = ET.fromstring(z.read(f'xl/worksheets/sheet{i}.xml'))
    rows = {}
    for r in root.findall('m:sheetData/m:row', NS):
        rn = int(r.get('r'))
        cells = {}
        for c in r.findall('m:c', NS):
            ref = c.get('r')
            t = c.get('t')
            v = c.find('m:v', NS)
            if v is None:
                continue
            col = re.match(r'[A-Z]+', ref).group()
            val = ss[int(v.text)] if t == 's' else v.text
            cells[col] = val
        rows[rn] = cells
    return rows


def parse_drawing(z, i):
    """return list of (row, col_float, mediafile); col_float is 1-based fractional column"""
    try:
        rels_xml = z.read(f'xl/drawings/_rels/drawing{i}.xml.rels')
    except KeyError:
        return []
    rels = {}
    for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="\.\./media/(image\d+\.\w+)"', rels_xml.decode('utf-8')):
        rels[m.group(1)] = m.group(2)
    xml = z.read(f'xl/drawings/drawing{i}.xml').decode('utf-8')
    anchors = []
    for m in re.finditer(
            r'<xdr:from><xdr:col>(\d+)</xdr:col><xdr:colOff>(\d+)</xdr:colOff><xdr:row>(\d+)</xdr:row><xdr:rowOff>(\d+)</xdr:rowOff></xdr:from><xdr:to><xdr:col>(\d+)</xdr:col><xdr:colOff>(\d+)</xdr:colOff><xdr:row>(\d+)</xdr:row>.*?</xdr:to>.*?r:embed="(rId\d+)"',
            xml, re.S):
        f_col = int(m.group(1))
        f_off = int(m.group(2))
        row = int(m.group(3)) + 1  # 1-based
        t_col = int(m.group(5))
        t_off = int(m.group(6))
        img = rels.get(m.group(8))
        if img:
            center = (f_col + f_off / 1200000.0 + t_col + t_off / 1200000.0) / 2.0 + 1.0
            anchors.append((row, center, img))
    return anchors


def block_images(anchors, hdr_rows, imgcols):
    img_by_block = {}
    for (row, cf, img) in anchors:
        h0 = max([h for h in hdr_rows if h <= row], default=None)
        if h0 is None:
            continue
        ic = min(imgcols, key=lambda x: abs(x - cf))
        img_by_block.setdefault((h0, ic), []).append(img)
    return img_by_block


def extract_color(model):
    if not model:
        return None, model
    m = model.strip()
    for co in COLORS:
        if m.endswith(co):
            return co, m[:-len(co)].rstrip('·  ')
    for co in COLORS:
        idx = m.find(co)
        while idx != -1:
            if 0 < idx < len(m) - len(co):
                base = m[:idx] + m[idx + len(co):]
                return co, base.strip().rstrip('·  ')
            idx = m.find(co, idx + 1)
    return None, m


def main():
    z = zipfile.ZipFile(XLSX)
    ss = parse_shared_strings(z)
    print(f"shared strings: {len(ss)}")

    catalog = []
    for i in range(1, 8):
        sname = SHEET_NAMES[i]
        data = read_sheet(z, i, ss)
        anchors = parse_drawing(z, i)
        hdr = sorted(rn for rn, row in data.items() if any(str(v).strip() == '图片' for v in row.values()))
        if not hdr:
            continue
        hrow = data[hdr[0]]
        imgcols = sorted(col_num(c) for c, v in hrow.items() if str(v).strip() == '图片')
        img_to_label = {cn: col_letter(cn + 1) for cn in imgcols}
        img_by_block = block_images(anchors, hdr, imgcols)

        max_row = max(data.keys())
        for h0 in hdr:
            nxt = min([h for h in hdr if h > h0] + [max_row + 1])
            for icn, lab in img_to_label.items():
                val_col = col_letter(col_num(lab) + 1)
                brand = model = price = color = None
                for rn in range(h0, nxt):
                    row = data.get(rn, {})
                    L = row.get(lab)
                    if L is None:
                        continue
                    lstr = str(L).strip()
                    v = row.get(val_col)
                    if lstr == '品牌':
                        brand = v
                    elif lstr.startswith('型号'):
                        model = v
                    elif lstr.startswith('批发价'):
                        price = v
                    elif lstr == '颜色':
                        color = v
                imgs = img_by_block.get((h0, icn), [])
                if model is None and brand is None and imgs == []:
                    continue
                colr, base = extract_color(model)
                catalog.append({
                    'sheet': i,
                    'category': CAT_MAP[sname],
                    'brand': brand,
                    'model': model,
                    'base_model': base,
                    'color': color or colr,
                    'price': price,
                    'images': imgs,
                })

    with open(os.path.join(OUT, 'catalog.json'), 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    print(f"catalog: {len(catalog)} products")
    from collections import Counter
    c = Counter(p['category'] for p in catalog)
    for k, v in c.items():
        print(f"  {k}: {v}")
    with_img = sum(1 for p in catalog if p['images'])
    print(f"products with images: {with_img}")


if __name__ == '__main__':
    main()
