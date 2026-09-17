import fs from 'fs'
import path from 'path'
import { shallowMount, Wrapper } from '@vue/test-utils'
import AiKnowledge from '@/views/aiKnowledge/index.vue'
import {
  createAiKnowledge,
  getAiKnowledgePage,
  retryAiKnowledgeEmbedding,
  updateAiKnowledge
} from '@/api/aiKnowledge'

jest.mock('@/api/aiKnowledge', () => ({
  createAiKnowledge: jest.fn(),
  getAiKnowledgePage: jest.fn(),
  retryAiKnowledgeEmbedding: jest.fn(),
  updateAiKnowledge: jest.fn()
}))

const projectRoot = path.resolve(__dirname, '../../..')
const createApi = createAiKnowledge as jest.Mock
const pageApi = getAiKnowledgePage as jest.Mock
const retryApi = retryAiKnowledgeEmbedding as jest.Mock
const updateApi = updateAiKnowledge as jest.Mock

function record(id: number, status = 'READY') {
  return {
    id,
    knowledgeKey: `ORDER_RULE_${id}`,
    title: `订单规则${id}`,
    category: 'ORDER',
    categoryDesc: '订单规则',
    content: '仅支持取消待付款且未支付的订单。',
    embeddingStatus: status,
    embeddingStatusDesc: status === 'READY' ? '可用' : '同步中',
    versionNo: 1,
    createTime: '2026-09-12T10:00:00',
    updateTime: '2026-09-12T10:00:00'
  }
}

function pageResponse(records = [record(1)], total = records.length) {
  return { data: { code: 1, data: { records, total } } }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(resolvePromise => { resolve = resolvePromise })
  return { promise, resolve }
}

function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function mountPage(): Wrapper<any> {
  return shallowMount(AiKnowledge, {
    directives: { loading: () => undefined },
    mocks: {
      $message: { success: jest.fn(), error: jest.fn() }
    },
    stubs: {
      PageHeader: true,
      StatusTag: true,
      EmptyState: true,
      'el-input': true,
      'el-select': true,
      'el-option': true,
      'el-button': true,
      'el-table': true,
      'el-table-column': true,
      'el-pagination': true,
      'el-dialog': true,
      'el-form': true,
      'el-form-item': true
    }
  })
}

describe('AI knowledge management', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    pageApi.mockResolvedValue(pageResponse())
  })

  it('registers the route and uses the three knowledge endpoints', () => {
    const router = fs.readFileSync(path.join(projectRoot, 'src/router.ts'), 'utf8')
    const api = fs.readFileSync(path.join(projectRoot, 'src/api/aiKnowledge.ts'), 'utf8')

    expect(router).toContain('path: "ai-knowledge"')
    expect(router).toContain('title: "知识管理"')
    expect(api).toContain("url: '/ai/knowledge/page'")
    expect(api).toContain("url: '/ai/knowledge'")
    expect(api).toContain('url: `/ai/knowledge/${id}`')
    expect(api).toContain('url: `/ai/knowledge/${id}/embedding/retry`')
  })

  it('loads the current knowledge page without sending empty filters', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(pageApi).toHaveBeenCalledWith({
      page: 1,
      pageSize: 10,
      keyword: undefined,
      category: undefined,
      embeddingStatus: undefined
    })
    expect(wrapper.vm.total).toBe(1)
    expect(wrapper.vm.records[0].knowledgeKey).toBe('ORDER_RULE_1')
    expect(wrapper.vm.embeddingStatusText('PENDING')).toBe('同步中')
    expect(wrapper.vm.embeddingStatusText('READY')).toBe('可用')
    expect(wrapper.vm.embeddingStatusText('FAILED')).toBe('同步失败')
    wrapper.destroy()
  })

  it('ignores an older list response that completes last', async () => {
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
    expect(wrapper.vm.records[0].id).toBe(3)
    expect(wrapper.vm.loading).toBe(false)
    wrapper.destroy()
  })

  it('keeps knowledgeKey immutable in edit requests', async () => {
    const wrapper = mountPage()
    await flushPromises()
    updateApi.mockResolvedValue({
      data: {
        code: 1,
        data: { knowledgeId: 1, embeddingStatus: 'PENDING', message: '保存成功，向量同步中' }
      }
    })
    wrapper.vm.editingId = 1
    wrapper.vm.form = {
      knowledgeKey: 'ORDER_RULE_1',
      title: '新标题',
      category: 'ORDER',
      content: '新规则'
    }
    wrapper.vm.$refs.knowledgeForm = { validate: (callback: Function) => callback(true) }

    wrapper.vm.submit()
    await flushPromises()

    expect(updateApi).toHaveBeenCalledWith(1, {
      title: '新标题',
      category: 'ORDER',
      content: '新规则'
    })
    expect(createApi).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('保存成功，向量同步中')
    wrapper.destroy()
  })

  it('requeues a failed embedding and refreshes its status', async () => {
    const wrapper = mountPage()
    await flushPromises()
    retryApi.mockResolvedValue({
      data: {
        code: 1,
        data: {
          knowledgeId: 1,
          embeddingStatus: 'PENDING',
          message: '已提交，向量同步中'
        }
      }
    })
    pageApi.mockResolvedValueOnce(pageResponse([record(1, 'PENDING')]))

    wrapper.vm.retryEmbedding(record(1, 'FAILED'))
    await flushPromises()
    await flushPromises()

    expect(retryApi).toHaveBeenCalledWith(1)
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith('已提交，向量同步中')
    expect(pageApi).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.retryingId).toBeNull()
    wrapper.destroy()
  })
})
