import { File as NodeFile } from 'node:buffer'
import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'

vi.mock('@/router', () => ({
  default: { push: vi.fn() }
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    token: null,
    clearAuth: vi.fn(),
    refreshToken: vi.fn()
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn()
  }
}))

import { uploadProductImage } from './upload'
import { http } from '@/utils/request'

describe('uploadProductImage timeout policy', () => {
  let server: Server
  let baseURL: string

  beforeAll(async () => {
    server = createServer((request, response) => {
      request.resume()
      setTimeout(() => {
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify({
          code: 200,
          data: { url: 'https://cdn.example.com/products/product.jpg' }
        }))
      }, 150)
    })

    await new Promise<void>((resolve) => {
      server.listen(0, '127.0.0.1', resolve)
    })
    const { port } = server.address() as AddressInfo
    baseURL = `http://127.0.0.1:${port}/api`
  })

  afterAll(async () => {
    await new Promise<void>((resolve, reject) => {
      server.close((error) => error ? reject(error) : resolve())
    })
  })

  it('does not inherit the global timeout while the upload is still in progress', async () => {
    http.defaults.baseURL = baseURL
    http.defaults.timeout = 50
    const file = new NodeFile(['image-content'], 'product.jpg', {
      type: 'image/jpeg'
    }) as unknown as File

    await expect(uploadProductImage(file)).resolves.toEqual({
      url: 'https://cdn.example.com/products/product.jpg'
    })
  })
})
