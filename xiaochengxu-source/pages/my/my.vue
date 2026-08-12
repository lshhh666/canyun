<template>
  <view class="account-page">
    <cloudmeal-header title="我的餐云" />

    <scroll-view class="account-scroll" scroll-y @scrolltolower="lower">
      <head-info
        :psersonUrl="psersonUrl"
        :nickName="nickName"
        @edit-profile="openProfileEditor"
      />

      <view class="account-actions">
        <view class="account-action" @click="goAddress">
          <view class="account-action__icon account-action__icon--address">
            <uni-icons type="location" color="#147ee8" size="20" />
          </view>
          <text class="account-action__label">收货地址</text>
          <uni-icons type="right" color="#91a0b2" size="18" />
        </view>
        <view class="account-action" @click="goOrder">
          <view class="account-action__icon">
            <uni-icons type="list" color="#147ee8" size="20" />
          </view>
          <text class="account-action__label">订单记录</text>
          <uni-icons type="right" color="#91a0b2" size="18" />
        </view>
        <view class="account-action" @click="handlePhone">
          <view class="account-action__icon">
            <uni-icons type="phone" color="#147ee8" size="20" />
          </view>
          <text class="account-action__label">联系门店</text>
          <text v-if="phoneNumber" class="account-action__meta">{{ phoneNumber }}</text>
          <uni-icons type="right" color="#91a0b2" size="18" />
        </view>
      </view>

      <view v-if="recentOrdersList.length" class="recent-title">最近订单</view>
      <order-list
        :recentOrdersList="recentOrdersList"
        :loading="loading"
        :loadingText="loadingText"
        @lower="lower"
        @goDetail="goDetail"
        @oneOrderFun="oneOrderFun"
      />

      <text class="version">餐云 CloudMeal</text>
    </scroll-view>

    <app-tabbar active="account" />

    <profile-editor
      v-if="profileEditorVisible"
      :profile="$store.state.baseUserInfo || {}"
      :allow-skip="false"
      :saving="profileSaving"
      @save="saveProfile"
      @close="closeProfileEditor"
    />
  </view>
</template>

<script>
import { getOrderPage, repetitionOrder, delShoppingCart, updateUserProfile } from '../api/api.js'
import { mapMutations } from 'vuex'
import HeadInfo from './components/headInfo.vue'
import OrderList from './components/orderList.vue'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import AppTabbar from '@/components/app-tabbar/app-tabbar.vue'
import { getErrorMessage } from '@/utils/error-message.js'
import { persistSession } from '@/utils/session.js'
import { uploadAvatar } from '@/utils/upload.js'

const DEFAULT_AVATAR = '/static/brand/cloudmeal-logo.png'
const DEFAULT_NICKNAME = '微信用户'

