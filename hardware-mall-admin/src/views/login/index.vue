<template>
  <div class="login-container">
    <div class="login-background">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
    </div>
    
    <div class="login-card animate-fade-in-up">
      <div class="login-header">
        <div class="login-logo">
          <el-icon><Tools /></el-icon>
        </div>
        <h1 class="login-title">五金商城管理系统</h1>
        <p class="login-subtitle">Hardware Mall Admin System</p>
      </div>
      
      <el-form 
        ref="formRef"
        :model="form" 
        :rules="rules" 
        class="login-form"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <div class="input-wrapper">
            <el-icon class="input-icon"><User /></el-icon>
            <el-input 
              v-model="form.username" 
              placeholder="请输入用户名"
              class="login-input"
              clearable
            />
          </div>
        </el-form-item>
        
        <el-form-item prop="password">
          <div class="input-wrapper">
            <el-icon class="input-icon"><Lock /></el-icon>
            <el-input 
              v-model="form.password" 
              type="password"
              placeholder="请输入密码"
              class="login-input"
              show-password
            />
          </div>
        </el-form-item>
        
        <el-form-item>
          <el-checkbox v-model="rememberMe">记住密码</el-checkbox>
        </el-form-item>
        
        <el-form-item>
          <el-button 
            type="primary" 
            :loading="loading" 
            class="login-btn"
            @click="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登录中...</span>
          </el-button>
        </el-form-item>
      </el-form>
      
    </div>
    
    <div class="login-decoration">
      <div class="decoration-circle circle-1"></div>
      <div class="decoration-circle circle-2"></div>
      <div class="decoration-circle circle-3"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/admin/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        const res = await login(form)
        localStorage.setItem('token', res.token)
        localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
        ElMessage.success('登录成功')
        router.push('/dashboard')
      } catch {
        // error handled by interceptor
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-background {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.bg-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1e3a5f 0%, #2d5a87 50%, #1a365d 100%);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: var(--space-xl);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: var(--radius-xl);
  box-shadow: 
    0 25px 50px -12px rgba(0, 0, 0, 0.25),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

[data-theme="dark"] .login-card {
  background: rgba(31, 41, 55, 0.95);
  box-shadow: 
    0 25px 50px -12px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.05);
}

.login-header {
  text-align: center;
  margin-bottom: var(--space-xl);
}

.login-logo {
  width: 56px;
  height: 56px;
  margin: 0 auto var(--space-md);
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.35);
}

.login-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--space-xs);
  letter-spacing: 2px;
}

.login-subtitle {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.login-form {
  margin-top: var(--space-lg);
}

.input-wrapper {
  position: relative;
  width: 100%;
}

.input-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 18px;
  color: var(--text-tertiary);
  z-index: 1;
  transition: color var(--transition-fast);
}

.input-wrapper:focus-within .input-icon {
  color: var(--primary-color);
}

.login-input {
  width: 100%;
}

.login-input :deep(.el-input__wrapper) {
  padding-left: 42px;
  height: 48px;
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--border-color);
  transition: all var(--transition-fast);
}

.login-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary-color);
}

.login-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px var(--primary-color);
}

.login-input :deep(.el-input__inner) {
  font-size: var(--font-size-base);
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: var(--font-size-base);
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
  transition: all var(--transition-base);
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.45);
}

.login-btn:active {
  transform: translateY(0);
}

.login-footer {
  margin-top: var(--space-lg);
  text-align: center;
}

.footer-text {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.login-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;
}

.circle-1 {
  width: 400px;
  height: 400px;
  background: var(--primary-light);
  top: -200px;
  right: -100px;
  animation: float 6s ease-in-out infinite;
}

.circle-2 {
  width: 300px;
  height: 300px;
  background: var(--warning);
  bottom: -150px;
  left: -100px;
  animation: float 8s ease-in-out infinite reverse;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: var(--success);
  top: 50%;
  left: 10%;
  animation: float 7s ease-in-out infinite;
}

:deep(.el-checkbox__label) {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: var(--primary-color);
  border-color: var(--primary-color);
}
</style>
