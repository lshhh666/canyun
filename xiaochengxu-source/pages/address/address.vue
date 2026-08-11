<template>
  <view class="address-page">
    <cloudmeal-header title="地址管理" show-back @back="goBack" />

    <scroll-view class="address-list" scroll-y>
      <view
        v-for="(item, index) in addressList"
        :key="item.id || index"
        class="address-card"
      >
        <view class="address-card__main" @click.stop="choseAddress(index, item)">
          <view class="address-card__content">
            <view class="address-card__line">
              <text class="address-tag">{{ getLableVal(item.label) }}</text>
              <text class="address-card__detail">{{ item.provinceName }}{{ item.cityName }}{{ item.districtName }}{{ item.detail }}</text>
            </view>
            <view class="address-card__contact">
              <text>{{ item.consignee }}{{ item.sex === '0' ? ' 先生' : ' 女士' }}</text>
              <text>{{ item.phone }}</text>
            </view>
          </view>
          <button class="edit-button" @click.stop="addOrEdit('编辑', item)">编辑</button>
        </view>
        <view class="address-card__default" @click.stop="getRadio(index, item)">
          <radio
            v-if="testValue"
            class="default-radio"
            color="#147EE8"
            :value="String(item.id)"
            :checked="isActive === index"
          />
          <text>{{ isActive === index ? '默认地址' : '设为默认地址' }}</text>
        </view>
      </view>

      <view v-if="isEmpty" class="address-empty">
        <text class="address-empty__title">还没有收货地址</text>
        <text class="address-empty__description">新增地址后即可用于配送</text>
      </view>
    </scroll-view>

    <view class="address-footer">
      <button class="primary-button" @click="addOrEdit('新增')">新增收货地址</button>
    </view>
  </view>
</template>

<script>
import { queryAddressBookList, putAddressBookDefault } from '../api/api.js'
import { mapState, mapMutations } from 'vuex'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import { getErrorMessage } from '../../utils/error-message'

export default {
  components: { CloudmealHeader },
  data () {
    return {
      testValue: true,
      addressList: [],
      formRouter: '',
      isActive: null,
      isEmpty: false
    }
  },
  onShow (options) {
    this.getAddressList()
    if (options && options.form) {
      this.formRouter = options.form
    }
  },
  computed: {
    ...mapState(['addressBackUrl']),
    statusBarHeight () {
      return uni.getSystemInfoSync().statusBarHeight + 'px'
    }
  },
  methods: {
    ...mapMutations(['setAddress']),
    goBack () {
      uni.redirectTo({ url: this.addressBackUrl || '/pages/order/index' })
    },
    getLableVal (item) {
      switch (String(item)) {
        case '1': return '公司'
        case '2': return '家'
        case '3': return '学校'
        default: return '其他'
      }
    },
    async getAddressList () {
      this.testValue = false
      uni.showLoading({ title: '加载中', mask: true })
      try {
        const res = await queryAddressBookList()
        const list = res.code === 1 && Array.isArray(res.data) ? res.data : []
        this.addressList = list
        this.isEmpty = list.length === 0
        this.isActive = list.findIndex(item => item.isDefault === 1)
        if (this.isActive < 0) this.isActive = null
        return res
      } catch (error) {
        this.addressList = []
        this.isEmpty = true
        uni.showToast({
          title: getErrorMessage(error, '地址列表加载失败，请重试'),
          icon: 'none'
        })
        return null
      } finally {
        this.testValue = true
        uni.hideLoading()
      }
    },
    addOrEdit (type, item) {
      if (type === '新增') {
        uni.redirectTo({ url: '/pages/addOrEditAddress/addOrEditAddress' })
        return
      }
      uni.redirectTo({
        url: `/pages/addOrEditAddress/addOrEditAddress?type=编辑&id=${item.id}`
      })
    },
    choseAddress (index, item) {
      if (this.addressBackUrl !== '/pages/order/index') return false
      this.setAddress(item)
      uni.redirectTo({
        url: '/pages/order/index?address=' + JSON.stringify(item)
      })
      return true
    },
    async getRadio (index, item) {
      const previousIndex = this.isActive
      this.isActive = index
      try {
        const res = await putAddressBookDefault({ id: item.id })
        if (res.code === 1) {
          this.addressList.forEach((address, addressIndex) => {
            address.isDefault = addressIndex === index ? 1 : 0
          })
          item.isDefault = 1
          uni.showToast({ title: '默认地址设置成功', duration: 2000, icon: 'none' })
        }
        return res
      } catch (error) {
        this.isActive = previousIndex
        uni.showToast({
          title: getErrorMessage(error, '默认地址设置失败，请重试'),
          icon: 'none'
        })
        return null
      }
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.address-page {
  min-height: 100vh;
  background: $cm-page;
  color: $cm-text;
}

.address-list {
  height: calc(100vh - 96rpx - env(safe-area-inset-top));
  padding: 24rpx 24rpx calc(140rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.address-card {
  margin-bottom: 20rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.address-card__main {
  display: flex;
  align-items: center;
  padding: 28rpx;
}

.address-card__content {
  min-width: 0;
  flex: 1;
}

.address-card__line {
  display: flex;
  align-items: flex-start;
}

.address-tag {
  flex: none;
  margin: 4rpx 12rpx 0 0;
  padding: 2rpx 10rpx;
  color: $cm-primary;
  background: $cm-primary-soft;
  border-radius: $cm-radius-sm;
  font-size: 22rpx;
}

.address-card__detail {
  color: $cm-text;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 42rpx;
}

.address-card__contact {
  display: flex;
  gap: 18rpx;
  margin-top: 14rpx;
  color: $cm-text-secondary;
  font-size: 25rpx;
}

.edit-button {
  width: 96rpx;
  height: 60rpx;
  margin: 0 0 0 20rpx;
  padding: 0;
  color: $cm-primary;
  background: transparent;
  border: 1rpx solid $cm-primary;
  border-radius: $cm-radius-sm;
  font-size: 24rpx;
  line-height: 58rpx;
}

.edit-button::after,
.primary-button::after {
  border: 0;
}

.address-card__default {
  display: flex;
  align-items: center;
  min-height: 76rpx;
  padding: 0 28rpx;
  color: $cm-text-secondary;
  border-top: 1rpx solid $cm-border;
  font-size: 24rpx;
}

.default-radio {
  transform: scale(0.76);
}

.address-empty {
  padding: 180rpx 40rpx 40rpx;
  text-align: center;
}

.address-empty__title {
  display: block;
  color: $cm-text;
  font-size: 32rpx;
  font-weight: 600;
}

.address-empty__description {
  display: block;
  margin-top: 12rpx;
  color: $cm-text-muted;
  font-size: 25rpx;
}

.address-footer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: $cm-surface;
  border-top: 1rpx solid $cm-border;
}

.primary-button {
  height: 84rpx;
  margin: 0;
  color: #fff;
  background: $cm-primary;
  border: 0;
  border-radius: $cm-radius-sm;
  font-size: 29rpx;
  font-weight: 600;
  line-height: 84rpx;
}
</style>
