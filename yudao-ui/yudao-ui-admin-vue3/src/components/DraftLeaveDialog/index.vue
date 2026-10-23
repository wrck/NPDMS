<template>
  <el-dialog
    :model-value="true" title="草稿保存未完成" width="min(520px, 94vw)" append-to-body align-center
    :close-on-click-modal="false" @update:model-value="emit('decision', 'stay')">
    <p>当前修改或文件上传尚未完成。可以重试保存，或放弃未保存内容后离开。</p>
    <p>已保存数据和已上传文件会保留。</p>
    <template #footer>
      <div class="draft-leave-actions">
        <el-button data-testid="draft-leave-stay" @click="emit('decision', 'stay')">继续编辑</el-button>
        <el-button data-testid="draft-leave-discard" type="danger" plain @click="emit('decision', 'discard')">放弃并离开</el-button>
        <el-button data-testid="draft-leave-retry" type="primary" @click="emit('decision', 'retry')">重试保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import type { DraftLeaveDecision } from '@/hooks/web/useDraftLeaveGuard'
const emit = defineEmits<{ decision: [decision: DraftLeaveDecision] }>()
</script>
<style scoped>
.draft-leave-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.draft-leave-actions .el-button { margin-left: 0; }
</style>
