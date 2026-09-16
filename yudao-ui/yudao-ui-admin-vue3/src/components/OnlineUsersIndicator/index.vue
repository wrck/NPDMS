<!-- src/components/OnlineUsersIndicator/index.vue -->
<script setup lang="ts">
/**
 * 在线用户指示器（批次5-T6）。
 *
 * <p>显示当前配置的在线协同用户头像列表，鼠标悬停显示用户名。
 * 当前用户高亮显示。</p>
 */
import { computed } from 'vue'
import type { OnlineUser } from '@/api/lowcode-collaboration'

interface Props {
  users: OnlineUser[]
  currentUserId?: number
}

const props = withDefaults(defineProps<Props>(), {
  currentUserId: 0
})

const displayUsers = computed(() => props.users.slice(0, 8))
const extraCount = computed(() => Math.max(0, props.users.length - 8))

function initials(name?: string): string {
  if (!name) return '?'
  return name.slice(0, 2).toUpperCase()
}

function avatarColor(userId?: number): string {
  if (!userId) return '#909399'
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9c27b0', '#00bcd4', '#ff9800']
  return colors[userId % colors.length]
}
</script>

<template>
  <div class="online-users-indicator">
    <el-tooltip
      v-for="u in displayUsers"
      :key="u.userId"
      :content="`${u.userName}${u.userId === currentUserId ? ' (我)' : ''}`"
      placement="bottom"
    >
      <div
        class="user-avatar"
        :class="{ 'is-current': u.userId === currentUserId }"
        :style="{ backgroundColor: avatarColor(u.userId) }"
      >
        {{ initials(u.userName) }}
      </div>
    </el-tooltip>
    <el-tooltip v-if="extraCount > 0" :content="`还有 ${extraCount} 位用户`" placement="bottom">
      <div class="user-avatar extra">+{{ extraCount }}</div>
    </el-tooltip>
    <span v-if="users.length === 0" class="no-users">无人在线</span>
  </div>
</template>

<style scoped lang="scss">
.online-users-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px var(--el-border-color);

  &.is-current {
    border-color: var(--el-color-success);
    box-shadow: 0 0 0 2px var(--el-color-success);
  }

  &.extra {
    background-color: var(--el-fill-color-dark);
    color: var(--el-text-color-regular);
  }
}

.no-users {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
