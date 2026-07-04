export type Sleeper = (delayMs: number) => Promise<void>;

export const defaultSleeper: Sleeper = (delayMs) =>
  new Promise((resolve) => setTimeout(resolve, delayMs));