export default {
  components: { HeadInfo, OrderList, CloudmealHeader, AppTabbar },
  data() {
    return {
      psersonUrl: DEFAULT_AVATAR,
      nickName: DEFAULT_NICKNAME,
      phoneNumber: '',
      recentOrdersList: [],
      pageInfo: {
        page: 1,
        pageSize: 10,
        total: 0
      },
      failedPage: null,
      loadingText: '',
      loading: false,
      profileEditorVisible: false,
      profileSaving: false
    }
  },
  onLoad() {
    const baseUserInfo = this.$store.state.baseUserInfo || {}
    const shopPhone = this.$store.state.shopPhone
    const rawPhone = shopPhone && typeof shopPhone === 'object'
      ? shopPhone.phone
      : shopPhone
    this.psersonUrl = baseUserInfo.avatarUrl || DEFAULT_AVATAR
    this.nickName = baseUserInfo.nickName || DEFAULT_NICKNAME
    this.phoneNumber = rawPhone ? String(rawPhone).trim() : ''
    this.getList()
  },
  methods: {
    ...mapMutations(['setAddressBackUrl', 'setBaseUserInfo']),
    openProfileEditor() {
      this.profileEditorVisible = true
    },
    closeProfileEditor() {
      if (!this.profileSaving) this.profileEditorVisible = false
    },
    async saveProfile({ name, tempAvatarPath, currentAvatar }) {
      if (this.profileSaving) return false
      this.profileSaving = true
      try {
        let avatar = currentAvatar
        if (tempAvatarPath) {
          const uploadResult = await uploadAvatar(tempAvatarPath)
          avatar = uploadResult && uploadResult.data
            ? uploadResult.data.url || uploadResult.data
            : ''
        }
        const response = await updateUserProfile({ name, avatar })
        const profileData = response.data || { name, avatar, profileCompleted: true }
        persistSession(this.$store, profileData)
        this.nickName = profileData.name || name
        this.psersonUrl = profileData.avatar || avatar || DEFAULT_AVATAR
        this.profileEditorVisible = false
        return true
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error, '资料保存失败，请重试'),
          icon: 'none'
        })
        return false
      } finally {
        this.profileSaving = false
      }
    },
    async getList(page = this.pageInfo.page) {
      if (this.loading) return false
      this.loading = true
      try {
        const res = await getOrderPage({
          pageSize: this.pageInfo.pageSize,
          page
        })
        if (!res || res.code !== 1) {
          throw new Error((res && res.msg) || '订单加载失败，请重试')
        }
        const data = res.data || {}
        const records = Array.isArray(data.records) ? data.records : []
        this.recentOrdersList = this.recentOrdersList.concat(records)
        this.failedPage = null
        this.pageInfo.page = page
        this.pageInfo.total = Number(data.total) || 0
        this.loadingText = ''
        return true
      } catch (error) {
        this.failedPage = page
        this.loadingText = getErrorMessage(error, '订单加载失败，请重试')
        uni.showToast({ title: this.loadingText, icon: 'none' })
        return false
      } finally {
        this.loading = false
      }
    },
    handlePhone() {
      if (!this.phoneNumber) {
        uni.showToast({ title: '门店暂未提供联系电话', icon: 'none' })
        return
      }
      uni.makePhoneCall({ phoneNumber: this.phoneNumber })
    },
    goAddress() {
      this.setAddressBackUrl('/pages/my/my')
      uni.redirectTo({ url: '/pages/address/address?form=my' })
    },
    goOrder() {
      uni.navigateTo({ url: '/pages/historyOrder/historyOrder' })
    },
    async oneOrderFun(id) {
      try {
        const clearRes = await delShoppingCart()
        if (!clearRes || ![1, 200].includes(Number(clearRes.code))) {
          throw new Error((clearRes && clearRes.msg) || '再来一单失败，请重试')
        }
        const res = await repetitionOrder(id)
        if (!res || ![1, 200].includes(Number(res.code))) {
          throw new Error((res && res.msg) || '再来一单失败，请重试')
        }

        const pages = getCurrentPages()
        const routeIndex = pages.findIndex(item => item.route === 'pages/index/index')
        if (routeIndex > -1) {
          const delta = pages.length - 1 - routeIndex
          if (delta > 0) {
            uni.navigateBack({ delta })
            return true
          }
        }
        uni.reLaunch({ url: '/pages/index/index' })
        return true
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error, '再来一单失败，请重试'),
          icon: 'none'
        })
        return false
      }
    },
    goDetail(id) {
      this.setAddressBackUrl('/pages/my/my')
      uni.navigateTo({ url: `/pages/details/index?orderId=${id}` })
    },
    async lower() {
      if (this.loading) return false
      if (this.failedPage !== null) {
        this.loadingText = '数据加载中…'
        return this.getList(this.failedPage)
      }
      const totalPages = Math.ceil(this.pageInfo.total / this.pageInfo.pageSize)
      if (this.pageInfo.page >= totalPages) {
        if (this.recentOrdersList.length) this.loadingText = '没有更多了'
        return false
      }
      this.loadingText = '数据加载中…'
      return this.getList(this.pageInfo.page + 1)
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.account-page {
  min-height: 100vh;
  background: $cm-page;
}

.account-scroll {
  height: calc(100vh - 96rpx - env(safe-area-inset-top) - 112rpx - env(safe-area-inset-bottom));
}

.account-actions {
  margin: 20rpx 24rpx 28rpx;
  padding: 0 28rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.account-action {
  display: flex;
  min-height: 104rpx;
  align-items: center;
  border-bottom: 1rpx solid $cm-border;
}

.account-action:last-child {
  border-bottom: 0;
}

.account-action__icon {
  display: flex;
  width: 64rpx;
  height: 64rpx;
  margin-right: 20rpx;
  align-items: center;
  justify-content: center;
  background: $cm-primary-soft;
  border-radius: $cm-radius-sm;
}

.account-action__label {
  min-width: 0;
  flex: 1;
  color: $cm-text;
  font-size: 29rpx;
  line-height: 42rpx;
}

.account-action__meta {
  max-width: 260rpx;
  margin-right: 12rpx;
  overflow: hidden;
  color: $cm-text-secondary;
  font-size: 24rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-title {
  padding: 0 32rpx 16rpx;
  color: $cm-text;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 44rpx;
}

.version {
  display: block;
  padding: 36rpx 24rpx calc(44rpx + env(safe-area-inset-bottom));
  color: $cm-text-muted;
  font-size: 22rpx;
  line-height: 32rpx;
  text-align: center;
}
</style>
