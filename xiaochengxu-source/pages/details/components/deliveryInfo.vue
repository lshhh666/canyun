<template>
  <view class="info-card">
    <text class="info-card__title">配送信息</text>
    <view class="info-row">
      <text class="info-row__label">期望时间</text>
      <text class="info-row__value">{{ deliveryTime }}</text>
    </view>
    <view class="info-row info-row--address">
      <text class="info-row__label">配送地址</text>
      <view class="info-row__value">
        <text class="address-contact">{{ cryptoName }} {{ orderDetailsData.phone }}</text>
        <text class="address-detail">{{ orderDetailsData.address || '暂无地址信息' }}</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    orderDetailsData: { type: Object, default: () => ({}) },
  },
  computed: {
    deliveryTime() {
      return Number(this.orderDetailsData.deliveryStatus) === 1
        ? '立即送出'
        : (this.orderDetailsData.estimatedDeliveryTime || '以商家通知为准')
    },
    cryptoName() {
      const name = this.orderDetailsData.consignee
      if (!name) return ''
      return `${name.charAt(0)}${Number(this.orderDetailsData.sex) === 0 ? '先生' : '女士'}`
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.info-card { margin-top: 20rpx; padding: 28rpx; background: $cm-surface; border: 1rpx solid $cm-border; border-radius: $cm-radius-md; }
.info-card__title { display: block; margin-bottom: 8rpx; color: $cm-text; font-size: 30rpx; font-weight: 600; }
.info-row { display: flex; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid $cm-border; }
.info-row:last-child { padding-bottom: 0; border-bottom: 0; }
.info-row__label { flex: none; color: $cm-text-secondary; font-size: 25rpx; }
.info-row__value { min-width: 0; margin-left: 40rpx; color: $cm-text; font-size: 25rpx; text-align: right; }
.address-contact,
.address-detail { display: block; line-height: 36rpx; }
.address-detail { margin-top: 6rpx; color: $cm-text-secondary; }
</style>
