import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const outputDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../public/icons');

mkdirSync(outputDir, { recursive: true });
writeFileSync(path.join(outputDir, 'icon-192.png'), createIcon(192));
writeFileSync(path.join(outputDir, 'icon-512.png'), createIcon(512));

function createIcon(size) {
  const raw = Buffer.alloc((size * 4 + 1) * size);
  const center = size / 2;
  const plateOuter = size * 0.28;
  const plateInner = size * 0.2;
  const meal = size * 0.11;

  for (let y = 0; y < size; y += 1) {
    const row = y * (size * 4 + 1);
    raw[row] = 0;
    for (let x = 0; x < size; x += 1) {
      const dx = x + 0.5 - center;
      const dy = y + 0.5 - center;
      const distance = Math.hypot(dx, dy);
      let red = 0x2e;
      let green = 0x7d;
      let blue = 0x32;
      if (distance < meal) {
        red = 0xf6;
        green = 0xf7;
        blue = 0xf4;
      } else if (distance < plateInner) {
        red = 0x1b;
        green = 0x5e;
        blue = 0x20;
      } else if (distance < plateOuter) {
        red = 0xf6;
        green = 0xf7;
        blue = 0xf4;
      }
      const offset = row + 1 + x * 4;
      raw[offset] = red;
      raw[offset + 1] = green;
      raw[offset + 2] = blue;
      raw[offset + 3] = 255;
    }
  }

  return encodePng(size, size, raw);
}

function encodePng(width, height, raw) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    signature,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const typeBuffer = Buffer.from(type);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])));
  return Buffer.concat([length, typeBuffer, data, checksum]);
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const value of buffer) {
    crc ^= value;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (~crc) >>> 0;
}
