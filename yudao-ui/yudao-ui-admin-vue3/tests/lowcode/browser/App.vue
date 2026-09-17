<script setup lang="ts">
import { defineComponent, h, markRaw, nextTick, onMounted, ref } from 'vue'
import { ElInput } from 'element-plus'
import Facade from '@/components/LowCodeFormRendererFacade/index.vue'
import TabRenderer from '@/components/LowCodeTabRenderer/index.vue'
import RelatedRenderer from '@/components/LowCodeRelatedPageRenderer/index.vue'
import type { FormConfig } from '@/api/lowcode'

const query = new URLSearchParams(location.search)
const mode = query.get('mode') || 'form'
const version = ref(query.get('version') || undefined)
const disabled = ref(false)
const model = ref<Record<string, unknown> | undefined>(query.has('uncontrolled') ? undefined : {
  title: '', note: 'initial-note', hidden: 'secret', businessId: 42
})
const form = ref<InstanceType<typeof Facade>>()
const other = ref<InstanceType<typeof Facade>>()
const events = ref<{ type: string; value: unknown }[]>([])
const config = ref<FormConfig>({
  fields: [
    { id: 'title', type: mode === 'custom' ? 'custom' : 'input', componentName: 'BusinessPicker', prop: 'title', label: 'Title', placeholder: 'Enter title', required: true, defaultValue: '' },
    { id: 'note', type: 'input', prop: 'note', label: 'Note', placeholder: 'Enter note', defaultValue: 'default-note' },
    { id: 'hidden', type: 'input', prop: 'hidden', label: 'Hidden', hidden: true, defaultValue: 'secret' }
  ],
  layout: { type: 'grid', gutter: 16 }
})
if (mode === 'upload') {
  config.value.fields.push({ id: 'file', type: 'upload', prop: 'file', label: 'File', props: { accept: '.pdf', multiple: true, tip: 'PDF only', action: '/fixture-upload' } })
}
if (mode === 'tabs') config.value.layout = {
  type: 'tabs', tabs: [{ title: 'First', name: 'first', fields: ['title'] }, { title: 'Second', name: 'second', fields: ['note'] }]
}
if (mode === 'collapse') config.value.layout = {
  type: 'collapse', collapse: [{ title: 'First', name: 'first', fields: ['title'] }, { title: 'Second', name: 'second', fields: ['note'] }]
}
function picker(id: string) {
  return markRaw(defineComponent({
    inheritAttrs: false,
    props: ['modelValue', 'disabled', 'field'],
    emits: ['update:modelValue', 'change'],
    setup(props, { emit }) {
      return () => h(ElInput, {
        modelValue: props.modelValue, disabled: props.disabled, 'data-testid': id,
        'onUpdate:modelValue': (value: unknown) => emit('update:modelValue', value),
        onChange: (value: unknown) => emit('change', { id: value, label: id })
      })
    }
  }))
}
const registryA = { BusinessPicker: picker('picker-a') }
const registryB = { BusinessPicker: picker('picker-b') }
const handlers = {
  changed: (value: unknown, _field: unknown, data: Record<string, unknown>) => events.value.push({ type: 'handler', value: { payload: value, title: data.title } })
}
config.value.fields[0].events = { change: 'changed' }
const nestedConfig = { ...config.value, rendererVersion: version.value || 'v1' }
const previewConfigs = { fixture: nestedConfig }
const tabConfig = {
  tabs: [{ id: 'nested', name: 'nested', title: 'Nested form', pageType: 'form', pageCode: 'fixture', props: { title: '${row.title}' } }]
}
const relatedConfig = {
  layout: 'grid', sections: [{ id: 'nested', title: 'Related form', type: 'form', pageCode: 'fixture', props: { title: '${row.title}' } }]
}

onMounted(() => {
  // Browser test bridge only. It drives the same public methods and input props
  // available to application consumers; no renderer implementation is replaced.
  Object.assign(window, {
    lowcodeFixture: {
      async setVersion(value: string) { version.value = value; await nextTick() },
      async setModel(value: Record<string, unknown>) { model.value = value; await nextTick() },
      async setDisabled(value: boolean) { disabled.value = value; await nextTick() },
      async submit() { await form.value?.submit(); await nextTick() },
      async reset() { form.value?.resetFields(); await nextTick() },
      async clear() { form.value?.clearValidate(); await nextTick() },
      snapshot() { return { data: form.value?.getFormData(), other: other.value?.getFormData(), events: events.value, version: form.value?.rendererVersion } }
    }
  })
})
</script>

<template>
  <main style="max-width: 900px; padding: 24px">
    <TabRenderer v-if="mode === 'consumer-tab'" :config="tabConfig" :preview-configs="previewConfigs" :context-data="{ row: { title: 'nested-value' } }" />
    <RelatedRenderer v-else-if="mode === 'consumer-related'" :config="relatedConfig" :preview-configs="previewConfigs" :context-data="{ row: { title: 'nested-value' } }" />
    <template v-else>
      <section id="primary">
        <Facade ref="form" :config="config" :renderer-version="version" :model-value="model" :disabled="disabled" :component-registry="registryA" :event-handlers="handlers"
          @submit="value => events.push({ type: 'submit', value })"
          @validate-fail="() => events.push({ type: 'invalid', value: true })"
          @field-change="(_field, value) => events.push({ type: 'change', value })"
        />
      </section>
      <section v-if="mode === 'custom'" id="secondary">
        <Facade ref="other" :config="config" renderer-version="v2" :component-registry="registryB" />
      </section>
    </template>
  </main>
</template>
