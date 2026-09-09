import type { ObjectDirective } from 'vue'

// Scoped integration for the installed FormCreate 3.2.38 group controls, which render as divs.
// Native group handlers still perform every operation; this only supplies keyboard/ARIA behavior.
const selector = [
  '._fc-group-plus-minus', '._fc-group-add', '._fc-group-up', '._fc-group-down',
  '._fc-group-sort-up', '._fc-group-sort-down'
].join(',')
interface ControlsState {
  readonly: boolean
  observer: MutationObserver
  keydown: (event: KeyboardEvent) => void
  keyup: (event: KeyboardEvent) => void
  click: (event: MouseEvent) => void
}
const mounted = new WeakMap<HTMLElement, ControlsState>()
const disabled = (button: HTMLElement, state: ControlsState) =>
  state.readonly || !!button.closest('._fc-group-disabled')

const decorate = (root: HTMLElement, state: ControlsState) => {
  root.querySelectorAll<HTMLElement>(selector).forEach((button) => {
    const group = button.closest('._fc-group')
    const title = group?.closest('.el-form-item')?.querySelector('.el-form-item__label')?.textContent?.trim() || '明细'
    const row = button.closest('._fc-group-container')?.querySelector('._fc-group-idx')?.textContent?.trim()
    const action = button.classList.contains('_fc-group-minus') ? '删除行'
      : button.classList.contains('_fc-group-sort-up') ? '上移行'
      : button.classList.contains('_fc-group-sort-down') ? '下移行'
      : button.classList.contains('_fc-group-up') ? '收起行'
      : button.classList.contains('_fc-group-down') ? '展开行' : '添加行'
    button.setAttribute('role', 'button')
    button.setAttribute('aria-label', `${title}${row ? ` 第${row}行` : ''} ${action}`)
    button.setAttribute('aria-disabled', String(disabled(button, state)))
    button.tabIndex = disabled(button, state) ? -1 : 0
  })
}

export const vFormCreateKeyboardRows: ObjectDirective<HTMLElement, boolean> = {
  mounted(root, binding) {
    const control = (event: Event) => {
      const button = (event.target as Element).closest<HTMLElement>(selector)
      return button && root.contains(button) ? button : undefined
    }
    const state: ControlsState = {
      readonly: binding.value,
      observer: new MutationObserver(() => decorate(root, state)),
      keydown(event) {
        const button = control(event)
        if (!button || !['Enter', ' '].includes(event.key)) return
        event.preventDefault()
        if (event.key === 'Enter' && !event.repeat && !disabled(button, state)) button.click()
      },
      keyup(event) {
        const button = control(event)
        if (button && event.key === ' ') {
          event.preventDefault()
          if (!disabled(button, state)) button.click()
        }
      },
      click(event) {
        const button = control(event)
        if (button && disabled(button, state)) {
          event.preventDefault()
          event.stopImmediatePropagation()
        }
      }
    }
    mounted.set(root, state)
    root.addEventListener('keydown', state.keydown)
    root.addEventListener('keyup', state.keyup)
    root.addEventListener('click', state.click, true)
    state.observer.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] })
    decorate(root, state)
  },
  updated(root, binding) {
    const state = mounted.get(root)!
    state.readonly = binding.value
    decorate(root, state)
  },
  unmounted(root) {
    const state = mounted.get(root)!
    state.observer.disconnect()
    root.removeEventListener('keydown', state.keydown)
    root.removeEventListener('keyup', state.keyup)
    root.removeEventListener('click', state.click, true)
    mounted.delete(root)
  }
}
