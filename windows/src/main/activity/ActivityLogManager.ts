/**
 * In-memory recent activity log for the dashboard.
 */

export interface ActivityEntry {
  id: string;
  timestamp: number;
  action: string;
  detail?: string;
}

export class ActivityLogManager {
  private entries: ActivityEntry[] = [];
  private readonly maxEntries = 50;
  private listeners: Array<(entries: ActivityEntry[]) => void> = [];

  add(action: string, detail?: string): void {
    const entry: ActivityEntry = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      timestamp: Date.now(),
      action,
      detail,
    };
    this.entries.unshift(entry);
    if (this.entries.length > this.maxEntries) {
      this.entries.length = this.maxEntries;
    }
    this.notify();
  }

  getRecent(limit = 10): ActivityEntry[] {
    return this.entries.slice(0, limit);
  }

  onChange(listener: (entries: ActivityEntry[]) => void): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  private notify(): void {
    const snapshot = this.getRecent();
    for (const listener of this.listeners) {
      listener(snapshot);
    }
  }
}
