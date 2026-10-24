const { app, BrowserWindow, dialog, shell } = require('electron');
const path = require('node:path');

const squirrelStartup = process.argv.some(argument => argument.startsWith('--squirrel-'));

let server;
let mainWindow;

async function start() {
  process.env.GJEJMA_DESKTOP = 'true';
  process.env.PORT = '0';
  const projectRoot = path.resolve(__dirname, '..');
  const serverDirectory = app.isPackaged ? path.join(process.resourcesPath, 'server') : path.join(projectRoot, 'server');
  const { httpServer } = require(path.join(serverDirectory, 'server.js'));
  server = httpServer;
  await new Promise((resolve, reject) => {
    if (server.listening) return resolve();
    server.once('listening', resolve);
    server.once('error', reject);
  });

  const address = server.address();
  const localUrl = `http://127.0.0.1:${address.port}`;
  const localOrigin = new URL(localUrl).origin;
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 860,
    minHeight: 620,
    title: 'Gjejma — Krahaso çmimet në Shqipëri',
    backgroundColor: '#f6f8f6',
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) shell.openExternal(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (new URL(url).origin !== localOrigin) {
      event.preventDefault();
      if (url.startsWith('https://') || url.startsWith('http://')) shell.openExternal(url);
    }
  });
  await mainWindow.loadURL(localUrl);
  mainWindow.on('closed', () => { mainWindow = null; });
}

if (squirrelStartup) {
  app.quit();
} else {
  app.setAppUserModelId('al.gjejma.desktop');
  app.whenReady().then(start).catch(error => {
    dialog.showErrorBox('Gjejma nuk u hap', `Nuk u nis dot aplikacioni lokal.\n\n${error.message}`);
    app.quit();
  });

  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', () => {
    if (server?.listening) server.close();
  });
}

