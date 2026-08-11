<template>
  <view class="success-page">
    <view class="success-mark">✓</view>
    <text class="success-title">支付成功</text>
    <text class="success-description">订单已提交给商家，请留意配送进度</text>

    <view class="success-actions">
      <button class="success-button success-button--secondary" @click="goOrder">查看订单</button>
      <button class="success-button success-button--primary" @click="goIndex">返回点餐</button>
    </view>
  </view>
</template>

<script>
export default {
  methods: {
    goOrder() {
      uni.reLaunch({ url: '/pages/historyOrder/historyOrder' })
    },
    goIndex() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.success-page {
  display: flex;
  min-height: 100vh;
  padding: 220rpx 48rpx calc(48rpx + env(safe-area-inset-bottom));
  align-items: center;
  flex-direction: column;
  color: $cm-text;
  background: $cm-page;
  box-sizing: border-box;
}

.success-mark {
  width: 128rpx;
  height: 128rpx;
  color: #fff;
  background: $cm-primary;
  border-radius: 50%;
  font-size: 70rpx;
  font-weight: 700;
  line-height: 128rpx;
  text-align: center;
}

.success-title { margin-top: 34rpx; color: $cm-text; font-size: 40rpx; font-weight: 700; }
.success-description { margin-top: 16rpx; color: $cm-text-secondary; font-size: 26rpx; line-height: 40rpx; text-align: center; }
.success-actions { display: flex; width: 100%; margin-top: 72rpx; gap: 20rpx; }
.success-button { height: 82rpx; flex: 1; margin: 0; border-radius: $cm-radius-sm; font-size: 28rpx; font-weight: 600; line-height: 82rpx; }
.success-button::after { border: 0; }
.success-button--secondary { color: $cm-primary; background: $cm-surface; border: 1rpx solid $cm-primary; }
.success-button--primary { color: #fff; background: $cm-primary; }
</style>
