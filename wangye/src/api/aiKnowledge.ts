import request from '@/utils/request'
import { AxiosPromise } from 'axios'

export type AiKnowledgeCategory = 'SHOP' | 'ORDER' | 'COUPON' | 'DISH' | 'DELIVERY'
export type AiKnowledgeEmbeddingStatus = 'PENDING' | 'READY' | 'FAILED'

export interface AiKnowledgePageParams {
  page: number
  pageSize: number
  keyword?: string
  category?: AiKnowledgeCategory
  embeddingStatus?: AiKnowledgeEmbeddingStatus
}

export interface AiKnowledgeRecord {
  id: number
  knowledgeKey: string
  title: string
  category: AiKnowledgeCategory
  categoryDesc: string
  content: string
  embeddingStatus: AiKnowledgeEmbeddingStatus
  embeddingStatusDesc: string
  versionNo: number
  createTime: string | number[] | null
  updateTime: string | number[] | null
}

export interface AiKnowledgePageData {
  total: number
  records: AiKnowledgeRecord[]
}

export interface AiKnowledgeCreateData {
  knowledgeKey: string
  title: string
  category: AiKnowledgeCategory
  content: string
}

export interface AiKnowledgeUpdateData {
  title: string
  category: AiKnowledgeCategory
  content: string
}

export interface AiKnowledgeSaveResult {
  knowledgeId: number
  embeddingStatus: AiKnowledgeEmbeddingStatus
  message: string
}

export interface ApiResult<T> {
  code: number | string
  msg?: string
  data: T
}

export const getAiKnowledgePage = (
  params: AiKnowledgePageParams
): AxiosPromise<ApiResult<AiKnowledgePageData>> => request({
  url: '/ai/knowledge/page',
  method: 'get',
  params
})

export const createAiKnowledge = (
  data: AiKnowledgeCreateData
): AxiosPromise<ApiResult<AiKnowledgeSaveResult>> => request({
  url: '/ai/knowledge',
  method: 'post',
  data
})

export const updateAiKnowledge = (
  id: number,
  data: AiKnowledgeUpdateData
): AxiosPromise<ApiResult<AiKnowledgeSaveResult>> => request({
  url: `/ai/knowledge/${id}`,
  method: 'put',
  data
})

export const retryAiKnowledgeEmbedding = (
  id: number
): AxiosPromise<ApiResult<AiKnowledgeSaveResult>> => request({
  url: `/ai/knowledge/${id}/embedding/retry`,
  method: 'post'
})
