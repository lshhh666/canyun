<template>
  <view class="ai-chat-page">
    <cloudmeal-header
      title="云小餐"
      subtitle="餐云 AI 客服"
      show-back
      @back="goBack"
    />

    <view class="conversation-tools">
      <text class="conversation-tools__hint">仅展示最近对话</text>
      <button
        class="conversation-tools__new"
        :disabled="sending || historyLoading"
        @click="startNewConversation"
      >
        新对话
      </button>
    </view>

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
          <view class="message-content">
            <view class="message-bubble">{{ item.content }}</view>
            <button
              v-if="item.role === 'assistant' && item.action && !item.action.consumed"
              class="message-action"
              :disabled="sending || historyLoading || item.action.confirming"
              @click="confirmPendingAction(item)"
            >
              {{ item.action.confirming ? '处理中' : item.action.label }}
            </button>
          </view>
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
            :disabled="sending || historyLoading"
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
          :disabled="sending || historyLoading"
          placeholder="问问云小餐…"
          @confirm="sendMessage"
        />
        <button class="composer__send" :disabled="sending || historyLoading || !canSend" @click="sendMessage">
          {{ sending ? '回答中' : '发送' }}
        </button>
      </view>
      <text class="composer__hint">一次最多输入 100 个字符，请勿发送隐私信息</text>
    </view>
  </view>
</template>

<script>
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import {
  sendAiChatMessage,
  confirmAiChatAction,
  getRecentAiChatHistory,
  closeAiChatSession,
  submitAiChatFeedback
} from '../api/api.js'

const BUSY_MESSAGE = '云小餐暂时有点忙，请稍后再试。'
const FEEDBACK_OPTIONS = ['HELPFUL', 'UNSOLVED', null]
const WELCOME_MESSAGE = {
  id: 'ai-message-0',
  role: 'assistant',
  content: '你好，我是云小餐。今天想了解菜品，还是查询订单和优惠券？'
}

