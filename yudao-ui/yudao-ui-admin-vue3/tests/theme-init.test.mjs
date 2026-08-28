import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import vm from 'node:vm'

const themeInitSource = await readFile(new URL('../public/theme-init.js', import.meta.url), 'utf8')
const entryHtml = await readFile(new URL('../index.html', import.meta.url), 'utf8')

const cached = (value) =>
  JSON.stringify({
    c: Date.now(),
    e: new Date('9999-12-31T23:59:59.000Z').getTime(),
    v: JSON.stringify(value)
  })

const runThemeInit = (items) => {
  const properties = new Map()
  const classes = new Set()
  const storage = new Map(Object.entries(items))
  const context = {
    Date,
    document: {
      documentElement: {
        classList: {
          add: (...names) => names.forEach((name) => classes.add(name)),
          remove: (...names) => names.forEach((name) => classes.delete(name))
        },
        style: {
          setProperty: (name, value) => properties.set(name, value)
        }
      }
    },
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      removeItem: (key) => storage.delete(key)
    }
  }

  vm.runInNewContext(themeInitSource, context)
  return { classes, properties }
}

test('applies the persisted menu theme before the application starts', () => {
  const result = runThemeInit({
    theme: cached({
      leftMenuBgColor: '#ffffff',
      leftMenuTextColor: '#303133',
      topHeaderBgColor: '#f8fafc'
    }),
    isDark: cached(false)
  })

  assert.equal(result.properties.get('--left-menu-bg-color'), '#ffffff')
  assert.equal(result.properties.get('--left-menu-text-color'), '#303133')
  assert.equal(result.properties.get('--top-header-bg-color'), '#f8fafc')
  assert.equal(result.classes.has('light'), true)
  assert.equal(result.classes.has('dark'), false)
})

test('ignores expired or malformed cached values without breaking startup', () => {
  const result = runThemeInit({
    theme: JSON.stringify({ c: 0, e: 0, v: JSON.stringify({ leftMenuBgColor: '#fff' }) }),
    isDark: '{malformed'
  })

  assert.equal(result.properties.size, 0)
  assert.equal(result.classes.size, 0)
})

test('restores dark mode when the theme cache is malformed', () => {
  const result = runThemeInit({
    theme: '{malformed',
    isDark: cached(true)
  })

  assert.equal(result.properties.size, 0)
  assert.equal(result.classes.has('dark'), true)
  assert.equal(result.classes.has('light'), false)
})

test('restores the theme when the dark-mode cache is malformed', () => {
  const result = runThemeInit({
    theme: cached({ leftMenuBgColor: '#ffffff' }),
    isDark: '{malformed'
  })

  assert.equal(result.properties.get('--left-menu-bg-color'), '#ffffff')
  assert.equal(result.classes.size, 0)
})

test('loads the persisted theme initializer synchronously from the document head', () => {
  const head = entryHtml.match(/<head>([\s\S]*?)<\/head>/i)?.[1]
  assert.ok(head)

  const themeInitializerTag = head.match(
    /<script\b[^>]*\bsrc=["']%BASE_URL%theme-init\.js["'][^>]*><\/script>/i
  )?.[0]
  assert.ok(themeInitializerTag)
  assert.doesNotMatch(themeInitializerTag, /\b(?:async|defer)(?:\s|=|>)/i)
  assert.doesNotMatch(themeInitializerTag, /\btype=["']module["']/i)

  const themeInitializerIndex = entryHtml.indexOf(themeInitializerTag)
  const applicationEntryIndex = entryHtml.indexOf('/src/main.ts')

  assert.notEqual(applicationEntryIndex, -1)
  assert.equal(themeInitializerIndex < applicationEntryIndex, true)
})
