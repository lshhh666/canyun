<template>
  <view v-if="Object.keys(orderDetailsData).length" class="detail-card dish-card">
    <text class="detail-card__title">{{ orderDetailsData.shopName || '菜品明细' }}</text>

    <view v-for="dish in orderDataes" :key="dish.id" class="dish-row">
      <image class="dish-row__image" mode="aspectFill" :src="dish.image" />
      <view class="dish-row__info">
        <text class="dish-row__name">{{ dish.name }}</text>
        <text v-if="dish.dishFlavor" class="dish-row__flavor">{{ dish.dishFlavor }}</text>
        <text class="dish-row__count">× {{ dish.number || 0 }}</text>
      </view>
      <text class="dish-row__price">￥{{ money(dish.amount) }}</text>
    </view>

    <button
      v-if="(orderDetailsData.orderDetailList || []).length > 2"
      class="dish-toggle"
      @click="$emit('toggle')"
    >{{ showDisplay ? '收起菜品' : '展开全部菜品' }}</button>

    <view class="fee-row"><text>打包费</text><text>￥{{ money(orderDetailsData.packAmount) }}</text></view>
    <view class="fee-row"><text>配送费</text><text>￥{{ money(orderDetailsData.deliveryFee) }}</text></view>
    <view class="fee-row fee-row--total"><text>合计</text><text>￥{{ money(orderDetailsData.amount) }}</text></view>
  </view>
</template>

<script>
export default {
  props: {
    orderDataes: { type: Array, default: () => [] },
    orderDetailsData: { type: Object, default: () => ({}) },
    showDisplay: { type: Boolean, default: false },
  },
  methods: {
    money(amount) {
      const value = Number(amount)
      return Number.isFinite(value) ? value.toFixed(2) : '0.00'
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.detail-card {
  margin-top: 20rpx;
  padding: 28rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.detail-card__title { display: block; margin-bottom: 12rpx; color: $cm-text; font-size: 30rpx; font-weight: 600; }
.dish-row { display: flex; align-items: flex-start; padding: 20rpx 0; border-bottom: 1rpx solid $cm-border; }
.dish-row__image { width: 104rpx; height: 104rpx; flex: none; border-radius: $cm-radius-sm; }
.dish-row__info { overflow: hidden; min-width: 0; flex: 1; margin-left: 18rpx; }
.dish-row__name { display: block; overflow: hidden; color: $cm-text; font-size: 27rpx; text-overflow: ellipsis; white-space: nowrap; }
.dish-row__flavor,
.dish-row__count { display: block; margin-top: 7rpx; color: $cm-text-secondary; font-size: 23rpx; }
.dish-row__price { flex: none; margin-left: 16rpx; color: $cm-text; font-size: 27rpx; font-weight: 600; }
.dish-toggle { height: 66rpx; margin: 10rpx 0; color: $cm-primary; background: transparent; border: 0; font-size: 24rpx; line-height: 66rpx; }
.dish-toggle::after { border: 0; }
.fee-row { display: flex; justify-content: space-between; margin-top: 18rpx; color: $cm-text-secondary; font-size: 25rpx; }
.fee-row text:last-child { color: $cm-text; }
.fee-row--total { margin-top: 24rpx; padding-top: 22rpx; color: $cm-text; border-top: 1rpx solid $cm-border; font-weight: 600; }
.fee-row--total text:last-child { color: $cm-primary; font-size: 31rpx; }
</style>
