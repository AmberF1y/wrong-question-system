import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

export function loadPerformanceConfig() {
  const configPath = __ENV.PERF_CONFIG;
  const resultPath = __ENV.PERF_RESULT_PATH;
  if (!configPath || !resultPath) {
    throw new Error('PERF_CONFIG and PERF_RESULT_PATH are required');
  }

  const config = JSON.parse(open(configPath));
  if (config.mode !== 'performance') {
    throw new Error('Configuration is not marked as performance mode');
  }
  if (!/^wrong_question_system_perf_[0-9]{8}(?:_[a-z0-9_]+)?$/.test(
    config.databaseName,
  )) {
    throw new Error('Unsafe performance database name');
  }
  if (
    config.databaseName === 'wrong_question_system'
    || config.databaseName === 'wrong_question_system_test'
  ) {
    throw new Error('Production and ordinary test databases are forbidden');
  }
  if (!/^http:\/\/127\.0\.0\.1:[0-9]+$/.test(config.baseUrl)) {
    throw new Error('Performance BaseUrl must use loopback HTTP');
  }
  const port = Number(config.baseUrl.split(':').pop());
  if (port === 8080 || port === 5173) {
    throw new Error('Production application ports are forbidden');
  }
  if (
    !config.imageDirectory
    || !config.imageDirectory.toLowerCase().includes(
      config.databaseName.toLowerCase(),
    )
  ) {
    throw new Error('Dedicated image directory identity is missing');
  }
  if (!/^perf-[a-z0-9][a-z0-9-]{2,63}$/.test(config.environmentId)) {
    throw new Error('Invalid performance environment id');
  }
  if (!/^[0-9a-f]{40}$/.test(config.gitCommit)) {
    throw new Error('Invalid Git commit identity');
  }
  return config;
}

export function verifyBackendIdentity(config) {
  const response = http.get(
    config.baseUrl + '/api/performance/identity',
    {
      headers: {
        'X-Performance-Environment': config.environmentId,
      },
      timeout: config.requestTimeout,
      tags: {
        endpoint: 'performance_identity',
        phase: 'safety',
      },
    },
  );
  if (response.status !== 200) {
    throw new Error(
      'Performance identity endpoint failed with status '
        + response.status,
    );
  }

  let identity;
  try {
    identity = response.json();
  } catch (error) {
    throw new Error('Performance identity response is not valid JSON');
  }
  const normalizedExpectedImage = config.imageDirectory
    .replaceAll('/', '\\')
    .toLowerCase();
  const normalizedActualImage = String(identity.imageDirectory)
    .replaceAll('/', '\\')
    .toLowerCase();
  if (
    identity.mode !== 'performance'
    || identity.environmentId !== config.environmentId
    || identity.databaseName !== config.databaseName
    || normalizedActualImage !== normalizedExpectedImage
    || identity.gitCommit !== config.gitCommit
  ) {
    throw new Error('Backend identity does not match performance config');
  }
  return {
    environmentId: identity.environmentId,
    databaseName: identity.databaseName,
    imageDirectory: identity.imageDirectory,
    gitCommit: identity.gitCommit,
  };
}

export function createEndpointMetrics(endpointNames) {
  const metrics = {};
  endpointNames.forEach((name) => {
    metrics[name] = {
      duration: new Trend('perf_' + name + '_duration', true),
      requests: new Counter('perf_' + name + '_requests'),
      errors: new Rate('perf_' + name + '_errors'),
    };
  });
  return {
    endpointNames,
    endpoints: metrics,
    unexpectedErrors: new Rate('perf_unexpected_errors'),
  };
}

export function recordEndpoint(
  metricSet,
  endpointName,
  response,
  successful,
  measured,
) {
  if (!measured) {
    return;
  }
  const endpoint = metricSet.endpoints[endpointName];
  endpoint.duration.add(response.timings.duration);
  endpoint.requests.add(1);
  endpoint.errors.add(successful ? 0 : 1);
  metricSet.unexpectedErrors.add(successful ? 0 : 1);
}

function metricValue(data, name, key, fallback) {
  const metric = data.metrics[name];
  if (!metric || !metric.values || metric.values[key] === undefined) {
    return fallback;
  }
  return metric.values[key];
}

export function createSummary(data, testName, metricSet, config) {
  const endpoints = {};
  metricSet.endpointNames.forEach((name) => {
    endpoints[name] = {
      requests: metricValue(
        data,
        'perf_' + name + '_requests',
        'count',
        0,
      ),
      requestsPerSecond: metricValue(
        data,
        'perf_' + name + '_requests',
        'rate',
        0,
      ),
      p50Ms: metricValue(
        data,
        'perf_' + name + '_duration',
        'med',
        null,
      ),
      p95Ms: metricValue(
        data,
        'perf_' + name + '_duration',
        'p(95)',
        null,
      ),
      p99Ms: metricValue(
        data,
        'perf_' + name + '_duration',
        'p(99)',
        null,
      ),
      maxMs: metricValue(
        data,
        'perf_' + name + '_duration',
        'max',
        null,
      ),
      errorRate: metricValue(
        data,
        'perf_' + name + '_errors',
        'rate',
        null,
      ),
    };
  });

  const output = {
    testName,
    generatedAt: new Date().toISOString(),
    environmentId: config.environmentId,
    databaseName: config.databaseName,
    datasetId: config.dataset.id,
    gitCommit: config.gitCommit,
    endpoints,
    overall: {
      iterations: metricValue(data, 'iterations', 'count', 0),
      droppedIterations: metricValue(
        data,
        'dropped_iterations',
        'count',
        0,
      ),
      unexpectedErrorRate: metricValue(
        data,
        'perf_unexpected_errors',
        'rate',
        null,
      ),
    },
  };
  return {
    [__ENV.PERF_RESULT_PATH]: JSON.stringify(output, null, 2),
    stdout: (
      testName
      + ': normalized endpoint metrics written to '
      + __ENV.PERF_RESULT_PATH
      + '\n'
    ),
  };
}

export function defaultRequestParams(config, endpointName, phase) {
  return {
    headers: {
      Accept: 'application/json',
      'X-Performance-Environment': config.environmentId,
    },
    timeout: config.requestTimeout,
    tags: {
      endpoint: endpointName,
      phase,
    },
  };
}

export function isJsonObject(response) {
  try {
    const body = response.json();
    return body !== null && typeof body === 'object';
  } catch (error) {
    return false;
  }
}

export { http };
