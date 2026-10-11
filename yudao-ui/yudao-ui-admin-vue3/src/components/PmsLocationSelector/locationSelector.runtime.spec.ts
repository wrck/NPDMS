import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import LocationSelector from './index.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/dict', () => ({
  DICT_TYPE: { PMS_SITE_LOCATION_TYPE: 'location' },
  getStrDictOptions: () => []
}))
vi.mock('@/api/pms/asset/location', () => ({
  getSitePage: vi
    .fn()
    .mockResolvedValue({ list: [{ id: 21, version: 2, addressId: 11, name: '站点' }] }),
  getAddressPage: vi.fn().mockResolvedValue({ list: [{ id: 11, fullAddress: '原地址' }] }),
  getSiteLocationTree: vi.fn().mockResolvedValue([])
}))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn().mockResolvedValue({}) }))
vi.mock('@/api/pms/customer', () => ({ getCustomerByCode: vi.fn() }))

const flush = async () => {
  for (let i = 0; i < 12; i++) await nextTick()
}
const render = () => {
  const props = reactive({
    projectId: 1,
    modelValue: {
      projectId: 1,
      address: { id: 11, expectedVersion: 3 },
      site: { id: 21, expectedVersion: 2 }
    },
    'onUpdate:modelValue': vi.fn()
  })
  const selector = ref()
  const host = defineComponent({
    setup: () => () => h(LocationSelector, { ...props, ref: selector })
  })
  const mounted = mount(
    host,
    {},
    {
      ElRadioGroup: passthrough,
      ElRadioButton: passthrough,
      ElSelect: passthrough,
      ElOption: passthrough,
      ElTreeSelect: passthrough,
      ElDivider: passthrough,
      PmsDivisionInput: passthrough,
      ElInput: passthrough,
      ElCol: passthrough,
      ElRow: passthrough
    }
  )
  return {
    ...mounted,
    props,
    get state() {
      return selector.value.$.setupState
    }
  }
}

beforeEach(() => vi.clearAllMocks())

it('preserves the saved address reference when customer context loads on mount', async () => {
  const m = render()
  try {
    await flush()
    expect(m.state.draft.address).toEqual({ id: 11, expectedVersion: 3 })
    for (const [command] of m.props['onUpdate:modelValue'].mock.calls) {
      expect(command.address).toEqual({ id: 11, expectedVersion: 3 })
    }
  } finally {
    m.app.unmount()
  }
})

it('clears an old customer address reference when the project changes', async () => {
  const m = render()
  try {
    await flush()
    m.props.projectId = 2
    await flush()
    expect(m.state.draft.address).toEqual({ countryCode: 'CN', countryName: '中国' })
  } finally {
    m.app.unmount()
  }
})
