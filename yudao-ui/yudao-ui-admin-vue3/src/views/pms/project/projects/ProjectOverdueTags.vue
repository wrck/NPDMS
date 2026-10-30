<template>
  <el-space wrap>
    <el-tooltip v-if="work.length" :content="describe(work, 'planEndTime')">
      <el-tag type="danger" size="small">工期超期</el-tag>
    </el-tooltip>
    <el-tooltip v-if="acceptance.length" :content="describe(acceptance, 'acceptanceTime')">
      <el-tag type="warning" size="small">验收超期</el-tag>
    </el-tooltip>
    <el-tooltip v-if="history.length" :content="describe(history, 'completedAt')">
      <el-tag type="info" size="small">历史超期（已完成）</el-tag>
    </el-tooltip>
    <span v-if="!work.length && !acceptance.length && !history.length">—</span>
  </el-space>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDate } from '@/utils/formatTime'
import type { ProjectMasterVO, ProjectOverdueNode } from '@/api/pms/project/projects'

const props = defineProps<{ project: ProjectMasterVO }>()
const work = computed(() => (props.project.overdueNodes || []).filter(node => node.workOverdue))
const acceptance = computed(() => (props.project.overdueNodes || []).filter(node => node.acceptanceOverdue))
const history = computed(() => props.project.completedOverdueNodes || [])
const describe = (nodes: ProjectOverdueNode[], field: 'planEndTime' | 'acceptanceTime' | 'completedAt') =>
  nodes.map(node => `${node.nodeKind === 'STAGE' ? '阶段' : '任务'}：${node.nodeName || node.nodeId}（${
    node[field] == null ? '日期未记录' : formatDate(new Date(node[field]!), 'YYYY-MM-DD')
  }）`).join('；')
</script>
