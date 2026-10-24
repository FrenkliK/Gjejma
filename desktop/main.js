const { app, BrowserWindow, dialog, shell } = require('electron');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

function handleSquirrelEvent() {
  const supported = ['--squirrel-install', '--squirrel-updated', '--squirrel-uninstall', '--squirrel-obsolete'];
  const event = process.argv.find(argument => supported.includes(argument));
  if (!supported.includes(event)) return false;

  if (event === '--squirrel-install' || event === '--squirrel-updated' || event === '--squirrel-uninstall') {
    const appFolder = path.resolve(process.execPath, '..');
    const installFolder = path.resolve(appFolder, '..');
    const updateExe = path.join(installFolder, 'Update.exe');
    const command = event === '--squirrel-uninstall' ? '--removeShortcut' : '--createShortcut';
    const child = spawn(updateExe, [command, path.basename(process.execPath)], { detached: true, stdio: 'ignore' });
    child.on('error', error => writeDiagnostic(`Gabim gjatë krijimit/heqjes së shkurtorit: ${error.message}`));
    child.unref();
  }
  return true;
}

function writeDiagnostic(error) {
  try {
    const directory = path.join(app.getPath('userData'), 'logs');
    fs.mkdirSync(directory, { recursive: true });
    const file = path.join(directory, 'gjejma.log');
    fs.appendFileSync(file, `[${new Date().toISOString()}] ${error?.stack || error}\n`);
    return file;
  } catch {
    return 'Nuk u krijua dot skedari i regjistrit.';
  }
}

let server;
let mainWindow;
const smokeTest = process.argv.includes('--gjejma-smoke-test');

async function startBackend() {
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
  return localUrl;
}

async function runSmokeTest(localUrl) {
  const [home, health, categories, shops] = await Promise.all([
    fetch(localUrl),
    fetch(`${localUrl}/api/health`),
    fetch(`${localUrl}/api/categories`),
    fetch(`${localUrl}/api/shops`),
  ]);
  if (!home.ok || !(await home.text()).includes('Gjejma')) throw new Error('Faqja e paketuar nuk u ngarkua.');
  if (!health.ok || !categories.ok || !shops.ok) throw new Error('API-ja e paketuar nuk iu përgjigj kërkesave.');
  const categoryData = await categories.json();
  const shopData = await shops.json();
  if (!categoryData.categories?.phones || !categoryData.categories?.consoles) throw new Error('Konfigurimi i filtrave mungon në paketë.');
  if (!Array.isArray(shopData.stores) || shopData.stores.filter(store => store.enabled).length < 15) throw new Error('Lista e dyqaneve mungon ose është e paplotë.');
}

async function start() {
  const localUrl = await startBackend();
  if (smokeTest) {
    await runSmokeTest(localUrl);
    console.log('GJEJMA_SMOKE_OK');
    server.close();
    server = null;
    app.exit(0);
    return;
  }

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
    let destination;
    try { destination = new URL(url); } catch { event.preventDefault(); return; }
    if (destination.origin !== localOrigin) {
      event.preventDefault();
      if (url.startsWith('https://') || url.startsWith('http://')) shell.openExternal(url);
    }
  });
  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    writeDiagnostic(`Dritarja e aplikacionit u mbyll papritur: ${details.reason} (${details.exitCode})`);
  });
  await mainWindow.loadURL(localUrl);
  mainWindow.on('closed', () => { mainWindow = null; });
}

if (handleSquirrelEvent()) {
  app.quit();
} else {
  app.setAppUserModelId('al.gjejma.desktop');
  app.whenReady().then(start).catch(error => {
    const logFile = writeDiagnostic(error);
    if (smokeTest) {
      console.error(error);
      app.exit(1);
    } else {
      dialog.showErrorBox('Gjejma nuk u hap', `Nuk u nis dot aplikacioni lokal.\n\n${error.message}\n\nRegjistri i gabimit: ${logFile}`);
      app.quit();
    }
  });

  process.on('uncaughtException', error => {
    const logFile = writeDiagnostic(error);
    dialog.showErrorBox('Ndodhi një gabim', `${error.message}\n\nRegjistri i gabimit: ${logFile}`);
    app.quit();
  });
  process.on('unhandledRejection', error => writeDiagnostic(error));

  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', () => {
    if (server?.listening) server.close();
  });
}

