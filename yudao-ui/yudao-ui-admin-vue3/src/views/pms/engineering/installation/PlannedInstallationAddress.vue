<template>
  <div>
    <el-button :loading="loading" @click="load">查询方案部署位置</el-button>
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-if="plan">
      <p>来源：{{ plan.solutionName }} · V{{ plan.solutionVersion }}</p>
      <p v-if="candidates.length === 1">{{ candidates[0] }}</p>
      <el-select v-else-if="candidates.length > 1" v-model="selected" placeholder="请选择方案部署位置">
        <el-option v-for="location in candidates" :key="location" :label="location" :value="location" />
      </el-select>
      <p v-else>所选设备没有共同的方案部署位置，请按实际情况填写。</p>
      <el-button v-if="chosen" type="primary" plain @click="emit('select', chosen)">使用此地址</el-button>
    </template>
  </div>
</template>
<script setup lang="ts">
import { getPlannedInstallationAddresses, type InstallationPlannedAddresses } from '@/api/pms/engineering/installation'
import { plannedAddressCandidates } from './plannedInstallationAddress'
const props = defineProps<{ projectId: number; productCodes: Array<string | undefined> }>()
const emit = defineEmits<{ select: [location: string] }>()
const plan = ref<InstallationPlannedAddresses | null>(null)
const loading = ref(false)
const error = ref('')
const selected = ref<string>()
const candidates = computed(() => plan.value ? plannedAddressCandidates(plan.value, props.productCodes) : [])
const chosen = computed(() => candidates.value.length === 1 ? candidates.value[0] : selected.value)
let generation = 0
onBeforeUnmount(() => { generation++ })
watch(() => [props.projectId, props.productCodes], () => { generation++; plan.value = null; selected.value = undefined; error.value = ''; loading.value = false }, { deep: true })
const load = async () => {
  const current = ++generation
  loading.value = true
  plan.value = null
  selected.value = undefined
  error.value = ''
  try {
    const result = await getPlannedInstallationAddresses(props.projectId)
    if (current !== generation) return
    plan.value = result
    if (!result) error.value = '本项目尚无最终批准的实施方案，请按实际情况填写。'
  } catch { if (current === generation) error.value = '方案部署位置读取失败，请重试。' }
  finally { if (current === generation) loading.value = false }
}
</script>
