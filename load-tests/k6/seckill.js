import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const unexpectedStatus = new Rate('seckill_unexpected_status');

export const options = {
  scenarios: {
    seckill: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    seckill_unexpected_status: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8088';
const activityId = __ENV.ACTIVITY_ID;
const accessTokens = (__ENV.ACCESS_TOKENS || __ENV.ACCESS_TOKEN || '')
  .split(',')
  .map((token) => token.trim())
  .filter(Boolean);
const userIds = (__ENV.USER_IDS || '')
  .split(',')
  .map((userId) => userId.trim())
  .filter(Boolean);

if (!activityId) {
  throw new Error('ACTIVITY_ID is required');
}

export default function () {
  const orderNo = `k6-${__VU}-${__ITER}`;
  const accessToken = accessTokens.length
    ? accessTokens[(__VU - 1) % accessTokens.length]
    : undefined;
  const userId = userIds.length
    ? userIds[(__VU - 1) % userIds.length]
    : undefined;
  const response = http.post(
    `${baseUrl}/api/v1/activities/${activityId}/seckill`,
    JSON.stringify({ clientOrderNo: orderNo }),
    {
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...(!accessToken && userId ? { 'X-User-Id': userId } : {}),
      },
    },
  );

  const accepted = [200, 400, 409, 429].includes(response.status);
  unexpectedStatus.add(!accepted);
  check(response, {
    'seckill response is accepted or rejected by business rule': (res) =>
      accepted,
  });
  sleep(1);
}
