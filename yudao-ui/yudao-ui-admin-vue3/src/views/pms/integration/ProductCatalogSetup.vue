<template>
  <el-form label-width="128px" @submit.prevent>
    <el-form-item label="产品编码">
      <el-input
        v-model="codes"
        type="textarea"
        :rows="6"
        placeholder="每行一个原产品编码；只承接填写的产品"
      />
    </el-form-item>
    <el-form-item>
      <el-button :loading="loading" @click="configure">应用产品目录承接配置</el-button>
    </el-form-item>
  </el-form>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import * as api from '@/api/pms/integration'
const props = defineProps<{ modelValue: api.Definition }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: api.Definition): void }>()
const message = useMessage()
const codes = ref('')
const loading = ref(false)
watch(
  () => props.modelValue.sources,
  (sources) => {
    const parameters = sources[0]?.parameters ?? {}
    codes.value = Object.keys(parameters)
      .filter((key) => /^productCode\d+$/.test(key))
      .sort((left, right) => Number(left.slice(11)) - Number(right.slice(11)))
      .map((key) => String(parameters[key]))
      .join('\n')
  },
  { immediate: true }
)
const configure = async () => {
  const selected = codes.value.split(/\r?\n/).filter((code) => code.trim().length > 0)
  if (!selected.length || new Set(selected).size !== selected.length) {
    message.error('请填写产品编码，且不能重复')
    return
  }
  loading.value = true
  try {
    const definition = await api.getDppmsProductTemplate({
      connectionId: props.modelValue.connectionId,
      productCodes: selected
    })
    emit('update:modelValue', definition)
    message.success('目录承接配置已应用，请保存任务并预览')
  } finally {
    loading.value = false
  }
}
</script>
