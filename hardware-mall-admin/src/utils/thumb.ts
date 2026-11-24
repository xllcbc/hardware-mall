export const THUMB_WIDTHS = {
  productList: 120,
  productDialog: 240,
  userAvatar: 80
} as const

const THUMB_QUALITY = 80

const OSS_HOST_SUFFIX = '.aliyuncs.com'
const PROCESS_PARAM = 'x-oss-process'

const extraOssHosts = new Set(
  ((import.meta.env.VITE_OSS_HOSTS as string | undefined) ?? '')
    .split(',')
    .map(host => host.trim().toLowerCase())
    .filter(Boolean)
)

const isOssImageUrl = (url: string): boolean => {
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return false
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return false
  }
  const hostname = parsed.hostname.toLowerCase()
  return hostname.endsWith(OSS_HOST_SUFFIX) || extraOssHosts.has(hostname)
}

const hasProcessParam = (urlWithoutFragment: string): boolean => {
  const queryIndex = urlWithoutFragment.indexOf('?')
  if (queryIndex === -1) {
    return false
  }
  return new URLSearchParams(urlWithoutFragment.slice(queryIndex + 1)).has(PROCESS_PARAM)
}

/**
 * Returns an OSS thumbnail URL for the given display width.
 *
 * Empty values, non-OSS URLs, local placeholders and already processed URLs
 * are returned unchanged so callers can safely use a single helper for both
 * uploaded images and placeholder assets. Existing query parameters are kept
 * and the processing parameter is appended with `&`.
 */
export function thumb(url: string | null | undefined, width: number): string {
  if (!url) {
    return ''
  }
  if (!Number.isFinite(width) || width <= 0) {
    return url
  }
  if (!isOssImageUrl(url)) {
    return url
  }

  const fragmentIndex = url.indexOf('#')
  const urlWithoutFragment = fragmentIndex === -1 ? url : url.slice(0, fragmentIndex)
  const fragment = fragmentIndex === -1 ? '' : url.slice(fragmentIndex)

  if (hasProcessParam(urlWithoutFragment)) {
    return url
  }

  const separator = urlWithoutFragment.endsWith('?')
    ? ''
    : urlWithoutFragment.includes('?')
      ? '&'
      : '?'

  const process = `image/resize,w_${Math.round(width)}/format,webp/quality,q_${THUMB_QUALITY}`
  return `${urlWithoutFragment}${separator}${PROCESS_PARAM}=${process}${fragment}`
}
