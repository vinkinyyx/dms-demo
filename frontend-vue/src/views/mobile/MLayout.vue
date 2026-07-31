<template>
  <div class="m-layout">
    <div class="m-content">
      <router-view />
    </div>
    <van-tabbar route>
      <van-tabbar-item to="/mobile/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/mobile/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item to="/mobile/receipt" icon="sign-o">收货</van-tabbar-item>
      <van-tabbar-item to="/mobile/shipment" icon="logistics-o">发货</van-tabbar-item>
      <van-tabbar-item to="/mobile/report" icon="chart-trending-o">报表</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
</script>

<style scoped>
.m-layout { min-height: 100vh; background: #f5f7fa; }
.m-content { padding-bottom: 60px; }
</style>
