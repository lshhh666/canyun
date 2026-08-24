<template>
  <main class="cm-page dashboard-container management-list">
    <PageHeader title="优惠券管理" description="创建优惠券草稿并控制开始发放与停止发放" />
    <div class="container cm-surface">
      <div class="tableBar cm-filter-bar">
        <div class="filters">
          <label>优惠券名称：</label>
          <el-input
            v-model="name"
            clearable
            placeholder="请输入优惠券名称"
            @clear="search"
            @keyup.enter.native="search"
          />
          <label>状态：</label>
          <el-select v-model="status" clearable placeholder="全部状态" @clear="search">
            <el-option label="草稿" :value="0" />
            <el-option label="发放中" :value="1" />
            <el-option label="已停用" :value="2" />
          </el-select>
          <el-button class="cm-query-action" @click="search">查询</el-button>
        </div>
        <el-button type="primary" class="cm-primary-action" @click="openCreate">
          + 新建优惠券
        </el-button>
      </div>

      <el-table v-if="tableData.length" :data="tableData" stripe class="tableBox">
        <el-table-column prop="name" label="优惠券名称" min-width="130" />
        <el-table-column label="优惠规则" min-width="130">
          <template slot-scope="scope">
            满￥{{ money(scope.row.thresholdAmount) }}减￥{{ money(scope.row.discountAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="剩余/总库存" width="120">
          <template slot-scope="scope">{{ scope.row.stock }}/{{ scope.row.totalStock }}</template>
        </el-table-column>
        <el-table-column label="领取时间" min-width="230">
          <template slot-scope="scope">
            {{ timeText(scope.row.receiveStartTime) }} 至 {{ timeText(scope.row.receiveEndTime) }}
          </template>
        </el-table-column>
        <el-table-column label="使用时间" min-width="230">
          <template slot-scope="scope">
            {{ timeText(scope.row.validStartTime) }} 至 {{ timeText(scope.row.validEndTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template slot-scope="scope">
            <StatusTag :status="statusTone(scope.row.status)" :text="statusText(scope.row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" class="blueBug" @click="openDetail(scope.row)">
              {{ statusValue(scope.row.status) === 0 ? '编辑' : '查看' }}
            </el-button>
            <el-button
              v-if="statusValue(scope.row.status) === 0"
              type="text"
              class="blueBug"
              @click="start(scope.row)"
            >开始发放</el-button>
            <el-button
              v-if="statusValue(scope.row.status) === 1"
              type="text"
              class="delBut"
              @click="stop(scope.row)"
            >停止发放</el-button>
            <el-button
              v-if="statusValue(scope.row.status) === 0"
              type="text"
              class="delBut"
              @click="remove(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState
        v-else
        :title="searched ? '未找到符合条件的优惠券' : '暂无优惠券'"
        description="创建草稿并确认规则后即可开始发放"
      />
      <el-pagination
        v-if="total > 0"
        class="pageList"
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[10, 20, 30, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="changePageSize"
        @current-change="changePage"
      />
    </div>

    <el-dialog
      :title="dialogTitle"
      :visible.sync="dialogVisible"
      width="720px"
      custom-class="coupon-dialog"
      :close-on-click-modal="false"
    >
      <el-form ref="couponForm" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="form.name" :disabled="isReadonly" maxlength="64" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="使用门槛" prop="thresholdAmount">
            <el-input-number v-model="form.thresholdAmount" :disabled="isReadonly" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="优惠金额" prop="discountAmount">
            <el-input-number v-model="form.discountAmount" :disabled="isReadonly" :min="0.01" :precision="2" />
          </el-form-item>
        </div>
        <el-form-item label="发行总量" prop="totalStock">
          <el-input-number v-model="form.totalStock" :disabled="isReadonly" :min="1" :step="1" :precision="0" />
          <span v-if="isReadonly" class="stock-tip">剩余库存 {{ form.stock }}</span>
        </el-form-item>
        <el-form-item label="领取时间" prop="receiveRange">
          <el-date-picker
            v-model="form.receiveRange"
            :disabled="isReadonly"
            type="datetimerange"
            value-format="yyyy-MM-dd HH:mm"
            range-separator="至"
            start-placeholder="领取开始时间"
            end-placeholder="领取结束时间"
          />
        </el-form-item>
        <el-form-item label="使用时间" prop="validRange">
          <el-date-picker
            v-model="form.validRange"
            :disabled="isReadonly"
            type="datetimerange"
            value-format="yyyy-MM-dd HH:mm"
            range-separator="至"
            start-placeholder="使用开始时间"
            end-placeholder="使用截止时间"
          />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">{{ isReadonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!isReadonly" type="primary" @click="submit">保存</el-button>
      </span>
    </el-dialog>
  </main>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import PageHeader from '@/components/PageHeader/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import EmptyState from '@/components/EmptyState/index.vue'
import {
  createCoupon,
  deleteCoupon,
  getCouponDetail,
  getCouponPage,
  startCoupon,
  stopCoupon,
  updateCoupon
} from '@/api/coupon'

function emptyForm() {
  return {
    id: null,
    name: '',
    thresholdAmount: 0,
    discountAmount: 0.01,
    totalStock: 1,
    stock: 0,
    receiveRange: [],
    validRange: []
  }
}

@Component({
  name: 'CouponManagement',
  components: { PageHeader, StatusTag, EmptyState }
})
export default class extends Vue {
  private name = ''
  private status: number = null
  private page = 1
  private pageSize = 10
  private total = 0
  private tableData: any[] = []
  private searched = false
  private dialogVisible = false
  private isReadonly = false
  private form: any = emptyForm()

  get dialogTitle() {
    if (!this.form.id) return '新建优惠券'
    return this.isReadonly ? '查看优惠券' : '编辑优惠券草稿'
  }

  get rules() {
    return {
      name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
      thresholdAmount: [{ validator: this.validateAmounts, trigger: 'change' }],
      discountAmount: [{ validator: this.validateAmounts, trigger: 'change' }],
      totalStock: [{ validator: this.validateStock, trigger: 'change' }],
      receiveRange: [{ validator: this.validateReceiveRange, trigger: 'change' }],
      validRange: [{ validator: this.validateValidRange, trigger: 'change' }]
    }
  }

  created() {
    this.load()
  }

  private async load() {
    try {
      const response = await getCouponPage({
        page: this.page,
        pageSize: this.pageSize,
        name: this.name || undefined,
        status: this.status === null ? undefined : this.status
      })
      if (!this.isSuccess(response)) return this.showBusinessError(response)
      const data = response.data.data || {}
      this.tableData = Array.isArray(data.records) ? data.records : []
      this.total = Number(data.total || 0)
    } catch (error) {
      this.$message.error('优惠券列表加载失败，请重试')
    }
  }

  private search() {
    this.page = 1
    this.searched = Boolean(this.name || this.status !== null)
    this.load()
  }

  private openCreate() {
    this.form = emptyForm()
    this.isReadonly = false
    this.dialogVisible = true
    this.clearValidation()
  }

  private async openDetail(row: any) {
    try {
      const response = await getCouponDetail(row.id)
      if (!this.isSuccess(response)) return this.showBusinessError(response)
      const coupon = response.data.data
      this.form = {
        id: coupon.id,
        name: coupon.name,
        thresholdAmount: Number(coupon.thresholdAmount),
        discountAmount: Number(coupon.discountAmount),
        totalStock: Number(coupon.totalStock),
        stock: Number(coupon.stock),
        receiveRange: [this.formTimeText(coupon.receiveStartTime), this.formTimeText(coupon.receiveEndTime)],
        validRange: [this.formTimeText(coupon.validStartTime), this.formTimeText(coupon.validEndTime)]
      }
      this.isReadonly = this.statusValue(coupon.status) !== 0
      this.dialogVisible = true
      this.clearValidation()
    } catch (error) {
      this.$message.error('优惠券详情加载失败，请重试')
    }
  }

  private submit() {
    const formRef: any = this.$refs.couponForm
    formRef.validate(async (valid: boolean) => {
      if (!valid) return
      const payload = {
        name: this.form.name.trim(),
        thresholdAmount: this.form.thresholdAmount,
        discountAmount: this.form.discountAmount,
        totalStock: this.form.totalStock,
        receiveStartTime: this.form.receiveRange[0],
        receiveEndTime: this.form.receiveRange[1],
        validStartTime: this.form.validRange[0],
        validEndTime: this.form.validRange[1]
      }
      try {
        const response = this.form.id
          ? await updateCoupon(this.form.id, payload)
          : await createCoupon(payload)
        if (!this.isSuccess(response)) return this.showBusinessError(response)
        this.$message.success(this.form.id ? '优惠券修改成功' : '优惠券创建成功')
        this.dialogVisible = false
        this.load()
      } catch (error) {
        this.$message.error('优惠券保存失败，请重试')
      }
    })
  }

  private start(row: any) {
    this.confirmAction('确认开始发放该优惠券？', () => startCoupon(row.id), '优惠券已开始发放')
  }

  private stop(row: any) {
    this.confirmAction('停发后用户将不能继续领取，是否确认？', () => stopCoupon(row.id), '优惠券已停止发放')
  }

  private remove(row: any) {
    this.confirmAction('草稿删除后无法恢复，是否确认删除？', () => deleteCoupon(row.id), '优惠券草稿已删除')
  }

  private confirmAction(message: string, action: Function, successMessage: string) {
    this.$confirm(message, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        const response = await action()
        if (!this.isSuccess(response)) return this.showBusinessError(response)
        this.$message.success(successMessage)
        this.load()
      } catch (error) {
        this.$message.error('操作失败，请刷新后重试')
      }
    }).catch(() => undefined)
  }

  private validateAmounts(rule: any, value: any, callback: Function) {
    const threshold = Number(this.form.thresholdAmount)
    const discount = Number(this.form.discountAmount)
    if (!Number.isFinite(threshold) || threshold < 0) return callback(new Error('使用门槛不能小于0'))
    if (!Number.isFinite(discount) || discount <= 0) return callback(new Error('优惠金额必须大于0'))
    if (threshold > 0 && discount > threshold) return callback(new Error('优惠金额不能大于使用门槛'))
    callback()
  }

  private validateStock(rule: any, value: any, callback: Function) {
    const stock = Number(value)
    if (!Number.isInteger(stock) || stock <= 0) return callback(new Error('发行总量必须是正整数'))
    callback()
  }

  private validateReceiveRange(rule: any, value: any, callback: Function) {
    if (!Array.isArray(value) || value.length !== 2) return callback(new Error('请选择完整的领取时间'))
    if (new Date(value[0]).getTime() >= new Date(value[1]).getTime()) {
      return callback(new Error('领取开始时间必须早于结束时间'))
    }
    callback()
  }

  private validateValidRange(rule: any, value: any, callback: Function) {
    if (!Array.isArray(value) || value.length !== 2) return callback(new Error('请选择完整的使用时间'))
    const start = new Date(value[0]).getTime()
    const end = new Date(value[1]).getTime()
    if (start >= end) return callback(new Error('使用开始时间必须早于截止时间'))
    const receiveEnd = this.form.receiveRange && new Date(this.form.receiveRange[1]).getTime()
    if (Number.isFinite(receiveEnd) && receiveEnd > end) {
      return callback(new Error('领取结束时间不能晚于使用截止时间'))
    }
    callback()
  }

  private statusValue(status: any) {
    if (status && typeof status === 'object' && status.value !== undefined) return Number(status.value)
    if (typeof status === 'number' || /^\d+$/.test(String(status || ''))) return Number(status)
    return { DRAFT: 0, DISTRIBUTING: 1, DISABLED: 2 }[String(status || '').toUpperCase()]
  }

  private statusText(status: any) {
    return ['草稿', '发放中', '已停用'][this.statusValue(status)] || '未知状态'
  }

  private statusTone(status: any) {
    const value = this.statusValue(status)
    return value === 1 ? 'success' : value === 2 ? 'danger' : 'neutral'
  }

  private money(value: any) {
    const amount = Number(value)
    return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
  }

  private timeText(value: any) {
    if (!value) return ''
    if (Array.isArray(value)) {
      const pad = (part: any) => String(part || 0).padStart(2, '0')
      return `${value[0]}-${pad(value[1])}-${pad(value[2])} ${pad(value[3])}:${pad(value[4])}:${pad(value[5])}`
    }
    return String(value).replace('T', ' ').slice(0, 19)
  }

  // 后端统一按“年-月-日 时:分”解析 LocalDateTime，表单提交必须保持同一格式。
  private formTimeText(value: any) {
    return this.timeText(value).slice(0, 16)
  }

  private isSuccess(response: any) {
    return response && response.data && String(response.data.code) === '1'
  }

  private showBusinessError(response: any) {
    const data = response && response.data
    this.$message.error((data && (data.msg || data.desc)) || '操作失败')
  }

  private clearValidation() {
    this.$nextTick(() => {
      const formRef: any = this.$refs.couponForm
      if (formRef) formRef.clearValidate()
    })
  }

  private changePageSize(size: number) {
    this.pageSize = size
    this.page = 1
    this.load()
  }

  private changePage(page: number) {
    this.page = page
    this.load()
  }
}
</script>

<style lang="scss" scoped>
.dashboard-container {
  margin: 30px;
}

.container {
  padding: 30px 28px;
  border-radius: 4px;
}

.tableBar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;

  .el-input,
  .el-select {
    width: 180px;
  }
}

.tableBox {
  width: 100%;
  border: 1px solid $gray-5;
  border-bottom: 0;
}

.pageList {
  margin-top: 30px;
  text-align: center;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;

  .el-form-item {
    width: auto;
    min-width: 0;
  }

  .el-input-number {
    width: 100%;
  }
}

.stock-tip {
  margin-left: 12px;
  color: $gray-6;
}
</style>

<style lang="scss">
.coupon-dialog {
  .el-date-editor {
    width: 100%;
  }

  .el-dialog__body {
    padding: 30px 36px 20px;
  }
}
</style>
