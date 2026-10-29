<template><SystemUserSelect v-bind="$attrs" presentation="form-create" /></template>
<script setup lang="ts">
import SystemUserSelect from './index.vue'
defineOptions({ name: 'UserSelect', inheritAttrs: false })
</script>
