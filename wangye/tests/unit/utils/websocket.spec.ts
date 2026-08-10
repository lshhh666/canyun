import fs from 'fs'
import path from 'path'
import { buildWebSocketUrl } from '@/utils/websocket'

describe('buildWebSocketUrl', () => {
  const location = { protocol: 'http:', host: 'localhost:8090' } as Location

  it('prefers an explicit base and normalizes its slash', () => {
    expect(buildWebSocketUrl('ws://127.0.0.1:8080/ws', 'c1', location))
      .toBe('ws://127.0.0.1:8080/ws/c1')
  })

  it('falls back to nginx on the current origin', () => {
    expect(buildWebSocketUrl(undefined, 'c1', location))
      .toBe('ws://localhost:8090/ws/c1')
  })

  it('uses wss for https', () => {
    const secure = { protocol: 'https:', host: 'meal.example.com' } as Location
    expect(buildWebSocketUrl('', 'c1', secure))
      .toBe('wss://meal.example.com/ws/c1')
  })
})

it('uses the builder without a permanent error notification', () => {
  const navbar = fs.readFileSync(
    path.resolve(__dirname, '../../../src/layout/components/Navbar/index.vue'),
    'utf8'
  )
  expect(navbar).toContain('buildWebSocketUrl(process.env.VUE_APP_SOCKET_URL, clientId)')
  expect(navbar).toContain("console.warn('CloudMeal WebSocket connection unavailable')")
  expect(navbar).toContain('if (this.websocket)')
})
