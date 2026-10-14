<template>
  <ContentWrap>
    <el-button link class="mb-12px" @click="close"><Icon icon="ep:back" />返回割接任务</el-button>
    <CutoverApprovalForm v-if="approvalId" :key="approvalId" :id="approvalId" />
    <el-result v-else icon="warning" title="审批实例编号无效" />
  </ContentWrap>
</template>
<script setup lang="ts">
import CutoverApprovalForm from './index.vue'
defineOptions({ name: 'PmsCutoverApprovalDetail' })
const route = useRoute()
const router = useRouter()
const approvalId = computed(() => typeof route.params.approvalId === 'string' && /^[1-9]\d*$/.test(route.params.approvalId) ? route.params.approvalId : null)
const close = () => {
  const previous = router.options.history.state.back
  if (typeof previous === 'string' && previous.startsWith('/pms/cutover/')) router.back()
  else void router.push('/pms/cutover/workbench')
}
</script>
