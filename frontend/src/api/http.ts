import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  headers: {
    Accept: 'application/json',
  },
})

export default http
