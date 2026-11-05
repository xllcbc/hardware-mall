import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { extname, isAbsolute, join, relative, resolve } from 'node:path'

const repositoryRoot = resolve(import.meta.dirname, '..')

const surfaces = [
  {
    port: 4173,
    root: process.env.E2E_ADMIN_DIST
      ? resolve(process.env.E2E_ADMIN_DIST)
      : join(repositoryRoot, 'hardware-mall-admin', 'dist')
  },
  {
    port: 4174,
    root: process.env.E2E_H5_DIST
      ? resolve(process.env.E2E_H5_DIST)
      : join(repositoryRoot, 'hardware-mall-uniapp', 'dist', 'build', 'h5')
  }
]

const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpeg', 'image/jpeg'],
  ['.jpg', 'image/jpeg'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.webp', 'image/webp'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2']
])

function startSurface({ port, root }) {
  if (!existsSync(root)) {
    throw new Error(`E2E build output is missing: ${root}`)
  }

  const rootPath = resolve(root)
  const server = createServer((request, response) => {
    const url = new URL(request.url ?? '/', `http://127.0.0.1:${port}`)
    if (url.pathname === '/__health') {
      response.writeHead(200, { 'content-type': 'text/plain' })
      response.end('ok')
      return
    }

    const relativePath = decodeURIComponent(url.pathname).replace(/^\/+/, '')
    const requestedPath = resolve(rootPath, relativePath || 'index.html')
    const pathFromRoot = relative(rootPath, requestedPath)
    const safePath = !pathFromRoot.startsWith('..') && !isAbsolute(pathFromRoot)
      ? requestedPath
      : join(rootPath, 'index.html')
    const filePath = existsSync(safePath) && statSync(safePath).isFile()
      ? safePath
      : join(rootPath, 'index.html')

    response.writeHead(200, {
      'cache-control': 'no-store',
      'content-type': contentTypes.get(extname(filePath)) ?? 'application/octet-stream'
    })
    createReadStream(filePath).pipe(response)
  })

  server.listen(port, '127.0.0.1')
  return server
}

const servers = surfaces.map(startSurface)

function shutdown() {
  for (const server of servers) {
    server.close()
  }
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
