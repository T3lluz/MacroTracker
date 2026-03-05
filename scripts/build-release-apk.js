const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const rootDir = path.resolve(__dirname, '..');
const androidDir = path.join(rootDir, 'android');
const releaseOutputDir = path.join(androidDir, 'app', 'build', 'outputs', 'apk', 'release');
const distDir = path.join(rootDir, 'dist');
const distApkPath = path.join(distDir, 'MacroTracker-release.apk');

function findApkFile(dir) {
  if (!fs.existsSync(dir)) return null;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const nested = findApkFile(fullPath);
      if (nested) return nested;
      continue;
    }
    if (entry.isFile() && entry.name.endsWith('.apk')) {
      return fullPath;
    }
  }
  return null;
}

const isWindows = process.platform === 'win32';
const gradleCommand = isWindows ? 'gradlew.bat' : './gradlew';
const gradleArgs = ['clean', 'assembleRelease'];

console.log('Building Android release APK...');
const buildResult = spawnSync(gradleCommand, gradleArgs, {
  cwd: androidDir,
  stdio: 'inherit',
  shell: isWindows
});

if (buildResult.status !== 0) {
  process.exit(buildResult.status || 1);
}

const apkPath = findApkFile(releaseOutputDir);
if (!apkPath) {
  console.error('Release APK not found under android/app/build/outputs/apk/release');
  process.exit(1);
}

fs.mkdirSync(distDir, { recursive: true });
fs.copyFileSync(apkPath, distApkPath);

console.log(`Saved APK to: ${distApkPath}`);