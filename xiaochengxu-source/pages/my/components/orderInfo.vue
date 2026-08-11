<template>
  <view class="account-links">
    <view class="account-link" @click="$emit('goAddress')">
      <text>收货地址</text>
      <uni-icons type="right" color="#91a0b2" size="18" />
    </view>
    <view class="account-link" @click="$emit('goOrder')">
      <text>订单记录</text>
      <uni-icons type="right" color="#91a0b2" size="18" />
    </view>
  </view>
</template>

<script>
export default {
  name: 'OrderInfo'
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.account-links {
  padding: 0 28rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.account-link {
  display: flex;
  min-height: 104rpx;
  align-items: center;
  justify-content: space-between;
  color: $cm-text;
  border-bottom: 1rpx solid $cm-border;
  font-size: 29rpx;
}

.account-link:last-child {
  border-bottom: 0;
}
</style>
