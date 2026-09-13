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
  __ENV.PERF_ENABLE_REVIEW_CONCURRENCY
  !== 'I_UNDERSTAND_2_REQUEST_REVIEW_MUTATION'
) {
  throw new Error(
    'Review concurrency requires PERF_ENABLE_REVIEW_CONCURRENCY='
      + 'I_UNDERSTAND_2_REQUEST_REVIEW_MUTATION',
  );
}

const config = loadPerformanceConfig();
const endpointNames = [
  'review_evaluation_success',
  'review_evaluation_expected_conflict',
  'review_evaluation_unexpected',
  'review_state_after',
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
    review_concurrency: {
      executor: 'shared-iterations',
      exec: 'evaluateConcurrently',
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
  const identity = verifyBackendIdentity(config);
  if (config.reviewConcurrentQuestionIds.length !== 20) {
    throw new Error('Expected exactly 20 dedicated review fixtures');
  }
  return identity;
}

function conflictCode(response) {
  try {
    return response.json().code;
  } catch (error) {
    return null;
  }
}

function recordEvaluation(response) {
  if (response.status === 200 && isJsonObject(response)) {
    recordEndpoint(
      metrics,
      'review_evaluation_success',
      response,
      true,
      true,
    );
    return 'success';
  }
  const code = conflictCode(response);
  if (
    response.status === 409
    && (
      code === 'REVIEW_CONCURRENT_MODIFICATION'
      || code === 'REVIEW_NOT_DUE'
    )
  ) {
    recordEndpoint(
      metrics,
      'review_evaluation_expected_conflict',
      response,
      true,
      true,
    );
    return 'conflict';
  }
  recordEndpoint(
    metrics,
    'review_evaluation_unexpected',
    response,
    false,
    true,
  );
  return 'unexpected';
}

export function evaluateConcurrently() {
  const questionId = config.reviewConcurrentQuestionIds[__ITER];
  const url = (
    config.baseUrl
    + '/api/reviews/'
    + questionId
    + '/evaluations'
  );
  const requestBody = JSON.stringify({ rating: 'NOT_KNOWN' });
  const params = {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Performance-Environment': config.environmentId,
    },
    timeout: config.requestTimeout,
    responseCallback: http.expectedStatuses(200, 409),
    tags: {
      endpoint: 'review_evaluation_attempt',
      phase: 'measure',
    },
  };
  const responses = http.batch([
    ['POST', url, requestBody, params],
    ['POST', url, requestBody, params],
  ]);
  const outcomes = responses.map(recordEvaluation);
  const pairSuccessful = (
    outcomes.filter((outcome) => outcome === 'success').length === 1
    && outcomes.filter((outcome) => outcome === 'conflict').length === 1
  );
  check(responses[0], {
    'review pair had one success and one expected conflict': () => (
      pairSuccessful
    ),
  });
  if (!pairSuccessful) {
    metrics.unexpectedErrors.add(1);
  }

  const stateResponse = http.get(
    config.baseUrl + '/api/questions/' + questionId,
    {
      headers: {
        Accept: 'application/json',
        'X-Performance-Environment': config.environmentId,
      },
      timeout: config.requestTimeout,
      tags: {
        endpoint: 'review_state_after',
        phase: 'measure',
      },
    },
  );
  let stateSuccessful = false;
  if (stateResponse.status === 200 && isJsonObject(stateResponse)) {
    const body = stateResponse.json();
    stateSuccessful = (
      body.reviewStatus === 'ACTIVE'
      && body.nextReviewDate !== null
    );
  }
  recordEndpoint(
    metrics,
    'review_state_after',
    stateResponse,
    stateSuccessful,
    true,
  );
  sleep(0.25);
}

export function handleSummary(data) {
  return createSummary(data, 'review-concurrency', metrics, config);
}
