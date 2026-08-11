<template>
  <view class="status-card">
    <view class="status-card__copy">
      <text class="status-card__title">{{ statusWord(orderDetailsData.status) }}</text>
      <text v-if="timeout && orderDetailsData.status === 1" class="status-card__hint">订单已超时</text>
      <text v-else-if="orderDetailsData.status === 1" class="status-card__hint">
        请在 {{ rocallTime }} 内完成支付
      </text>
      <text v-else-if="orderDetailsData.status === 7" class="status-card__hint">
        {{ cancellationReason }}
      </text>
    </view>

    <view v-if="showActions" class="status-actions">
      <button
        v-if="canCancel"
        class="status-action status-action--secondary"
        @click="handleCancel('center', orderDetailsData)"
      >取消订单</button>
      <button
        v-if="hasAction('pay')"
        class="status-action status-action--primary"
        @click="handlePay(orderDetailsData.id)"
      >继续支付</button>
      <button
        v-if="hasAction('reminder')"
        class="status-action status-action--primary"
        @click="handleReminder('center', orderDetailsData.id)"
      >催单</button>
      <button
        v-if="canRefund"
        class="status-action status-action--danger"
        @click="handleRefund('center')"
      >申请退款</button>
      <button
        v-if="hasAction('repeat')"
        class="status-action status-action--primary"
        @click="oneMoreOrder(orderDetailsData.id)"
      >再来一单</button>
    </view>
  </view>
</template>

<script>
import { statusWord } from '@/utils/index.js'
import { getOrderActions } from '@/utils/order-segments.js'

export default {
  props: {
    orderDetailsData: {
      type: Object,
      default: () => ({}),
    },
    timeout: {
      type: Boolean,
      default: false,
    },
    rocallTime: {
      type: String,
      default: '',
    },
  },
  computed: {
    actions() {
      return getOrderActions(this.orderDetailsData.status, { timeout: this.timeout })
    },
    canCancel() {
      return !this.timeout && [1, 2].includes(Number(this.orderDetailsData.status))
    },
    canRefund() {
      return Number(this.orderDetailsData.status) === 5
    },
    showActions() {
      return this.actions.length > 0 || this.canCancel || this.canRefund
    },
    cancellationReason() {
      if ([1, 2].includes(Number(this.orderDetailsData.payStatus))) return '退款成功'
      return this.orderDetailsData.cancelReason || this.orderDetailsData.rejectionReason || '订单已取消'
    },
  },
  methods: {
    statusWord(status) {
      this.$emit('statusWord', status)
      return statusWord(status)
    },
    paymentTime(value) {
      this.$emit('paymentTime', value)
    },
    hasAction(action) {
      return this.actions.includes(action)
    },
    handleCancel(type, obj) {
      this.$emit('handleCancel', { type, obj })
    },
    handlePay(id) {
      this.$emit('handlePay', id)
    },
    handleReminder(type, id) {
      this.$emit('handleReminder', { type, id })
    },
    handleRefund(type) {
      this.$emit('handleRefund', type)
    },
    oneMoreOrder(id) {
      this.$emit('oneMoreOrder', id)
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.status-card {
  padding: 30rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.status-card__title {
  display: block;
  color: $cm-text;
  font-size: 34rpx;
  font-weight: 700;
  line-height: 48rpx;
}

.status-card__hint {
  display: block;
  margin-top: 10rpx;
  color: $cm-text-secondary;
  font-size: 24rpx;
  line-height: 36rpx;
}

.status-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 26rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid $cm-border;
}

.status-action {
  min-width: 172rpx;
  height: 68rpx;
  margin: 0 0 0 16rpx;
  padding: 0 22rpx;
  border-radius: $cm-radius-sm;
  font-size: 26rpx;
  line-height: 68rpx;
}

.status-action::after { border: 0; }
.status-action--primary { color: $cm-surface; background: $cm-primary; }
.status-action--secondary { color: $cm-text-secondary; background: $cm-surface; border: 1rpx solid $cm-border; }
.status-action--danger { color: $cm-danger; background: $cm-surface; border: 1rpx solid rgba(217, 75, 75, 0.45); }
</style>
