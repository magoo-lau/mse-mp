<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.accountId" placeholder="公众号" clearable style="width: 200px">
        <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="规则名 / 匹配值" clearable style="width: 200px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button v-permission="'mp:rule:save'" type="primary" plain @click="openCreate">新建规则</el-button>
    </div>

    <el-table :data="rows" size="small" border>
      <el-table-column label="公众号" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ accountName(row.accountId) }}</template>
      </el-table-column>
      <el-table-column prop="name" label="规则名" min-width="130" show-overflow-tooltip />
      <el-table-column label="匹配类型" width="130">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ MATCH_TYPE_LABELS[row.matchType] || row.matchType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="matchValue" label="匹配值" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.matchValue || '-' }}</template>
      </el-table-column>
      <el-table-column label="回复" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ replySummary(row) }}</template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="70" align="center" />
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }">
          <el-switch v-permission="'mp:rule:save'" :model-value="row.enabled === 1" @change="changeEnabled(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center">
        <template #default="{ row }">
          <el-button v-permission="'mp:rule:save'" size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'mp:rule:delete'" size="small" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNum" v-model:size="query.pageSize" @change="load" />

    <!-- 规则编辑 -->
    <el-dialog v-model="editDialog" :title="editForm.id ? '编辑规则' : '新建规则'" width="620px">
      <el-form label-width="90px" size="small">
        <el-form-item label="公众号" required>
          <el-select v-model="editForm.accountId" style="width: 100%" @change="loadClickKeyOptions">
            <el-option v-for="a in accounts" :key="a.id" :label="`${a.name}（${a.appId}）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名" required>
          <el-input v-model="editForm.name" placeholder="便于管理识别" />
        </el-form-item>
        <el-form-item label="匹配类型" required>
          <el-select v-model="editForm.matchType" style="width: 100%">
            <el-option v-for="(label, value) in MATCH_TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
          <div class="hint">
            匹配顺序：关注 → 取关 → 菜单点击 → 任意消息 → 关键词精确 → 关键词包含 → 兜底；同类型内优先级大者先。
          </div>
        </el-form-item>
        <el-form-item v-if="needMatchValue" :label="editForm.matchType === 'click' ? '菜单 Key' : '关键词'" required>
          <el-select v-if="editForm.matchType === 'click'" v-model="editForm.matchValue"
                     filterable allow-create placeholder="与菜单按钮 Key 一致" style="width: 100%">
            <el-option v-for="k in clickKeyOptions" :key="k" :label="k" :value="k" />
          </el-select>
          <el-input v-else v-model="editForm.matchValue" placeholder="多关键词用逗号/空格/竖线分隔" />
        </el-form-item>
        <el-form-item label="回复类型" required>
          <el-radio-group v-model="editForm.replyType">
            <el-radio value="text">文本</el-radio>
            <el-radio value="news">图文</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.replyType === 'text'" label="回复内容" required>
          <el-input v-model="textContent" type="textarea" :rows="4" maxlength="600" show-word-limit />
        </el-form-item>
        <el-form-item v-else label="图文列表" required>
          <div class="news-list">
            <div v-for="(item, index) in newsItems" :key="index" class="news-item">
              <el-input v-model="item.title" placeholder="标题（必填）" />
              <el-input v-model="item.description" placeholder="摘要" />
              <el-input v-model="item.picUrl" placeholder="封面图片 URL" />
              <el-input v-model="item.url" placeholder="原文链接 URL" />
              <el-button size="small" link type="danger" @click="newsItems.splice(index, 1)">删除</el-button>
            </div>
            <el-button size="small" :disabled="newsItems.length >= 8" @click="newsItems.push({ title: '', description: '', picUrl: '', url: '' })">
              + 图文（{{ newsItems.length }}/8）
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editForm.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeRuleStatus, createRule, deleteRule, getMenuDraft, pageAccounts, pageRules, updateRule, type MpRule
} from '../api/mp'
import { Pagination } from 'mse-ui-shared/pc'

const MATCH_TYPE_LABELS: Record<string, string> = {
  subscribe: '关注',
  unsubscribe: '取关',
  click: '菜单点击',
  msg_any: '任意消息',
  keyword_exact: '关键词精确',
  keyword_contains: '关键词包含',
  default: '兜底'
}

const accounts = ref<any[]>([])
const rows = ref<MpRule[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; accountId: number | undefined; keyword: string }>({
  pageNum: 1, pageSize: 10, accountId: undefined, keyword: ''
})

const editDialog = ref(false)
const editForm = reactive<{ id?: number; accountId: number | undefined; name: string; matchType: string;
  matchValue: string; replyType: string; priority: number }>(initForm())
const editEnabled = ref(true)
const textContent = ref('')
const newsItems = ref<{ title: string; description: string; picUrl: string; url: string }[]>([])

const needMatchValue = computed(() =>
  ['click', 'keyword_exact', 'keyword_contains'].includes(editForm.matchType))

// 菜单 Key 候选 = 当前账号菜单草稿 button[].key（含子菜单层级）+ click 规则 matchValue，去重；
// 账号未选或加载失败时选项为空，仍可经 allow-create 手输
const draftKeys = ref<string[]>([])
const ruleKeys = ref<string[]>([])
const clickKeyOptions = computed(() =>
  [...new Set([...draftKeys.value, ...ruleKeys.value].filter(Boolean))])

