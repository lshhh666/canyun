<!-- 备注与餐具 -->
<template>
  <view class="box order_list">
    <view class="uniInfo">
      <view class="info-row" @click="goRemark">
        <text class="info-row__label">订单备注</text>
        <text class="info-row__value">{{ remark || '无备注' }}</text>
        <text class="info-row__arrow"></text>
      </view>
      <view class="info-row" @click="openPopuos('bottom')">
        <text class="info-row__label">餐具数量</text>
        <text class="info-row__value">{{ tablewareData }}</text>
        <text class="info-row__arrow"></text>
      </view>

      <view class="container">
        <uni-popup ref="popup" @change="change" class="popupBox">
          <view class="popup-content">
            <view class="popupTitle">
              <text>按政府条例要求，商家不得主动提供一次性餐具，请按需选择。</text>
            </view>
            <view class="popupCon">
              <view class="popupBtn">
                <text @click="closePopup">取消</text>
                <text>选择本单餐具</text>
                <text @click="handlePiker">确定</text>
              </view>
              <pikers :baseData="baseData" ref="piker" @changeCont="changeCont" />
            </view>
            <view class="popupSet">
              <view>后续订单餐具设置</view>
              <radio-group @change="handleRadio">
                <label v-for="item in radioGroup" :key="item">
                  <radio :value="item" color="#147EE8" :checked="item == activeRadio" />{{ item }}
                </label>
              </radio-group>
            </view>
          </view>
        </uni-popup>
      </view>
    </view>
  </view>
</template>

<script>
import Pikers from '@/components/uni-piker/index.vue'

export default {
  props: {
    remark: {
      type: String,
      default: ''
    },
    tablewareData: {
      type: String,
      default: ''
    },
    radioGroup: {
      type: Array,
      default: () => []
    },
    activeRadio: {
      type: String,
      default: ''
    },
    baseData: {
      type: Array,
      default: () => []
    }
  },
  components: { Pikers },
  methods: {
    goRemark () {
      this.$emit('goRemark')
    },
    openPopuos (type) {
      this.$refs.popup.open(type)
    },
    change (event) {
      this.$emit('change', event)
    },
    closePopup (type) {
      this.$refs.popup.close(type)
    },
    handlePiker () {
      this.$emit('handlePiker')
      this.closePopup()
    },
    changeCont (value) {
      this.$emit('changeCont', value)
    },
    handleRadio (event) {
      this.$emit('handleRadio', event)
    }
  }
}
</script>

<style src="./../style.scss" lang="scss" scoped></style>
