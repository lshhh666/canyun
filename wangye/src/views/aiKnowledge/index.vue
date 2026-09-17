<template>
  <main class="cm-page dashboard-container knowledge-page">
    <PageHeader
      title="知识管理"
      description="维护 AI 客服权威知识，并查看向量同步状态"
    />

    <section class="cm-surface knowledge-panel">
      <div class="knowledge-filter cm-filter-bar">
        <div class="knowledge-filter__fields">
          <label>关键词：</label>
          <el-input
            v-model="keyword"
            clearable
            placeholder="知识标识或标题"
            @clear="search"
            @keyup.enter.native="search"
          />
          <label>分类：</label>
          <el-select v-model="category" clearable placeholder="全部分类">
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <label>同步状态：</label>
          <el-select v-model="embeddingStatus" clearable placeholder="全部状态">
            <el-option label="同步中" value="PENDING" />
            <el-option label="可用" value="READY" />
            <el-option label="同步失败" value="FAILED" />
          </el-select>
        </div>
        <div class="knowledge-filter__actions">
          <el-button :disabled="loading" @click="search">查询</el-button>
          <el-button :disabled="loading" @click="load">刷新状态</el-button>
          <el-button type="primary" @click="openCreate">+ 新建知识</el-button>
        </div>
      </div>

      <EmptyState
        v-if="loadError"
        type="error"
        title="知识列表加载失败"
        description="请检查服务连接后重试"
      >
        <template #action>
          <el-button type="primary" size="small" @click="load">重新加载</el-button>
        </template>
      </EmptyState>

      <el-table
        v-else-if="records.length || loading"
        v-loading="loading"
        :data="records"
        stripe
        row-key="id"
      >
        <el-table-column prop="knowledgeKey" label="知识标识" min-width="150" />
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column label="分类" width="110">
          <template slot-scope="scope">
            {{ scope.row.categoryDesc || categoryText(scope.row.category) }}
          </template>
        </el-table-column>
        <el-table-column label="规则正文" min-width="280">
          <template slot-scope="scope">
            <p class="knowledge-content" :title="scope.row.content">{{ scope.row.content }}</p>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template slot-scope="scope">v{{ scope.row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="向量状态" width="120">
          <template slot-scope="scope">
            <StatusTag
              :status="embeddingStatusTone(scope.row.embeddingStatus)"
              :text="embeddingStatusText(scope.row.embeddingStatus)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template slot-scope="scope">{{ timeText(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.embeddingStatus === 'FAILED'"
              type="text"
              :loading="retryingId === scope.row.id"
              @click="retryEmbedding(scope.row)"
            >重新同步</el-button>
            <el-button type="text" @click="openEdit(scope.row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState
        v-else
        title="暂无知识"
        description="新建知识后，系统会在后台自动生成向量"
      />

      <el-pagination
        v-if="total > 0"
        class="knowledge-pagination"
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :disabled="loading"
        @size-change="changePageSize"
        @current-change="changePage"
      />
    </section>

    <el-dialog
      :title="editingId ? '修改知识' : '新建知识'"
      :visible.sync="dialogVisible"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form ref="knowledgeForm" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="知识标识" prop="knowledgeKey">
          <el-input
            v-model.trim="form.knowledgeKey"
            :disabled="Boolean(editingId)"
            maxlength="64"
            placeholder="例如 ORDER_CANCEL_RULE"
          />
          <p class="field-help">同一条业务规则跨版本保持不变，建议使用大写英文和下划线。</p>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model.trim="form.title" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类">
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="规则正文" prop="content">
          <el-input
            v-model.trim="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="请输入提供给 AI 客服的权威规则"
          />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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
  AiKnowledgeCategory,
  AiKnowledgeEmbeddingStatus,
  AiKnowledgeRecord,
  createAiKnowledge,
  getAiKnowledgePage,
  retryAiKnowledgeEmbedding,
  updateAiKnowledge
} from '@/api/aiKnowledge'

function emptyForm() {
  return {
    knowledgeKey: '',
    title: '',
    category: '',
    content: ''
  }
}

@Component({
  name: 'AiKnowledgeManagement',
  components: { PageHeader, StatusTag, EmptyState }
})
export default class extends Vue {
  private keyword = ''
  private category: AiKnowledgeCategory | '' = ''
  private embeddingStatus: AiKnowledgeEmbeddingStatus | '' = ''
  private page = 1
  private pageSize = 10
  private total = 0
  private records: AiKnowledgeRecord[] = []
  private loading = false
  private loadError = false
  private loadSequence = 0
  private dialogVisible = false
  private saving = false
  private retryingId: number | null = null
  private editingId: number | null = null
  private form: any = emptyForm()

  private readonly categoryOptions = [
    { value: 'SHOP', label: '门店规则' },
    { value: 'ORDER', label: '订单规则' },
    { value: 'COUPON', label: '优惠券规则' },
    { value: 'DISH', label: '菜品信息' },
    { value: 'DELIVERY', label: '配送规则' }
  ]

  get rules() {
    return {
      knowledgeKey: [{ required: true, message: '请输入知识标识', trigger: 'blur' }],
      title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
      category: [{ required: true, message: '请选择分类', trigger: 'change' }],
      content: [{ required: true, message: '请输入规则正文', trigger: 'blur' }]
    }
  }

  created() {
    this.load()
  }

  private async load() {
    const requestId = ++this.loadSequence
    this.loading = true
    this.loadError = false
    try {
      const response = await getAiKnowledgePage({
        page: this.page,
        pageSize: this.pageSize,
        keyword: this.keyword.trim() || undefined,
        category: this.category || undefined,
        embeddingStatus: this.embeddingStatus || undefined
      })
      if (!this.isSuccess(response)) throw new Error('business error')
      if (requestId !== this.loadSequence) return
      const data = response.data.data
      this.total = Number(data && data.total) || 0
      this.records = data && Array.isArray(data.records) ? data.records : []
    } catch (error) {
      if (requestId !== this.loadSequence) return
      this.records = []
      this.total = 0
      this.loadError = true
    } finally {
      if (requestId === this.loadSequence) this.loading = false
    }
  }

  private search() {
    this.page = 1
    this.load()
  }

  private changePage(page: number) {
    this.page = page
    this.load()
  }

  private changePageSize(pageSize: number) {
    this.page = 1
    this.pageSize = pageSize
    this.load()
  }

  private openCreate() {
    this.editingId = null
    this.form = emptyForm()
    this.dialogVisible = true
    this.clearValidation()
  }

  private openEdit(record: AiKnowledgeRecord) {
    this.editingId = record.id
    this.form = {
      knowledgeKey: record.knowledgeKey,
      title: record.title,
      category: record.category,
      content: record.content
    }
    this.dialogVisible = true
    this.clearValidation()
  }

  private clearValidation() {
    this.$nextTick(() => {
      const form: any = this.$refs.knowledgeForm
      if (form) form.clearValidate()
    })
  }

  private submit() {
    const form: any = this.$refs.knowledgeForm
    if (!form) return
    form.validate(async (valid: boolean) => {
      if (!valid || this.saving) return
      this.saving = true
      try {
        const data = {
          title: this.form.title.trim(),
          category: this.form.category as AiKnowledgeCategory,
          content: this.form.content.trim()
        }
        const response = this.editingId
          ? await updateAiKnowledge(this.editingId, data)
          : await createAiKnowledge({
            knowledgeKey: this.form.knowledgeKey.trim(),
            ...data
          })
        if (!this.isSuccess(response)) {
          throw new Error(response.data && response.data.msg
            ? response.data.msg : '知识保存失败')
        }
        this.$message.success(response.data.data.message || '保存成功，向量同步中')
        this.dialogVisible = false
        this.page = 1
        await this.load()
      } catch (error) {
        const message = error && (error as any).message
          ? (error as any).message : '知识保存失败'
        this.$message.error(message)
      } finally {
        this.saving = false
      }
    })
  }

  private async retryEmbedding(record: AiKnowledgeRecord) {
    if (this.retryingId !== null) return
    this.retryingId = record.id
    try {
      const response = await retryAiKnowledgeEmbedding(record.id)
      if (!this.isSuccess(response)) {
        throw new Error(response.data && response.data.msg
          ? response.data.msg : '重新同步失败')
      }
      this.$message.success(response.data.data.message || '知识正在同步中')
      await this.load()
    } catch (error) {
      const message = error && (error as any).message
        ? (error as any).message : '重新同步失败'
      this.$message.error(message)
    } finally {
      this.retryingId = null
    }
  }

  private isSuccess(response: any) {
    const code = response && response.data && response.data.code
    return Number(code) === 1
  }

  private categoryText(category: AiKnowledgeCategory) {
    const item = this.categoryOptions.find(option => option.value === category)
    return item ? item.label : category
  }

  private embeddingStatusText(status: AiKnowledgeEmbeddingStatus) {
    if (status === 'READY') return '可用'
    if (status === 'FAILED') return '同步失败'
    return '同步中'
  }

  private embeddingStatusTone(status: AiKnowledgeEmbeddingStatus) {
    if (status === 'READY') return 'success'
    if (status === 'FAILED') return 'danger'
    return 'warning'
  }

  private timeText(value: string | number[] | null) {
    if (!value) return '-'
    if (Array.isArray(value)) {
      const parts = value.map(item => String(item).padStart(2, '0'))
      return `${parts[0]}-${parts[1]}-${parts[2]} ${parts[3]}:${parts[4]}`
    }
    return String(value).replace('T', ' ').slice(0, 16)
  }
}
</script>

<style lang="scss" scoped>
.knowledge-panel {
  overflow: hidden;
}

.knowledge-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.knowledge-filter__fields,
.knowledge-filter__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.knowledge-filter__fields {
  flex-wrap: wrap;

  label {
    color: #5f6b7a;
    font-size: 14px;
    white-space: nowrap;
  }

  .el-input { width: 190px; }
  .el-select { width: 145px; }
}

.knowledge-filter__actions {
  flex-shrink: 0;
}

.knowledge-content {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  line-height: 20px;
  text-overflow: ellipsis;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.knowledge-pagination {
  padding: 18px 20px;
  text-align: right;
  border-top: 1px solid #dfe5eb;
}

.field-help {
  margin: 5px 0 0;
  color: #8a97a6;
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 1200px) {
  .knowledge-filter {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
