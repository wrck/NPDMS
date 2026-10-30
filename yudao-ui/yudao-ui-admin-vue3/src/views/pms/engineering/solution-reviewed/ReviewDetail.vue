<template>
  <section v-loading="loading" aria-label="待审方案冻结正文">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-else-if="solution" :model="solution" label-position="top">
      <SolutionDocMetaForm v-model="solution" :read-only="true" />
      <SolutionChapterForm v-model="solution" :read-only="true" :review-context="true" :review-requirement="materials?.requirement" />
      <div v-if="materials?.attachments.length" aria-label="本次审批冻结附件">
        <p>本次审批附件</p>
        <el-link v-for="file in materials.attachments" :key="file.referenceId" type="primary" class="mr-16px" @click="downloadAttachment(file)">{{ file.name }}</el-link>
      </div>
      <el-alert v-if="attachmentError" :title="attachmentError" type="error" :closable="false" />
    </el-form>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import request from '@/config/axios'
import type { SolutionVO } from '@/api/pms/engineering/solution'
import SolutionDocMetaForm from './SolutionDocMetaForm.vue'
import SolutionChapterForm from './SolutionReviewChapterForm.vue'
const props = defineProps<{ id: string }>()
const solution = ref<SolutionVO>(), loading = ref(false), error = ref('')
interface Attachment { referenceId: number; name: string }
interface Materials { requirement: Record<string, unknown>; attachments: Attachment[] }
const materials = ref<Materials>(), attachmentError = ref('')
const downloadAttachment = async (file: Attachment) => {
  const businessKey = props.id
  attachmentError.value = ''
  try {
    const blob = await request.download<Blob>({ url: '/api/v1/pms/solution-reviews/attachment', params: { businessKey, referenceId: file.referenceId } })
    if (businessKey !== props.id) return
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.name; anchor.click()
    URL.revokeObjectURL(url)
  } catch (failure: any) { if (businessKey === props.id) attachmentError.value = failure?.message || '无法读取本次审批附件' }
}
let sequence = 0
watch(() => props.id, async businessKey => {
  const requestId = ++sequence
  loading.value = true; error.value = ''; solution.value = undefined; materials.value = undefined; attachmentError.value = ''
  try {
    const [result, frozen] = await Promise.all([
      request.get({ url: '/api/v1/pms/solution-reviews/source', params: { businessKey } }),
      request.get<Materials>({ url: '/api/v1/pms/solution-reviews/materials', params: { businessKey } })
    ])
    if (requestId === sequence) { materials.value = frozen; solution.value = result }
  } catch (failure: any) { if (requestId === sequence) error.value = failure?.message || '无法读取待审方案' }
  finally { if (requestId === sequence) loading.value = false }
}, { immediate: true })
</script>
