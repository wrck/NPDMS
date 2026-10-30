import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, type PropType } from 'vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ReviewDetail from './ReviewDetail.vue'
const mocks = vi.hoisted(() => ({ get: vi.fn(), download: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: mocks }))
vi.mock('./SolutionDocMetaForm.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('./SolutionReviewChapterForm.vue', () => ({ default: defineComponent({
  props: { modelValue: { type: Object as PropType<{ name: string }>, required: true }, reviewContext: Boolean },
  setup: props => () => h('p', `review:${props.modelValue.name}:${props.reviewContext}`)
}) }))
const flush = async () => { for (let i = 0; i < 15; i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => vi.resetAllMocks())
it('does not display a source when frozen materials authorization fails', async () => {
  mocks.get.mockImplementation(({ url }) => url.endsWith('/source') ? Promise.resolve({ name: '方案A' }) : Promise.reject(new Error('待办资格已撤销')))
  const view = mount(ReviewDetail, { id: 'SOL_REVIEW:1:42' })
  try {
    await flush()
    expect(textOf(view.root)).toContain('待办资格已撤销')
    expect(textOf(view.root)).not.toContain('review:方案A')
    expect(mocks.get.mock.calls.map(([arg]) => arg.url).sort()).toEqual(['/api/v1/pms/solution-reviews/materials', '/api/v1/pms/solution-reviews/source'])
  } finally { view.app.unmount() }
})
it('discards a previous tasks delayed source and materials after switching tasks', async () => {
  const pending: Array<(value: unknown) => void> = []
  mocks.get.mockImplementation(({ params, url }) => params.businessKey.endsWith(':42')
    ? new Promise(resolve => pending.push(resolve))
    : Promise.resolve(url.endsWith('/source') ? { name: '方案B' } : { requirement: {}, attachments: [] }))
  const key = ref('SOL_REVIEW:1:42')
  const view = mount(defineComponent({ setup: () => () => h(ReviewDetail, { id: key.value }) }))
  try {
    await flush(); key.value = 'SOL_REVIEW:1:43'; await flush()
    pending[0]({ name: '方案A' }); pending[1]({ requirement: {}, attachments: [] }); await flush()
    expect(textOf(view.root)).toContain('review:方案B:true')
    expect(textOf(view.root)).not.toContain('方案A')
  } finally { view.app.unmount() }
})
