<template>
  <span :class="['cm-status', `cm-status--${status}`]">{{ text }}</span>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component
export default class StatusTag extends Vue {
  @Prop({ default: 'neutral' })
  private readonly status!: 'success' | 'warning' | 'danger' | 'info' | 'neutral'

  @Prop({ required: true }) private readonly text!: string
}
</script>

<style lang="scss" scoped>
.cm-status {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
}

.cm-status--success { color: #267b53; background: #e9f6ef; }
.cm-status--warning { color: #a86917; background: #fff2df; }
.cm-status--danger { color: #b84545; background: #fdeaea; }
.cm-status--info { color: #1f6fab; background: #e8f3fc; }
.cm-status--neutral { color: #667587; background: #eef1f4; }
</style>
