const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const rootDir = path.resolve(__dirname, '..');

function resolveApkPath() {
  const fromEnv = process.env.APK_PATH;
  if (fromEnv) {
    return path.isAbsolute(fromEnv) ? fromEnv : path.resolve(rootDir, fromEnv);
  }

  const distDefault = path.join(rootDir, 'dist', 'MacroTracker-release.apk');
  if (fs.existsSync(distDefault)) {
    return distDefault;
  }

  return path.join(rootDir, 'android', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
}

const firebaseAppId = process.env.FIREBASE_APP_ID;
const firebaseToken = process.env.FIREBASE_TOKEN;
const testers = process.env.FIREBASE_TESTERS;
const groups = process.env.FIREBASE_GROUPS || process.env.FIREBASE_TESTER_GROUPS;
const releaseNotes = process.env.FIREBASE_RELEASE_NOTES || `Local build ${new Date().toISOString()}`;

if (!firebaseAppId) {
  console.error('Missing FIREBASE_APP_ID environment variable.');
  process.exit(1);
}

if (!firebaseToken) {
  console.error('Missing FIREBASE_TOKEN environment variable.');
  process.exit(1);
}

const apkPath = resolveApkPath();
if (!fs.existsSync(apkPath)) {
  console.error(`APK file not found: ${apkPath}`);
  console.error('Build first with: npm run apk:build');
  process.exit(1);
}

const isWindows = process.platform === 'win32';
const npxCommand = isWindows ? 'npx.cmd' : 'npx';

const args = [
  'firebase-tools',
  'appdistribution:distribute',
  apkPath,
  '--app',
  firebaseAppId,
  '--token',
  firebaseToken,
  '--release-notes',
  releaseNotes
];

if (testers) {
  args.push('--testers', testers);
}

if (groups) {
  args.push('--groups', groups);
}

console.log(`Uploading APK to Firebase App Distribution: ${apkPath}`);
const result = spawnSync(npxCommand, args, { stdio: 'inherit', shell: isWindows });

process.exit(result.status || 0);