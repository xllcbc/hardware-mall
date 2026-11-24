import { afterEach, describe, expect, it, vi } from 'vitest'
import { THUMB_WIDTHS, thumb } from './thumb'

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/20261124120000abcd.jpg'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('thumb', () => {
  it('appends resize, webp and quality parameters to OSS images', () => {
    expect(thumb(OSS_IMAGE, 120)).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_120/format,webp/quality,q_80`
    )
    expect(thumb(OSS_IMAGE, 240)).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_240/format,webp/quality,q_80`
    )
  })

  it('uses the documented display widths for admin surfaces', () => {
    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.productList)).toContain(
      '/resize,w_120/format,webp/quality,q_80'
    )
    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.productDialog)).toContain(
      '/resize,w_240/format,webp/quality,q_80'
    )
    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.userAvatar)).toContain(
      '/resize,w_80/format,webp/quality,q_80'
    )
  })

  it('treats configured custom domains as OSS images', async () => {
    vi.stubEnv('VITE_OSS_HOSTS', 'img.example.com, cdn.example.com')
    vi.resetModules()
    const { thumb: thumbWithConfiguredHosts } = await import('./thumb')

    expect(thumbWithConfiguredHosts('https://img.example.com/products/a.jpg', 120)).toBe(
      'https://img.example.com/products/a.jpg?x-oss-process=image/resize,w_120/format,webp/quality,q_80'
    )
    expect(thumbWithConfiguredHosts('https://cdn.example.com/products/a.jpg', 120)).toBe(
      'https://cdn.example.com/products/a.jpg?x-oss-process=image/resize,w_120/format,webp/quality,q_80'
    )
  })

  it('appends with & when the URL already has query parameters', () => {
    expect(thumb(`${OSS_IMAGE}?v=1`, 120)).toBe(
      `${OSS_IMAGE}?v=1&x-oss-process=image/resize,w_120/format,webp/quality,q_80`
    )
  })

  it('keeps fragments after the processing parameters', () => {
    expect(thumb(`${OSS_IMAGE}?v=1#top`, 120)).toBe(
      `${OSS_IMAGE}?v=1&x-oss-process=image/resize,w_120/format,webp/quality,q_80#top`
    )
  })

  it('does not stack processing parameters when the image is already processed', () => {
    const alreadyProcessed = `${OSS_IMAGE}?x-oss-process=image/resize,w_500`
    expect(thumb(alreadyProcessed, 120)).toBe(alreadyProcessed)
  })

  it('returns empty input untouched', () => {
    expect(thumb(undefined, 120)).toBe('')
    expect(thumb(null, 120)).toBe('')
    expect(thumb('', 120)).toBe('')
  })

  it('leaves local placeholders, data urls and relative paths untouched', () => {
    expect(thumb('/static/placeholder.png', 120)).toBe('/static/placeholder.png')
    expect(thumb('static/placeholder.png', 120)).toBe('static/placeholder.png')
    expect(thumb('data:image/png;base64,iVBORw0KGgo=', 120)).toBe('data:image/png;base64,iVBORw0KGgo=')
  })

  it('leaves non-OSS urls untouched', () => {
    expect(thumb('https://example.com/products/a.jpg', 120)).toBe('https://example.com/products/a.jpg')
    expect(thumb('http://localhost:8080/products/a.jpg', 120)).toBe(
      'http://localhost:8080/products/a.jpg'
    )
  })

  it('leaves the url untouched when the width is not a positive number', () => {
    expect(thumb(OSS_IMAGE, 0)).toBe(OSS_IMAGE)
    expect(thumb(OSS_IMAGE, -120)).toBe(OSS_IMAGE)
    expect(thumb(OSS_IMAGE, Number.NaN)).toBe(OSS_IMAGE)
  })
})
