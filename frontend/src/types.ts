export type Panel = 'console' | 'dash' | 'players' | 'aliases' | 'sessions' | 'audit' | 'config';

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

export interface AuditEntry {
  timestamp?: string;
  action?: string;
  username?: string;
  ip?: string;
  detail?: string;
  raw?: string;
}

export interface CompatibilityInfo {
  pluginVersion: string;
  pluginApiVersion: string;
  serverName: string;
  serverVersion: string;
  bukkitVersion: string;
  javaVersion: string;
  javaVendor: string;
  detectedServerLine: string;
  jarLine: string;
  compatible: boolean;
}

export interface ErrorGroup {
  signature: string;
  count: number;
  firstLine: number;
  lastLine: number;
  sample: string;
}

export interface AliasInfo {
  name: string;
  command: string;
}

export interface PlayerInfo {
  name: string;
  uuid: string;
  world: string;
  ping: number;
  op: boolean;
}

export interface PlayerProfile extends PlayerInfo {
  online: boolean;
  gamemode: string;
  health: number;
  maxHealth: number;
  food: number;
  level: number;
  x: number;
  y: number;
  z: number;
  history: Array<{ type: string; timestamp: number; player: string; uuid: string; command?: string }>;
}

export interface EditableConfig {
  logging: {
    logCommands: boolean;
    logAuth: boolean;
    auditLog: boolean;
  };
  systemStats: {
    enabled: boolean;
    updateIntervalSeconds: number;
    showDisk: boolean;
  };
  blockedCommands: string[];
  aliases: Array<{ name: string; command: string }>;
}

export interface PlayerActivitySummary {
  joins?: number;
  leaves?: number;
  commands?: number;
  topPlayers?: Array<{ player: string; score: number; commands: number }>;
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
  playerActivitySummary?: PlayerActivitySummary;
}
