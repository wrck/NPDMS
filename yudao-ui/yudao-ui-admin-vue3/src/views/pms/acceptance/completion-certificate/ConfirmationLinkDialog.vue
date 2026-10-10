<template>
  <Dialog v-model="visible" title="推送至客户" width="min(640px, 94vw)" @closed="result = undefined">
    <el-alert title="完工证明与满意度调查表已一起外发，请将链接或二维码发送给客户。" type="success" :closable="false" />
    <div v-if="url" class="confirmation-qr">
      <Qrcode :text="url" :width="200" @done="value => qrImage = value" />
      <p>客户核对两份表单后一次签字统一提交</p>
      <a v-if="qrImage" :href="qrImage" download="完工及满意度确认二维码.png">保存二维码</a>
    </div>
    <el-form label-position="top"><el-form-item label="客户可访问的系统地址"><el-input v-model="baseUrl" /></el-form-item></el-form>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="确认链接"><el-link v-if="url" :href="url" target="_blank">{{ url }}</el-link><span v-else>请输入有效HTTP(S)地址</span></el-descriptions-item>
      <el-descriptions-item label="有效期">{{ formatDate(result?.tokenExpiresAt) }}</el-descriptions-item>
    </el-descriptions>
    <template #footer><el-button :disabled="!url" @click="copy">复制链接</el-button><el-button type="primary" @click="visible = false">关闭</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import type { CompletionIssueVO } from '@/api/pms/acceptance/completion-certificate'
import { Qrcode } from '@/components/Qrcode'
import { getTenantId } from '@/utils/auth'
import { formatDate } from '@/utils/formatTime'
const visible = ref(false), result = ref<CompletionIssueVO>(), qrImage = ref('')
const baseUrl = ref(import.meta.env.VITE_CUSTOMER_CONFIRM_BASE_URL || window.location.origin)
const message = useMessage()
const url = computed(() => {
  if (!result.value) return ''
  try { const base = new URL(baseUrl.value); if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password) return ''; return `${base.origin}${result.value.signPath}?tenantId=${getTenantId() ?? 0}` } catch { return '' }
})
const open = (issued: CompletionIssueVO) => { result.value = issued; qrImage.value = ''; visible.value = true }
const close = () => { visible.value = false; result.value = undefined; qrImage.value = '' }
const copy = async () => { await navigator.clipboard.writeText(url.value); message.success('链接已复制') }
defineExpose({ open, close })
</script>
<style scoped>
.confirmation-qr { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px 0; }
.confirmation-qr p { margin: 0; }
</style>
