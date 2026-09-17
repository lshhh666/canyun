import fs from 'fs'
import path from 'path'
import { shallowMount, Wrapper } from '@vue/test-utils'
import AiFeedback from '@/views/aiFeedback/index.vue'
import {
  getLatestAiFeedbackRetest,
  getAiFeedbackStatistics,
  getUnsolvedAiFeedbackPage,
  reviewAiFeedbackRetest,
  submitAiFeedbackRetest
} from '@/api/aiFeedback'
import { getAiKnowledgePage } from '@/api/aiKnowledge'

jest.mock('@/api/aiFeedback', () => ({
  getAiFeedbackStatistics: jest.fn(),
  getUnsolvedAiFeedbackPage: jest.fn(),
  getLatestAiFeedbackRetest: jest.fn(),
  reviewAiFeedbackRetest: jest.fn(),
  submitAiFeedbackRetest: jest.fn()
}))
jest.mock('@/api/aiKnowledge', () => ({
  getAiKnowledgePage: jest.fn()
}))

const projectRoot = path.resolve(__dirname, '../../..')
const statisticsApi = getAiFeedbackStatistics as jest.Mock
const pageApi = getUnsolvedAiFeedbackPage as jest.Mock
const submitRetestApi = submitAiFeedbackRetest as jest.Mock
const latestRetestApi = getLatestAiFeedbackRetest as jest.Mock
const reviewRetestApi = reviewAiFeedbackRetest as jest.Mock
const knowledgeApi = getAiKnowledgePage as jest.Mock

function statisticsResponse() {
  return {
    data: {
      code: 1,
      data: {
        helpfulCount: 2,
        unsolvedCount: 1,
        totalCount: 3,
        resolutionRate: 66.67
      }
    }
  }
}

function record(feedbackId: number) {
  return {
    feedbackId,
    sessionId: feedbackId + 100,
    userId: feedbackId + 200,
    userQuestion: `问题${feedbackId}`,
    aiAnswer: `回答${feedbackId}`,
    handleStatus: 'PENDING',
    handleStatusDesc: '待处理',
    feedbackTime: '2026-09-10T12:00:00'
  }
}

function pageResponse(records = [record(1)], total = records.length) {
  return { data: { code: 1, data: { records, total } } }
}

