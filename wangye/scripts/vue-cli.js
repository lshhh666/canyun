const path = require('path')
const { spawnSync } = require('child_process')

const cliArguments = process.argv.slice(2)
if (cliArguments.length === 0) {
  console.error('缺少 Vue CLI 命令，例如 serve 或 build')
  process.exit(1)
}

const environment = { ...process.env }
const nodeMajorVersion = Number(process.versions.node.split('.')[0])
if (nodeMajorVersion >= 17) {
  const currentOptions = environment.NODE_OPTIONS || ''
  if (!currentOptions.includes('--openssl-legacy-provider')) {
    environment.NODE_OPTIONS = `${currentOptions} --openssl-legacy-provider`.trim()
  }
}

const cliPath = path.resolve(
  __dirname,
  '../node_modules/@vue/cli-service/bin/vue-cli-service.js'
)
const result = spawnSync(process.execPath, [cliPath, ...cliArguments], {
  env: environment,
  stdio: 'inherit'
})

if (result.error) {
  console.error(`Vue CLI 启动失败：${result.error.message}`)
  process.exit(1)
}

process.exit(result.status === null ? 1 : result.status)
