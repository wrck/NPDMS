<template>
  <main>
    <header>
      <h1>需求分析 · Demo V2</h1>
      <p>内容渲染测试：真实 FormCreate / Element Plus；富文本及附件为测试占位，不连接业务系统。</p>
      <el-switch v-model="readonly" active-text="只读预览" inactive-text="填写验证" />
      <el-button @click="validate">校验表单</el-button>
      <span role="status" data-testid="validation">{{ validation }}</span>
    </header>
    <form-create v-model="values" v-model:api="api" :rule="rule" :option="option" />
    <details>
      <summary>本地表单值（不保存到服务端）</summary>
      <pre data-testid="values">{{ JSON.stringify(values, null, 2) }}</pre>
    </details>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import template from '../demo-template.json'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
const decoded = decodeDynamicForm(template.formConfJson, template.formRulesJson)
const rule = ref(decoded.rule)
const values = ref<Record<string, unknown>>({})
const api = ref<{ validate: () => Promise<boolean> }>()
const readonly = ref(false)
const validation = ref('尚未校验')
const option = computed(() => ({
  ...decoded.option,
  form: { ...template.formConfJson.form, disabled: readonly.value },
  global: { Editor: { props: { readonly: readonly.value } } }
}))
const validate = async () => {
  try {
    validation.value = (await api.value?.validate()) ? '校验通过' : '必填项未完成'
  } catch {
    validation.value = '必填项未完成'
  }
}
</script>

<style>
body {
  margin: 0;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  font-family: sans-serif;
}
main {
  max-width: 960px;
  margin: auto;
  padding: 24px;
  background: var(--el-bg-color);
}
header {
  margin-bottom: 24px;
}
h1 {
  font-size: 24px;
}
p {
  line-height: 1.6;
}
textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}
.file-fixture {
  margin: 0;
  color: var(--el-text-color-secondary);
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.el-checkbox-group {
  display: flex;
  flex-wrap: wrap;
}
@media (max-width: 767px) {
  main {
    padding: 12px;
  }
}
</style>
