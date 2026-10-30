import { expect, it } from 'vitest'
import Detail from './InspectionUmcRuleDetail.vue'
import {
  mount,
  passthrough,
  textOf
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

it('renders all metric objects, empty results, errors and full output without requiring nested table expansion', () => {
  const output = '<script>alert(1)</script>\n' + 'full output '.repeat(200)
  const page = mount(
    Detail,
    {
      rule: {
        id: 'r',
        ruleid: '1',
        name: '多对象检查',
        support: true,
        result: true,
        output,
        targetlist: [
          {
            id: 'a',
            name: '端口',
            result: true,
            info: '检查接口',
            valuelist: [
              { id: 'a1', object: 'port 1', value: '10', result: false },
              { id: 'a2', object: 'port 2', value: '', result: true }
            ]
          },
          { id: 'b', name: '没有对象的指标', error: '命令不支持', valuelist: [] }
        ]
      }
    },
    { ElCollapse: passthrough, ElCollapseItem: passthrough }
  )
  try {
    const text = textOf(page.root)
    for (const expected of [
      '端口',
      'port 1',
      'port 2',
      '10',
      '（空值）',
      '没有对象的指标',
      '命令不支持',
      '本指标未返回对象值',
      output
    ]) {
      expect(text).toContain(expected)
    }
    // The untrusted output remains text in a pre element, never an HTML node.
    const nodes = (node: typeof page.root): (typeof page.root)[] => [
      node,
      ...node.children.flatMap(nodes)
    ]
    expect(nodes(page.root).some((node) => node.type === 'script')).toBe(false)
    expect(nodes(page.root).filter((node) => node.type === 'tbody')).toHaveLength(2)
    expect(nodes(page.root).filter((node) => node.type === 'td')).toHaveLength(9)
  } finally {
    page.app.unmount()
  }
})
