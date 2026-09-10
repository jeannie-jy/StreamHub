import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    seckill: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8088';
const activityId = __ENV.ACTIVITY_ID;
const accessToken = __ENV.ACCESS_TOKEN;

if (!activityId) {
  throw new Error('ACTIVITY_ID is required');
}

export default function () {
  const orderNo = `k6-${__VU}-${__ITER}`;
  const response = http.post(
    `${baseUrl}/api/v1/activities/${activityId}/seckill`,
    JSON.stringify({ clientOrderNo: orderNo }),
    {
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    },
  );

  check(response, {
    'seckill response is accepted or rejected by business rule': (res) =>
      [200, 400, 409, 429].includes(res.status),
  });
  sleep(1);
}
