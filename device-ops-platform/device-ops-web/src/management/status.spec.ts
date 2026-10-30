import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import ManagementStatus from '@/components/management/ManagementStatus.vue'
describe('shared management status language', () => {
  it.each([['FAILED', '失败', 'danger'], ['WAITING', '等待中', 'warning'], ['EXECUTING', '执行中', 'primary'], [null, '未知', 'info']])('renders %s as a shared Element Plus status tag', (status, label, tone) => {
    const wrapper = mount(ManagementStatus, { props: { status }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain(label)
    expect(wrapper.find(`.el-tag--${tone}`).exists()).toBe(true)
  })
})
