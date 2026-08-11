import {
  getOrderDetail,
  repetitionOrder,
  delShoppingCart,
  reminderOrder,
  cancelOrder,
} from '../api/api.js'
import { mapState, mapMutations } from 'vuex'
import { call, statusWord as formatStatusWord } from '@/utils/index.js'
import { getErrorMessage } from '../../utils/error-message.js'
import { getOrderActions } from '../../utils/order-segments.js'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import Status from './components/status.vue'
import OrderDetail from './components/orderDetail.vue'
import DeliveryInfo from './components/deliveryInfo.vue'
import OrderInfo from './components/orderInfo.vue'

export default {
  components: { CloudmealHeader, Status, OrderDetail, DeliveryInfo, OrderInfo },
  data() {
    return {
      showDisplay: false,
      rocallTime: '',
      textTip: '',
      showConfirm: false,
      orderDetailsData: {},
      timeout: false,
      orderId: null,
      times: null,
      phone: '',
    }
  },
  computed: {
    orderListDataes() {
      return this.orderListData() || []
    },
    orderDataes() {
      return this.showDisplay ? this.orderListDataes : this.orderListDataes.slice(0, 2)
    },
  },
  onLoad(options) {
    this.orderId = options.orderId
    this.getBaseData(this.orderId)
  },
  onUnload() {
    clearTimeout(this.times)
  },
  methods: {
    ...mapMutations(['setOrderData', 'initdishListMut']),
    ...mapState(['orderListData']),
    async getBaseData(id) {
      try {
        const res = await getOrderDetail(id)
        if (!res || res.code !== 1) throw new Error((res && res.msg) || '订单详情加载失败，请重试')
        this.orderDetailsData = res.data || {}
        this.initdishListMut(this.orderDetailsData.orderDetailList || [])
        clearTimeout(this.times)
        this.timeout = false
        if (Number(this.orderDetailsData.status) === 1) this.runTimeBack(this.orderDetailsData.orderTime)
        return this.orderDetailsData
      } catch (error) {
        uni.showToast({ title: getErrorMessage(error, '订单详情加载失败，请重试'), icon: 'none' })
        return null
      }
    },
    async handleReminder(payload) {
      if (!getOrderActions(this.orderDetailsData.status).includes('reminder')) return false
      try {
        const res = await reminderOrder(payload.id)
        if (!res || res.code !== 1) throw new Error((res && res.msg) || '催单失败，请重试')
        this.showConfirm = true
        this.textTip = '您的催单信息已发出！'
        this.$refs.commonPopup.open(payload.type)
        return true
      } catch (error) {
        uni.showToast({ title: getErrorMessage(error, '催单失败，请重试'), icon: 'none' })
        return false
      }
    },
    async cancel(type, order) {
      try {
        const res = await cancelOrder(order.id)
        if (!res || res.code !== 1) throw new Error((res && res.msg) || '取消订单失败，请重试')
        this.showConfirm = true
        this.textTip = '您的订单已取消！'
        this.$refs.commonPopup.open(type)
        this.orderId = order.id
        return true
      } catch (error) {
        uni.showToast({ title: getErrorMessage(error, '取消订单失败，请重试'), icon: 'none' })
        return false
      }
    },
    handleCancel(payload) {
      if ([1, 2].includes(Number(payload.obj.status))) return this.cancel(payload.type, payload.obj)
      this.showConfirm = false
      this.textTip = '请联系商家进行取消！'
      this.$refs.commonPopup.open(payload.type)
      return false
    },
    async oneMoreOrder(id) {
      if (!getOrderActions(this.orderDetailsData.status).includes('repeat')) return false
      try {
        await delShoppingCart()
        const res = await repetitionOrder(id)
        if (!res || res.code !== 1) throw new Error((res && res.msg) || '加购失败，请重试')
        uni.reLaunch({ url: '/pages/index/index' })
        return true
      } catch (error) {
        uni.showToast({ title: getErrorMessage(error, '加购失败，请重试'), icon: 'none' })
        return false
      }
    },
    statusWord(status) {
      return formatStatusWord(status)
    },
    paymentTime(value) {
      if (typeof value === 'string') this.rocallTime = value
    },
    runTimeBack(time) {
      if (!time) return
      const end = Date.parse(String(time).replace(/-/g, '/'))
      const remaining = (15 * 60 * 1000) - (Date.now() - end)
      if (remaining <= 0) {
        this.timeout = true
        clearTimeout(this.times)
        return
      }

      const minutes = String(Math.floor(remaining / 60000)).padStart(2, '0')
      const seconds = String(Math.floor((remaining % 60000) / 1000)).padStart(2, '0')
      this.rocallTime = `${minutes}:${seconds}`
      this.times = setTimeout(() => this.runTimeBack(time), 1000)
    },
    goBack() {
      uni.redirectTo({ url: '/pages/historyOrder/historyOrder' })
    },
    handleRefund(type) {
      this.showConfirm = false
      this.textTip = '请联系商家进行退款！'
      this.$refs.commonPopup.open(type)
    },
    handlePhone(type, phone) {
      if (!phone) {
        uni.showToast({ title: '暂无联系电话', icon: 'none' })
        return
      }
      this.phone = phone
      this.$refs.phone.open(type)
    },
    closePopup(type) {
      this.$refs.phone.close(type)
    },
    closePopupInfo(type) {
      this.$refs.commonPopup.close(type)
      if (this.orderId) this.getBaseData(this.orderId)
    },
    handlePay(id) {
      if (!getOrderActions(this.orderDetailsData.status, { timeout: this.timeout }).includes('pay')) return
      this.setOrderData({
        orderNumber: this.orderDetailsData.number,
        orderAmount: this.orderDetailsData.amount,
        orderTime: this.orderDetailsData.orderTime,
      })
      uni.redirectTo({ url: `/pages/pay/index?orderId=${id}` })
    },
    call() {
      call(this.phone)
    },
  },
}
