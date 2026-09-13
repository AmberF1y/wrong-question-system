import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
  createEndpointMetrics,
  createSummary,
  defaultRequestParams,
  http,
  isJsonObject,
  loadPerformanceConfig,
  recordEndpoint,
  verifyBackendIdentity,
} from './lib/common.js';

if (__ENV.PERF_ENABLE_CONCURRENT_READS !== 'I_UNDERSTAND_5_VUS') {
  throw new Error(
    'Concurrent reads require PERF_ENABLE_CONCURRENT_READS='
      + 'I_UNDERSTAND_5_VUS',
  );
}

const config = loadPerformanceConfig();
const endpointNames = [
  'concurrent_questions_page',
  'concurrent_question_detail',
  'concurrent_reviews_due',
  'concurrent_knowledge_tree',
];
const metrics = createEndpointMetrics(endpointNames);

export const options = {
  summaryTrendStats: [
    'avg',
    'min',
    'med',
    'max',
    'p(90)',
    'p(95)',
    'p(99)',
  ],
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      exec: 'readCycle',
      vus: 1,
      duration: '15s',
      tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-vus',
      exec: 'readCycle',
      vus: 5,
      startTime: '20s',
      duration: '3m',
      gracefulStop: '5s',
      tags: { phase: 'measure' },
    },
  },
  thresholds: {
    perf_unexpected_errors: [
      {
        threshold: 'rate<0.01',
        abortOnFail: true,
        delayAbortEval: '15s',
      },
    ],
  },
};

export function setup() {
  return verifyBackendIdentity(config);
}

function request(name, path) {
  const measured = exec.scenario.name === 'measure';
  const response = http.get(
    config.baseUrl + path,
    defaultRequestParams(config, name, exec.scenario.name),
  );
  const successful = response.status === 200 && isJsonObject(response);
  check(response, {
    [name + ' returned expected JSON']: () => successful,
  });
  recordEndpoint(metrics, name, response, successful, measured);
  sleep(0.1);
}

export function readCycle() {
  const id = config.detailQuestionIds[
    (__ITER + __VU) % config.detailQuestionIds.length
  ];
  request(
    'concurrent_questions_page',
    '/api/questions?page=0&size=' + config.pageSize,
  );
  request('concurrent_question_detail', '/api/questions/' + id);
  request('concurrent_reviews_due', '/api/reviews/due/next');
  request('concurrent_knowledge_tree', '/api/knowledge-points/tree');
}

export function handleSummary(data) {
  return createSummary(data, 'concurrent-reads', metrics, config);
}
