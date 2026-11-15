<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.accountId" placeholder="公众号" clearable style="width: 200px">
        <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-select v-model="query.kind" placeholder="类别" clearable style="width: 110px">
        <el-option label="事件" value="event" />
        <el-option label="消息" value="message" />
      </el-select>
      <el-select v-model="query.type" placeholder="类型" clearable filterable style="width: 150px">
        <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="OpenID / 内容 / 回复" clearable style="width: 200px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
    </div>

    <el-table :data="rows" size="small" border>
      <el-table-column prop="createTime" label="时间" width="150">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="公众号" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ accountName(row.accountId) }}</template>
      </el-table-column>
      <el-table-column prop="openId" label="OpenID" width="150" show-overflow-tooltip />
      <el-table-column label="类别" width="70" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.kind === 'event' ? 'warning' : 'primary'">{{ row.kind === 'event' ? '事件' : '消息' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.type }}</el-tag></template>
      </el-table-column>
      <el-table-column label="内容" show-overflow-tooltip>
        <template #default="{ row }">{{ summaryText(row.payload) }}</template>
      </el-table-column>
      <el-table-column label="已回复" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.replyContent ? 'success' : 'info'">{{ row.replyContent ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNum" v-model:size="query.pageSize" @change="load" />

    <el-drawer v-model="detailDialog" title="消息详情" size="480px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ fmt(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="公众号">{{ accountName(detail.accountId) }}</el-descriptions-item>
        <el-descriptions-item label="OpenID">{{ detail.openId }}</el-descriptions-item>
        <el-descriptions-item label="方向 / 类别">{{ detail.direction }} / {{ detail.kind }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.type }}</el-descriptions-item>
        <el-descriptions-item label="MsgId">{{ detail.msgId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="traceId">{{ detail.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="幂等键">{{ detail.dedupKey }}</el-descriptions-item>
      </el-descriptions>
      <div class="block">
        <div class="block-title">载荷（payload）</div>
        <pre class="pre">{{ pretty(detail?.payload) }}</pre>
      </div>
      <div v-if="detail?.replyContent" class="block">
        <div class="block-title">被动回复（缓存）</div>
        <pre class="pre">{{ detail?.replyContent }}</pre>
      </div>
      <div class="block">
        <div class="block-title">原始报文（解密后明文 XML）</div>
        <pre class="pre">{{ detail?.rawXml || '（未记录）' }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageAccounts, pageMessages, type MpMessage } from '../api/mp'
import { Pagination } from 'mse-ui-shared/pc'

const TYPE_OPTIONS = [
  'subscribe', 'subscribe_scan', 'unsubscribe', 'click', 'view', 'scan', 'location',
  'text', 'image', 'voice', 'video', 'shortvideo', 'link'
]

const accounts = ref<any[]>([])
const rows = ref<MpMessage[]>([])
const total = ref(0)
const detail = ref<MpMessage | null>(null)
const detailDialog = ref(false)
const query = reactive<{ pageNum: number; pageSize: number; accountId: number | undefined; kind: string; type: string; keyword: string }>({
  pageNum: 1, pageSize: 10, accountId: undefined, kind: '', type: '', keyword: ''
})

function accountName(accountId: number) {
  return accounts.value.find((a) => a.id === accountId)?.name || accountId
}

function fmt(time: string) {
  return (time || '').replace('T', ' ').slice(0, 19)
}

function summaryText(payload: string | null) {
  if (!payload) return ''
  try {
    const json = JSON.parse(payload)
    return json.content || json.event || json.title || json.label || json.recognition || JSON.stringify(json)
  } catch {
    return payload
  }
}

function pretty(text: string | null | undefined) {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

async function load() {
  const data: any = await pageMessages({
    ...query,
    kind: query.kind || undefined,
    type: query.type || undefined
  })
  rows.value = data.records
  total.value = Number(data.total)
}

function openDetail(row: MpMessage) {
  detail.value = row
  detailDialog.value = true
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
.block {
  margin-top: 12px;
}
.block-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}
.pre {
  margin: 0;
  padding: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
}
</style>
