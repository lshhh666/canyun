<template>
  <view class="cloudmeal-header">
    <uni-status-bar />
    <view class="cloudmeal-header__bar">
      <view
        v-if="showBack"
        class="cloudmeal-header__back"
        role="button"
        aria-label="返回"
        @tap="$emit('back')"
      >
        <uni-icons type="back" color="#12263f" size="24" />
      </view>
      <image
        v-else
        class="cloudmeal-header__logo"
        src="/static/brand/cloudmeal-logo.png"
        mode="aspectFit"
      />

      <view class="cloudmeal-header__content">
        <view class="cloudmeal-header__title-row">
          <text v-if="title" class="cloudmeal-header__title">{{ title }}</text>
          <text v-if="status" class="cloudmeal-header__status">{{ status }}</text>
        </view>
        <text v-if="subtitle" class="cloudmeal-header__subtitle">{{ subtitle }}</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'CloudmealHeader',
  props: {
    title: {
      type: String,
      default: ''
    },
    showBack: {
      type: Boolean,
      default: false
    },
    status: {
      type: String,
      default: ''
    },
    subtitle: {
      type: String,
      default: ''
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.cloudmeal-header {
  background: $cm-surface;
  border-bottom: 1rpx solid $cm-border;
}

.cloudmeal-header__bar {
  display: flex;
  min-height: 96rpx;
  padding: 12rpx 32rpx;
  align-items: center;
  box-sizing: border-box;
}

.cloudmeal-header__logo,
.cloudmeal-header__back {
  width: 64rpx;
  height: 64rpx;
  flex: 0 0 64rpx;
}

.cloudmeal-header__back {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.cloudmeal-header__content {
  min-width: 0;
  margin-left: 20rpx;
}

.cloudmeal-header__title-row {
  display: flex;
  align-items: center;
}

.cloudmeal-header__title {
  color: $cm-text;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.cloudmeal-header__status {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  color: $cm-success;
  background: $cm-primary-soft;
  border-radius: $cm-radius-sm;
  font-size: 22rpx;
  line-height: 32rpx;
}

.cloudmeal-header__subtitle {
  display: block;
  margin-top: 2rpx;
  color: $cm-text-secondary;
  font-size: 24rpx;
  line-height: 34rpx;
}
</style>
