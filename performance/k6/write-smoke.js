import { check, sleep } from 'k6';
import {
  createEndpointMetrics,
  createSummary,
  http,
  isJsonObject,
  loadPerformanceConfig,
  recordEndpoint,
  verifyBackendIdentity,
} from './lib/common.js';

if (
  __ENV.PERF_ENABLE_WRITES
  !== 'I_UNDERSTAND_DEDICATED_PERF_WRITES'
) {
  throw new Error(
    'Writes require PERF_ENABLE_WRITES='
      + 'I_UNDERSTAND_DEDICATED_PERF_WRITES',
  );
}

const config = loadPerformanceConfig();
const endpointNames = ['question_create', 'question_update'];
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
    write_smoke: {
      executor: 'shared-iterations',
      exec: 'writeQuestion',
      vus: 1,
      iterations: 20,
      maxDuration: '2m',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    perf_unexpected_errors: [
      {
        threshold: 'rate<0.01',
        abortOnFail: true,
        delayAbortEval: '5s',
      },
    ],
  },
};

export function setup() {
  return verifyBackendIdentity(config);
}

function questionPayload(marker, suffix) {
  return {
    questionText: (
      '[PERF WRITE '
      + marker
      + '] 写入性能夹具题目 '
      + suffix
      + '，公式 $x^2+1$。'
    ),
    wrongAnswer: '性能写入夹具错误答案 ' + marker,
    correctAnswer: '性能写入夹具正确答案 ' + marker,
    analysis: '性能写入夹具解析 ' + marker + ' ' + suffix,
    errorReason: '性能写入夹具错误原因 ' + marker,
    knowledgePointIds: config.createKnowledgePointIds,
  };
}

export function writeQuestion() {
  const marker = (
    config.environmentId
    + '-vu'
    + __VU
    + '-iter'
    + __ITER
  );
  const createResponse = http.post(
    config.baseUrl + '/api/questions',
    JSON.stringify(questionPayload(marker, 'create')),
    {
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Performance-Environment': config.environmentId,
      },
      timeout: config.requestTimeout,
      responseCallback: http.expectedStatuses(201),
      tags: {
        endpoint: 'question_create',
        phase: 'measure',
      },
    },
  );
  const createSuccessful = (
    createResponse.status === 201
    && isJsonObject(createResponse)
  );
  check(createResponse, {
    'question create succeeded': () => createSuccessful,
  });
  recordEndpoint(
    metrics,
    'question_create',
    createResponse,
    createSuccessful,
    true,
  );

  if (!createSuccessful) {
    sleep(0.5);
    return;
  }

  const questionId = createResponse.json().id;
  const updateResponse = http.put(
    config.baseUrl + '/api/questions/' + questionId,
    JSON.stringify(questionPayload(marker, 'update')),
    {
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Performance-Environment': config.environmentId,
      },
      timeout: config.requestTimeout,
      responseCallback: http.expectedStatuses(200),
      tags: {
        endpoint: 'question_update',
        phase: 'measure',
      },
    },
  );
  const updateSuccessful = (
    updateResponse.status === 200
    && isJsonObject(updateResponse)
  );
  check(updateResponse, {
    'question update succeeded': () => updateSuccessful,
  });
  recordEndpoint(
    metrics,
    'question_update',
    updateResponse,
    updateSuccessful,
    true,
  );
  sleep(0.5);
}

export function handleSummary(data) {
  return createSummary(data, 'write-smoke', metrics, config);
}
