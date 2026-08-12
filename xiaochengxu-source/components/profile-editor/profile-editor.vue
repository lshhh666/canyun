<template>
  <view class="profile-editor" @touchmove.stop.prevent>
    <view class="profile-editor__mask" @click="close" />
    <view class="profile-editor__sheet">
      <view class="profile-editor__header">
        <view>
          <text class="profile-editor__title">完善个人资料</text>
          <text class="profile-editor__description">用于展示订单中的用户信息</text>
        </view>
        <button class="profile-editor__close" @click="close">×</button>
      </view>

      <button
        class="profile-editor__avatar-button"
        open-type="chooseAvatar"
        @chooseavatar="handleChooseAvatar"
      >
        <image
          class="profile-editor__avatar"
          :src="tempAvatarPath || currentAvatar || defaultAvatar"
          mode="aspectFill"
        />
        <text>选择头像</text>
      </button>

      <view class="profile-editor__field">
        <text class="profile-editor__label">昵称</text>
        <input
          class="profile-editor__input"
          type="nickname"
          :value="name"
          maxlength="32"
          placeholder="请输入昵称"
          @input="handleNicknameInput"
        />
      </view>

      <button class="profile-editor__save" :disabled="saving" @click="submit">
        {{ saving ? '保存中…' : '保存' }}
      </button>
      <button v-if="allowSkip" class="profile-editor__skip" :disabled="saving" @click="skip">
        暂时跳过
      </button>
    </view>
  </view>
</template>

<script>
export default {
  name: 'ProfileEditor',
  props: {
    profile: {
      type: Object,
      default: () => ({})
    },
    allowSkip: {
      type: Boolean,
      default: false
    },
    saving: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      name: '',
      currentAvatar: '',
      tempAvatarPath: '',
      defaultAvatar: '/static/brand/cloudmeal-logo.png'
    }
  },
  watch: {
    profile: {
      deep: true,
      handler(value) {
        this.reset(value)
      }
    }
  },
  created() {
    this.reset(this.profile)
  },
  methods: {
    reset(profile = {}) {
      this.name = profile.nickName || ''
      this.currentAvatar = profile.avatarUrl || ''
      this.tempAvatarPath = ''
    },
    handleChooseAvatar(event) {
      this.tempAvatarPath = event && event.detail ? event.detail.avatarUrl || '' : ''
    },
    handleNicknameInput(event) {
      this.name = event && event.detail ? event.detail.value || '' : ''
    },
    submit() {
      this.$emit('save', {
        name: this.name.trim(),
        tempAvatarPath: this.tempAvatarPath,
        currentAvatar: this.currentAvatar
      })
    },
    skip() {
      if (this.allowSkip && !this.saving) this.$emit('skip')
    },
    close() {
      if (!this.saving) this.$emit('close')
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.profile-editor {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: flex;
  align-items: flex-end;
}

.profile-editor__mask {
  position: absolute;
  inset: 0;
  background: rgba(18, 38, 63, 0.42);
}

.profile-editor__sheet {
  position: relative;
  width: 100%;
  padding: 36rpx 32rpx calc(28rpx + #{$cm-safe-bottom});
  background: $cm-surface;
  border-radius: 28rpx 28rpx 0 0;
  box-sizing: border-box;
  box-shadow: $cm-shadow-float;
}

.profile-editor__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.profile-editor__title,
.profile-editor__description {
  display: block;
}

.profile-editor__title {
  color: $cm-text;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.profile-editor__description {
  margin-top: 6rpx;
  color: $cm-text-secondary;
  font-size: 24rpx;
  line-height: 36rpx;
}

.profile-editor__close {
  width: 64rpx;
  height: 64rpx;
  margin: -12rpx -12rpx 0 16rpx;
  padding: 0;
  color: $cm-text-secondary;
  background: transparent;
  font-size: 42rpx;
  font-weight: 400;
  line-height: 60rpx;
}

.profile-editor__close::after,
.profile-editor__avatar-button::after,
.profile-editor__save::after,
.profile-editor__skip::after {
  border: 0;
}

.profile-editor__avatar-button {
  display: flex;
  margin: 36rpx auto 28rpx;
  padding: 0;
  align-items: center;
  color: $cm-primary;
  background: transparent;
  font-size: 26rpx;
  line-height: 40rpx;
}

.profile-editor__avatar {
  width: 104rpx;
  height: 104rpx;
  margin-right: 20rpx;
  background: $cm-primary-soft;
  border: 2rpx solid $cm-border;
  border-radius: 50%;
}

.profile-editor__field {
  padding: 22rpx 24rpx;
  background: $cm-page;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.profile-editor__label {
  display: block;
  color: $cm-text-secondary;
  font-size: 23rpx;
  line-height: 34rpx;
}

.profile-editor__input {
  height: 48rpx;
  margin-top: 8rpx;
  color: $cm-text;
  font-size: 30rpx;
  line-height: 48rpx;
}

.profile-editor__save {
  margin-top: 28rpx;
  color: $cm-surface;
  background: $cm-primary;
  border-radius: $cm-radius-md;
  font-size: 29rpx;
  font-weight: 600;
}

.profile-editor__save[disabled] {
  opacity: 0.6;
}

.profile-editor__skip {
  margin-top: 12rpx;
  color: $cm-text-secondary;
  background: transparent;
  font-size: 25rpx;
}
</style>
