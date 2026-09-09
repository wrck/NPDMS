import { createApp, defineComponent, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import formCreate from '@form-create/element-ui'
import Fixture from './Fixture.vue'

// Only the unchanged application-specific controls are fixtures. No business API is imported.
formCreate.component(
  'Editor',
  defineComponent({
    props: ['modelValue', 'readonly'],
    emits: ['update:modelValue'],
    setup(props, { emit }) {
      return () =>
        h('textarea', {
          value: props.modelValue || '',
          rows: 3,
          disabled: props.readonly,
          onInput: (event: Event) =>
            emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
        })
    }
  })
)
formCreate.component(
  'PmsFileArtifact',
  defineComponent({
    setup: () => () => h('p', { class: 'file-fixture' }, '受控附件槽位（测试占位，不提供上传）')
  })
)
createApp(Fixture).use(ElementPlus).use(formCreate).mount('#app')
