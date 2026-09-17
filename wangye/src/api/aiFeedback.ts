import request from '@/utils/request'
import { AxiosPromise } from 'axios'

export interface AiFeedbackDateParams {
  begin?: string
  end?: string
}

export interface AiFeedbackPageParams extends AiFeedbackDateParams {
  page: number
  pageSize: number
}

export interface AiFeedbackStatistics {
  helpfulCount: number
  unsolvedCount: number
  totalCount: number
  resolutionRate: number
}

export interface AiFeedbackRecord {
  feedbackId: number
  sessionId: number
  userId: number
  userQuestion: string | null
  aiAnswer: string | null
  handleStatus: 'PENDING' | 'PENDING_RETEST'
  handleStatusDesc: string
  feedbackTime: string | number[] | null
}

export interface AiFeedbackRetestSubmitData {
  question: string
  knowledgeKeys: string[]
}

export interface AiFeedbackRetestSubmitResult {
  feedbackId: number
  retestId: number | null
  handleStatus: 'PENDING' | 'PENDING_RETEST' | 'HANDLED'
  executionStatus: 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | null
  message: string
}

export type AiFeedbackRetestExecutionStatus =
  'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED'
export type AiFeedbackRetestReviewResult = 'CORRECT' | 'INCORRECT'

export interface AiFeedbackRetestDetail {
  feedbackId: number
  retestId: number
  question: string
  answer: string | null
  usedKnowledgeIds: string | null
  executionStatus: AiFeedbackRetestExecutionStatus
  executionStatusDesc: string
  reviewResult: AiFeedbackRetestReviewResult | null
  reviewResultDesc: string | null
  retryCount: number
  maxRetryCount: number
  nextRetryTime: string | number[] | null
  lastError: string | null
  handleStatus: 'PENDING' | 'PENDING_RETEST' | 'HANDLED'
  handleStatusDesc: string
  updateTime: string | number[] | null
  message?: string
}

export interface AiFeedbackPageData {
  total: number
  records: AiFeedbackRecord[]
}

export interface ApiResult<T> {
  code: number | string
  msg?: string
  data: T
}

/** 查询 AI 客服评价统计。 */
export const getAiFeedbackStatistics = (
  params: AiFeedbackDateParams
): AxiosPromise<ApiResult<AiFeedbackStatistics>> => request({
  url: '/ai/feedback/statistics',
  method: 'get',
  params
})

/** 分页查询“没解决”的 AI 客服评价。 */
export const getUnsolvedAiFeedbackPage = (
  params: AiFeedbackPageParams
): AxiosPromise<ApiResult<AiFeedbackPageData>> => request({
  url: '/ai/feedback/unsolved/page',
  method: 'get',
  params
})

/** 关联已同步知识，并创建后台复测任务。 */
export const submitAiFeedbackRetest = (
  feedbackId: number,
  data: AiFeedbackRetestSubmitData
): AxiosPromise<ApiResult<AiFeedbackRetestSubmitResult>> => request({
  url: `/ai/feedback/${feedbackId}/retest`,
  method: 'post',
  data
})

/** 查询一条反馈最新一次复测的执行及人工确认状态。 */
export const getLatestAiFeedbackRetest = (
  feedbackId: number
): AxiosPromise<ApiResult<AiFeedbackRetestDetail>> => request({
  url: `/ai/feedback/${feedbackId}/retest/latest`,
  method: 'get'
})

/** 管理员确认复测回答正确或仍不正确。 */
export const reviewAiFeedbackRetest = (
  feedbackId: number,
  retestId: number,
  reviewResult: AiFeedbackRetestReviewResult
): AxiosPromise<ApiResult<AiFeedbackRetestDetail>> => request({
  url: `/ai/feedback/${feedbackId}/retest/${retestId}/review`,
  method: 'post',
  data: { reviewResult }
})
