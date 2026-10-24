const path = require('node:path');

module.exports = {
  packagerConfig: {
    asar: true,
    executableName: 'Gjejma',
    appBundleId: 'al.gjejma.desktop',
    extraResource: [
      path.resolve(__dirname, '../server'),
      path.resolve(__dirname, '../web'),
    ],
    win32metadata: {
      CompanyName: 'Kontribuesit e Gjejma-s',
      FileDescription: 'Krahasim i çmimeve në dyqanet shqiptare',
      ProductName: 'Gjejma',
    },
  },
  makers: [
    {
      name: '@electron-forge/maker-squirrel',
      config: {
        name: 'GjejmaDesktop',
        authors: 'Kontribuesit e Gjejma-s',
        description: 'Aplikacion desktop për krahasimin e çmimeve në Shqipëri.',
        setupExe: 'Gjejma-Setup.exe',
      },
    },
  ],
};

