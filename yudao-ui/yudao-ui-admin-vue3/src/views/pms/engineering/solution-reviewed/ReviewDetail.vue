<template>
  <section v-loading="loading" aria-label="待审方案冻结正文">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-else-if="solution" :model="solution" label-position="top">
      <SolutionChapterForm v-model="solution" :read-only="true" />
    </el-form>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import request from '@/config/axios'
import type { SolutionVO } from '@/api/pms/engineering/solution'
import SolutionChapterForm from './SolutionReviewChapterForm.vue'
const props = defineProps<{ id: string }>()
const solution = ref<SolutionVO>(), loading = ref(false), error = ref('')
let sequence = 0
watch(() => props.id, async businessKey => {
  const requestId = ++sequence
  loading.value = true; error.value = ''; solution.value = undefined
  try {
    const result = await request.get({ url: '/api/v1/pms/solution-reviews/source', params: { businessKey } })
    if (requestId === sequence) solution.value = result
  } catch (failure: any) { if (requestId === sequence) error.value = failure?.message || '无法读取待审方案' }
  finally { if (requestId === sequence) loading.value = false }
}, { immediate: true })
</script>
