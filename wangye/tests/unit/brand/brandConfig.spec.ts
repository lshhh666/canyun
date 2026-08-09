import fs from 'fs'
import path from 'path'

describe('CloudMeal public brand configuration', () => {
  const projectRoot = path.resolve(__dirname, '../../..')

  it('uses the CloudMeal product name in public metadata', () => {
    const html = fs.readFileSync(path.join(projectRoot, 'public/index.html'), 'utf8')
    const manifest = JSON.parse(
      fs.readFileSync(path.join(projectRoot, 'public/manifest.json'), 'utf8')
    )

    expect(html).toContain('<title>餐云管理平台</title>')
    expect(manifest.name).toBe('餐云管理平台')
    expect(manifest.short_name).toBe('餐云')
  })

  it('keeps development and final delivery on port 8090', () => {
    const vueConfig = fs.readFileSync(path.join(projectRoot, 'vue.config.js'), 'utf8')
    const nginxConfig = fs.readFileSync(
      path.resolve(projectRoot, '../nginx-1.20.2/conf/nginx.conf'),
      'utf8'
    )

    expect(vueConfig).toContain('port: 8090')
    expect(nginxConfig).toContain('listen       8090;')
    expect(nginxConfig).toContain('http://localhost:8080/admin/')
  })
})
