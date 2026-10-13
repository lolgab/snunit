// Reads package directories (one per line) on the standard input and, for each npm package, copies its license and
// notice files into <out>/<name>@<version>/ and records its license in <out>/packages.tsv.
// Fails if a package is under a strong copyleft license (GPL, AGPL, SSPL, BUSL...), unless an alternative of an
// "A OR B" expression is permissive.
// Usage: node collect-npm-licenses.js <out dir> [<label>]
'use strict'
const fs = require('fs')
const path = require('path')

const out = process.argv[2]
const label = process.argv[3] || ''
fs.mkdirSync(out, { recursive: true })

const dirs = fs.readFileSync(0, 'utf8').split('\n').map(s => s.trim()).filter(Boolean)
const strong = /(^|[^L])GPL|AGPL|SSPL|BUSL|Commons-Clause|CC-BY-NC/i
const seen = new Set()
const rows = []
const violations = []
const lgpl = []
const unknown = []

// Guess the license from the text of the license file when package.json doesn't say.
function detect(dir) {
  for (const name of fs.readdirSync(dir)) {
    if (!/^(licen[cs]e|copying)/i.test(name)) continue
    const text = fs.readFileSync(path.join(dir, name), 'utf8')
    if (/GNU (AFFERO )?GENERAL PUBLIC LICENSE|GNU LESSER/i.test(text)) return 'GPL-family (see file)'
    if (/Apache License/i.test(text)) return 'Apache-2.0 (see file)'
    if (/Permission is hereby granted, free of charge/i.test(text)) return 'MIT (see file)'
    if (/Redistribution and use in source and binary forms/i.test(text)) return 'BSD (see file)'
    if (/Permission to use, copy, modify, and\/or distribute/i.test(text)) return 'ISC (see file)'
  }
  return null
}

function licenseOf(pkg) {
  if (typeof pkg.license === 'string') return pkg.license
  if (pkg.license && typeof pkg.license.type === 'string') return pkg.license.type
  if (Array.isArray(pkg.licenses)) return pkg.licenses.map(l => (typeof l === 'string' ? l : l.type)).join(' OR ')
  return 'UNKNOWN'
}

function isStrong(expression) {
  // "(MIT OR GPL-3.0)": fine if one alternative is not strong. "A AND B": every part applies.
  const alternatives = expression.replace(/[()]/g, '').split(/\s+OR\s+/i)
  return alternatives.every(a => strong.test(a))
}

for (const dir of dirs) {
  const file = path.join(dir, 'package.json')
  if (!fs.existsSync(file)) continue
  let pkg
  try { pkg = JSON.parse(fs.readFileSync(file, 'utf8')) } catch (e) { continue }
  if (!pkg.name || !pkg.version) continue
  const id = `${pkg.name}@${pkg.version}`
  if (seen.has(id)) continue
  seen.add(id)
  let license = licenseOf(pkg)
  if (license === 'UNKNOWN') license = detect(dir) || license
  if (license === 'UNKNOWN') unknown.push(id)
  rows.push(`${pkg.name}\t${pkg.version}\t${license}`)
  if (isStrong(license)) violations.push(`${id}: ${license}`)
  else if (/LGPL/i.test(license)) lgpl.push(`${id}: ${license}`)
  const target = path.join(out, id.replace(/\//g, '__'))
  for (const name of fs.readdirSync(dir)) {
    if (/^(licen[cs]e|notice|copying|patents)/i.test(name) && fs.statSync(path.join(dir, name)).isFile()) {
      fs.mkdirSync(target, { recursive: true })
      fs.copyFileSync(path.join(dir, name), path.join(target, name))
    }
  }
}

rows.sort()
fs.appendFileSync(path.join(out, 'packages.tsv'), rows.join('\n') + (rows.length ? '\n' : ''))
console.error(`${label} ${seen.size} npm packages`.trim())
if (unknown.length) console.error('Packages with no license information (their own project, or check them):\n  ' + unknown.join('\n  '))
if (lgpl.length) console.error('LGPL packages (dynamic use only, check them):\n  ' + lgpl.join('\n  '))
if (violations.length) {
  console.error('Strong copyleft licenses found:\n  ' + violations.join('\n  '))
  process.exit(1)
}
