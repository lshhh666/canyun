<template>
  <view class="info-card">
    <text class="info-card__title">订单信息</text>
    <view class="info-row"><text>订单号码</text><text>{{ orderDetailsData.number || '-' }}</text></view>
    <view class="info-row"><text>订单时间</text><text>{{ orderDetailsData.orderTime || '-' }}</text></view>
    <view class="info-row"><text>支付方式</text><text>{{ paymentMethod }}</text></view>
    <view class="info-row"><text>订单备注</text><text>{{ orderDetailsData.remark || '暂无信息' }}</text></view>
    <view class="info-row"><text>餐具数量</text><text>{{ tablewareText }}</text></view>
  </view>
</template>

<script>
export default {
  props: {
    orderDetailsData: { type: Object, default: () => ({}) },
  },
  computed: {
    paymentMethod() {
      if (Number(this.orderDetailsData.payMethod) === 1) return '微信支付'
      if (Number(this.orderDetailsData.payMethod) === 2) return '支付宝'
      return '未支付'
    },
    tablewareText() {
      const number = Number(this.orderDetailsData.tablewareNumber || 0)
      return number > 0 ? `${number} 份` : '无需餐具'
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.info-card { margin-top: 20rpx; padding: 28rpx; background: $cm-surface; border: 1rpx solid $cm-border; border-radius: $cm-radius-md; }
.info-card__title { display: block; margin-bottom: 8rpx; color: $cm-text; font-size: 30rpx; font-weight: 600; }
.info-row { display: flex; justify-content: space-between; padding: 20rpx 0; color: $cm-text-secondary; border-bottom: 1rpx solid $cm-border; font-size: 25rpx; }
.info-row:last-child { padding-bottom: 0; border-bottom: 0; }
.info-row text:last-child { overflow-wrap: anywhere; max-width: 440rpx; margin-left: 30rpx; color: $cm-text; text-align: right; }
</style>
