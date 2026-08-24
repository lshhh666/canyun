import Phone from "@/components/uni-phone/index.vue" //拨打电话
import CloudmealHeader from "@/components/cloudmeal-header/cloudmeal-header.vue"
import AppTabbar from "@/components/app-tabbar/app-tabbar.vue"
import StatePanel from "@/components/state-panel/state-panel.vue"
import popMask from "./components/popMask.vue" //规格
import popCart from "./components/popCart.vue" //购物车弹出层
import dishDetail from "./components/dishDetail.vue" //菜品详情
import {
	// 点餐页相关接口
	userLogin,
	updateUserProfile,
	getCategoryList,
	dishListByCategoryId,
	// 查询套餐列表的接口
	querySetmeaList,
	// 获取购物车集合
	getShoppingCartList,
	// 新的购物车添加逻辑接口
	newAddShoppingCartAdd,
	// 新的购物车减少接口
	newShoppingCartSub,
	// 清空购物车
	delShoppingCart,
	// 此接口为首页查询套餐详情展示的接口
	querySetmealDishById,
	// 获取店铺信息
	getShopStatus,
	// 获取店铺联系方式
	getShopInfo,
} from "../api/api.js"
import { mapState, mapMutations } from "vuex"
import { baseUrl } from "../../utils/env"
import { getErrorMessage } from "../../utils/error-message"
import { persistSession } from "../../utils/session.js"
import { waitForSessionReady } from "../../utils/session.js"
import { uploadAvatar } from "../../utils/upload.js"
export default {
	data() {
		return {
			title: "餐云点餐",
			// 去结算部分
			openOrderCartList: false,
			// 存放左侧滚动区域菜品分类数组
			typeListData: [],
			dishListData: [],
			// 存放右侧对应菜品每个菜名称的数组
			dishListItems: [],
			dishDetailes: {},
			openDetailPop: false,
			openMoreNormPop: false,
			moreNormDataes: null,
			tableInfo: null,
			moreNormDishdata: {},
			moreNormdata: [],
			// 套餐中查询到的菜品名称
			dishMealData: [],
			openTablePeoPleNumber: 1,
			orderData: 0,
			// 选中左侧菜品的索引
			typeIndex: 0,
			// 控制菜品详情显示
			openTablePop: false,
			// 规格有关的数组
			flavorDataes: [],
			// 加入购物车数量
			orderDishNumber: 0,
			// 菜品金额
			orderDishPrice: 0,
			params: {
				shopId: "f3deb",
				storeId: "1282344676983062530",
				tableId: "1282346960773238786",
			},
			// 添加一个右侧number更新以后重新刷新接口的id --- 这个id来自左侧菜品分类的id
			rightIdAndType: {},
			phoneData: "",
			tablewareNumber: 0,
			shopStatus: null,
			scrollTop: 0,
			menuHeight: 0, // 左边菜单的高度
			menuItemHeight: 0, // 左边菜单item的高度
			itemId: "", // 栏目右边scroll-view用于滚动的id
			arr: [],
			menuLoading: false,
			menuLoadFailed: false,
			menuLifecycleId: 0,
			menuRequestId: 0,
			categoryRequestId: 0,
			networkStatusHandler: null,
			isUnloaded: false,
			elRectRetryLimit: 50,
			elRectRetryTasks: [],
			profileEditorVisible: false,
			profileSaving: false,
			loginPromptPending: false,
			menuInitializationPromise: null,
		}
	},
	//   组件
	components: {
		CloudmealHeader,
		AppTabbar,
		StatePanel,
		Phone,
		popMask,
		popCart,
		dishDetail,
	},
	//   计算属性
	computed: {
		shouldPromptProfileEditor: function () {
			return Boolean(
				this.token() && this.profileCompleted() === false && !this.profilePromptSkipped()
			)
		},
		shopStatusText: function () {
			if (this.shopStatus === null) return "状态加载中"
			return this.shopStatus === 1 ? "营业中" : "休息中"
		},
		// 购物车信息列表
		orderListDataes: function () {
			return this.orderListData()
		},
		shopAddressText: function () {
			const info = this.shopInfo()
			return info && typeof info === 'object' && info.shopAddress
				? info.shopAddress
				: '门店信息暂未完善'
		},
		deliveryFeeText: function () {
			const fee = Number(this.deliveryFee())
			return Number.isFinite(fee) && fee >= 0 ? fee.toFixed(2) : '0.00'
		},
		// 计算购物车清单
		orderAndUserInfo: function () {
			let orderData = []
			Array.isArray(this.orderListDataes) &&
				this.orderListDataes.forEach((n, i) => {
					let userData = {}
					userData.nickName = n.name ?? ""
					userData.avatarUrl = n.image ?? ""
					userData.dishList = [n]
					const num = orderData.findIndex(
						(o) => o.nickName == userData.nickName
					)
					if (num != -1) {
						orderData[num].dishList.push(n)
					} else {
						orderData.push(userData)
					}
				})
			return orderData
		},
		ht: function () {
			return (
				uni.getMenuButtonBoundingClientRect().top +
				uni.getMenuButtonBoundingClientRect().height +
				7
			)
		},
	},

	onReady() {
		this.getMenuItemTop()
	},
	async onLoad(options) {
		this.isUnloaded = false
		this.networkStatusHandler = (res) => {
			if (!this.isUnloaded && res.isConnected === false) {
				uni.navigateTo({
					url: "/pages/nonet/index",
				})
			}
		}
		uni.onNetworkStatusChange(this.networkStatusHandler)
		if (options) {
			if (!options.status && !options.formOrder) {
				await this.getData()
			}
		}
	},
	onUnload() {
		this.isUnloaded = true
		this.menuLifecycleId += 1
		this.categoryRequestId += 1
		this.menuRequestId += 1
		if (this.networkStatusHandler && typeof uni.offNetworkStatusChange === "function") {
			uni.offNetworkStatusChange(this.networkStatusHandler)
		}
		this.networkStatusHandler = null
		const retryTasks = this.elRectRetryTasks.splice(0)
		retryTasks.forEach((task) => {
			clearTimeout(task.timer)
			task.resolve(false)
		})
	},
	async onShow() {
		await waitForSessionReady()
		this.syncProfileEditorVisibility()
		if (this.token()) {
			await this.initializeMenuOnce()
		} else {
			await this.getData()
		}
	},
	methods: {
		//   vuex储存信息
		...mapMutations([
			"setShopInfo", //设置店铺信息
			"setShopPhone", //设置电话
			"setShopStatus", //设置店铺状态
			"initdishListMut", //设置购物车订单
			"setStoreInfo",
			"setBaseUserInfo", //设置用户基本信息
			"setToken", //设置token
			"setProfileCompleted",
			"setProfilePromptSkipped",
			"setDeliveryFee", //设置配送费
			"setSelectedCoupon", // 新的结算流程不沿用上一张券
		]),
		// 从vuex信息
		...mapState([
			"shopInfo", //店铺信息
			"shopPhone", //电话
			"orderListData",
			"baseUserInfo", //用户信息
			"token", //token
			"profileCompleted",
			"profilePromptSkipped",
			"deliveryFee", //配送费
		]),
		loginSync() {
			return new Promise((resolve, reject) => {
				uni.login({
					provider: "weixin",
					success: (loginRes) => {
						if (loginRes && loginRes.errMsg === "login:ok" && loginRes.code) {
							resolve(loginRes.code)
							return
						}
						reject({ code: 'LOGIN_FAILED', message: '微信登录失败，请重试', raw: loginRes })
					},
					fail: error => reject({ code: 'LOGIN_FAILED', message: '微信登录失败，请重试', raw: error })
				})
			})
		},
		requestLocationWithoutBlockingLogin() {
			try {
				uni.getLocation({
					type: 'gcj02',
					isHighAccuracy: true,
					success() {},
					fail() {}
				})
			} catch (error) {}
		},
		async initializeMenuOnce() {
			if (this.menuInitializationPromise) return this.menuInitializationPromise
			const initialization = Promise.resolve().then(() => this.init())
			this.menuInitializationPromise = initialization
			try {
				return await initialization
			} finally {
				if (this.menuInitializationPromise === initialization) {
					this.menuInitializationPromise = null
				}
			}
		},
		async loginAndInitialize() {
			const code = await this.loginSync()
			const loginResult = await userLogin({ code })
			const data = loginResult.data || {}
			persistSession(this.$store, data)
			this.setProfilePromptSkipped(false)
			this.profileEditorVisible = !data.profileCompleted
			this.setDeliveryFee(data.deliveryFee)
			this.setShopInfo({
				shopName: data.shopName,
				shopAddress: data.shopAddress,
				shopId: data.shopId
			})
			this.requestLocationWithoutBlockingLogin()
			await this.initializeMenuOnce()
			return loginResult
		},
		syncProfileEditorVisibility() {
			this.profileEditorVisible = this.shouldPromptProfileEditor
		},
		skipProfileEditor() {
			this.setProfilePromptSkipped(true)
			this.profileEditorVisible = false
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
				persistSession(this.$store, response.data || { name, avatar, profileCompleted: true })
				this.setProfilePromptSkipped(false)
				this.profileEditorVisible = false
				return true
			} catch (error) {
				uni.showToast({ title: getErrorMessage(error, '资料保存失败，请重试'), icon: 'none' })
				return false
			} finally {
				this.profileSaving = false
			}
		},
		// 获取用户信息
		async getData() {
			await waitForSessionReady()
			let res = wx.getMenuButtonBoundingClientRect()
			// 获取店铺状态
			this.getShopInfo()
			this.selectHeight = res.height
			if (this.token() === "") {
				if (this.loginPromptPending) return false
				this.loginPromptPending = true
				let handled = false
				uni.showModal({
					title: "温馨提示",
					content: "亲，授权微信登录后才能点餐！",
					showCancel: false,
					success: async res => {
						handled = true
						if (res.confirm) {
							try {
								await this.loginAndInitialize()
							} catch (error) {
								uni.showToast({ title: getErrorMessage(error, '微信登录失败，请重试'), icon: 'none' })
							} finally {
								this.loginPromptPending = false
							}
						} else {
							this.loginPromptPending = false
						}
					},
					complete: () => {
						if (!handled) this.loginPromptPending = false
					}
				})
			}
			return true
		},

		async init() {
			const requestId = ++this.categoryRequestId
			const lifecycleId = ++this.menuLifecycleId
			this.menuLoading = true
			this.menuLoadFailed = false
			this.menuRequestId++
			if (this.typeIndex !== 0) this.typeIndex = 0

			this.getMerchantInfo()
			this.getTableOrderDishListes()
			try {
				const res = await getCategoryList()
				if (requestId !== this.categoryRequestId || lifecycleId !== this.menuLifecycleId) return
				if (!res || res.code !== 1) {
					throw new Error((res && res.msg) || '菜单加载失败，请重试')
				}

				const categories = Array.isArray(res.data) ? res.data : []
				this.typeListData = categories
				if (categories.length > 0) {
					const loaded = await this.getDishListDataes(
						categories[this.typeIndex || 0],
						this.typeIndex || 0
					)
					if (requestId !== this.categoryRequestId || lifecycleId !== this.menuLifecycleId) return
					this.menuLoadFailed = loaded === false
				}
			} catch (error) {
				if (requestId !== this.categoryRequestId || lifecycleId !== this.menuLifecycleId) return
				this.menuLoadFailed = true
				this.showMenuError(error)
			} finally {
				if (requestId === this.categoryRequestId && lifecycleId === this.menuLifecycleId) {
					this.menuLoading = false
				}
			}
		},
		reloadMenu() {
			return this.init()
		},
		// 点击左边的栏目切换
		async swichMenu(params, index) {
			if (this.arr.length == 0) {
				await this.getMenuItemTop()
			}
			if (index == this.typeIndex) return
			const lifecycleId = ++this.menuLifecycleId
			this.menuLoading = true
			this.menuLoadFailed = false
			this.$nextTick(function () {
				this.typeIndex = index
				this.leftMenuStatus(index)
			})
			try {
				const loaded = await this.getDishListDataes(params, index)
				if (lifecycleId !== this.menuLifecycleId || loaded === null) return
				this.menuLoadFailed = loaded === false
			} finally {
				if (lifecycleId === this.menuLifecycleId) this.menuLoading = false
			}
		},
		// 获取一个目标元素的高度
		getElRect(elClass, dataVal, retryCount = 0) {
			return new Promise((resolve) => {
				if (this.isUnloaded) {
					resolve(false)
					return
				}
				const query = uni.createSelectorQuery().in(this)
				query
					.select("." + elClass)
					.fields(
						{
							size: true,
						},
						(res) => {
							if (this.isUnloaded) {
								resolve(false)
								return
							}
							// 如果节点尚未生成，res值为null，循环调用执行
							if (!res) {
								if (retryCount >= this.elRectRetryLimit) {
									resolve(false)
									return
								}
								const retryTask = { timer: null, resolve }
								retryTask.timer = setTimeout(() => {
									const taskIndex = this.elRectRetryTasks.indexOf(retryTask)
									if (taskIndex > -1) this.elRectRetryTasks.splice(taskIndex, 1)
									this.getElRect(elClass, dataVal, retryCount + 1).then(resolve)
								}, 10)
								this.elRectRetryTasks.push(retryTask)
								return
							}
							this[dataVal] = res.height
							resolve(true)
						}
					)
					.exec()
			})
		},
		// 设置左边菜单的滚动状态
		async leftMenuStatus(index) {
			this.typeIndex = index
			// 如果为0，意味着尚未初始化
			if (this.menuHeight == 0 || this.menuItemHeight == 0) {
				await this.getElRect("menu-scroll-view", "menuHeight")
				await this.getElRect("type_item", "menuItemHeight")
			}
			// 将菜单活动item垂直居中
			this.scrollTop =
				index * this.menuItemHeight +
				this.menuItemHeight / 2 -
				this.menuHeight / 2
		},
		// 获取右边菜单每个item到顶部的距离
		getMenuItemTop() {
			return new Promise((resolve) => {
				let selectorQuery = uni.createSelectorQuery().in(this)
				selectorQuery
					.selectAll(".type_list .type_item")
					.boundingClientRect((rects) => {
						this.arr = rects || []
						resolve(this.arr)
					})
					.exec()
			})
		},
		// 获取菜品列表
		async getDishListDataes(params, index) {
			const requestId = ++this.menuRequestId
			if (index !== undefined) this.typeIndex = index
			this.dishListData = []
			this.dishListItems = []
			this.rightIdAndType = {
				id: params.id,
				type: params.type,
			}
			const param = {
				categoryId: params.id,
			}

			try {
				const response = params.type === 1
					? await dishListByCategoryId(param)
					: await querySetmeaList(param)
				if (requestId !== this.menuRequestId) return null
				if (!response || response.code !== 1) {
					this.showMenuError(new Error((response && response.msg) || '菜单加载失败，请重试'))
					return false
				}

				const rows = Array.isArray(response.data) ? response.data : []
				this.dishListData = rows.map((obj) => ({
					...obj,
					type: params.type === 1 ? 1 : 2,
					newCardNumber: 0,
				}))
				this.setOrderNum()
				return true
			} catch (error) {
				if (requestId !== this.menuRequestId) return null
				this.showMenuError(error)
				return false
			}
		},
		// 获取首页店铺信息
		async getShopInfo() {
			await getShopStatus()
				.then((res) => {
					this.shopStatus = res.data
					console.log(res.data);
					this.setShopStatus(res.data)
				})
				.catch((err) => { })
		},
		// 获取店铺电话
		async getMerchantInfo() {
			await getShopInfo()
				.then((res) => {
					this.phoneData = res.data.phone
					this.shopStatus = res.data.status
					this.setShopStatus(res.data.status)
					this.setShopInfo(res.data)
					this.setShopPhone(res.data.phone || '')
					this.setDeliveryFee(res.data.deliveryFee)
				})
				.catch((err) => { })
		},
		// 重新拼装image
		getNewImage(image) {
			return `${baseUrl}/common/download?name=${image}`
		},
		// 获取购物车订单列表
		async getTableOrderDishListes() {
			// 调用获取购物车集合接口
			await getShoppingCartList({})
				.then((res) => {
					if (res.code === 1) {
						const orderList = Array.isArray(res.data) ? res.data : []
						this.initdishListMut(orderList)
						this.computOrderInfo()
					}
				})
				.catch((err) => { })
		},
		// 去订单页面
		goOrder() {
			if (this.shopStatus !== 1) {
				uni.showToast({
					title: "门店休息中，暂时无法结算",
					icon: "none",
				})
				return
			}
			if (this.orderListData().length === 0) return
			this.setSelectedCoupon(null)
			uni.navigateTo({
				url: "/pages/order/index",
			})
		},
		showMenuError(error) {
			uni.showToast({
				title: getErrorMessage(error, "菜单加载失败，请重试"),
				icon: "none",
			})
		},
		async refreshCartAndMenu() {
			await this.getTableOrderDishListes()
			await this.getDishListDataes(this.rightIdAndType)
		},
		// 加菜 - 添加菜品
		async addDishAction(item, form) {
			if (item && item.obj) {
				form = item.item
				item = item.obj
			}
			// 规格
			if (
				this.openMoreNormPop &&
				(!this.flavorDataes || this.flavorDataes.length <= 0)
			) {
				uni.showToast({
					title: "请选择规格",
					icon: "none",
				})
				return false
			}
			this.openMoreNormPop = false
			// 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
			this.tablewareNumber++
			this.dishDetailes.dishNumber++
			if (
				this.orderListDataes &&
				!this.orderListDataes.some((n) => n.id == item.dishId) &&
				this.flavorDataes.length > 0
			) {
				item.flavorRemark = JSON.stringify(this.flavorDataes)
			}
			// 有sort字段是菜品
			let dishFlavorDatas = ""
			let flavorRemark = []
			if (item.flavorRemark) {
				flavorRemark = JSON.parse(item.flavorRemark)
			}
			if (item.dishFlavor !== "" && item.dishFlavor) {
				dishFlavorDatas = item.dishFlavor
			} else if (flavorRemark.length > 0) {
				dishFlavorDatas = flavorRemark.join(',')
			} else {
				dishFlavorDatas = null
			}
			let params = {
				dishFlavor: dishFlavorDatas,
			}
			if (item.type === 1) {
				params = {
					...params,
					dishId: item.id,
				}
			} else if (item.type === 2) {
				params = {
					setmealId: item.id,
				}
			} else if (form === "购物车") {
				if (item.dishId) {
					params = {
						...params,
						dishId: item.dishId,
					}
				} else {
					params = {
						setmealId: item.setmealId,
					}
				}
			}
			try {
				const res = await newAddShoppingCartAdd(params)
				if (res.code === 1) {
					await this.refreshCartAndMenu()
					this.flavorDataes = []
				}
			} catch (err) { }
		},
		// 加入购物车
		addShop(item) {
			console.log(item);
			this.dishDetailes = item
			return this.addDishAction(item, "普通")
		},
		// 减菜 - 添加菜品
		async redDishAction(item, form) {
			if (item && item.obj) {
				form = item.item
				item = item.obj
			}
			// 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
			this.tablewareNumber--
			this.dishDetailes.dishNumber--
			let dishFlavorDatas = ""
			let flavorRemark = []
			if (item.flavorRemark) {
				flavorRemark = JSON.parse(item.flavorRemark)
			}
			if (item.dishFlavor !== "" && item.dishFlavor) {
				dishFlavorDatas = item.dishFlavor
			} else if (flavorRemark.length > 0) {
				dishFlavorDatas = flavorRemark[0]
			} else {
				dishFlavorDatas = null
			}
			let params = {
				dishFlavor: dishFlavorDatas,
			}
			if (item.type === 1) {
				params = {
					...params,
					dishId: item.id,
				}
			} else if (item.type === 2) {
				params = {
					// ...params,
					setmealId: item.id,
				}
			} else if (form === "购物车") {
				if (item.dishId) {
					params = {
						...params,
						dishId: item.dishId,
					}
				} else {
					params = {
						setmealId: item.setmealId,
					}
				}
			}
			try {
				const res = await newShoppingCartSub(params)
				if (res.code === 1) {
					await this.refreshCartAndMenu()
				}
			} catch (err) { }
		},
		// 清空购物车
		async clearCardOrder() {
			try {
				const res = await delShoppingCart()
				if (res.code === 1) {
					this.openOrderCartList = false
					await this.refreshCartAndMenu()
				}
			} catch (err) { }
		},
		// 打开菜品牌详情
		openDetailHandle(item) {
			this.dishDetailes = item
			if (item.type === 2) {
				querySetmealDishById({
					id: item.id,
				})
					.then((res) => {
						if (res.code === 1) {
							this.openDetailPop = true
							this.dishMealData = res.data
						}
					})
					.catch((err) => { })
			} else {
				this.openDetailPop = true
			}
		},
		// 关闭菜品详情
		dishClose() {
			this.openDetailPop = false
		},
		// 多规格数据处理
		moreNormDataesHandle(item) {
			this.flavorDataes.splice(0)
			this.moreNormDishdata = item
			this.openDetailPop = false
			this.openMoreNormPop = true
			this.moreNormdata = item.flavors.map((obj) => ({
				...obj,
				value: JSON.parse(obj.value),
			}))
			this.moreNormdata.forEach((item) => {
				if (item.value && item.value.length > 0) {
					this.flavorDataes.push(item.value[0])
				}
			})
		},
		// 选规格 处理一行只能选择一种
		checkMoreNormPop(val) {
			let obj = val.obj
			let item = val.item
			let ind
			let findst = obj.some((n) => {
				ind = this.flavorDataes.findIndex((o) => o == n)
				return ind != -1
			})
			const num = this.flavorDataes.findIndex((it) => it == item)
			if (num == -1 && !findst) {
				this.flavorDataes.push(item)
			} else if (findst) {
				this.flavorDataes.splice(ind, 1)
				this.flavorDataes.push(item)
			} else {
				this.flavorDataes.splice(num, 1)
			}
		},
		// 关闭选规格弹窗
		closeMoreNorm(moreNormDishdata) {
			this.flavorDataes.splice(0, this.flavorDataes.length)
			this.openMoreNormPop = false
		},
		// 订单里和总订单价格计算
		computOrderInfo() {
			let oriData = this.orderListDataes
			this.orderDishNumber = this.orderDishPrice = 0
			oriData.map((n, i) => {
				this.orderDishNumber += n.number
				this.orderDishPrice += n.number * n.amount
			})
			this.orderDishPrice = this.orderDishPrice
		},
		// 处理点餐数量 - 更新菜品已点餐数量
		setOrderNum() {
			let ODate = this.dishListData
			let CData = this.orderListDataes
			ODate &&
				ODate.map((obj, index) => {
					obj.dishNumber = 0
					// 去除空的规格
					if (obj.flavors) {
						obj.flavors.forEach((value, i) => {
							if (value.name === "") {
								obj.flavors.splice(i, 1)
							}
						})
					}

					if (CData.length > 0) {
						CData &&
							CData.forEach((tg, ind) => {
								if (obj.id === tg.dishId) {
									obj.dishNumber = tg.number
								}
								if (obj.id === tg.setmealId) {
									obj.dishNumber = tg.number
								}
							})
					}
				})
			if (this.dishListItems.length == 0) {
				this.dishListItems = ODate
			} else {
				this.dishListItems.splice(0, this.dishListItems.length, ...ODate)
			}
		},
		// 拨打电话弹层
		handlePhone(type) {
			this.$refs.phone.$refs.popup.open(type)
		},
		// 关闭电话弹层
		closePopup(type) {
			this.$refs.phone.$refs.popup.close(type)
		},
		disabledScroll() {
			return false
		},
	},
}
