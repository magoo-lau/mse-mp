<template>
  <div class="page">
    <div class="cards">
      <div class="card">
        <div class="card-num">{{ summary?.accountTotal ?? '-' }}</div>
        <div class="card-label">接入公众号</div>
      </div>
      <div class="card">
        <div class="card-num">{{ summary?.fanSubscribed ?? '-' }}</div>
        <div class="card-label">关注粉丝</div>
      </div>
      <div class="card">
        <div class="card-num">{{ summary?.messageToday ?? '-' }}</div>
        <div class="card-label">今日消息/事件</div>
      </div>
      <div class="card">
        <div class="card-num">{{ summary?.ruleEnabled ?? '-' }}</div>
        <div class="card-label">启用中规则</div>
      </div>
    </div>

    <div class="row">
      <div class="panel trend">
        <div class="panel-title">近 7 天入站趋势</div>
        <el-table :data="trend" size="small" border height="220">
          <el-table-column prop="date" label="日期" width="110" />
          <el-table-column label="事件" align="center">
            <template #default="{ row }">
              <div class="bar-wrap">
                <div class="bar event" :style="{ width: barWidth(row.eventCount) }" />
                <span class="bar-num">{{ row.eventCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="消息" align="center">
            <template #default="{ row }">
              <div class="bar-wrap">
                <div class="bar message" :style="{ width: barWidth(row.messageCount) }" />
                <span class="bar-num">{{ row.messageCount }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel recent">
        <div class="panel-title">最近消息/事件</div>
        <el-table :data="recent" size="small" border height="220">
          <el-table-column prop="createTime" label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="公众号" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column prop="openId" label="OpenID" width="150" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="110">
            <template #default="{ row }"><el-tag size="small">{{ row.type }}</el-tag></template>
          </el-table-column>
          <el-table-column label="内容" show-overflow-tooltip>
            <template #default="{ row }">{{ summaryText(row.payload) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSummary, getTrend, pageAccounts, pageMessages, type Summary, type TrendPoint } from '../api/mp'

const summary = ref<Summary>()
const trend = ref<TrendPoint[]>([])
const recent = ref<any[]>([])
const accounts = ref<any[]>([])

const maxTrend = computed(() =>
  Math.max(1, ...trend.value.flatMap((t) => [Number(t.eventCount), Number(t.messageCount)])))

function barWidth(count: number | string) {
  return `${(Number(count) / maxTrend.value) * 100}%`
}

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
    return json.content || json.event || json.title || json.label || JSON.stringify(json)
  } catch {
    return payload
  }
}

onMounted(async () => {
  const [s, t, m, a]: any = await Promise.all([
    getSummary(),
    getTrend({ days: 7 }),
    pageMessages({ pageNum: 1, pageSize: 10 }),
    pageAccounts({ pageNum: 1, pageSize: 100 })
  ])
  summary.value = s
  trend.value = t
  recent.value = m.records
  accounts.value = a.records
})
</script>

<style scoped>
.page {
  padding: 12px;
  height: 100%;
  box-sizing: border-box;
  overflow: auto;
}
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 14px 16px;
  background: var(--el-bg-color);
}
.card-num {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.card-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.row {
  display: flex;
  gap: 12px;
}
.panel {
  flex: 1;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--el-bg-color);
}
.panel-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
}
.bar-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.bar {
  height: 8px;
  border-radius: 4px;
  min-width: 2px;
}
.bar.event {
  background: var(--el-color-success);
}
.bar.message {
  background: var(--el-color-primary);
}
.bar-num {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