export default {
  components: { CloudmealHeader },
  data() {
    return {
      inputValue: '',
      sending: false,
      historyLoading: false,
      historyTimer: null,
      sessionId: null,
      scrollIntoView: '',
      messageSequence: 1,
      quickPrompts: ['推荐不辣的菜', '怎么取消订单？', '优惠券怎么使用？'],
      messages: [{ ...WELCOME_MESSAGE }]
    }
  },
  computed: {
    canSend() {
      return this.inputValue.trim().length > 0
    }
  },
  onLoad() {
    this.loadRecentHistory()
  },
  onUnload() {
    this.clearHistoryTimer()
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
      if (this.sending || this.historyLoading) return
      this.inputValue = prompt
    },
    resetConversation() {
      this.clearHistoryTimer()
      this.inputValue = ''
      this.sending = false
      this.sessionId = null
      this.messageSequence = 1
      this.messages = [{ ...WELCOME_MESSAGE }]
      this.$nextTick(() => {
        this.scrollIntoView = WELCOME_MESSAGE.id
      })
    },
    async startNewConversation() {
      if (this.sending || this.historyLoading) return
      if (this.sessionId == null) {
        this.resetConversation()
        return
      }

      this.clearHistoryTimer()
      this.historyLoading = true
      try {
        const feedbackResult = await this.chooseSessionFeedback()
        if (feedbackResult === undefined) return

        const closedSessionId = this.sessionId
        await closeAiChatSession(closedSessionId)
        this.resetConversation()
        // 关闭成功后立即解锁新会话；评价走独立请求，不等待它返回。
        this.historyLoading = false
        this.submitSessionFeedbackInBackground(closedSessionId, feedbackResult)
        if (uni.showToast) uni.showToast({ title: '已开始新对话', icon: 'none' })
      } catch (error) {
        if (uni.showToast) uni.showToast({ title: BUSY_MESSAGE, icon: 'none' })
      } finally {
        this.historyLoading = false
      }
    },
    chooseSessionFeedback() {
      if (!uni.showActionSheet) return Promise.resolve(null)
      return new Promise(resolve => {
        uni.showActionSheet({
          title: '这次对话解决了你的问题吗？',
          itemList: ['有帮助', '没解决', '暂不评价'],
          success: result => {
            const index = result && result.tapIndex
            resolve(Number.isInteger(index) ? FEEDBACK_OPTIONS[index] : undefined)
          },
          fail: () => resolve(undefined)
        })
      })
    },
    submitSessionFeedbackInBackground(sessionId, result) {
      if (!result) return
      submitAiChatFeedback(sessionId, result).catch(() => {
        // 这里只提示评价结果，不改变已经结束的会话，也不锁住新会话输入。
        if (uni.showToast) uni.showToast({ title: '评价暂未提交', icon: 'none' })
      })
    },
    normalizeAction(action) {
      return action && Number.isSafeInteger(action.actionId)
        && action.actionId > 0 && typeof action.label === 'string'
        ? { ...action, consumed: false, confirming: false }
        : null
    },
    appendMessage(role, content, action = null) {
      const id = `ai-message-${this.messageSequence++}`
      const safeAction = this.normalizeAction(action)
      this.messages.push({ id, role, content, action: safeAction })
      this.$nextTick(() => {
        this.scrollIntoView = id
      })
    },
    captureSessionId(payload) {
      let candidate = payload && payload.data && payload.data.sessionId
      if (candidate == null && payload && payload.raw && payload.raw.data
        && payload.raw.data.data) {
        candidate = payload.raw.data.data.sessionId
      }
      if (typeof candidate === 'number' && Number.isSafeInteger(candidate) && candidate > 0) {
        this.sessionId = candidate
      }
    },
    clearHistoryTimer() {
      if (this.historyTimer != null) {
        clearTimeout(this.historyTimer)
        this.historyTimer = null
      }
    },
    scheduleHistoryRefresh() {
      this.clearHistoryTimer()
      this.historyTimer = setTimeout(() => this.loadRecentHistory(), 1000)
    },
    async loadRecentHistory() {
      if (this.historyLoading) return
      this.historyLoading = true
      try {
        const result = await getRecentAiChatHistory()
        const history = result && result.data ? result.data : {}
        const sessionId = history.sessionId
        const restored = Array.isArray(history.messages)
          ? history.messages
            .filter(item => item && Number.isSafeInteger(item.messageId)
              && item.messageId > 0
              && (item.role === 'user' || item.role === 'assistant')
              && typeof item.content === 'string' && item.content.trim())
            .map(item => ({
              id: `ai-message-history-${item.messageId}`,
              role: item.role,
              content: item.content.trim(),
              action: this.normalizeAction(item.action)
            }))
          : []
        this.sessionId = Number.isSafeInteger(sessionId) && sessionId > 0
          ? sessionId : null
        this.messages = [{ ...WELCOME_MESSAGE }, ...restored]
        this.messageSequence = this.messages.length
        this.sending = history.processing === true
        this.clearHistoryTimer()
        if (this.sending) this.scheduleHistoryRefresh()
        this.$nextTick(() => {
          const last = this.messages[this.messages.length - 1]
          this.scrollIntoView = this.sending ? 'ai-message-loading' : last.id
        })
      } catch (error) {
        if (this.sending) this.scheduleHistoryRefresh()
      } finally {
        this.historyLoading = false
      }
    },
    async sendMessage() {
      const message = this.inputValue.trim()
      if (!message || this.sending || this.historyLoading) return

      this.clearHistoryTimer()
      this.appendMessage('user', message)
      this.inputValue = ''
      this.sending = true
      this.$nextTick(() => {
        this.scrollIntoView = 'ai-message-loading'
      })

      try {
        const result = await sendAiChatMessage(message, this.sessionId)
        this.captureSessionId(result)
        const answer = result && result.data && typeof result.data.answer === 'string'
          ? result.data.answer.trim()
          : ''
        const action = result && result.data ? result.data.action : null
        this.appendMessage('assistant', answer || BUSY_MESSAGE, answer ? action : null)
      } catch (error) {
        this.captureSessionId(error)
        this.appendMessage('assistant', BUSY_MESSAGE)
      } finally {
        this.sending = false
      }
    },
    async confirmPendingAction(messageItem) {
      const action = messageItem && messageItem.action
      if (!action || action.consumed || action.confirming || this.sending || this.historyLoading) return

      this.clearHistoryTimer()
      action.confirming = true
      this.sending = true
      this.appendMessage('user', '确认取消订单')
      this.$nextTick(() => {
        this.scrollIntoView = 'ai-message-loading'
      })
      try {
        const result = await confirmAiChatAction(action.actionId)
        this.captureSessionId(result)
        const answer = result && result.data && typeof result.data.answer === 'string'
          ? result.data.answer.trim()
          : ''
        if (!answer) throw new Error('empty answer')
        action.consumed = true
        this.appendMessage('assistant', answer)
      } catch (error) {
        this.captureSessionId(error)
        this.appendMessage('assistant', BUSY_MESSAGE)
      } finally {
        action.confirming = false
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
  top: calc(180rpx + var(--status-bar-height));
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

.conversation-tools {
  position: fixed;
  top: calc(116rpx + var(--status-bar-height));
  right: 0;
  left: 0;
  z-index: 19;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  height: 64rpx;
  padding: 5rpx 24rpx 5rpx 36rpx;
  background: rgba(244, 248, 252, 0.92);
}

.conversation-tools__hint {
  color: #9aa8b6;
  font-size: 21rpx;
}

.conversation-tools__new {
  width: auto;
  height: 54rpx;
  margin: 0;
  padding: 0 20rpx;
  color: #147ee8;
  background: #ffffff;
  border: 1rpx solid #bddbfa;
  border-radius: 27rpx;
  font-size: 22rpx;
  line-height: 52rpx;
}

.conversation-tools__new::after {
  border: 0;
}

.conversation-tools__new[disabled] {
  color: #9aabba;
  background: #f3f6f9;
  border-color: #dfe7ee;
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

.message-content {
  display: flex;
  max-width: 520rpx;
  flex-direction: column;
  align-items: flex-start;
}

.message-row--user .message-content {
  align-items: flex-end;
}

.message-content .message-bubble {
  max-width: 100%;
}

.message-action {
  width: auto;
  height: 62rpx;
  margin: 12rpx 0 0;
  padding: 0 28rpx;
  color: #ffffff;
  background: #e95b45;
  border-radius: 31rpx;
  font-size: 24rpx;
  line-height: 62rpx;
}

.message-action::after {
  border: 0;
}

.message-action[disabled] {
  color: #9aabba;
  background: #e6edf4;
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
