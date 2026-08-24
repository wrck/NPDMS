const fs = require('node:fs')
const path = require('node:path')

const projectRoot = path.resolve(__dirname, '..')
const sourceRoot = path.join(projectRoot, 'src')
const iconifyRoot = path.join(projectRoot, 'node_modules', '@iconify', 'json', 'json')
const outputFile = path.join(sourceRoot, 'plugins', 'svgIcon', 'iconify.generated.json')
const fullCollections = new Set(['ep', 'fa', 'fa-solid'])
const sourceExtensions = new Set(['.js', '.jsx', '.json', '.ts', '.tsx', '.vue'])
const checkOnly = process.argv.includes('--check')

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return walk(entryPath)
    return sourceExtensions.has(path.extname(entry.name)) ? [entryPath] : []
  })
}

function addIcon(collection, name, icons, aliases) {
  if (collection.icons[name]) {
    icons[name] = collection.icons[name]
    return true
  }

  const alias = collection.aliases?.[name]
  if (!alias) return false

  aliases[name] = alias
  return addIcon(collection, alias.parent, icons, aliases)
}

const availablePrefixes = new Set(
  fs
    .readdirSync(iconifyRoot)
    .filter((name) => name.endsWith('.json'))
    .map((name) => name.slice(0, -5))
)
const requestedIcons = new Map()
const literalPattern = /(['"`])([a-z0-9-]+):([a-z0-9-]+)\1/g
const forbiddenOnlinePatterns = [
  { pattern: /['"]@iconify\/vue['"]/, label: '@iconify/vue online entry' },
  {
    pattern: /api\.(?:iconify\.design|simplesvg\.com|unisvg\.com)/,
    label: 'Iconify API endpoint'
  }
]
const onlineReferences = []

for (const file of walk(sourceRoot)) {
  if (path.resolve(file) === path.resolve(outputFile)) continue

  const source = fs.readFileSync(file, 'utf8')
  for (const forbidden of forbiddenOnlinePatterns) {
    if (forbidden.pattern.test(source)) {
      onlineReferences.push(`${path.relative(projectRoot, file)}: ${forbidden.label}`)
    }
  }
  for (const match of source.matchAll(literalPattern)) {
    const [, , prefix, name] = match
    if (fullCollections.has(prefix) || !availablePrefixes.has(prefix)) continue

    const names = requestedIcons.get(prefix) ?? new Set()
    names.add(name)
    requestedIcons.set(prefix, names)
  }
}

if (onlineReferences.length) {
  console.error(
    `Online Iconify references are forbidden:\n${onlineReferences.map((item) => `- ${item}`).join('\n')}`
  )
  process.exit(1)
}

const generatedCollections = []
const missingIcons = []

for (const prefix of [...requestedIcons.keys()].sort()) {
  const collection = require(path.join(iconifyRoot, `${prefix}.json`))
  const icons = {}
  const aliases = {}

  for (const name of [...requestedIcons.get(prefix)].sort()) {
    if (!addIcon(collection, name, icons, aliases)) missingIcons.push(`${prefix}:${name}`)
  }

  generatedCollections.push({
    prefix,
    ...(collection.width ? { width: collection.width } : {}),
    ...(collection.height ? { height: collection.height } : {}),
    icons,
    ...(Object.keys(aliases).length ? { aliases } : {})
  })
}

if (missingIcons.length) {
  console.error(`Iconify icons not found:\n${missingIcons.map((icon) => `- ${icon}`).join('\n')}`)
  process.exit(1)
}

const generated = `${JSON.stringify(generatedCollections, null, 2)}\n`

if (checkOnly) {
  const current = fs.existsSync(outputFile) ? fs.readFileSync(outputFile, 'utf8') : ''
  if (current !== generated) {
    console.error('Offline Iconify collection is stale. Run: pnpm icons:generate')
    process.exit(1)
  }
  console.log(`Offline Iconify collection is current (${generatedCollections.length} collections).`)
} else {
  fs.writeFileSync(outputFile, generated)
  console.log(`Generated ${generatedCollections.length} offline Iconify collections.`)
}
