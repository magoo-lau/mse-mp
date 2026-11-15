<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.accountId" placeholder="公众号" clearable style="width: 200px">
        <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-select v-model="query.subscribed" placeholder="关注状态" clearable style="width: 120px">
        <el-option label="关注中" :value="1" />
        <el-option label="已取关" :value="0" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="OpenID / 昵称 / UnionID" clearable style="width: 220px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
    </div>

    <el-table :data="rows" size="small" border>
      <el-table-column label="粉丝" min-width="160">
        <template #default="{ row }">
          <div class="fan-cell">
            <el-avatar :size="24" :src="row.headImgUrl || undefined">{{ (row.nickname || '粉')[0] }}</el-avatar>
            <span class="fan-name">{{ row.nickname || '-' }}</span>
            <el-tag v-if="row.profileIncomplete" size="small" type="warning">资料不全</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="openId" label="OpenID" min-width="150" show-overflow-tooltip />
      <el-table-column label="关注状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.subscribed ? 'success' : 'info'">{{ row.subscribed ? '关注中' : '已取关' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="性别" width="60" align="center">
        <template #default="{ row }">{{ ['未知', '男', '女'][row.sex] || '未知' }}</template>
      </el-table-column>
      <el-table-column label="地区" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ [row.province, row.city].filter(Boolean).join(' ') || '-' }}</template>
      </el-table-column>
      <el-table-column prop="subscribeScene" label="关注场景" width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.subscribeScene || '-' }}</template>
      </el-table-column>
      <el-table-column label="关注时间" width="150">
        <template #default="{ row }">{{ fmt(row.subscribeTime) }}</template>
      </el-table-column>
      <el-table-column label="取关时间" width="150">
        <template #default="{ row }">{{ fmt(row.unsubscribeTime) }}</template>
      </el-table-column>
      <el-table-column label="最近活跃" width="150">
        <template #default="{ row }">{{ fmt(row.lastActiveTime) }}</template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNum" v-model:size="query.pageSize" @change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageAccounts, pageFans, type MpFan } from '../api/mp'
import { Pagination } from 'mse-ui-shared/pc'

const accounts = ref<any[]>([])
const rows = ref<MpFan[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; accountId: number | undefined; subscribed: number | undefined; keyword: string }>({
  pageNum: 1, pageSize: 10, accountId: undefined, subscribed: undefined, keyword: ''
})

function fmt(time: string | null) {
  return (time || '').replace('T', ' ').slice(0, 19)
}

async function load() {
  const data: any = await pageFans(query)
  rows.value = data.records
  total.value = Number(data.total)
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
.fan-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fan-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
