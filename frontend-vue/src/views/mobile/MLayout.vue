<template>
  <div class="m-layout">
    <div class="m-content">
      <router-view />
    </div>
    <van-tabbar route>
      <van-tabbar-item to="/mobile/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/mobile/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item to="/mobile/surgery-reports" icon="edit-o">报台</van-tabbar-item>
      <van-tabbar-item to="/mobile/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
</script>

<style scoped>
.m-layout { min-height: 100vh; background: #f5f7fa; }
.m-content { padding-bottom: 60px; }
</style>