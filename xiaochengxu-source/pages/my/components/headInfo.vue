<template>
  <view class="account-head">
    <image class="account-head__avatar" :src="psersonUrl" mode="aspectFill" />
    <view class="account-head__copy">
      <text class="account-head__eyebrow">个人中心</text>
      <text class="account-head__name">{{ nickName }}</text>
    </view>
  </view>
</template>

<script>
export default {
  name: 'HeadInfo',
  props: {
    psersonUrl: {
      type: String,
      default: '/static/brand/cloudmeal-logo.png'
    },
    nickName: {
      type: String,
      default: '微信用户'
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.account-head {
  display: flex;
  margin: 24rpx;
  padding: 32rpx;
  align-items: center;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
  box-shadow: 0 8rpx 24rpx rgba(18, 38, 63, 0.06);
}

.account-head__avatar {
  width: 112rpx;
  height: 112rpx;
  flex: 0 0 112rpx;
  background: $cm-primary-soft;
  border: 4rpx solid $cm-surface;
  border-radius: 50%;
  box-shadow: 0 0 0 1rpx $cm-border;
}

.account-head__copy {
  min-width: 0;
  margin-left: 28rpx;
}

.account-head__eyebrow,
.account-head__name {
  display: block;
}

.account-head__eyebrow {
  color: $cm-text-secondary;
  font-size: 23rpx;
  line-height: 34rpx;
}

.account-head__name {
  margin-top: 8rpx;
  overflow: hidden;
  color: $cm-text;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 50rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
