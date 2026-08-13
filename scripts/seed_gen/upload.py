#!/usr/bin/env python3
"""Upload product images to Aliyun OSS (idempotent)."""
import json, os, re, sys, zipfile
import oss2

BASE = os.path.dirname(os.path.abspath(__file__))
XLSX = os.path.join(os.path.dirname(os.path.dirname(BASE)), "乾程五金锁具价格表(1).xlsx")
CATALOG = os.path.join(BASE, "catalog.json")

BUCKET = os.environ.get("OSS_BUCKET_NAME", "java0251014")
REGION = os.environ.get("OSS_REGION", "cn-beijing")
DOMAIN = os.environ.get("OSS_DOMAIN", "https://java0251014.oss-cn-beijing.aliyuncs.com")
ENV = os.path.join(os.path.dirname(os.path.dirname(BASE)), "hardware-mall-backend", ".env")


def load_env(path):
    env = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def main():
    env = load_env(ENV)
    akid = os.environ.get("OSS_ACCESS_KEY_ID") or env.get("OSS_ACCESS_KEY_ID")
    aks = os.environ.get("OSS_ACCESS_KEY_SECRET") or env.get("OSS_ACCESS_KEY_SECRET")
    if not akid or not aks:
        print("ERROR: OSS credentials not found")
        sys.exit(1)

    auth = oss2.Auth(akid, aks)
    bucket = oss2.Bucket(auth, f"https://oss-{REGION}.aliyuncs.com", BUCKET)

    catalog = json.load(open(CATALOG, encoding="utf-8"))
    z = zipfile.ZipFile(XLSX)

    img_cat = {}
    for p in catalog:
        for im in p["images"]:
            if im not in img_cat:
                img_cat[im] = p["category"]

    results = {}
    failed = []
    skipped = 0
    total = len(img_cat)
    for idx, (img, cat) in enumerate(sorted(img_cat.items())):
        try:
            data = z.read(f"xl/media/{img}")
        except KeyError:
            failed.append((img, cat, "media not in xlsx"))
            continue
        cat_path = re.sub(r'[^\w\u4e00-\u9fff-]', '', cat)
        object_name = f"products/{cat_path}/{img}"
        try:
            if bucket.object_exists(object_name):
                skipped += 1
                url = f"{DOMAIN}/{object_name}"
            else:
                bucket.put_object(object_name, data, headers={"Content-Type": "image/jpeg" if img.lower().endswith(("jpg", "jpeg")) else "image/png"})
                url = f"{DOMAIN}/{object_name}"
            results[img] = url
        except Exception as e:
            failed.append((img, cat, str(e)))
            print(f"[{idx+1}/{total}] FAIL {img}: {e}")

    with open(os.path.join(BASE, "img_url.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    with open(os.path.join(BASE, "img_url_failed.json"), "w", encoding="utf-8") as f:
        json.dump(failed, f, ensure_ascii=False, indent=1)

    print(f"\nDONE: uploaded/verified {len(results)} (skipped {skipped}), failed {len(failed)}")
    if failed:
        for img, cat, err in failed:
            print("  FAILED:", img, cat, err)


if __name__ == "__main__":
    main()
