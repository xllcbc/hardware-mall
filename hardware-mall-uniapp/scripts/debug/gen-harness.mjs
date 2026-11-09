// 调试工具（非 CI）：用编译后的小程序 WXSS + 商品数据生成一个可打开的网页，
// 量出分类页两列商品网格的实际列宽，并在页面上渲染 PASS/FAIL。
//
// 产生原因：2026-11 分类页两列错乱（左列被撑大、右列显示不全），根因是小程序
// 中网格项是 product-card 组件宿主节点（无 min-width:0），长副标题(nowrap)把
// 1fr 列撑破。修复为 minmax(0,1fr)。此脚本用于回归自查：改完网格/卡片样式后，
// 重新 build 再跑一次，确认所有分类两列等宽。不参与构建与 CI。
//
// 用法：
//   npm run build:mp-weixin                       # 先生成最新 dist
//   node scripts/debug/gen-harness.mjs [备份.sql]  # 可选：用生产备份的真实文案
//   打开 scripts/debug/harness.html 查看结果
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseProduction } from './parse-prod.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const uniappRoot = path.resolve(here, '..', '..')
const repoRoot = path.resolve(uniappRoot, '..')
const dist = path.join(uniappRoot, 'dist/build/mp-weixin')
const RPX = 0.5 // 750rpx 设计宽度，按 375px 视口换算

if (!fs.existsSync(path.join(dist, 'pages/category/index.wxss'))) {
  console.error('未找到编译产物，请先运行: npm run build:mp-weixin')
  process.exit(1)
}

function css(file) {
  let s = fs.readFileSync(path.join(dist, file), 'utf-8')
  s = s.replace(/\.data-v-[0-9a-f]+/g, '')
  s = s.replace(/(-?[\d.]+)rpx/g, (_, n) => parseFloat(n) * RPX + 'px')
  return s
}

const parsePrice = s => { const v = parseFloat(s); return isNaN(v) ? null : v }

function seedProducts() {
  const catalog = JSON.parse(fs.readFileSync(path.join(repoRoot, 'scripts/seed_gen/catalog.json'), 'utf-8'))
  const groups = new Map()
  for (const p of catalog) {
    const key = JSON.stringify([p.category, p.brand || '', p.base_model || ''])
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(p)
  }
  const byCat = {}
  for (const [, prods] of groups) {
    const p0 = prods[0]
    const brand = (p0.brand || '').trim()
    const model = (p0.base_model || '').trim()
    const prices = prods.map(p => parsePrice(p.price) ?? 1234567)
    const min = Math.min(...prices)
    ;(byCat[p0.category] ||= []).push({
      name: brand && model ? `${brand} ${model}` : (model || brand),
      subtitle: brand,
      price: min !== Math.max(...prices) ? min.toFixed(2) + '起' : min.toFixed(2)
    })
  }
  return byCat
}

function loadProducts() {
  const sqlFile = process.argv[2]
  if (!sqlFile) return seedProducts()
  const byCat = parseProduction(fs.readFileSync(sqlFile, 'utf-8'))
  // 备份里没有 SKU 价格，这里用固定价——价格不影响列宽（已实测）
  for (const items of Object.values(byCat)) items.forEach(i => { i.price = i.price || '128.00' })
  return byCat
}

const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))

function card(s) {
  // 网格项＝product-card 宿主节点；内部结构与编译产物一致
  return `<product-card><view class="product-card"><view class="image-wrapper"><image class="product-image"></image></view><view class="product-info"><text class="product-name">${esc(s.name)}</text>${s.subtitle ? `<text class="product-subtitle">${esc(s.subtitle)}</text>` : ''}<view class="product-bottom"><view class="price-wrap"><text class="price-symbol">¥</text><text class="price-value">${esc(s.price)}</text></view></view></view></view></product-card>`
}

// 每个分类取「副标题最长的商品」当最坏用例，再补几个普通商品凑成两列
function phone(cat, items) {
  const sorted = [...items].sort((a, b) => b.subtitle.length - a.subtitle.length)
  const worst = sorted[0]
  const rest = sorted.slice(1, 5)
  const cards = [worst, ...rest].filter(Boolean)
  return `<section class="phone" data-cat="${esc(cat)}" style="width:375px">
  <h3>${esc(cat)} <span class="badge">—</span></h3>
  <view class="category-container"><view class="category-content">
    <view class="category-left"></view>
    <view class="category-right">
      <view class="category-header"><text class="header-title">${esc(cat)}</text></view>
      <view class="product-grid">${cards.map(card).join('')}</view>
    </view>
  </view></view>
</section>`
}

const byCat = loadProducts()
const phones = Object.entries(byCat).map(([cat, items]) => phone(cat, items)).join('\n')

const style = css('pages/category/index.wxss') + '\n' + css('components/common/ProductCard.wxss') +
  '\n.category-container{height:auto!important}.phone{margin:8px 0 20px;font-family:sans-serif}'
  + '.phone h3{font-size:13px;margin:4px} .badge{font-weight:700}'

const measure = `
(function(){
  var rows = [];
  document.querySelectorAll('.phone').forEach(function(ph){
    var cat = ph.getAttribute('data-cat');
    var grid = ph.querySelector('.product-grid');
    var badge = ph.querySelector('.badge');
    if(!grid){ rows.push([cat,'NO GRID',false]); badge.textContent='NO GRID'; badge.style.color='#999'; return; }
    var t = getComputedStyle(grid).gridTemplateColumns.split(' ').map(parseFloat);
    var diff = Math.abs(t[0]-t[1]);
    var overflow = grid.scrollWidth - grid.clientWidth;
    var pass = t.length===2 && diff <= 1 && overflow <= 1;
    rows.push([cat, t.map(function(x){return Math.round(x*10)/10}).join(' / ')+'px', pass]);
    badge.textContent = pass ? 'PASS' : 'FAIL (' + Math.round(diff) + 'px 差)';
    badge.style.color = pass ? '#1a7f37' : '#cf222e';
  });
  var fail = rows.filter(function(r){return !r[2]}).length;
  document.title = (fail ? 'FAIL ' : 'PASS ') + '(' + fail + '/' + rows.length + ' 失败)';
  var box = document.createElement('table');
  box.style.cssText = 'border-collapse:collapse;font:12px sans-serif;margin:8px';
  box.innerHTML = '<tr><th style="text-align:left;padding:2px 8px">分类</th><th style="text-align:left;padding:2px 8px">两列宽度</th><th style="padding:2px 8px">结果</th></tr>' +
    rows.map(function(r){return '<tr><td style="padding:2px 8px">'+r[0]+'</td><td style="padding:2px 8px">'+r[1]+'</td><td style="padding:2px 8px;color:'+(r[2]?'#1a7f37':'#cf222e')+'">'+(r[2]?'PASS':'FAIL')+'</td></tr>'}).join('');
  document.body.insertBefore(box, document.body.firstChild);
  console.log('[gen-harness]', document.title, rows);
})();`

const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=375">
<title>grid harness</title><style>view,product-card{display:block}image{display:block}text{display:inline}
${style}</style></head><body>${phones}<script>${measure}</script></body></html>`

fs.writeFileSync(path.join(here, 'harness.html'), html)
console.log('已生成 scripts/debug/harness.html；分类数:', Object.keys(byCat).length, '（打开后看页面顶部 PASS/FAIL 表）')