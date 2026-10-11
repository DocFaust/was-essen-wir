import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const project = 'was-essen-wir-e2e';
const port = process.env.E2E_APP_PORT ?? '18080';
const env = {
  ...process.env,
  POSTGRES_DB: 'wasessenwir',
  POSTGRES_USER: 'wasessenwir',
  POSTGRES_PASSWORD: randomBytes(18).toString('hex'),
  APP_PORT: port,
  E2E_BASE_URL: `http://127.0.0.1:${port}`,
  AI_ENABLED: 'true',
  AI_PROVIDER: 'fixture',
};

const compose = ['compose', '--project-name', project, '-f', 'deploy/compose.yaml'];

function run(command, args, cwd = root) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, env, stdio: 'inherit' });
    child.on('error', reject);
    child.on('exit', (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`${command} ${args.join(' ')} exited with ${code}`));
    });
  });
}

function capture(command, args, cwd = root) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, env });
    let output = '';
    child.stdout?.on('data', (chunk) => {
      output += chunk;
    });
    child.stderr?.on('data', (chunk) => {
      output += chunk;
    });
    child.on('error', reject);
    child.on('exit', (code) => {
      if (code === 0) {
        resolve(output);
        return;
      }
      reject(new Error(`${command} ${args.join(' ')} exited with ${code}`));
    });
  });
}

let exitCode = 0;
try {
  await run('docker', [...compose, 'up', '--build', '--detach', '--wait', '--wait-timeout', '600']);
  const logs = await capture('docker', [...compose, 'logs', 'backend']);
  const matches = [...logs.matchAll(/SETUP_TOKEN\s+([A-Za-z0-9_-]+)/g)];
  if (matches.length === 0) {
    throw new Error('Das Setup-Token steht nicht in den Backend-Logs.');
  }
  env.E2E_SETUP_TOKEN = matches[matches.length - 1][1];
  const playwrightCli = path.join(root, 'frontend', 'node_modules', '@playwright', 'test', 'cli.js');
  await run(process.execPath, [playwrightCli, 'test'], path.join(root, 'frontend'));
} catch (error) {
  exitCode = 1;
  console.error(error instanceof Error ? error.message : error);
  await run('docker', [...compose, 'logs', '--tail', '200']).catch(() => undefined);
} finally {
  await run('docker', [...compose, 'down', '--volumes', '--remove-orphans']).catch((error) => {
    exitCode = 1;
    console.error(error instanceof Error ? error.message : error);
  });
}

process.exit(exitCode);
