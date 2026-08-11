<!-- 新增或编辑收货地址 -->
<template>
  <view class="address-form-page">
    <cloudmeal-header :title="delId ? '编辑收货地址' : '新增收货地址'" show-back @back="goBack" />

    <scroll-view class="address-form-scroll" scroll-y>
      <form class="address-form">
        <view class="form-row">
          <text class="form-label">联系人</text>
          <uni-easyinput
            class="form-control"
            v-model="form.name"
            placeholder-class="form-placeholder"
            placeholder="请填写收货人的姓名"
            minlength="2"
            maxlength="12"
          />
        </view>
        <view class="choice-row">
          <view
            v-for="item in items"
            :key="item.value"
            class="choice-item"
            @click="sexChangeHandle(item.value)"
          >
            <view class="choice-dot" :class="{ 'choice-dot--active': item.value === form.sex }"></view>
            <text>{{ item.name }}</text>
          </view>
        </view>

        <view class="form-row">
          <text class="form-label">手机号</text>
          <uni-easyinput
            class="form-control"
            v-model="form.phone"
            type="number"
            placeholder-class="form-placeholder"
            placeholder="请填写收货人手机号码"
            maxlength="11"
          />
        </view>

        <view class="form-row form-row--stack">
          <text class="form-label">所在地区</text>
          <view class="region-picker" @click="openAddres">
            <text :class="address ? 'region-value' : 'form-placeholder'">{{ address || '省/市/区' }}</text>
            <text class="region-arrow"></text>
          </view>
        </view>

        <view class="form-row form-row--stack">
          <text class="form-label">详细地址</text>
          <textarea
            class="detail-input"
            :class="{ 'detail-input--ios': platform === 'ios' }"
            v-model="form.detail"
            maxlength="80"
            placeholder-class="form-placeholder"
            placeholder="精确到门牌号"
          />
        </view>

        <view class="form-row form-row--stack">
          <text class="form-label">地址标签</text>
          <view class="tag-options">
            <text
              v-for="item in options"
              :key="item.type"
              class="tag-option"
              :class="{ 'tag-option--active': form.type === item.type }"
              @click="getTextOption(item)"
            >{{ item.name }}</text>
          </view>
        </view>
      </form>
    </scroll-view>

    <view class="form-footer">
      <button v-if="showDel" class="delete-button" @click="deleteAddressFun()">删除地址</button>
      <button class="save-button" @click="addAddressFun()">保存地址</button>
    </view>

    <simple-address
      ref="simpleAddress"
      :pickerValueDefault="cityPickerValueDefault"
      @onConfirm="onConfirm"
      @isClass="isClass"
      themeColor="#147EE8"
    />
  </view>
</template>

<script>
import simpleAddress from '../common/simple-address/simple-address.nvue'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import {
  addAddressBook,
  delAddressBook,
  queryAddressBookById,
  editAddressBook
} from '../api/api.js'
import { getErrorMessage } from '../../utils/error-message'

