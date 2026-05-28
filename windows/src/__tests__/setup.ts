/**
 * Jest test setup — runs before all tests
 */

// Mock electron module
jest.mock('electron', () => ({
  app: {
    requestSingleInstanceLock: jest.fn(() => true),
    quit: jest.fn(),
    on: jest.fn(),
    whenReady: jest.fn(() => Promise.resolve()),
  },
  BrowserWindow: jest.fn(() => ({
    webContents: {
      openDevTools: jest.fn(),
      send: jest.fn(),
    },
    loadURL: jest.fn(),
    loadFile: jest.fn(),
    once: jest.fn(),
    on: jest.fn(),
    show: jest.fn(),
    isMinimized: jest.fn(() => false),
    restore: jest.fn(),
    focus: jest.fn(),
  })),
  ipcMain: {
    handle: jest.fn(),
    on: jest.fn(),
  },
  Tray: jest.fn(() => ({
    setContextMenu: jest.fn(),
  })),
  Menu: {
    buildFromTemplate: jest.fn(),
  },
  nativeImage: {
    createEmpty: jest.fn(),
    createFromPath: jest.fn(),
  },
  clipboard: {
    readText: jest.fn(),
    writeText: jest.fn(),
    readImage: jest.fn(() => ({
      toPNG: jest.fn(() => Buffer.from('fake-png')),
    })),
  },
}));

// Mock keytar
jest.mock('keytar', () => ({
  getPassword: jest.fn(),
  setPassword: jest.fn(),
  deletePassword: jest.fn(),
}));

// Mock electron-store
jest.mock('electron-store', () => {
  return jest.fn().mockImplementation(() => ({
    get: jest.fn(),
    set: jest.fn(),
    clear: jest.fn(),
  }));
});

// Mock bonjour-service
jest.mock('bonjour-service', () => ({
  Bonjour: jest.fn().mockImplementation(() => ({
    find: jest.fn(),
    publish: jest.fn(),
  })),
}));

// Suppress console output in tests
global.console = {
  ...console,
  log: jest.fn(),
  debug: jest.fn(),
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
};

process.env.NODE_ENV = 'test';
