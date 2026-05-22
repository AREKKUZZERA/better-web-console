export type Panel = 'console' | 'dash' | 'players' | 'aliases' | 'sessions';

export interface StatusResponse {
  authenticated: boolean;
  username?: string;
}

export interface SessionInfo {
  username: string;
  remoteIp: string;
  createdAt: number;
  lastSeenAt: number;
  expiresAt: number;
  expiresInSeconds: number;
}

export interface PlayerInfo {
  name: string;
  uuid: string;
  world: string;
  ping: number;
  op: boolean;
}

export interface StatsPayload {
  type?: 'stats';
  tps?: number;
  ramUsed?: number;
  ramMax?: number;
  players?: number;
  maxPlayers?: number;
  worlds?: Array<{ name: string; chunks: number; entities: number; environment: string }>;
  playerList?: PlayerInfo[];
  playerActivityDays?: Array<{ date: string; label: string; items: Array<Record<string, unknown>> }>;
  playerActivitySummary?: {
    joins?: number;
    leaves?: number;
    commands?: number;
    topPlayers?: Array<{ player: string; score: number; commands: number }>;
  };
}