export default {
  components: { simpleAddress, CloudmealHeader },
  data () {
    return {
      platform: 'ios',
      showDel: false,
      showInput: true,
      valueMan: true,
      valueWoman: true,
      showClass: false,
      items: [
        { value: '0', name: '先生' },
        { value: '1', name: '女士' }
      ],
      current: 0,
      options: [
        { name: '公司', type: 1 },
        { name: '家', type: 2 },
        { name: '学校', type: 3 }
      ],
      form: {
        name: '',
        phone: '',
        type: 1,
        sex: '0',
        provinceCode: '11',
        provinceName: '',
        cityCode: '1101',
        cityName: '',
        districtCode: '110102',
        districtName: '',
        detail: ''
      },
      cityPickerValueDefault: [0, 0, 1],
      pickerText: '',
      address: '',
      delId: ''
    }
  },
  onLoad (options) {
    this.init()
    if (options && options.type === '编辑') {
      this.showDel = true
      this.delId = options.id
      this.queryAddressBookById(options.id)
    } else {
      this.showDel = false
    }
  },
  onUnload () {
    uni.removeStorage({ key: 'edit' })
  },
  computed: {
    statusBarHeight () {
      return uni.getSystemInfoSync().statusBarHeight + 'px'
    }
  },
  methods: {
    init () {
      this.platform = uni.getSystemInfoSync().platform
    },
    goBack () {
      uni.redirectTo({ url: '/pages/address/address' })
    },
    async queryAddressBookById (id) {
      try {
        const res = await queryAddressBookById({ id })
        if (res.code === 1) {
          this.form = {
            provinceCode: res.data.provinceCode,
            cityCode: res.data.cityCode,
            districtCode: res.data.districtCode,
            phone: res.data.phone,
            name: res.data.consignee,
            sex: String(res.data.sex),
            type: Number(res.data.label),
            detail: res.data.detail,
            id: res.data.id
          }
          if (res.data.provinceName && res.data.cityName && res.data.districtName) {
            this.address = [res.data.provinceName, res.data.cityName, res.data.districtName].join('/')
          }
        }
        return res
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error, '地址详情加载失败，请重试'),
          icon: 'none'
        })
        return null
      }
    },
    isClass (value) {
      this.showClass = value
    },
    openAddres () {
      this.$refs.simpleAddress.open()
      uni.hideKeyboard()
    },
    onConfirm (event) {
      this.form.provinceCode = event.provinceCode
      this.form.cityCode = event.cityCode
      this.form.districtCode = event.areaCode
      this.address = event.label
    },
    bindTextAreaBlur () {},
    radioChange (event) {
      this.form.sex = event.detail.value === 'man' ? '0' : '1'
    },
    sexChangeHandle (value) {
      this.form.sex = value
    },
    validateForm () {
      const rules = [
        [!this.form.name, '联系人不能为空'],
        [!this.form.phone, '手机号不能为空'],
        [this.form.type === '', '所属标签不能为空'],
        [!this.address, '所在地区不能为空'],
        [!this.form.detail, '详细地址不能为空']
      ]
      const invalid = rules.find(rule => rule[0])
      if (invalid) {
        uni.showToast({ title: invalid[1], duration: 1000, icon: 'none' })
        return false
      }
      const phonePattern = /^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/
      if (!phonePattern.test(this.form.phone)) {
        uni.showToast({ title: '手机号输入有误', duration: 1000, icon: 'none' })
        return false
      }
      const namePattern = /^[\u0391-\uFFE5A-Za-z0-9]{2,12}$/
      if (!namePattern.test(this.form.name)) {
        uni.showToast({ title: '请输入合法的2-12个字符', duration: 1000, icon: 'none' })
        return false
      }
      return true
    },
    async addAddressFun () {
      if (!this.validateForm()) return false
      const region = this.address.split('/')
      const params = {
        ...this.form,
        label: this.form.type,
        consignee: this.form.name,
        provinceName: region[0],
        cityName: region[1],
        districtName: region[2]
      }
      try {
        if (this.showDel) {
          await editAddressBook(params)
        } else {
          delete params.id
          await addAddressBook(params)
        }
        uni.redirectTo({ url: '/pages/address/address' })
        return true
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error, this.showDel ? '地址修改失败，请重试' : '地址保存失败，请重试'),
          icon: 'none'
        })
        return false
      }
    },
    async deleteAddressFun () {
      try {
        await delAddressBook(this.delId)
        uni.showToast({ title: '地址删除成功', duration: 1000, icon: 'none' })
        uni.redirectTo({ url: '/pages/address/address' })
        this.form.name = ''
        this.form.phone = ''
        this.address = ''
        this.form.type = 1
        this.form.sex = '0'
        this.form.provinceCode = '11'
        this.form.cityCode = '1101'
        this.form.districtCode = '110102'
        return true
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error, '地址删除失败，请重试'),
          icon: 'none'
        })
        return false
      }
    },
    getTextOption (item) {
      this.form.type = item.type
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.address-form-page {
  min-height: 100vh;
  background: $cm-page;
  color: $cm-text;
}

.address-form-scroll {
  height: calc(100vh - 96rpx - env(safe-area-inset-top));
  padding: 24rpx 24rpx calc(150rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.address-form {
  padding: 0 28rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.form-row {
  display: flex;
  align-items: center;
  min-height: 104rpx;
  border-bottom: 1rpx solid $cm-border;
}

.form-row--stack {
  display: block;
  padding: 24rpx 0;
}

.form-label {
  width: 140rpx;
  flex: none;
  color: $cm-text;
  font-size: 27rpx;
  font-weight: 600;
}

.form-control {
  min-width: 0;
  flex: 1;
}

::v-deep .is-input-border {
  border: 0 !important;
}

::v-deep .uni-easyinput__content-input {
  padding-left: 0 !important;
  color: $cm-text;
  font-size: 27rpx;
}

.form-placeholder,
::v-deep .form-placeholder {
  color: $cm-text-muted !important;
  font-size: 26rpx;
}

.choice-row {
  display: flex;
  gap: 48rpx;
  padding: 0 0 24rpx 140rpx;
  border-bottom: 1rpx solid $cm-border;
}

.choice-item {
  display: flex;
  align-items: center;
  color: $cm-text-secondary;
  font-size: 25rpx;
}

.choice-dot {
  width: 28rpx;
  height: 28rpx;
  margin-right: 10rpx;
  border: 2rpx solid $cm-border;
  border-radius: 50%;
  box-sizing: border-box;
}

.choice-dot--active {
  border: 8rpx solid $cm-primary;
}

.region-picker {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
}

.region-value {
  min-width: 0;
  flex: 1;
  color: $cm-text;
  font-size: 27rpx;
}

.region-arrow {
  width: 16rpx;
  height: 16rpx;
  margin-left: auto;
  border-top: 3rpx solid $cm-text-muted;
  border-right: 3rpx solid $cm-text-muted;
  transform: rotate(45deg);
}

.detail-input {
  width: 100%;
  height: 120rpx;
  margin-top: 12rpx;
  padding: 0;
  color: $cm-text;
  font-size: 27rpx;
  line-height: 40rpx;
}

.detail-input--ios {
  padding-top: 6rpx;
}

.tag-options {
  display: flex;
  gap: 18rpx;
  margin-top: 18rpx;
}

.tag-option {
  min-width: 92rpx;
  padding: 10rpx 18rpx;
  color: $cm-text-secondary;
  background: $cm-page;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-sm;
  font-size: 24rpx;
  text-align: center;
}

.tag-option--active {
  color: $cm-primary;
  background: $cm-primary-soft;
  border-color: $cm-primary;
}

.form-footer {
  position: fixed;
  z-index: 10;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  gap: 18rpx;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: $cm-surface;
  border-top: 1rpx solid $cm-border;
}

.save-button,
.delete-button {
  height: 84rpx;
  margin: 0;
  border-radius: $cm-radius-sm;
  font-size: 29rpx;
  font-weight: 600;
  line-height: 84rpx;
}

.save-button {
  min-width: 0;
  flex: 1;
  color: #fff;
  background: $cm-primary;
}

.delete-button {
  width: 210rpx;
  color: $cm-danger;
  background: #fff;
  border: 1rpx solid $cm-danger;
}

.save-button::after,
.delete-button::after {
  border: 0;
}
</style>
