import ws from 'k6/ws';
import { check } from 'k6';

export const options = {
  scenarios: {
    websocket: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    checks: ['rate>0.95'],
  },
};

const websocketUrl = __ENV.WS_URL || 'ws://localhost:8090';
const roomId = __ENV.ROOM_ID || '1';
const userId = __ENV.USER_ID || `${__VU}`;
const accessToken = __ENV.ACCESS_TOKEN;

export default function () {
  const url = `${websocketUrl}/ws/chat?roomId=${roomId}&userId=${userId}`;
  const params = {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  };
  let connected = false;
  let heartbeatAck = false;

  const response = ws.connect(url, params, (socket) => {
    socket.on('message', (message) => {
      if (message.includes('CONNECTED')) {
        connected = true;
        socket.send(JSON.stringify({ type: 'HEARTBEAT' }));
      }
      if (message.includes('HEARTBEAT_ACK')) {
        heartbeatAck = true;
        socket.close();
      }
    });
    socket.setTimeout(() => socket.close(), 10000);
  });

  check(response, {
    'websocket handshake succeeded': (res) => res && res.status === 101,
    'websocket connected event received': () => connected,
    'websocket heartbeat acknowledged': () => heartbeatAck,
  });
}
