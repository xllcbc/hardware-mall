#!/usr/bin/env python3
"""Generate seed_real_products_v2.sql from catalog.json + img_url.json."""
import json, os, re
from collections import OrderedDict, defaultdict

BASE = os.path.dirname(os.path.abspath(__file__))
CATALOG = os.path.join(BASE, "catalog.json")
IMG_URL = os.path.join(BASE, "img_url.json")
OUT = os.path.join(BASE, "..", "..", "hardware-mall-backend", "src", "main", "resources", "db", "seed_real_products_v2.sql")
OUT = os.path.normpath(OUT)

catalog = json.load(open(CATALOG, encoding="utf-8"))
img_url = json.load(open(IMG_URL, encoding="utf-8"))

CATEGORY_ORDER = ['锁具', '胶类', '合页', '门吸', '拉手', '轨道', '隐形锁合页暗插']
CATEGORY_ICON = {
    '锁具': '🔐', '胶类': '🧴', '合页': '🛠', '门吸': '🧲',
    '拉手': '✋', '轨道': '🚂', '隐形锁合页暗插': '🔒',
}
cat_id = {name: i + 1 for i, name in enumerate(CATEGORY_ORDER)}
cat_sort = {name: 100 - i * 10 for i, name in enumerate(CATEGORY_ORDER)}


def parse_price(s):
    if not s:
        return None
    s = str(s).strip()
    m = re.search(r'(\d+(?:\.\d+)?)', s)
    return float(m.group(1)) if m else None


def esc(v):
    v = str(v).replace('\n', ' ').replace('\r', ' ')
    v = re.sub(r'\s+', ' ', v).strip()
    return v.replace("\\", "\\\\").replace("'", "''")


# ===== group into SPUs =====
groups = OrderedDict()
no_model_idx = defaultdict(int)
for p in catalog:
    if p['model']:
        key = (p['category'], p['brand'], p['base_model'], None)
    else:
        no_model_idx[(p['category'], p['brand'])] += 1
        key = (p['category'], p['brand'], None, f"nm{no_model_idx[(p['category'], p['brand'])]}")
    groups.setdefault(key, []).append(p)

# colors per category (ordered by first appearance)
colors_by_cat = defaultdict(OrderedDict)
for p in catalog:
    co = p['color']
    if co and co not in colors_by_cat[p['category']]:
        colors_by_cat[p['category']][co] = None

# itemId 必须等于 spec_item 表全表自增 id:
# 按「分类顺序 + 分类内首现顺序」全局递增，与下方 INSERT 顺序完全一致
item_counter = 0
for name in CATEGORY_ORDER:
    for co in colors_by_cat.get(name, {}):
        item_counter += 1
        colors_by_cat[name][co] = item_counter

lines = []
lines.append("-- ==========================================")
lines.append("-- 五金商城系统 - 真实商品数据 v2 (含OSS图片)")
lines.append("-- 来源：乾程五金锁具价格表(1).xlsx")
lines.append("-- 图片已上传至阿里云OSS products/目录")
lines.append("-- 价格为 1234567 的商品表示原始Excel中无价格数据")
lines.append("-- ==========================================")
lines.append("")
lines.append("USE `hardware_mall`;")
lines.append("")
lines.append("-- 清理旧商品测试数据（保留管理员账号admin和物流信息）")
lines.append("DELETE FROM `cart`;")
lines.append("DELETE FROM `order_item`;")
lines.append("DELETE FROM `shop_order`;")
lines.append("DELETE FROM `sku`;")
lines.append("DELETE FROM `spu`;")
lines.append("DELETE FROM `spec_item`;")
lines.append("DELETE FROM `spec_template`;")
lines.append("DELETE FROM `category`;")
lines.append("ALTER TABLE `category` AUTO_INCREMENT = 1;")
lines.append("ALTER TABLE `spec_template` AUTO_INCREMENT = 1;")
lines.append("ALTER TABLE `spec_item` AUTO_INCREMENT = 1;")
lines.append("ALTER TABLE `spu` AUTO_INCREMENT = 1;")
lines.append("ALTER TABLE `sku` AUTO_INCREMENT = 1;")
lines.append("")

lines.append("-- ==================== 商品分类 ====================")
for name in CATEGORY_ORDER:
    lines.append(f"INSERT INTO `category` (`name`, `icon`, `sort_order`, `status`) VALUES ('{name}', '{CATEGORY_ICON[name]}', {cat_sort[name]}, 1);")
lines.append("")

lines.append("-- ==================== 规格模板 ====================")
for name in CATEGORY_ORDER:
    cid = cat_id[name]
    lines.append(f"INSERT INTO `spec_template` (`category_id`, `name`, `spec_type`, `is_required`, `sort_order`) VALUES ({cid}, '颜色', 1, 1, 1);")
lines.append("")

lines.append("-- ==================== 规格项（颜色值）====================")
for name in CATEGORY_ORDER:
    cid = cat_id[name]
    for idx, (co, iid) in enumerate(colors_by_cat.get(name, {}).items(), start=1):
        lines.append(f"INSERT INTO `spec_item` (`template_id`, `value`, `sort_order`) VALUES ({cid}, '{esc(co)}', {idx});")
