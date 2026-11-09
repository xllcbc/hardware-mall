// 调试工具（非 CI）：解析生产备份 SQL，按分类统计商品「名称 / 副标题」长度，
// 标出可能撑破两列商品网格的分类。
//
// 产生原因：2026-11 分类页部分分类两列错乱（左列被撑大、右列显示不全），
// 根因是长副标题（white-space:nowrap）把 1fr 网格列撑破。此脚本用于拿真实数据
// 定位哪些分类/商品踩到长度阈值，不参与构建与 CI。
//
// 用法：node scripts/debug/parse-prod.mjs <备份.sql>
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const RISK_SUBTITLE_LEN = 10

function grabTuples(stmt) {
  const out = []
  let depth = 0
  let inQuote = false
  let escaped = false
  let cur = ''
  for (let i = 0; i < stmt.length; i++) {
    const ch = stmt[i]
    if (inQuote) {
      cur += ch
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === "'") inQuote = false
      continue
    }
    if (ch === "'") { inQuote = true; cur += ch; continue }
    if (ch === '(') { depth++; cur += ch; continue }
    if (ch === ')') { depth--; cur += ch; if (depth === 0) { out.push(cur); cur = '' } continue }
    if (depth > 0) cur += ch
  }
  return out
}

function statementValues(sql, table) {
  const re = new RegExp('INSERT INTO `' + table + '` VALUES ', 'g')
  let m
  let res = ''
  while ((m = re.exec(sql))) {
    const start = m.index + m[0].length
    let inQuote = false
    let escaped = false
    let end = sql.length
    for (let i = start; i < sql.length; i++) {
      const ch = sql[i]
      if (inQuote) {
        if (escaped) escaped = false
        else if (ch === '\\') escaped = true
        else if (ch === "'") inQuote = false
        continue
      }
      if (ch === "'") { inQuote = true; continue }
      if (ch === ';') { end = i; break }
    }
    res += sql.slice(start, end) + ','
  }
  return res
}

const unquote = s => s.replace(/^'|'$/g, '').replace(/\\'/g, "'").replace(/''/g, "'").replace(/\\n/g, '\n')

export function parseProduction(sqlText) {
  const catName = {}
  for (const t of grabTuples(statementValues(sqlText, 'category'))) {
    const m = t.match(/^\((\d+),(\d+),'((?:[^'\\]|\\.)*)'/)
    if (m) catName[+m[1]] = unquote("'" + m[3] + "'")
  }
  const byCat = {}
  for (const t of grabTuples(statementValues(sqlText, 'spu'))) {
    const m = t.match(/^\((\d+),(\d+),'((?:[^'\\]|\\.)*)','((?:[^'\\]|\\.)*)'/)
    if (!m) continue
    const cat = catName[+m[2]] || 'cat#' + m[2]
    ;(byCat[cat] ||= []).push({ name: unquote("'" + m[3] + "'"), subtitle: unquote("'" + m[4] + "'") })
  }
  return byCat
}

function main() {
  const file = process.argv[2]
  if (!file) {
    console.error('用法: node scripts/debug/parse-prod.mjs <备份.sql>')
    process.exit(1)
  }
  const byCat = parseProduction(fs.readFileSync(file, 'utf-8'))
  for (const [cat, items] of Object.entries(byCat)) {
    const maxName = Math.max(...items.map(i => i.name.length))
    const longest = [...items].sort((a, b) => b.subtitle.length - a.subtitle.length)[0]
    const risk = longest.subtitle.length >= RISK_SUBTITLE_LEN ? '  <-- 副标题偏长，注意两列网格' : ''
    console.log(`[${cat}] spu=${items.length} maxName=${maxName} maxSubtitle=${longest.subtitle.length}${risk}`)
    console.log(`  最长副标题: name=${JSON.stringify(longest.name)} subtitle=${JSON.stringify(longest.subtitle)}`)
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()