import { afterEach, describe, expect, it, vi } from 'vitest'
import { THUMB_WIDTHS, thumb } from './thumb'

const OSS_IMAGE = 'https://java0251014.oss-cn-beijing.aliyuncs.com/products/20261124120000abcd.jpg'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('thumb', () => {
  it('appends resize, webp and quality parameters to OSS images', () => {
    expect(thumb(OSS_IMAGE, 200)).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_200/format,webp/quality,q_80`
    )
    expect(thumb(OSS_IMAGE, 400)).toBe(
      `${OSS_IMAGE}?x-oss-process=image/resize,w_400/format,webp/quality,q_80`
    )
  })

  it('uses the documented display widths for uniapp surfaces', () => {
    expect(THUMB_WIDTHS.productCard).toBe(400)
    expect(THUMB_WIDTHS.productDetail).toBe(750)
    expect(THUMB_WIDTHS.listItem).toBe(200)
    expect(THUMB_WIDTHS.banner).toBe(750)

    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.productCard)).toContain(
      '/resize,w_400/format,webp/quality,q_80'
    )
    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.productDetail)).toContain(
      '/resize,w_750/format,webp/quality,q_80'
    )
    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.listItem)).toContain(
      '/resize,w_200/format,webp/quality,q_80'
    )
    expect(thumb(OSS_IMAGE, THUMB_WIDTHS.banner)).toContain(
      '/resize,w_750/format,webp/quality,q_80'
    )
  })

  it('treats configured custom domains as OSS images', async () => {
    vi.stubEnv('VITE_OSS_HOSTS', 'img.example.com, cdn.example.com')
    vi.resetModules()
    const { thumb: thumbWithConfiguredHosts } = await import('./thumb')

    expect(thumbWithConfiguredHosts('https://img.example.com/products/a.jpg', 200)).toBe(
      'https://img.example.com/products/a.jpg?x-oss-process=image/resize,w_200/format,webp/quality,q_80'
    )
    expect(thumbWithConfiguredHosts('https://cdn.example.com/products/a.jpg', 200)).toBe(
      'https://cdn.example.com/products/a.jpg?x-oss-process=image/resize,w_200/format,webp/quality,q_80'
    )
  })

  it('appends with & when the URL already has query parameters', () => {
    expect(thumb(`${OSS_IMAGE}?v=1`, 200)).toBe(
      `${OSS_IMAGE}?v=1&x-oss-process=image/resize,w_200/format,webp/quality,q_80`
    )
  })

  it('keeps fragments after the processing parameters', () => {
    expect(thumb(`${OSS_IMAGE}?v=1#top`, 200)).toBe(
      `${OSS_IMAGE}?v=1&x-oss-process=image/resize,w_200/format,webp/quality,q_80#top`
    )
  })

  it('does not stack processing parameters when the image is already processed', () => {
    const alreadyProcessed = `${OSS_IMAGE}?x-oss-process=image/resize,w_500`
    expect(thumb(alreadyProcessed, 200)).toBe(alreadyProcessed)
  })

  it('returns empty input untouched', () => {
    expect(thumb(undefined, 200)).toBe('')
    expect(thumb(null, 200)).toBe('')
    expect(thumb('', 200)).toBe('')
  })

  it('leaves local placeholders, data urls and relative paths untouched', () => {
    expect(thumb('/static/images/placeholder.svg', 200)).toBe('/static/images/placeholder.svg')
    expect(thumb('static/images/default.png', 200)).toBe('static/images/default.png')
    expect(thumb('data:image/png;base64,iVBORw0KGgo=', 200)).toBe(
      'data:image/png;base64,iVBORw0KGgo='
    )
  })

  it('leaves non-OSS urls untouched', () => {
    expect(thumb('https://example.com/products/a.jpg', 200)).toBe(
      'https://example.com/products/a.jpg'
    )
    expect(thumb('http://localhost:8080/products/a.jpg', 200)).toBe(
      'http://localhost:8080/products/a.jpg'
    )
  })

  it('leaves the url untouched when the width is not a positive number', () => {
    expect(thumb(OSS_IMAGE, 0)).toBe(OSS_IMAGE)
    expect(thumb(OSS_IMAGE, -200)).toBe(OSS_IMAGE)
    expect(thumb(OSS_IMAGE, Number.NaN)).toBe(OSS_IMAGE)
  })
})
