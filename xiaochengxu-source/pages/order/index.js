import {
	// 提交订单
	submitOrderSubmit,
	// 查询默认地址
	getAddressBookDefault,
	queryAddressBookList, previewOrder
} from '../api/api.js'
import {
	mapState,
	mapMutations,
} from 'vuex'
import {
	baseUrl
} from '../../utils/env'
import {
	getLableVal,
	dateFormat,
	getWeekDate

} from '../../utils/index.js'
import Pikers from '@/components/uni-piker/index.vue'//餐具信息
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import AddressPop from "./components/address.vue" //地址
import DishDetail from "./components/dishDetail.vue" //菜品详情
import DishInfo from "./components/dishInfo.vue" //菜品信息
import dayjs from "@/utils/lib/dayjs.min.js";
import { getErrorMessage } from '../../utils/error-message'
import { getCouponEligibility } from '../../utils/coupon.js'
export default {
	data() {
		return {
			platform: 'ios',
			orderDishPrice: 0,
			openPayType: false,
			psersonUrl: '../../static/btn_waiter_sel.png',
			nickName: '',//名字
			gender: 0,
			phoneNumber: '',//电话
			address: '',//地址
			remark: '',//备注
			arrivalTime: '',// 用户选择的送达时间
			orderTime: '',// 服务端返回的送达时间
			deliveryMode: 'immediate',
			addressBookId: '',
			addressLabel: '',
			tagLabel: '',
			// 加入购物车数量
			orderDishNumber: 0,
			showDisplay: false,//是否显示更多收起
			type: 'center',
			expirationTime: '',
			// rocallTime:'',
			tablewareData: '无需餐具',
			tableware: '',
			packAmount: 0,
			value: [0, 0],
			timeValue: [0, 0],
			indicatorStyle: `height: 44px;color:#333`,
			tabIndex: 0,
			scrollinto: 'tab0',
			scrollH: 0,
			popleft: ['今天', '明天'],// 时间选中的左侧数据（今天、明天）
			visible: true,
			baseData: [
				'无需餐具', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'
			],
			activeRadio: '无需餐具', //存的是选中的value值
			radioGroup: ['依据餐量提供', '无需餐具'],
			popright: ['立即派送', '09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '12:30', '13:00',
				'13:30', '14:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00', '17:30', '18:00', '18:30',
				'19:00', '19:30', '20:00', '20:30', '21:00', '21:30', '22:00', '22:30', '23:00'
			],
			newDateData: [],// 时间段
			// styleType: 'button',
			textTip: '',
			showConfirm: false,
			toDate: null,
			tomorrowStart: null,
			newDate: null,
			selectValue: 0,
			selectDateValue: 0,
			timeout: false,
			isTomorrow: false,
			status: 0,
			num: 0,
			weeks: [],
			scrollTop: 0,
			addressList: [],
			addressLoadState: 'loading',
			previewState: 'idle',
			previewData: null,
			previewRequestId: 0,
			isHandlePy: false
		}
	},
	computed: {
		// 商品金额（提交总额仍沿用 orderDishPrice）
		dishAmount: function () {
			return this.previewNumber('goodsAmount')
		},
		packFeeAmount: function () {
			return this.previewNumber('packAmount')
		},
		deliveryFeeAmount: function () {
			return this.previewNumber('deliveryFee')
		},
		totalAmount: function () {
			return this.previewNumber('totalAmount')
		},
		selectedCouponData: function () {
			return this.$store.state.selectedCoupon || null
		},
		couponDiscount: function () {
			if (!this.selectedCouponData || !this.isSelectedCouponUsable()) return 0
			return Math.min(Number(this.selectedCouponData.discountAmount) || 0, this.totalAmount)
		},
		payableAmount: function () {
			return Math.max(0, this.totalAmount - this.couponDiscount)
		},
		couponDisplayText: function () {
			if (!this.selectedCouponData) return '选择优惠券'
			return `-${this.couponDiscount.toFixed(2)}元`
		},
		// 菜品数据
		orderListDataes: function () {
			return this.orderListData()
		},
		// 菜品数据
		orderDataes: function () {
			let testList = []
			if (this.showDisplay === false) {
				if (this.orderListDataes.length > 3) {
					for (var i = 0; i < 3; i++) {
						testList.push(this.orderListDataes[i])
					}
				} else {
					testList = this.orderListDataes
				}
				return testList
			} else {
				return this.orderListDataes
			}
		}
	},
	created() {
		let time = new Date()
		this.toDate = new Date(time.toLocaleDateString()).getTime()
		this.tomorrowStart = this.toDate + 3600 * 24 * 1000
		this.newDate = time.getHours() * 3600 + time.getMinutes() * 60

		const weekDay = [this.toDate, this.tomorrowStart]

		weekDay.forEach((date) => {
			this.weeks.push(getWeekDate(date))

		})

		this.getAddressList()
	},
	mounted() {
		this.countdown()
	},
	components: {
		Pikers,
		CloudmealHeader,
		// Popup,
		AddressPop,
		DishDetail,
		DishInfo
	},
	async onLoad(options) {
		this.initPlatform()
		this.psersonUrl = this.$store.state.baseUserInfo && this.$store.state.baseUserInfo.avatarUrl
		this.nickName = this.$store.state.baseUserInfo && this.$store.state.baseUserInfo.nickName
		this.gender = this.$store.state.baseUserInfo && this.$store.state.baseUserInfo.gender
		this.remark = this.remarkData()
		this.init()
		// 存在options说明换地址了
		if (this.addressData() && this.addressData().detail) {
			this.addressBookId = ''
			const newAddress = this.addressData()
			this.address = newAddress.provinceName + newAddress.cityName + newAddress.districtName + newAddress.detail
			this.phoneNumber = newAddress.phone
			this.nickName = newAddress.consignee
			this.gender = newAddress.sex

			this.addressBookId = newAddress.id
			this.addressLabel = getLableVal(newAddress.label)
		} else {
			// 默认地址查询
			await this.getAddressBookDefault()
		}

		await this.loadPreview()
		this.setArrivalTime(this.arrivalTime)
		this.setGender(this.gender)
	},
	onReady() {
		uni.getSystemInfo({
			success: (res) => {
				this.scrollH = res.windowHeight - uni.upx2px(100)
			}
		})
	},
	methods: {
		...mapState(['orderListData', 'remarkData', 'addressData', 'storeInfo', 'shopInfo', 'deliveryFee']),
		...mapMutations(['setAddressBackUrl', 'setOrderData', 'setArrivalTime', 'setRemark', 'setGender', 'setSelectedCoupon']),
		init() {
			this.computOrderInfo()

		},
		initPlatform() {
			const res = uni.getSystemInfoSync()
			this.platform = res.platform
		},
		previewNumber(field) {
			const amount = Number(this.previewData && this.previewData[field])
			return Number.isFinite(amount) ? amount : 0
		},
		isSelectedCouponUsable() {
			return getCouponEligibility(this.selectedCouponData, this.dishAmount).eligible
		},
		validateSelectedCoupon(showToast = false) {
			if (!this.selectedCouponData || this.isSelectedCouponUsable()) return true
			this.setSelectedCoupon(null)
			if (showToast) {
				uni.showToast({ title: '已选优惠券当前不可用，请重新选择', icon: 'none' })
			}
			return false
		},
		openCouponSelector() {
			if (this.previewState !== 'ready') {
				uni.showToast({ title: '订单金额计算中，请稍候', icon: 'none' })
				return
			}
			uni.navigateTo({
				url: `/pages/coupon/index?select=1&goodsAmount=${encodeURIComponent(this.dishAmount.toFixed(2))}`
			})
		},
		immediateDeliveryTime() {
			const value = String(this.previewData && this.previewData.estimatedDeliveryTime || '').replace('T', ' ')
			if (!value) throw new Error('预计送达时间无效，请重试')
			return value.length === 16 ? `${value}:00` : value
		},
		// 获取用户送餐期望时间
		async loadPreview() {
			if (!this.addressBookId) {
				this.previewRequestId += 1
				this.previewState = 'idle'
				this.previewData = null
				return null
			}
			const requestId = ++this.previewRequestId
			this.previewState = 'loading'
			try {
				const result = await previewOrder({ addressBookId: this.addressBookId })
				if (requestId !== this.previewRequestId) return null
				this.previewData = result.data || null
				this.previewState = this.previewData ? 'ready' : 'error'
				this.orderTime = this.previewData && this.previewData.estimatedDeliveryTime
				this.arrivalTime = this.orderTime ? dayjs(this.orderTime).format('HH:mm') : ''
				this.deliveryMode = 'immediate'
				if (this.orderTime) this.getDateDate()
				this.setArrivalTime(this.arrivalTime)
				this.validateSelectedCoupon(true)
				return result
			} catch (error) {
				if (requestId !== this.previewRequestId) return null
				this.previewState = 'error'
				this.previewData = null
				uni.showToast({
					title: getErrorMessage(error, '送达时间获取失败，请重试'),
					icon: 'none'
				})
				return null
			}
		},
		// 根据系统派送时间 格式化时间  [16:00,16:30]
		getDateDate() {
			let currentDayjs = dayjs(this.orderTime);
			const list = ['立即派送']
			if (!(currentDayjs.hour() >= 22 && currentDayjs.minute() > 30)) {
				if (currentDayjs.minute() > 30) {
					currentDayjs = currentDayjs.add(1, 'hour').set('minute', 0)
				} else {
					currentDayjs = currentDayjs.set('minute', 30)
				}
				while (true) {
					if (currentDayjs.hour() === 23 && currentDayjs.minute() === 30) {
						break
					}
					const start = `${currentDayjs.format("HH")}:${currentDayjs.format('mm')}`;
					list.push(`${start}`)
					currentDayjs = currentDayjs.add(30, 'minute')
				}
			}
			this.newDateData = list
		},
		// 获取地址
		async getAddressList() {
			this.testValue = false
			this.addressLoadState = 'loading'
			try {
				const res = await queryAddressBookList()
				if (res.code === 1) {
					this.testValue = true
					this.addressList = Array.isArray(res.data) ? res.data : []
					this.addressLoadState = 'ready'
					return res
				}
				throw new Error(res.msg || '地址列表加载失败，请重试')
			} catch (error) {
				this.addressLoadState = 'error'
				uni.showToast({
					title: getErrorMessage(error, '地址列表加载失败，请重试'),
					icon: 'none'
				})
				return null
			}
		},
		// 默认地址查询
		async getAddressBookDefault() {
			try {
				const res = await getAddressBookDefault()
				if (res.code === 1 && res.data) {
					this.addressBookId = ''
					this.address = res.data.provinceName + res.data.cityName + res.data.districtName + res.data
						.detail
					this.phoneNumber = res.data.phone
					this.nickName = res.data.consignee
					this.gender = res.data.sex
					this.addressBookId = res.data.id
					this.addressLabel = getLableVal(res.data.label)
					this.tagLabel = res.data.label
				}
				return res
			} catch (error) {
				uni.showToast({
					title: getErrorMessage(error, '默认地址获取失败，请重试'),
					icon: 'none'
				})
				return null
			}
		},
		// 去地址页面
		goAddress() {
			this.setAddressBackUrl('/pages/order/index')
			if (this.addressLoadState === 'loading') {
				uni.showToast({
					title: '地址加载中，请稍候',
					icon: 'none'
				})
				return false
			}
			if (this.addressLoadState === 'ready' && this.addressList.length === 0) {
				uni.redirectTo({
					url: '/pages/addOrEditAddress/addOrEditAddress'
				})
			} else {
				uni.redirectTo({
					url: '/pages/address/address'
				})
			}
			return true
		},
		// // 重新拼装image
		getNewImage(image) {
			return `${baseUrl}/common/download?name=${image}`
		},
		// 订单里和总订单价格计算
		computOrderInfo() {
			let oriData = this.orderListDataes
			this.orderDishNumber = 0
			oriData.map((n, i) => {
				this.orderDishNumber += n.number
			})
		},
		// 返回上一级
		goBack() {
			uni.navigateBack({ delta: 1 })
		},
		closeMask() {
			this.openPayType = false
		},
		// 支付下单
		async payOrderHandle() {
			if (this.isHandlePy) return false
			if (!this.address) {
				uni.showToast({
					title: '请选择收货地址',
					icon: 'none',
				})
				return false
			}
			if (this.previewState !== 'ready' || !this.previewData) {
				uni.showToast({ title: '订单金额尚未准备好，请重试', icon: 'none' })
				return false
			}
			if (!this.validateSelectedCoupon(true)) return false
			this.isHandlePy = true
			try {
				const params = {
					payMethod: 1,
					addressBookId: this.addressBookId,
					remark: this.remark,
					estimatedDeliveryTime: this.deliveryMode === 'immediate' ? this.immediateDeliveryTime() : dateFormat(this.isTomorrow,
						this.arrivalTime),
					deliveryStatus: this.deliveryMode === 'immediate' ? 1 : 0,
					tablewareStatus: this.status,
					tablewareNumber: this.num,
					userCouponId: this.selectedCouponData ? this.selectedCouponData.id : null
				}
				const res = await submitOrderSubmit(params)
				if (res.code === 1) {
					this.setOrderData(res.data)
					this.setRemark('')
					this.setSelectedCoupon(null)

					uni.navigateTo({
						url: '/pages/pay/index?orderId=' + res.data.id
					})
				} else {
					uni.showToast({
						title: res.msg || '订单提交失败，请重试',
						icon: 'none',
					})
				}
				return res
			} catch (error) {
				uni.showToast({
					title: getErrorMessage(error, '订单提交失败，请重试'),
					icon: 'none'
				})
				return null
			} finally {
				this.isHandlePy = false
			}
		},
		// 拨打电话
		call() {
			uni.makePhoneCall({
				phoneNumber: '114' //仅为示例
			})
		},
		// // 联系商家进行取消弹层
		handleContact(type) {
			this.showConfirm = false
			this.openPopuos(type)
			this.textTip = '请联系商家进行取消！'
		},
		// 联系商家进行退款弹层
		handleRefund(type) {
			this.showConfirm = false
			this.openPopuos(type)
			this.textTip = '请联系商家进行退款！'
		},
		// 进入备注页
		goRemark() {
			this.setAddressBackUrl('/pages/order/index')
			uni.redirectTo({
				url: '/pages/remark/index'
			})
		},
		// 打开参数数量弹层
		openPopuos(type) {
			// open 方法传入参数 等同在 uni-popup 组件上绑定 type属性
			this.$refs.popup.open(type)
		},
		// 关闭餐具弹层
		closePopup(type) {
			this.$refs.popup.close(type)
		},
		change(e) {
		},
		// 确定本单餐具
		handlePiker() {
			if (this.tableware !== '') {
				this.num = Number(this.tableware)
				this.status = 0
				if (this.tableware === '无需餐具') {
					this.num = 0
					this.status = 0
				}
				if (this.tableware === '依据餐量提供') {
					this.num = this.orderDishNumber
					this.status = 1
				}

				if (this.tableware !== '依据餐量提供' || this.tableware !== '无需餐具') {
					this.tablewareData = this.tableware + '份'

				} else {
					this.tablewareData = this.tableware
				}
			} else {
				//是默认值，在点击的时候抛出去
				let cont = this.baseData[this.$refs.dishinfo.$refs.piker.defaultValue[0]]
				this.tablewareData = cont
				if (this.activeRadio === '依据餐量提供') {
					this.num = this.orderDishNumber
					this.status = 1
				} else {
					this.num = 0
					this.status = 0
				}
			}
		},
		// 确定本单餐具
		changeCont(val) {
			this.tableware = val
		},
		// 餐具数量的后续订单餐具设置
		handleRadio(e) {
			this.activeRadio = e.detail.value
		},
		countdown() {
			const end = Date.parse(new Date())
		},
		// 星期几选择
		dateChange(index) {
			if (index === 1) {
				this.newDateData = this.popright.slice(1)
				this.isTomorrow = true
			} else {
				this.isTomorrow = false
				this.newDateData = []
				this.getDateDate()
			}
			// 点击的还是当前数据的时候直接return
			if (this.tabIndex == index) {
				return
			}
			this.tabIndex = index
		},
		// 选中时间段
		timeClick: function (val) {
			this.selectValue = val.i
			this.setTime(val.val)
		},
		// 设置时间
		setTime(val) {
			if (val === '立即派送') {
				this.deliveryMode = 'immediate'
				this.arrivalTime = dayjs(this.orderTime).format('HH:mm')
			} else {
				this.deliveryMode = 'scheduled'
				this.arrivalTime = val
			}

			this.setArrivalTime(this.arrivalTime)

		},
		touchstart(e) {
			if (e.changedTouches[0].clientY > 400) {
			}
		}
	}
}
