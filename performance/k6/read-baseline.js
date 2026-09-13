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

const config = loadPerformanceConfig();
const endpointNames = [
  'health',
  'knowledge_tree',
  'questions_page_first',
  'questions_page_middle',
  'questions_page_last',
  'questions_subject',
  'questions_active',
  'questions_mastered',
  'questions_subject_active',
  'question_detail',
  'reviews_due',
  'reviews_due_subject',
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
      duration: '30s',
      gracefulStop: '5s',
      tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-vus',
      exec: 'readCycle',
      vus: 1,
      startTime: '35s',
      duration: '5m',
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

function request(endpointName, path, validator) {
  const measured = exec.scenario.name === 'measure';
  const response = http.get(
    config.baseUrl + path,
    defaultRequestParams(config, endpointName, exec.scenario.name),
  );
  const successful = (
    response.status === 200
    && (!validator || validator(response))
  );
  check(response, {
    [endpointName + ' returned expected response']: () => successful,
  });
  recordEndpoint(
    metrics,
    endpointName,
    response,
    successful,
    measured,
  );
  sleep(0.1);
}

export function readCycle() {
  const index = (__ITER + __VU) % config.detailQuestionIds.length;
  const questionId = config.detailQuestionIds[index];
  const subject = config.subjects[
    (__ITER + __VU) % config.subjects.length
  ];
  const encodedSubject = encodeURIComponent(subject);
  const pageSize = config.pageSize;

  request('health', '/api/health', (response) => {
    try {
      return response.json().status === 'ok';
    } catch (error) {
      return false;
    }
  });
  request(
    'knowledge_tree',
    '/api/knowledge-points/tree',
    isJsonObject,
  );
  request(
    'questions_page_first',
    '/api/questions?page=0&size=' + pageSize,
    isJsonObject,
  );
  request(
    'questions_page_middle',
    '/api/questions?page=' + config.middlePage + '&size=' + pageSize,
    isJsonObject,
  );
  request(
    'questions_page_last',
    '/api/questions?page=' + config.lastPage + '&size=' + pageSize,
    isJsonObject,
  );
  request(
    'questions_subject',
    '/api/questions?page=0&size=' + pageSize
      + '&subject=' + encodedSubject,
    isJsonObject,
  );
  request(
    'questions_active',
    '/api/questions?page=0&size=' + pageSize
      + '&reviewStatus=ACTIVE',
    isJsonObject,
  );
  request(
    'questions_mastered',
    '/api/questions?page=0&size=' + pageSize
      + '&reviewStatus=MASTERED',
    isJsonObject,
  );
  request(
    'questions_subject_active',
    '/api/questions?page=0&size=' + pageSize
      + '&subject=' + encodedSubject
      + '&reviewStatus=ACTIVE',
    isJsonObject,
  );
  request(
    'question_detail',
    '/api/questions/' + questionId,
    isJsonObject,
  );
  request('reviews_due', '/api/reviews/due/next', isJsonObject);
  request(
    'reviews_due_subject',
    '/api/reviews/due/next?subject=' + encodedSubject,
    isJsonObject,
  );
}

export function handleSummary(data) {
  return createSummary(data, 'read-baseline', metrics, config);
}
