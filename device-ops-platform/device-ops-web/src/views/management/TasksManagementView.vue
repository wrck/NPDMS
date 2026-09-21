<script setup lang="ts">
import '@/styles/management.css'
import { ref } from 'vue'
import AppShell from '@/components/AppShell.vue'
import CollectionList from '@/components/management/CollectionList.vue'
import ParseTasksPanel from '@/components/management/ParseTasksPanel.vue'
import SchedulesPanel from '@/components/management/SchedulesPanel.vue'
const tab = ref('collections')
</script>
<template>
  <AppShell
    class="management-page"
    element-layout
    title="任务中心"
    subtitle="采集、解析与到期通知分开管理"
  >
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane
          label="采集任务"
          name="collections"
        >
          <CollectionList v-if="tab === 'collections'" />
        </el-tab-pane>
        <el-tab-pane
          label="解析任务"
          name="parser"
        >
          <ParseTasksPanel v-if="tab === 'parser'" />
        </el-tab-pane>
        <el-tab-pane
          label="调度通知"
          name="schedules"
        >
          <SchedulesPanel v-if="tab === 'schedules'" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </AppShell>
</template>
