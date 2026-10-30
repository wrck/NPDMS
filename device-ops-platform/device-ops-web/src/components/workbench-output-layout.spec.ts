import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ElementPlus from 'element-plus'
import ParserSidebar from './ParserSidebar.vue'
import ProjectCollectionView from '@/views/ProjectCollectionView.vue'
import type { ScriptDraft } from '@/types/collection'

const api = vi.hoisted(() => ({ getParserSelectionOptions: vi.fn(), upsertSchedule: vi.fn() }))
vi.mock('@/api/device-ops', () => api)
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
const script: ScriptDraft = { source: 'LOCAL_MANAGED', key: 'show', version: '1', content: 'show version', sha256: 'a'.repeat(64), policy: 'EXECUTION_ONLY', parserType: 'NONE', parserConfig: '' }
const wrappers: VueWrapper[] = []
const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const mainCss = source('../styles/main.css')
const editor = source('./ScriptArtifactEditor.vue')
const view = source('../views/ProjectCollectionView.vue')

beforeEach(() => {
  vi.clearAllMocks()
  api.getParserSelectionOptions.mockResolvedValue({ automaticEnabled: true, defaultAvailable: true, options: [] })
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals() })

describe('workbench output layout ownership', () => {
  it('renders configuration only and preserves parser retry', async () => {
    api.getParserSelectionOptions.mockRejectedValueOnce(new Error('版本加载失败'))
    const wrapper = mount(ParserSidebar, { props: { modelValue: { ...script }, selection: { mode: 'AUTO' } }, global: { plugins: [ElementPlus], stubs: { SemanticResultPanel: true } } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('semantic-result-panel-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('解析策略')
    expect(wrapper.text()).toContain('兼容解析配置')
    await wrapper.findAll('button').find(button => button.text().includes('重试'))!.trigger('click')
    await flushPromises()
    expect(api.getParserSelectionOptions).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('暂无已发布解析版本')
  })

  it('does not read or forward task results while preserving start submission', async () => {
    const readResults = vi.fn(() => [])
    const start = vi.fn().mockResolvedValue(true)
    const rememberCurrent = vi.fn()
    const wrapper = mount(ProjectCollectionView, { props: { projectKey: 'direct' }, global: { plugins: [ElementPlus], stubs: {
      AppShell: { template: '<div><slot /></div>' },
      TargetSelector: defineComponent({ setup(_, { expose }) { expose({ buildSelection: () => ({ namespace: 'ns', deviceLabel: 'host', connection: { savedConnectionId: 'saved', credentialNamespace: 'ns' } }), rememberCurrent }); return () => null } }),
      ScriptArtifactEditor: defineComponent({ emits: ['update:modelValue'], mounted() { this.$emit('update:modelValue', { ...script }) }, template: '<div />' }),
      ParserSidebar: true,
      CollectionTaskPanel: defineComponent({ emits: ['submit'], setup(_, { expose }) { expose({ start, stopPolling: vi.fn(), get latestParsedFacts() { return readResults() }, get semanticResults() { return readResults() }, get latestCollectionDetails() { return readResults() } }) }, template: '<button @click="$emit(\'submit\')">submit</button>' })
    } } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(readResults).not.toHaveBeenCalled()
    const sidebar = wrapper.findComponent(ParserSidebar)
    for (const name of ['parsed-facts', 'semantic-results', 'collection-details']) expect(sidebar.attributes()).not.toHaveProperty(name)
    await wrapper.findAll('button').find(button => button.text() === 'submit')!.trigger('click')
    await flushPromises()
    expect(start).toHaveBeenCalledOnce()
    expect(rememberCurrent).toHaveBeenCalledOnce()
  })

  it('uses one desktop connection body scrollbar and a nonshrinking header', () => {
    expect(mainCss).toMatch(/\.connection-workbench\s*\{[^}]*overflow:\s*visible/s)
    expect(mainCss).toMatch(/\.connection-workbench\s*>\s*\.el-card__header\s*\{[^}]*flex:\s*0 0 auto/s)
    expect(mainCss).toMatch(/\.connection-workbench\s*>\s*\.el-card__body[^}]*overflow-y:\s*auto/s)
    expect(mainCss).toContain('@media (min-width: 1200px)')
    expect(mainCss).toContain('@media (max-width: 1199px)')
  })

  it('does not stretch parser configuration and removes obsolete sidebar facts styles', () => {
    expect(mainCss).not.toMatch(/\.workbench-parser\s*>\s*\.parser-sidebar\s*\{\s*height:\s*100%/s)
    expect(mainCss).not.toContain('.parser-sidebar__facts')
  })

  it('bounds desktop columns without an extra shell content scrollbar', () => {
    expect(view).toContain('class="project-collection"')
    // Pixel reachability lives in e2e/output-tabs-visibility.spec.ts.
    expect(view).toContain('@media (min-width: 1200px) and (min-height: 851px)')
    expect(view).toMatch(/\.project-collection\s*:deep\(\.app-shell__content\)\s*\{[^}]*display:\s*flex;[^}]*overflow:\s*hidden/s)
    expect(view).toMatch(/\.workflow-grid\s*\{[^}]*flex:\s*1 1 0/s)
  })

  it('wraps script metadata by available width and uses Element Plus shortcut buttons', () => {
    expect(editor).toContain('repeat(auto-fit, minmax(min(100%, 13rem), 1fr))')
    expect(editor).toMatch(/<el-button\s+v-for="command in commonCommands"/)
    expect(editor).toMatch(/\.artifact-details__body\s*\{[^}]*width:\s*100%/s)
    expect(editor).toMatch(/\.script-editor\s*\{[^}]*flex:\s*0 0 auto/s)
  })

  it('excludes task output from management pre styling', () => {
    const css = source('../styles/management.css')
    expect(css).not.toMatch(/\.management-page\s+pre\s*\{/)
    expect(css).toContain('.management-page pre:not(.task-panel pre)')
    const style = document.createElement('style')
    style.textContent = css
    document.head.append(style)
    const host = document.createElement('div')
    host.className = 'management-page'
    host.innerHTML = '<pre>management</pre><section class="task-panel"><pre>output</pre></section>'
    document.body.append(host)
    expect(host.querySelector('pre')!.matches('.management-page pre:not(.task-panel pre)')).toBe(true)
    expect(host.querySelector('.task-panel pre')!.matches('.management-page pre:not(.task-panel pre)')).toBe(false)
    host.remove()
    style.remove()
  })
})