async function loadClickKeyOptions() {
  const accId = editForm.accountId
  if (!accId) {
    draftKeys.value = []
    ruleKeys.value = []
    return
  }
  try {
    const draft: any = await getMenuDraft(accId)
    const keys = new Set<string>()
    const collect = (list: any[]) => {
      for (const b of list || []) {
        if (b.key) keys.add(b.key)
        if (Array.isArray(b.sub_button)) collect(b.sub_button)
      }
    }
    try {
      const parsed = JSON.parse(draft?.menuJson || '{}')
      collect(Array.isArray(parsed?.button) ? parsed.button : [])
    } catch { /* 草稿 JSON 无法解析时忽略，不影响规则候选 */ }
    draftKeys.value = [...keys]
  } catch {
    draftKeys.value = []
  }
  try {
    // 后端 /mp/rule/page 暂不支持 matchType 查询参数，拉回后前端过滤
    const data: any = await pageRules({ pageNum: 1, pageSize: 100, accountId: accId })
    ruleKeys.value = (data.records || [])
      .filter((r: any) => r.matchType === 'click' && r.matchValue)
      .map((r: any) => r.matchValue as string)
  } catch {
    ruleKeys.value = []
  }
}

function initForm() {
  return { accountId: undefined as number | undefined, name: '', matchType: 'keyword_contains',
    matchValue: '', replyType: 'text', priority: 0 }
}

function accountName(accountId: number) {
  return accounts.value.find((a) => a.id === accountId)?.name || accountId
}

function replySummary(rule: MpRule) {
  try {
    const json = JSON.parse(rule.replyContent || '{}')
    if (rule.replyType === 'news') {
      return `图文 ×${json.items?.length || 0}`
    }
    return json.content || ''
  } catch {
    return ''
  }
}

async function load() {
  const data: any = await pageRules(query)
  rows.value = data.records
  total.value = Number(data.total)
}

function openCreate() {
  Object.assign(editForm, initForm())
  editForm.id = undefined
  editForm.accountId = query.accountId ?? accounts.value[0]?.id
  editEnabled.value = true
  textContent.value = ''
  newsItems.value = [{ title: '', description: '', picUrl: '', url: '' }]
  editDialog.value = true
  loadClickKeyOptions()
}

function openEdit(rule: MpRule) {
  Object.assign(editForm, initForm())
  editForm.id = rule.id
  editForm.accountId = rule.accountId
  editForm.name = rule.name
  editForm.matchType = rule.matchType
  editForm.matchValue = rule.matchValue || ''
  editForm.replyType = rule.replyType
  editForm.priority = rule.priority
  editEnabled.value = rule.enabled === 1
  try {
    const json = JSON.parse(rule.replyContent || '{}')
    if (rule.replyType === 'news') {
      newsItems.value = (json.items || []).map((i: any) =>
        ({ title: i.title || '', description: i.description || '', picUrl: i.picUrl || '', url: i.url || '' }))
      textContent.value = ''
    } else {
      textContent.value = json.content || ''
      newsItems.value = []
    }
  } catch {
    textContent.value = ''
    newsItems.value = []
  }
  editDialog.value = true
  loadClickKeyOptions()
}

function buildReplyContent(): string {
  if (editForm.replyType === 'news') {
    const items = newsItems.value
      .filter((i) => i.title.trim())
      .map((i) => ({ title: i.title, description: i.description, picUrl: i.picUrl, url: i.url }))
    return JSON.stringify({ items })
  }
  return JSON.stringify({ content: textContent.value })
}

async function save() {
  if (!editForm.accountId) {
    ElMessage.warning('请选择公众号')
    return
  }
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写规则名')
    return
  }
  if (needMatchValue.value && !editForm.matchValue.trim()) {
    ElMessage.warning('请填写匹配值')
    return
  }
  if (editForm.replyType === 'text' && !textContent.value.trim()) {
    ElMessage.warning('请填写回复内容')
    return
  }
  if (editForm.replyType === 'news' && !newsItems.value.some((i) => i.title.trim())) {
    ElMessage.warning('请至少填写一条图文（标题必填）')
    return
  }
  const body = {
    id: editForm.id,
    accountId: editForm.accountId,
    name: editForm.name,
    matchType: editForm.matchType,
    matchValue: editForm.matchValue,
    replyType: editForm.replyType,
    replyContent: buildReplyContent(),
    priority: editForm.priority,
    enabled: editEnabled.value ? 1 : 0
  }
  if (editForm.id) {
    await updateRule(body)
  } else {
    await createRule(body)
  }
  ElMessage.success('已保存')
  editDialog.value = false
  load()
}

async function changeEnabled(rule: MpRule, enable: any) {
  await changeRuleStatus(rule.id!, !!enable)
  load()
}

async function remove(rule: MpRule) {
  await ElMessageBox.confirm(`确认删除规则「${rule.name}」？`, '删除', { type: 'warning' })
  await deleteRule(rule.id!)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  const data: any = await pageAccounts({ pageNum: 1, pageSize: 100 })
  accounts.value = data.records
  await load()
})
</script>

<style scoped>
.page {
  padding: 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: auto;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  align-items: center;
}
.spacer {
  flex: 1;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  width: 100%;
}
.news-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.news-item {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr auto;
  gap: 6px;
  align-items: center;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
}
</style>
