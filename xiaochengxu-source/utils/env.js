const ENVIRONMENTS = {
  local: 'http://localhost:8080',
  lan: '',
  test: '',
  production: ''
}

export const currentEnvironment = 'local'
export const baseUrl = ENVIRONMENTS[currentEnvironment]
