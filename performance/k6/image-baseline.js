import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
  createEndpointMetrics,
  createSummary,
  http,
  loadPerformanceConfig,
  recordEndpoint,
  verifyBackendIdentity,
} from './lib/common.js';

const config = loadPerformanceConfig();
const endpointNames = ['image_small_64k', 'image_large_1m'];
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
      exec: 'imageCycle',
      vus: 1,
      duration: '15s',
      gracefulStop: '5s',
      tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-vus',
      exec: 'imageCycle',
      vus: 1,
      startTime: '20s',
      duration: '1m',
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

function readImage(endpointName, questionId) {
  const measured = exec.scenario.name === 'measure';
  const response = http.get(
    config.baseUrl + '/api/questions/' + questionId + '/image',
    {
      headers: {
        Accept: 'image/png',
        'X-Performance-Environment': config.environmentId,
      },
      timeout: config.requestTimeout,
      responseType: 'binary',
      tags: {
        endpoint: endpointName,
        phase: exec.scenario.name,
      },
    },
  );
  const bodyLength = response.body && response.body.byteLength !== undefined
    ? response.body.byteLength
    : String(response.body || '').length;
  const successful = response.status === 200 && bodyLength > 0;
  check(response, {
    [endpointName + ' returned image bytes']: () => successful,
  });
  recordEndpoint(
    metrics,
    endpointName,
    response,
    successful,
    measured,
  );
}

export function imageCycle() {
  const smallIndex = (__ITER + __VU)
    % config.smallImageQuestionIds.length;
  const largeIndex = (__ITER + __VU)
    % config.largeImageQuestionIds.length;
  readImage(
    'image_small_64k',
    config.smallImageQuestionIds[smallIndex],
  );
  sleep(0.5);
  readImage(
    'image_large_1m',
    config.largeImageQuestionIds[largeIndex],
  );
  sleep(0.5);
}

export function handleSummary(data) {
  return createSummary(data, 'image-baseline', metrics, config);
}
