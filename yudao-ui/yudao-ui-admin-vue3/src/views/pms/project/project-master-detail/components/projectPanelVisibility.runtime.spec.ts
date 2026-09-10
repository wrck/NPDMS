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
      expect(show.exp.content).toBe(`activeTab === '${key}'`)
      expect(node.props.find((prop: any) => prop.name === 'if').exp.content).toBe(`detail?.id && visitedTabs.has('${key}')`)
    }
    node.children?.forEach(visit)
  }
  visit(ast)
  expect(invalid).toEqual([])
  expect(hosts).toHaveLength(15)
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
