import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// jsdom does not cascade stylesheet rules into getComputedStyle. To keep the
// assertions on real rendered values (not whole-CSS string matching), these
// tests parse the production stylesheets, apply the matched declarations to a
// probe element, and assert through getComputedStyle.

const projectFile = (...parts: string[]) => resolve(process.cwd(), ...parts)
const readProject = (...parts: string[]) => readFileSync(projectFile(...parts), 'utf8')

type Decl = Record<string, string>

interface Rule {
  selector: string
  decls: Decl
}

function parseRules(css: string): Rule[] {
  const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '')
  const rules: Rule[] = []
  let depth = 0
  let blockStart = 0
  let selectorStart = 0
  for (let i = 0; i < withoutComments.length; i++) {
    const ch = withoutComments[i]
    if (ch === '{') {
      if (depth === 0) selectorStart = i
      depth++
    } else if (ch === '}') {
      depth--
      if (depth === 0) {
        const selector = withoutComments.slice(blockStart, selectorStart).trim()
        const decls: Decl = {}
        for (const declaration of withoutComments.slice(selectorStart + 1, i).split(';')) {
          const idx = declaration.indexOf(':')
          if (idx > 0) decls[declaration.slice(0, idx).trim().toLowerCase()] = declaration.slice(idx + 1).trim()
        }
        rules.push({ selector, decls })
        blockStart = i + 1
      }
    }
  }
  return rules
}

/** Merges every rule whose selector list contains the target (later rules win). */
function declsFor(rules: Rule[], selector: string): Decl {
  const merged: Decl = {}
  let found = false
  for (const rule of rules) {
    if (rule.selector.split(',').map(part => part.trim()).includes(selector)) {
      found = true
      Object.assign(merged, rule.decls)
    }
  }
  if (!found) throw new Error(`missing rule: ${selector}`)
  return merged
}

/** Applies declarations (plus root tokens) inline and reads values through getComputedStyle. */
function computedFor<T>(decls: Decl, read: (style: CSSStyleDeclaration) => T): T {
  const probe = document.createElement('span')
  const tokens = rootTokens()
  for (const [prop, value] of Object.entries(tokens)) probe.style.setProperty(prop, value)
  for (const [prop, value] of Object.entries(decls)) probe.style.setProperty(prop, value)
  document.body.appendChild(probe)
  const result = read(getComputedStyle(probe))
  probe.remove()
  return result
}

function computedPair(decls: Decl, first: string, second?: string): string[] {
  const values = computedFor(decls, style => second ? [style.getPropertyValue(first), style.getPropertyValue(second)] : [style.getPropertyValue(first)])
  return values.map(resolveValue)
}

const mainRules = () => parseRules(readProject('src', 'styles', 'main.css'))
const managementRules = () => parseRules(readProject('src', 'styles', 'management.css'))
const rootTokens = () => declsFor(mainRules(), ':root')
const componentStyle = (name: string) => {
  const sfc = readProject('src', 'components', name)
  const match = sfc.match(/<style[^>]*>([\s\S]*?)<\/style>/)
  if (!match) throw new Error(`missing style block in ${name}`)
  return parseRules(match[1]!)
}

// jsdom keeps var() unresolved in computed values; substitute from main.css tokens.
function resolveValue(value: string): string {
  const match = value.match(/^var\((.+)\)$/)
  return match ? rootTokens()[match[1]!.trim()] ?? value : value
}

describe('字体层级契约（computed style）', () => {
  it('main.css 声明标题/正文/辅助字号与权重 token', () => {
    const root = declsFor(mainRules(), ':root')
    expect(root['--ops-fs-h1']).toBe('1.125rem')
    expect(root['--ops-fs-h2']).toBe('0.9375rem')
    expect(root['--ops-fs-h3']).toBe('0.875rem')
    expect(root['--ops-weight-heading']).toBe('600')
    expect(root['--ops-weight-body']).toBe('400')
  })

  it('工作台面板 h2 统一 0.9375rem/600，死样式区块标题已删除', () => {
    expect(computedPair(declsFor(mainRules(), '.workflow-grid.workflow-grid .panel-header h2'), 'font-size', 'font-weight')).toEqual(['0.9375rem', '600'])
    expect(() => declsFor(mainRules(), '.section-heading h2')).toThrow('missing rule')
    expect(() => declsFor(mainRules(), '.shell h1')).toThrow('missing rule')
    expect(computedPair(declsFor(componentStyle('PanelHeader.vue'), '.panel-header h2'), 'font-size', 'font-weight')).toEqual(['0.9375rem', '600'])
  })

  it('卡片头紧凑：面板头 36px/边距 6px/间距 8px，卡片头 padding 上下 6px', () => {
    const header = declsFor(mainRules(), '.workflow-grid.workflow-grid .panel-header.panel-header')
    expect(header['min-height']).toBe('2.25rem')
    expect(header['margin']).toBe('0 0 0.375rem')
    expect(header['gap']).toBe('0.5rem')
    expect(declsFor(mainRules(), '.connection-workbench > .el-card__header')['padding-block']).toBe('0.375rem')
    expect(declsFor(mainRules(), '.script-editor > .el-card__header')['padding-block']).toBe('0.375rem')
    expect(declsFor(mainRules(), '.parser-sidebar > .el-card__header')['padding-block']).toBe('0.375rem')
  })

  it('表单 label 600、helper 400，形成辅助层级', () => {
    expect(computedPair(declsFor(mainRules(), '.connection-workbench .el-form-item__label'), 'font-weight')).toEqual(['600'])
    expect(computedPair(declsFor(mainRules(), '.workbench-parser .el-form-item__label'), 'font-weight')).toEqual(['600'])
    expect(computedPair(declsFor(mainRules(), '.connection-helper'), 'font-weight')).toEqual(['400'])
  })

  it('管理页卡片头为语义 h2 0.9375rem/600，h3 0.875rem/600，16px strong 已删除', () => {
    expect(computedPair(declsFor(managementRules(), '.management-page .el-card__header h2'), 'font-size', 'font-weight')).toEqual(['0.9375rem', '600'])
    expect(computedPair(declsFor(managementRules(), '.management-page h3'), 'font-size', 'font-weight')).toEqual(['0.875rem', '600'])
    expect(() => declsFor(managementRules(), '.management-page .el-card__header strong')).toThrow('missing rule')
  })

  it('应用壳 h1 显式 1.25rem/600，副标题 12px/400', () => {
    const shell = componentStyle('AppShell.vue')
    expect(computedPair(declsFor(shell, '.app-shell__header h1'), 'font-size', 'font-weight')).toEqual(['1.125rem', '600'])
    expect(computedPair(declsFor(shell, '.app-shell__subtitle'), 'font-size', 'font-weight')).toEqual(['0.75rem', '400'])
  })
})
