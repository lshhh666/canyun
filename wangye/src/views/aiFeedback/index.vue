<template>
  <main class="cm-page dashboard-container ai-feedback-page">
    <PageHeader
      title="AI客服评价"
      description="查看客服解决效果，并追踪用户反馈为“没解决”的完整问答"
    />

    <section class="cm-surface feedback-filter">
      <div class="feedback-filter__fields">
          <label for="feedback-date-begin">评价时间：</label>
          <el-date-picker
            v-model="dateRange"
            :id="['feedback-date-begin', 'feedback-date-end']"
            type="daterange"
          value-format="yyyy-MM-dd"
          range-separator="至"
          start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="true"
          />
          <label class="visually-hidden" for="feedback-date-end">评价结束日期</label>
      </div>
      <div class="feedback-filter__actions">
        <el-button :disabled="loading" @click="reset">重置</el-button>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>
    </section>

    <EmptyState
      v-if="statisticsError"
      class="cm-surface feedback-error"
      type="error"
      title="评价统计加载失败"
      description="请稍后重试，或检查服务连接"
    >
      <template #action>
        <el-button type="primary" size="small" @click="loadStatistics">重新加载</el-button>
      </template>
    </EmptyState>

    <section
      v-else
      v-loading="statisticsLoading"
      class="feedback-statistics"
      aria-label="AI客服评价统计"
    >
        <article class="cm-surface statistic-card">
          <span>评价总数</span>
          <strong>{{ statistics.totalCount }}</strong>
        </article>
        <article class="cm-surface statistic-card statistic-card--success">
          <span>有帮助</span>
          <strong>{{ statistics.helpfulCount }}</strong>
        </article>
        <article class="cm-surface statistic-card statistic-card--danger">
          <span>没解决</span>
          <strong>{{ statistics.unsolvedCount }}</strong>
        </article>
        <article class="cm-surface statistic-card statistic-card--primary">
          <span>解决率</span>
          <strong>{{ resolutionRateText }}</strong>
        </article>
    </section>

    <section class="cm-surface feedback-list">
        <div class="feedback-list__header">
          <div>
            <h2>没解决的评价</h2>
            <p>点击每行左侧箭头，可查看用户问题与 AI 回答全文</p>
          </div>
          <span>共 {{ total }} 条</span>
        </div>

        <EmptyState
          v-if="pageError"
          type="error"
          title="未解决评价加载失败"
          description="请稍后重试，或检查服务连接"
        >
          <template #action>
            <el-button type="primary" size="small" @click="loadPage">重新加载</el-button>
          </template>
        </EmptyState>

        <el-table
          v-else-if="tableData.length || pageLoading"
          ref="feedbackTable"
          v-loading="pageLoading"
          :data="tableData"
          stripe
          class="feedback-table"
          row-key="feedbackId"
          @expand-change="onExpandChange"
        >
          <el-table-column type="expand" width="48">
            <template slot-scope="scope">
              <div class="feedback-detail">
                <div>
                  <label>用户问题</label>
                  <p>{{ scope.row.userQuestion || '无' }}</p>
                </div>
                <div>
                  <label>AI 回答</label>
                  <p>{{ scope.row.aiAnswer || '无' }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="评价时间" width="180">
            <template slot-scope="scope">{{ timeText(scope.row.feedbackTime) }}</template>
          </el-table-column>
          <el-table-column prop="userId" label="用户ID" width="110" />
          <el-table-column prop="sessionId" label="会话ID" width="110" />
          <el-table-column label="处理状态" width="110">
            <template slot-scope="scope">
              <StatusTag
                :text="scope.row.handleStatusDesc || '待处理'"
                :tone="scope.row.handleStatus === 'PENDING_RETEST' ? 'warning' : 'neutral'"
              />
            </template>
          </el-table-column>
          <el-table-column label="用户问题" min-width="240">
            <template slot-scope="scope">
              <p class="feedback-summary" :title="scope.row.userQuestion">
                {{ scope.row.userQuestion || '无' }}
              </p>
            </template>
          </el-table-column>
          <el-table-column label="AI回答" min-width="300">
            <template slot-scope="scope">
              <p class="feedback-summary" :title="scope.row.aiAnswer">
                {{ scope.row.aiAnswer || '无' }}
              </p>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template slot-scope="scope">
              <el-button
                v-if="scope.row.handleStatus !== 'PENDING_RETEST'"
                type="text"
                @click="openRetest(scope.row)"
              >
                处理
              </el-button>
              <el-button
                v-else
                type="text"
                @click="openReview(scope.row)"
              >
                查看复测
              </el-button>
              <el-button
                type="text"
                :aria-expanded="isExpanded(scope.row.feedbackId) ? 'true' : 'false'"
                :aria-label="`${isExpanded(scope.row.feedbackId) ? '收起' : '查看'}会话${scope.row.sessionId}完整问答`"
                @click="toggleDetails(scope.row)"
              >
                {{ isExpanded(scope.row.feedbackId) ? '收起' : '查看全文' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <EmptyState
          v-else
          title="所选时间范围暂无“没解决”的评价"
          description="可以切换其他时间范围继续查看"
        />

        <el-pagination
          v-if="total > 0"
          class="feedback-pagination"
          :current-page="page"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :disabled="pageLoading"
          @size-change="changePageSize"
          @current-change="changePage"
        />
    </section>

    <el-dialog
      title="关联知识并发起复测"
      :visible.sync="retestDialogVisible"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="retestForm"
        :model="retestForm"
        :rules="retestRules"
        label-width="92px"
      >
        <el-form-item label="复测问题" prop="question">
          <el-input
            v-model.trim="retestForm.question"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
          />
          <p class="field-help">默认使用该评价最后一次用户提问，可按实际问题修正。</p>
        </el-form-item>
        <el-form-item label="关联知识" prop="knowledgeKeys">
          <el-select
            v-model="retestForm.knowledgeKeys"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchKnowledge"
            :loading="knowledgeLoading"
            placeholder="输入知识标识或标题搜索"
          >
            <el-option
              v-for="item in knowledgeOptions"
              :key="item.knowledgeKey"
              :label="`${item.title}（${item.knowledgeKey}）`"
              :value="item.knowledgeKey"
            />
          </el-select>
          <p class="field-help">只显示已同步知识，最多选择10条。</p>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button :disabled="retestSubmitting" @click="retestDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="retestSubmitting" @click="submitRetest">
          提交复测
        </el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="复测结果"
      :visible.sync="reviewDialogVisible"
      width="640px"
      :close-on-click-modal="false"
      @closed="stopReviewPolling"
    >
      <div v-loading="reviewLoading" class="retest-review">
        <EmptyState
          v-if="reviewError"
          type="error"
          title="复测结果加载失败"
          description="请稍后重试"
        >
          <template #action>
            <el-button type="primary" size="small" @click="loadReviewDetail(true)">
              重新加载
            </el-button>
          </template>
        </EmptyState>
        <template v-else-if="reviewDetail">
          <div class="retest-review__status">
            <StatusTag
              :text="reviewDetail.executionStatusDesc"
              :tone="reviewStatusTone(reviewDetail.executionStatus)"
            />
            <span>
              已重试 {{ reviewDetail.retryCount || 0 }}/{{ reviewDetail.maxRetryCount || 0 }} 次
            </span>
          </div>
          <div class="retest-review__block">
            <label>复测问题</label>
            <p>{{ reviewDetail.question }}</p>
          </div>
          <div class="retest-review__block">
            <label>复测回答</label>
            <p>{{ reviewDetail.answer || '尚未生成回答' }}</p>
          </div>
          <div v-if="reviewDetail.usedKnowledgeIds" class="retest-review__block">
            <label>知识版本快照</label>
            <p>{{ reviewDetail.usedKnowledgeIds }}</p>
          </div>
          <div v-if="reviewDetail.lastError" class="retest-review__error">
            <label>最近失败原因（仅后台可见）</label>
            <p>{{ reviewDetail.lastError }}</p>
          </div>
          <p v-if="isReviewRunning" class="field-help">
            系统正在后台复测，本窗口每5秒自动刷新。
          </p>
          <p v-if="reviewDetail.reviewResultDesc" class="retest-review__reviewed">
            人工确认结果：{{ reviewDetail.reviewResultDesc }}
          </p>
        </template>
      </div>
      <span slot="footer">
        <el-button :disabled="reviewSubmitting" @click="reviewDialogVisible = false">
          关闭
        </el-button>
        <el-button
          v-if="canRestartRetest"
          type="primary"
          @click="restartRetest"
        >
          修改知识并重新复测
        </el-button>
        <template v-if="canReviewRetest">
          <el-button :loading="reviewSubmitting" @click="submitReview('INCORRECT')">
            回答仍不正确
          </el-button>
          <el-button
            type="primary"
            :loading="reviewSubmitting"
            @click="submitReview('CORRECT')"
          >
            回答正确，标记已处理
          </el-button>
        </template>
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
  AiFeedbackDateParams,
  AiFeedbackPageData,
  AiFeedbackRecord,
  AiFeedbackStatistics,
  AiFeedbackRetestDetail,
  AiFeedbackRetestExecutionStatus,
  AiFeedbackRetestReviewResult,
  getLatestAiFeedbackRetest,
  getAiFeedbackStatistics,
  getUnsolvedAiFeedbackPage,
  reviewAiFeedbackRetest,
  submitAiFeedbackRetest
} from '@/api/aiFeedback'
import {
  AiKnowledgeRecord,
  getAiKnowledgePage
} from '@/api/aiKnowledge'

function emptyStatistics(): AiFeedbackStatistics {
  return {
    helpfulCount: 0,
    unsolvedCount: 0,
    totalCount: 0,
    resolutionRate: 0
  }
}

@Component({
  name: 'AiFeedback',
  components: { PageHeader, StatusTag, EmptyState }
})
export default class extends Vue {
  private dateRange: string[] | null = []
  private appliedDateRange: string[] = []
  private statistics: AiFeedbackStatistics = emptyStatistics()
  private tableData: AiFeedbackRecord[] = []
  private page = 1
  private pageSize = 10
  private total = 0
  private statisticsLoading = false
  private pageLoading = false
  private statisticsError = false
  private pageError = false
  private statisticsRequestId = 0
  private pageRequestId = 0
  private expandedFeedbackIds: number[] = []
  private retestDialogVisible = false
  private retestSubmitting = false
  private retestFeedbackId: number | null = null
  private retestForm: { question: string; knowledgeKeys: string[] } = {
    question: '',
    knowledgeKeys: []
  }
  private knowledgeOptions: AiKnowledgeRecord[] = []
  private knowledgeLoading = false
  private knowledgeRequestId = 0
  private reviewDialogVisible = false
  private reviewLoading = false
  private reviewSubmitting = false
  private reviewError = false
  private reviewFeedbackId: number | null = null
  private reviewDetail: AiFeedbackRetestDetail | null = null
  private reviewRequestId = 0
  private reviewPollTimer: number | null = null

  get retestRules() {
    return {
      question: [{ required: true, message: '请输入复测问题', trigger: 'blur' }],
      knowledgeKeys: [{
        type: 'array',
        required: true,
        min: 1,
        max: 10,
        message: '请选择1到10条知识',
        trigger: 'change'
      }]
    }
  }

  created() {
    this.loadAll()
  }

  beforeDestroy() {
    this.stopReviewPolling()
  }

  get resolutionRateText() {
    const rate = Number(this.statistics.resolutionRate)
    return `${Number.isFinite(rate) ? rate.toFixed(2) : '0.00'}%`
  }

  get loading() {
    return this.statisticsLoading || this.pageLoading
  }

  get isReviewRunning() {
    return !!this.reviewDetail && (
      this.reviewDetail.executionStatus === 'PENDING' ||
      this.reviewDetail.executionStatus === 'PROCESSING'
    )
  }

  get canReviewRetest() {
    return !!this.reviewDetail &&
      this.reviewDetail.executionStatus === 'SUCCEEDED' &&
      !this.reviewDetail.reviewResult
  }

  get canRestartRetest() {
    return !!this.reviewDetail && (
      this.reviewDetail.executionStatus === 'FAILED' ||
      this.reviewDetail.reviewResult === 'INCORRECT'
    )
  }

  private get dateParams(): AiFeedbackDateParams {
    return {
      begin: this.appliedDateRange.length === 2 ? this.appliedDateRange[0] : undefined,
      end: this.appliedDateRange.length === 2 ? this.appliedDateRange[1] : undefined
    }
  }

  private async loadAll() {
    await Promise.all([this.loadStatistics(), this.loadPage()])
  }

  private async loadStatistics() {
    const requestId = ++this.statisticsRequestId
    const params = { ...this.dateParams }
    this.statisticsLoading = true
    this.statisticsError = false
    try {
      const response = await getAiFeedbackStatistics(params)
      if (requestId !== this.statisticsRequestId) return
      if (!this.isSuccess(response)) throw new Error('business error')
      this.applyStatistics(response.data.data)
    } catch (error) {
      if (requestId !== this.statisticsRequestId) return
      this.statistics = emptyStatistics()
      this.statisticsError = true
    } finally {
      if (requestId === this.statisticsRequestId) this.statisticsLoading = false
    }
  }

  private async loadPage() {
    const requestId = ++this.pageRequestId
    const params = {
      ...this.dateParams,
      page: this.page,
      pageSize: this.pageSize
    }
    this.pageLoading = true
    this.pageError = false
    this.tableData = []
    this.expandedFeedbackIds = []
    try {
      const response = await getUnsolvedAiFeedbackPage(params)
      if (requestId !== this.pageRequestId) return
      if (!this.isSuccess(response)) throw new Error('business error')
      this.applyPage(response.data.data)
    } catch (error) {
      if (requestId !== this.pageRequestId) return
      this.tableData = []
      this.total = 0
      this.pageError = true
    } finally {
      if (requestId === this.pageRequestId) this.pageLoading = false
    }
  }

  private applyStatistics(data: AiFeedbackStatistics) {
    const source: Partial<AiFeedbackStatistics> = data || {}
    this.statistics = {
      helpfulCount: Number(source.helpfulCount || 0),
      unsolvedCount: Number(source.unsolvedCount || 0),
      totalCount: Number(source.totalCount || 0),
      resolutionRate: Number(source.resolutionRate || 0)
    }
  }

  private applyPage(data: AiFeedbackPageData) {
    const source: Partial<AiFeedbackPageData> = data || {}
    this.tableData = Array.isArray(source.records) ? source.records : []
    this.total = Number(source.total || 0)
  }

  private search() {
    this.appliedDateRange = Array.isArray(this.dateRange) ? this.dateRange.slice() : []
    this.page = 1
    this.loadAll()
  }

  private reset() {
    this.dateRange = []
    this.appliedDateRange = []
    this.page = 1
    this.loadAll()
  }

  private changePageSize(size: number) {
    this.pageSize = size
    this.page = 1
    this.loadPage()
  }

  private changePage(page: number) {
    this.page = page
    this.loadPage()
  }

  private isSuccess(response: any) {
    return response && response.data && String(response.data.code) === '1'
  }

  private isExpanded(feedbackId: number) {
    return this.expandedFeedbackIds.indexOf(feedbackId) >= 0
  }

  private onExpandChange(row: AiFeedbackRecord, expandedRows: AiFeedbackRecord[]) {
    this.expandedFeedbackIds = expandedRows.map(item => item.feedbackId)
  }

  private toggleDetails(row: AiFeedbackRecord) {
    const table: any = this.$refs.feedbackTable
    if (table && table.toggleRowExpansion) {
      table.toggleRowExpansion(row, !this.isExpanded(row.feedbackId))
    }
  }

  private openRetest(row: AiFeedbackRecord) {
    this.retestFeedbackId = row.feedbackId
    this.retestForm = {
      question: (row.userQuestion || '').trim(),
      knowledgeKeys: []
    }
    this.knowledgeOptions = []
    this.retestDialogVisible = true
    this.searchKnowledge('')
    this.$nextTick(() => {
      const form: any = this.$refs.retestForm
      if (form) form.clearValidate()
    })
  }

  private async openReview(row: AiFeedbackRecord) {
    this.stopReviewPolling()
    this.reviewFeedbackId = row.feedbackId
    this.reviewDetail = null
    this.reviewError = false
    this.reviewDialogVisible = true
    await this.loadReviewDetail(true)
  }

  private async loadReviewDetail(showMessage: boolean) {
    if (this.reviewFeedbackId === null) return
    const feedbackId = this.reviewFeedbackId
    const requestId = ++this.reviewRequestId
    this.reviewLoading = true
    this.reviewError = false
    try {
      const response = await getLatestAiFeedbackRetest(feedbackId)
      if (requestId !== this.reviewRequestId || !this.reviewDialogVisible) return
      if (!this.isSuccess(response)) throw new Error('business error')
      this.reviewDetail = response.data.data
      this.syncReviewPolling()
    } catch (error) {
      if (requestId !== this.reviewRequestId || !this.reviewDialogVisible) return
      this.reviewError = true
      this.stopReviewPolling()
      if (showMessage) this.$message.error('复测结果加载失败，请稍后重试')
    } finally {
      if (requestId === this.reviewRequestId) this.reviewLoading = false
    }
  }

  private syncReviewPolling() {
    if (!this.isReviewRunning || !this.reviewDialogVisible) {
      this.stopReviewPolling()
      return
    }
    if (this.reviewPollTimer !== null) return
    this.reviewPollTimer = window.setInterval(() => {
      this.loadReviewDetail(false)
    }, 5000)
  }

  private stopReviewPolling() {
    if (this.reviewPollTimer !== null) {
      window.clearInterval(this.reviewPollTimer)
      this.reviewPollTimer = null
    }
  }

  private reviewStatusTone(status: AiFeedbackRetestExecutionStatus) {
    if (status === 'SUCCEEDED') return 'success'
    if (status === 'FAILED') return 'danger'
    return 'warning'
  }

  private async submitReview(reviewResult: AiFeedbackRetestReviewResult) {
    if (!this.reviewDetail || this.reviewFeedbackId === null || this.reviewSubmitting) return
    if (reviewResult === 'CORRECT') {
      try {
        await this.$confirm(
          '确认该回答已经正确？确认后反馈将标记为已处理。',
          '确认处理结果',
          { type: 'warning' }
        )
      } catch (error) {
        return
      }
    }
    this.reviewSubmitting = true
    try {
      const response = await reviewAiFeedbackRetest(
        this.reviewFeedbackId,
        this.reviewDetail.retestId,
        reviewResult
      )
      if (!this.isSuccess(response)) throw new Error('business error')
      this.reviewDetail = response.data.data
      this.stopReviewPolling()
      this.$message.success(
        response.data.data.message ||
        (reviewResult === 'CORRECT' ? '反馈已处理' : '已记录复测仍未解决')
      )
      if (reviewResult === 'CORRECT') this.reviewDialogVisible = false
      await this.loadPage()
    } catch (error) {
      this.$message.error('复测确认失败，请刷新后重试')
    } finally {
      this.reviewSubmitting = false
    }
  }

  private restartRetest() {
    if (!this.reviewDetail || this.reviewFeedbackId === null) return
    const row = this.tableData.find(item => item.feedbackId === this.reviewFeedbackId)
    if (!row) return
    const question = this.reviewDetail.question
    this.reviewDialogVisible = false
    this.stopReviewPolling()
    this.openRetest(row)
    this.retestForm.question = question
  }

  private async searchKnowledge(keyword: string) {
    const requestId = ++this.knowledgeRequestId
    this.knowledgeLoading = true
    try {
      const response = await getAiKnowledgePage({
        page: 1,
        pageSize: 20,
        keyword: String(keyword || '').trim() || undefined,
        embeddingStatus: 'READY'
      })
      if (requestId !== this.knowledgeRequestId) return
      if (!this.isSuccess(response)) throw new Error('business error')
      const data = response.data.data
      this.knowledgeOptions = data && Array.isArray(data.records)
        ? data.records : []
    } catch (error) {
      if (requestId !== this.knowledgeRequestId) return
      this.knowledgeOptions = []
      this.$message.error('知识列表加载失败，请稍后重试')
    } finally {
      if (requestId === this.knowledgeRequestId) this.knowledgeLoading = false
    }
  }

  private submitRetest() {
    const form: any = this.$refs.retestForm
    if (!form || this.retestFeedbackId === null) return
    form.validate(async (valid: boolean) => {
      if (!valid || this.retestSubmitting || this.retestFeedbackId === null) return
      this.retestSubmitting = true
      try {
        const response = await submitAiFeedbackRetest(this.retestFeedbackId, {
          question: this.retestForm.question.trim(),
          knowledgeKeys: this.retestForm.knowledgeKeys.slice()
        })
        if (!this.isSuccess(response)) {
          throw new Error(response.data && response.data.msg
            ? response.data.msg : '复测任务提交失败')
        }
        this.$message.success(response.data.data.message || '已提交，等待系统复测')
        this.retestDialogVisible = false
        await this.loadPage()
      } catch (error) {
        const message = error && (error as any).message
          ? (error as any).message : '复测任务提交失败'
        this.$message.error(message)
      } finally {
        this.retestSubmitting = false
      }
    })
  }

  private timeText(value: string | number[] | null) {
    if (!value) return '-'
    if (Array.isArray(value)) {
      const pad = (part: any) => String(part || 0).padStart(2, '0')
      return `${value[0]}-${pad(value[1])}-${pad(value[2])} ${pad(value[3])}:${pad(value[4])}:${pad(value[5])}`
    }
    return String(value).replace('T', ' ').slice(0, 19)
  }
}
</script>

<style lang="scss" scoped>
.dashboard-container {
  margin: 30px;
}

.feedback-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  margin-bottom: 18px;
}

.feedback-filter__fields,
.feedback-filter__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.feedback-filter__fields label {
  color: $cm-text-secondary;
  font-size: 14px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.feedback-statistics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  min-height: 112px;
  margin-bottom: 18px;
}

.statistic-card {
  position: relative;
  min-width: 0;
  padding: 22px 24px;
  overflow: hidden;
  border-left: 4px solid #8da0b3;

  span {
    display: block;
    margin-bottom: 10px;
    color: $cm-text-secondary;
    font-size: 13px;
  }

  strong {
    color: $cm-text-primary;
    font-size: 28px;
    font-weight: 600;
    line-height: 34px;
  }
}

.statistic-card--success { border-left-color: #22a06b; }
.statistic-card--danger { border-left-color: #d95656; }
.statistic-card--primary { border-left-color: #147ee8; }

.feedback-error {
  margin-top: 18px;
}

.feedback-list {
  overflow: hidden;
}

.feedback-list__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 22px;
  border-bottom: 1px solid #dfe5eb;

  h2 {
    margin: 0 0 5px;
    color: $cm-text-primary;
    font-size: 16px;
    font-weight: 600;
  }

  p,
  > span {
    margin: 0;
    color: $cm-text-secondary;
    font-size: 13px;
    line-height: 20px;
  }
}

.feedback-table {
  width: 100%;
}

.feedback-summary {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: $cm-text-primary;
  line-height: 20px;
  text-overflow: ellipsis;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.feedback-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 28px;
  padding: 8px 58px 16px;

  label {
    display: block;
    margin-bottom: 8px;
    color: $cm-text-secondary;
    font-size: 13px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: $cm-text-primary;
    line-height: 22px;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.feedback-pagination {
  padding: 18px 20px;
  text-align: right;
  border-top: 1px solid #dfe5eb;
}

.field-help {
  margin: 6px 0 0;
  color: $cm-text-secondary;
  font-size: 12px;
  line-height: 18px;
}

.retest-review {
  min-height: 160px;
}

.retest-review__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  color: $cm-text-secondary;
  font-size: 13px;
}

.retest-review__block,
.retest-review__error {
  padding: 14px 16px;
  margin-bottom: 12px;
  background: #f6f8fa;
  border-radius: 4px;

  label {
    display: block;
    margin-bottom: 7px;
    color: $cm-text-secondary;
    font-size: 13px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: $cm-text-primary;
    line-height: 22px;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.retest-review__error {
  background: #fff1f0;
  border: 1px solid #ffd6d2;
}

.retest-review__reviewed {
  color: $cm-text-secondary;
  font-size: 13px;
}

.ai-feedback-page ::v-deep .el-dialog .el-select {
  width: 100%;
}

@media (max-width: 1200px) {
  .feedback-statistics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .feedback-detail { grid-template-columns: 1fr; }
}
</style>
