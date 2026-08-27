<template>
  <view class="ai-chat-page">
    <cloudmeal-header
      title="云小餐"
      subtitle="餐云 AI 客服"
      show-back
      @back="goBack"
    />

    <scroll-view
      class="chat-scroll"
      scroll-y
      :scroll-into-view="scrollIntoView"
      :scroll-with-animation="true"
    >
      <view class="assistant-card">
        <image class="assistant-card__image" src="/static/ai/yunxiaocan-mascot.png" mode="aspectFit" />
        <view class="assistant-card__copy">
          <text class="assistant-card__name">云小餐</text>
          <text class="assistant-card__description">可以咨询菜品、订单和优惠券问题</text>
        </view>
      </view>

      <view class="safety-note">回答仅供咨询，订单与账户操作仍由餐云后端校验。</view>

      <view class="message-list">
        <view
          v-for="item in messages"
          :id="item.id"
          :key="item.id"
          class="message-row"
          :class="`message-row--${item.role}`"
        >
          <image
            v-if="item.role === 'assistant'"
            class="message-avatar"
            src="/static/ai/yunxiaocan-mascot.png"
            mode="aspectFit"
          />
          <view class="message-bubble">{{ item.content }}</view>
        </view>

        <view v-if="sending" id="ai-message-loading" class="message-row message-row--assistant">
          <image class="message-avatar" src="/static/ai/yunxiaocan-mascot.png" mode="aspectFit" />
          <view class="message-bubble typing-bubble">
            <text class="typing-dot">·</text><text class="typing-dot">·</text><text class="typing-dot">·</text>
          </view>
        </view>
      </view>

      <view class="quick-prompts">
        <text class="quick-prompts__title">你可以这样问</text>
        <view class="quick-prompts__list">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            class="quick-prompt"
            :disabled="sending"
            @click="selectPrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </view>
      </view>
      <view class="chat-scroll__spacer" />
    </scroll-view>

    <view class="composer">
      <view class="composer__box">
        <input
          v-model="inputValue"
          class="composer__input"
          type="text"
          maxlength="100"
          confirm-type="send"
          :disabled="sending"
          placeholder="问问云小餐…"
          @confirm="sendMessage"
        />
        <button class="composer__send" :disabled="sending || !canSend" @click="sendMessage">
          {{ sending ? '回答中' : '发送' }}
        </button>
      </view>
      <text class="composer__hint">一次最多输入 100 个字符，请勿发送隐私信息</text>
    </view>
  </view>
</template>

<script>
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import { sendAiChatMessage } from '../api/api.js'

const BUSY_MESSAGE = '云小餐暂时有点忙，请稍后再试。'