function retestResponse(overrides: Record<string, any> = {}) {
  return {
    data: {
      code: 1,
      data: {
        feedbackId: 1,
        retestId: 91,
        question: '问题1',
        answer: '正确回答',
        usedKnowledgeIds: '[7]',
        executionStatus: 'SUCCEEDED',
        executionStatusDesc: '成功',
        reviewResult: null,
        reviewResultDesc: null,
        retryCount: 0,
        maxRetryCount: 3,
        lastError: null,
        handleStatus: 'PENDING_RETEST',
        handleStatusDesc: '待复测',
        ...overrides
      }
    }
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: any) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function mountPage(): Wrapper<any> {
  const formStub = {
    name: 'ElForm',
    methods: {
      validate(callback: (valid: boolean) => void) { callback(true) },
      clearValidate() { return undefined }
    },
    template: '<form><slot /></form>'
  }
  return shallowMount(AiFeedback, {
    directives: { loading: () => undefined },
    mocks: {
      $message: { error: jest.fn(), success: jest.fn() },
      $confirm: jest.fn().mockResolvedValue(true)
    },
    stubs: {
      PageHeader: true,
      StatusTag: true,
      EmptyState: {
        props: ['title', 'description'],
        template: '<section>{{ title }} {{ description }}<slot name="action" /></section>'
      },
      'el-date-picker': true,
      'el-button': true,
      'el-table': true,
      'el-table-column': true,
      'el-pagination': true,
      'el-dialog': {
        template: '<section><slot /><slot name="footer" /></section>'
      },
      'el-form': formStub,
      'el-form-item': true,
      'el-input': true,
      'el-select': true,
      'el-option': true
    }
  })
}

describe('AI feedback management', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    statisticsApi.mockResolvedValue(statisticsResponse())
    pageApi.mockResolvedValue(pageResponse())
    knowledgeApi.mockResolvedValue({
      data: {
        code: 1,
        data: {
          total: 1,
          records: [{
            id: 7,
            knowledgeKey: 'ORDER_CANCEL',
            title: '订单取消规则',
            category: 'ORDER',
            embeddingStatus: 'READY'
          }]
        }
      }
    })
    submitRetestApi.mockResolvedValue({
      data: {
        code: 1,
        data: { message: '已提交，等待系统复测' }
      }
    })
    latestRetestApi.mockResolvedValue(retestResponse())
    reviewRetestApi.mockResolvedValue(retestResponse({
      reviewResult: 'CORRECT',
      reviewResultDesc: '回答正确',
      handleStatus: 'HANDLED',
      handleStatusDesc: '已处理',
      message: '已确认回答正确，反馈已处理'
    }))
  })

  it('registers the route immediately after statistics and uses both GET endpoints', () => {
    const router = fs.readFileSync(path.join(projectRoot, 'src/router.ts'), 'utf8')
    const api = fs.readFileSync(path.join(projectRoot, 'src/api/aiFeedback.ts'), 'utf8')
    const statisticsTitle = router.indexOf('title: "数据统计"')
    const feedbackPath = router.indexOf('path: "ai-feedback"')
    const nextOrderPath = router.indexOf('path: "order"')

    expect(statisticsTitle).toBeLessThan(feedbackPath)
    expect(feedbackPath).toBeLessThan(nextOrderPath)
    expect(api).toContain("url: '/ai/feedback/statistics'")
    expect(api).toContain("url: '/ai/feedback/unsolved/page'")
    expect(api).toContain('`/ai/feedback/${feedbackId}/retest/latest`')
    expect(api).toContain('`/ai/feedback/${feedbackId}/retest/${retestId}/review`')
  })

  it('loads all-time statistics and the first unresolved page', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(statisticsApi).toHaveBeenCalledWith({ begin: undefined, end: undefined })
    expect(pageApi).toHaveBeenCalledWith({
      begin: undefined,
      end: undefined,
      page: 1,
      pageSize: 10
    })
    expect(wrapper.vm.statistics.totalCount).toBe(3)
    expect(wrapper.vm.resolutionRateText).toBe('66.67%')
    expect(wrapper.vm.tableData[0].feedbackId).toBe(1)
    wrapper.destroy()
  })

  it('applies one date range to statistics and list requests', async () => {
    const wrapper = mountPage()
    await flushPromises()
    jest.clearAllMocks()

    await wrapper.setData({ dateRange: ['2026-09-01', '2026-09-10'] })
    wrapper.vm.search()
    await flushPromises()

    expect(statisticsApi).toHaveBeenCalledWith({ begin: '2026-09-01', end: '2026-09-10' })
    expect(pageApi).toHaveBeenCalledWith({
      begin: '2026-09-01',
      end: '2026-09-10',
      page: 1,
      pageSize: 10
    })
    wrapper.destroy()
  })

  it('treats a cleared date picker as an all-time query', async () => {
    const wrapper = mountPage()
    await flushPromises()
    jest.clearAllMocks()

    await wrapper.setData({ dateRange: null })
    wrapper.vm.search()
    await flushPromises()

    expect(statisticsApi).toHaveBeenCalledWith({ begin: undefined, end: undefined })
    expect(pageApi).toHaveBeenCalledWith({
      begin: undefined,
      end: undefined,
      page: 1,
      pageSize: 10
    })
    wrapper.destroy()
  })

  it('fetches only the list when the administrator changes pages', async () => {
    const wrapper = mountPage()
    await flushPromises()
    jest.clearAllMocks()

    wrapper.vm.changePage(2)
    await flushPromises()

    expect(statisticsApi).not.toHaveBeenCalled()
    expect(pageApi).toHaveBeenCalledWith({
      begin: undefined,
      end: undefined,
      page: 2,
      pageSize: 10
    })
    wrapper.destroy()
  })

  it('ignores a stale pagination response that finishes last', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const pageTwo = deferred<any>()
    const pageThree = deferred<any>()
    pageApi
      .mockImplementationOnce(() => pageTwo.promise)
      .mockImplementationOnce(() => pageThree.promise)

    wrapper.vm.changePage(2)
    wrapper.vm.changePage(3)
    pageThree.resolve(pageResponse([record(3)]))
    await flushPromises()
    pageTwo.resolve(pageResponse([record(2)]))
    await flushPromises()

    expect(wrapper.vm.page).toBe(3)
    expect(wrapper.vm.tableData).toHaveLength(1)
    expect(wrapper.vm.tableData[0].feedbackId).toBe(3)
    expect(wrapper.vm.pageLoading).toBe(false)
    wrapper.destroy()
  })

  it('clears stale rows and shows a generic state when pagination fails', async () => {
    const wrapper = mountPage()
    await flushPromises()
    pageApi.mockRejectedValueOnce(new Error('secret database detail'))

    wrapper.vm.changePage(2)
    await flushPromises()

    expect(wrapper.vm.tableData).toEqual([])
    expect(wrapper.vm.total).toBe(0)
    expect(wrapper.vm.pageError).toBe(true)
    expect(wrapper.text()).toContain('未解决评价加载失败')
    expect(wrapper.text()).toContain('请稍后重试，或检查服务连接')
    expect(wrapper.text()).not.toContain('secret database detail')
    wrapper.destroy()
  })

  it('provides keyboard-accessible full-answer controls', () => {
    const page = fs.readFileSync(path.join(projectRoot, 'src/views/aiFeedback/index.vue'), 'utf8')

    expect(page).toContain('type="expand"')
    expect(page).toContain("{{ isExpanded(scope.row.feedbackId) ? '收起' : '查看全文' }}")
    expect(page).toContain(':aria-expanded=')
    expect(page).toContain('@click="toggleDetails(scope.row)"')
    expect(page).not.toContain('error.message')
  })

  it('loads ready knowledge and submits the selected correction', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openRetest(record(8))
    await flushPromises()
    expect(knowledgeApi).toHaveBeenCalledWith({
      page: 1,
      pageSize: 20,
      keyword: undefined,
      embeddingStatus: 'READY'
    })

    await wrapper.setData({
      retestForm: {
        question: '问题8',
        knowledgeKeys: ['ORDER_CANCEL']
      }
    })
    wrapper.vm.submitRetest()
    await flushPromises()

    expect(submitRetestApi).toHaveBeenCalledWith(8, {
      question: '问题8',
      knowledgeKeys: ['ORDER_CANCEL']
    })
    expect(wrapper.vm.retestDialogVisible).toBe(false)
    wrapper.destroy()
  })

  it('loads the latest successful retest for manual review', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const pendingRetest = { ...record(1), handleStatus: 'PENDING_RETEST' }

    await wrapper.vm.openReview(pendingRetest)
    await flushPromises()

    expect(latestRetestApi).toHaveBeenCalledWith(1)
    expect(wrapper.vm.reviewDetail.answer).toBe('正确回答')
    expect(wrapper.vm.canReviewRetest).toBe(true)
    expect(wrapper.vm.canRestartRetest).toBe(false)
    wrapper.destroy()
  })

  it('keeps feedback open when administrator marks retest incorrect', async () => {
    reviewRetestApi.mockResolvedValue(retestResponse({
      reviewResult: 'INCORRECT',
      reviewResultDesc: '回答错误',
      message: '已记录回答仍不正确，请修改知识后重新复测'
    }))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.setData({
      reviewFeedbackId: 1,
      reviewDetail: retestResponse().data.data,
      reviewDialogVisible: true
    })

    await wrapper.vm.submitReview('INCORRECT')
    await flushPromises()

    expect(reviewRetestApi).toHaveBeenCalledWith(1, 91, 'INCORRECT')
    expect(wrapper.vm.canRestartRetest).toBe(true)
    expect(wrapper.vm.reviewDialogVisible).toBe(true)
    expect(wrapper.vm.reviewDetail.reviewResult).toBe('INCORRECT')
    wrapper.destroy()
  })

  it('requires confirmation before marking a correct retest handled', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.setData({
      reviewFeedbackId: 1,
      reviewDetail: retestResponse().data.data,
      reviewDialogVisible: true
    })

    await wrapper.vm.submitReview('CORRECT')
    await flushPromises()

    expect((wrapper.vm as any).$confirm).toHaveBeenCalled()
    expect(reviewRetestApi).toHaveBeenCalledWith(1, 91, 'CORRECT')
    expect(wrapper.vm.reviewDialogVisible).toBe(false)
    wrapper.destroy()
  })
})
