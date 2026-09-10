import { readFileSync } from 'node:fs'
import { parse } from 'vue/compiler-sfc'
import { defineComponent, h, nextTick, ref, vShow, withDirectives } from 'vue'
import { expect, it } from 'vitest'
import { mount, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

it('puts cached panel visibility on native hosts, independent of component root shape', () => {
  const source = read('../index.vue')
  const ast = parse(source).descriptor.template!.ast!
  const hosts: any[] = []
  const invalid: string[] = []
  const visit = (node: any) => {
    const show = node.props?.find((prop: any) => prop.name === 'show' && prop.exp)
    if (show && /^[A-Z]/.test(node.tag) && node.tag !== 'ContentWrap') invalid.push(node.tag)
    const testId = node.props?.find((prop: any) => prop.name === 'data-testid')?.value?.content
    if (testId?.startsWith('project-pane-')) {
      hosts.push(node)
      const key = testId.slice('project-pane-'.length)
      expect(node.tag).toBe('div')
      const sharedStagePanel = key === 'stage-gates'
      expect(show.exp.content).toBe(sharedStagePanel
        ? "['base', 'tasks', 'stage-gates'].includes(activeTab)"
        : `activeTab === '${key}'`)
      expect(node.props.find((prop: any) => prop.name === 'if').exp.content).toBe(sharedStagePanel
        ? "detail?.id && (visitedTabs.has('base') || visitedTabs.has('tasks') || visitedTabs.has('stage-gates'))"
        : `detail?.id && visitedTabs.has('${key}')`)
    }
    node.children?.forEach(visit)
  }
  visit(ast)
  expect(invalid).toEqual([])
  expect(hosts).toHaveLength(15)
  const children = hosts.flatMap(node => node.children)
  const taskPanel = children.find(node => node.tag === 'ProjectTaskPanel')
  expect(taskPanel.props.find((prop: any) => prop.name === 'on' && prop.arg?.content === 'updated').exp.content).toBe('loadAll')
  expect(children.some(node => node.tag === 'ProjectNormalClosurePanel')).toBe(true)
  expect(children.some(node => node.tag === 'ProjectClosureGuardPanel')).toBe(true)
})

it('hides a multi-root business panel without remounting or losing its local state', async () => {
  const visited = ref(false)
  const active = ref(false)
  let mounts = 0
  const fragmentPanel = defineComponent({ setup() {
    mounts++
    return () => [h('span', 'query'), h('span', 'business content')]
  } })
  const host = defineComponent({ setup: () => () => visited.value
    ? withDirectives(h('div', { 'data-testid': 'panel-host' }, [h(fragmentPanel)]), [[vShow, active.value]])
    : null })
  const mounted = mount(host)
  try {
    expect(mounts).toBe(0)
    visited.value = true; active.value = true; await nextTick()
    expect(mounts).toBe(1)
    active.value = false; await nextTick()
    expect(findByTestId(mounted.root, 'panel-host')?.style?.display).toBe('none')
    active.value = true; await nextTick()
    expect(findByTestId(mounted.root, 'panel-host')?.style?.display).not.toBe('none')
    expect(mounts).toBe(1)
  } finally { mounted.app.unmount() }
})
