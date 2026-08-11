<template>
  <state-panel
    :description="textLabel"
    :action-text="actionText"
    @action="handleAction"
  />
</template>

<script>
import StatePanel from '@/components/state-panel/state-panel.vue'

export default {
  name: 'Empty',
  components: { StatePanel },
  props: {
    textLabel: {
      type: String,
      default: '暂无数据'
    },
    actionText: {
      type: String,
      default: ''
    }
  },
  methods: {
    handleAction() {
      this.$emit('action')
    }
  }
}
</script>