export default {
  components: { CloudmealHeader },
  data() {
    return {
      inputValue: '',
      sending: false,
      scrollIntoView: '',
      messageSequence: 1,
      quickPrompts: ['推荐不辣的菜', '怎么取消订单？', '优惠券怎么使用？'],
      messages: [
        {
          id: 'ai-message-0',
          role: 'assistant',
          content: '你好，我是云小餐。今天想了解菜品，还是查询订单和优惠券？'
        }
      ]
    }
  },
  computed: {
    canSend() {
      return this.inputValue.trim().length > 0
    }
  },
  methods: {
    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
        return
      }
      uni.reLaunch({ url: '/pages/index/index' })
    },
    selectPrompt(prompt) {
      if (this.sending) return
      this.inputValue = prompt
    },
    appendMessage(role, content) {
      const id = `ai-message-${this.messageSequence++}`
      this.messages.push({ id, role, content })
      this.$nextTick(() => {
        this.scrollIntoView = id
      })
    },
    async sendMessage() {
      const message = this.inputValue.trim()
      if (!message || this.sending) return

      this.appendMessage('user', message)
      this.inputValue = ''
      this.sending = true
      this.$nextTick(() => {
        this.scrollIntoView = 'ai-message-loading'
      })

      try {
        const result = await sendAiChatMessage(message)
        const answer = result && result.data && typeof result.data.answer === 'string'
          ? result.data.answer.trim()
          : ''
        this.appendMessage('assistant', answer || BUSY_MESSAGE)
      } catch (error) {
        this.appendMessage('assistant', BUSY_MESSAGE)
      } finally {
        this.sending = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.ai-chat-page {
  min-height: 100vh;
  color: #12263f;
  background: #f4f8fc;
}

.chat-scroll {
  position: fixed;
  top: calc(116rpx + var(--status-bar-height));
  right: 0;
  bottom: calc(154rpx + env(safe-area-inset-bottom));
  left: 0;
  box-sizing: border-box;
  padding: 24rpx 24rpx 0;
}

.assistant-card {
  display: flex;
  align-items: center;
  padding: 22rpx;
  background: #ffffff;
  border: 1rpx solid #dbeafa;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(18, 38, 63, 0.06);
}

.assistant-card__image {
  width: 108rpx;
  height: 108rpx;
  margin-right: 20rpx;
  background: #e9f4ff;
  border-radius: 54rpx;
}

.assistant-card__copy {
  display: flex;
  flex-direction: column;
}

.assistant-card__name {
  font-size: 32rpx;
  font-weight: 700;
}

.assistant-card__description {
  margin-top: 8rpx;
  color: #6d7f91;
  font-size: 24rpx;
}

.safety-note {
  margin: 18rpx 12rpx 26rpx;
  color: #7b8c9d;
  font-size: 22rpx;
  line-height: 34rpx;
  text-align: center;
}

.message-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.message-row--user {
  justify-content: flex-end;
}

.message-avatar {
  flex: none;
  width: 64rpx;
  height: 64rpx;
  margin-right: 12rpx;
  background: #e9f4ff;
  border-radius: 32rpx;
}

.message-bubble {
  max-width: 520rpx;
  padding: 20rpx 24rpx;
  background: #ffffff;
  border-radius: 8rpx 22rpx 22rpx 22rpx;
  box-shadow: 0 6rpx 18rpx rgba(18, 38, 63, 0.06);
  font-size: 28rpx;
  line-height: 42rpx;
  word-break: break-all;
}

.message-row--user .message-bubble {
  color: #ffffff;
  background: #147ee8;
  border-radius: 22rpx 8rpx 22rpx 22rpx;
}

.typing-bubble {
  padding: 10rpx 26rpx 18rpx;
  color: #147ee8;
  font-size: 44rpx;
  line-height: 34rpx;
  letter-spacing: 5rpx;
}

.typing-dot:nth-child(2) { opacity: 0.65; }
.typing-dot:nth-child(3) { opacity: 0.35; }

.quick-prompts {
  margin: 34rpx 0 12rpx 76rpx;
}

.quick-prompts__title {
  display: block;
  margin-bottom: 14rpx;
  color: #7b8c9d;
  font-size: 23rpx;
}

.quick-prompts__list {
  display: flex;
  flex-wrap: wrap;
}

.quick-prompt {
  width: auto;
  height: 58rpx;
  margin: 0 12rpx 12rpx 0;
  padding: 0 20rpx;
  color: #147ee8;
  background: #ffffff;
  border: 1rpx solid #bddbfa;
  border-radius: 29rpx;
  font-size: 23rpx;
  line-height: 56rpx;
}

.quick-prompt::after,
.composer__send::after {
  border: 0;
}

.quick-prompt[disabled] {
  color: #9aabba;
  background: #f3f6f9;
  border-color: #dfe7ee;
}

.chat-scroll__spacer {
  height: 32rpx;
}

.composer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  box-sizing: border-box;
  padding: 16rpx 24rpx calc(12rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1rpx solid #e4ebf2;
}

.composer__box {
  display: flex;
  align-items: center;
}

.composer__input {
  flex: 1;
  box-sizing: border-box;
  height: 76rpx;
  padding: 0 24rpx;
  background: #f1f5f9;
  border-radius: 38rpx;
  font-size: 27rpx;
}

.composer__send {
  flex: none;
  width: 124rpx;
  height: 72rpx;
  margin: 0 0 0 14rpx;
  padding: 0;
  color: #ffffff;
  background: #147ee8;
  border-radius: 36rpx;
  font-size: 25rpx;
  font-weight: 600;
  line-height: 72rpx;
}

.composer__send[disabled] {
  color: #a7b6c5;
  background: #e6edf4;
}

.composer__hint {
  display: block;
  margin-top: 8rpx;
  color: #9aa8b6;
  font-size: 19rpx;
  text-align: center;
}
</style>