lines.append("")

lines.append("-- ==================== 商品数据 (SPU + SKU) ====================")
spu_id = 0
sku_id = 0
no_price_sku = 0
dup_merged = 0

ordered_groups = sorted(groups.items(), key=lambda kv: (CATEGORY_ORDER.index(kv[0][0]), list(groups.keys()).index(kv[0])))

for key, prods in ordered_groups:
    cat, brand, base_model, uid = key
    cid = cat_id[cat]
    spu_id += 1

    # images for SPU: all unique URLs across products
    imgs = []
    for p in prods:
        for im in p['images']:
            url = img_url.get(im)
            if url and url not in imgs:
                imgs.append(url)

    brand_name = (brand or '').strip()
    model_name = (base_model or '').strip()
    if model_name and brand_name:
        name = f"{brand_name} {model_name}"
    elif model_name:
        name = model_name
    elif brand_name:
        name = brand_name
    else:
        name = f"{cat}商品{spu_id}"
    subtitle = brand_name or None

    orig = None
    for p in prods:
        pr = parse_price(p['price'])
        if pr is not None:
            orig = pr
            break
    orig_sql = f"{orig:.2f}" if orig is not None else "NULL"
    images_sql = json.dumps(imgs, ensure_ascii=False).replace("'", "''") if imgs else "NULL"

    lines.append(f"INSERT INTO `spu` (`category_id`, `name`, `subtitle`, `original_price`, `weight`, `sales_count`, `status`, `is_recommend`, `images`) VALUES ({cid}, '{esc(name)}', {('NULL' if not subtitle else chr(39)+esc(subtitle)+chr(39))}, {orig_sql}, NULL, 0, 1, 1, {('NULL' if images_sql=='NULL' else chr(39)+images_sql+chr(39))});")
    lines.append("SET @spu_id = LAST_INSERT_ID();")

    # dedupe SKUs by spec_hash (keep first price, merge images into SPU already done)
    seen_hashes = set()
    for p in prods:
        co = p['color']
        price = parse_price(p['price'])
        if price is None:
            price = 1234567.00
            no_price_sku += 1
        if co and co in colors_by_cat.get(cat, {}):
            iid = colors_by_cat[cat][co]
            hash_ = f"auto_c{cid}_i{iid}"
        else:
            hash_ = "default"
        if hash_ in seen_hashes:
            dup_merged += 1
            continue
        seen_hashes.add(hash_)
        sku_id += 1
        price_sql = f"{price:.2f}"
        sku_img = img_url.get(p['images'][0]) if p['images'] else None
        sku_img_sql = f"'{esc(sku_img)}'" if sku_img else "NULL"
        if co and co in colors_by_cat.get(cat, {}):
            iid = colors_by_cat[cat][co]
            specs = f'[{{"templateId":{cid},"itemId":{iid},"name":"颜色","value":"{esc(co)}"}}]'
            hash_ = f"auto_c{cid}_i{iid}"
        else:
            specs = '[{"name":"颜色","value":"默认"}]'
            hash_ = "default"
        lines.append(f"INSERT INTO `sku` (`spu_id`, `specs`, `price`, `stock`, `status`, `spec_hash`, `image`) VALUES (@spu_id, '{specs}', {price_sql}, 999, 1, '{hash_}', {sku_img_sql});")

lines.append("")

lines.append("-- ==================== 保留数据 ====================")
lines.append("-- 管理员账号 (如果不存在则创建)")
lines.append("INSERT IGNORE INTO `user` (`id`, `openid`, `nickname`, `role`, `status`) VALUES (1, 'admin', '管理员', 2, 1);")
lines.append("")
lines.append("-- 物流方式 (如果不存在则创建)")
lines.append("INSERT IGNORE INTO `logistics` (`name`, `code`, `phones`, `sort_order`, `status`) VALUES ('德邦物流', 'debang', '[\"400-800-8888\"]', 100, 1);")
lines.append("INSERT IGNORE INTO `logistics` (`name`, `code`, `phones`, `sort_order`, `status`) VALUES ('顺心捷达', 'shunxin', '[\"400-900-9999\"]', 90, 1);")
lines.append("INSERT IGNORE INTO `logistics` (`name`, `code`, `phones`, `sort_order`, `status`) VALUES ('安能物流', 'anneng', '[\"400-700-7777\"]', 80, 1);")
lines.append("")
lines.append(f"-- 统计: {spu_id} 个SPU, {sku_id} 个SKU")
lines.append(f"-- 其中 {no_price_sku} 个SKU在原始Excel中无价格数据（标记为1234567）")
lines.append(f"-- 重复颜色SKU合并数: {dup_merged}")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print(f"SQL written: {OUT}")
print(f"SPUs: {spu_id}, SKUs: {sku_id}, no-price SKUs: {no_price_sku}, dup merged: {dup_merged}")
