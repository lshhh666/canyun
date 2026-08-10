<template>
  <section class="order-tasks cm-surface">
    <header>
      <div>
        <h2>待处理事项</h2>
        <p>优先处理会影响顾客体验的订单</p>
      </div>
      <router-link to="/order">进入订单管理</router-link>
    </header>
    <div class="order-tasks__list">
      <router-link
        v-for="item in taskItems"
        :key="item.label"
        :to="item.to"
        class="order-task"
      >
        <span :class="['order-task__dot', `order-task__dot--${item.level}`]" />
        <div>
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </div>
        <b>{{ item.value }}</b>
        <i class="el-icon-arrow-right" />
      </router-link>
    </div>
    <footer>
      <span>今日全部订单</span>
      <strong>{{ number(orderviewData.allOrders) }}</strong>
    </footer>
  </section>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'

@Component({ name: 'Orderview' })
export default class extends Vue {
  @Prop({ default: () => ({}) }) private readonly orderviewData!: any

  get taskItems() {
    const data = this.orderviewData || {}
    return [
      { label: '待接单', description: '需要尽快确认', value: this.number(data.waitingOrders), to: '/order?status=2', level: 'warning' },
      { label: '待派送', description: '等待开始配送', value: this.number(data.deliveredOrders), to: '/order?status=3', level: 'info' },
      { label: '已取消', description: '建议关注取消原因', value: this.number(data.cancelledOrders), to: '/order?status=6', level: 'danger' },
    ]
  }

  private number(value: any) {
    return Number(value || 0).toLocaleString('zh-CN')
  }
}
</script>

<style lang="scss" scoped>
.order-tasks { padding: $cm-space-5; }
header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 10px; }
h2 { margin: 0 0 4px; color: $cm-text-primary; font-size: 16px; font-weight: 600; }
p { margin: 0; color: $cm-text-secondary; font-size: 12px; }
header a { color: $cm-primary; font-size: 13px; }
.order-task { display: grid; grid-template-columns: 10px minmax(0, 1fr) auto 16px; gap: 12px; align-items: center; min-height: 64px; color: $cm-text-regular; border-bottom: 1px solid $cm-border; }
.order-task:hover strong { color: $cm-primary; }
.order-task__dot { width: 8px; height: 8px; border-radius: 50%; }
.order-task__dot--warning { background: $cm-warning; }
.order-task__dot--info { background: $cm-info; }
.order-task__dot--danger { background: $cm-danger; }
.order-task strong, .order-task small { display: block; }
.order-task strong { color: $cm-text-primary; font-size: 14px; font-weight: 500; }
.order-task small { margin-top: 4px; color: $cm-text-secondary; font-size: 12px; }
.order-task b { color: $cm-text-primary; font-size: 20px; font-weight: 600; }
.order-task i { color: #a3afba; }
footer { display: flex; justify-content: space-between; padding-top: 16px; color: $cm-text-secondary; font-size: 13px; }
footer strong { color: $cm-text-primary; font-weight: 600; }
</style>
