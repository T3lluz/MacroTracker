const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const assetsDir = path.join(__dirname, 'assets');

// Ensure assets directory exists
if (!fs.existsSync(assetsDir)) {
  fs.mkdirSync(assetsDir, { recursive: true });
}

// Create a simple solid color image with text
async function createImage(filename, width, height, color) {
  const svg = `
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <rect width="${width}" height="${height}" fill="${color}"/>
      <text x="50%" y="50%" font-size="48" fill="white" text-anchor="middle" dominant-baseline="middle" font-family="Arial">MT</text>
    </svg>
  `;

  await sharp(Buffer.from(svg))
    .png()
    .toFile(path.join(assetsDir, filename));

  console.log(`✓ Created ${filename}`);
}

async function main() {
  try {
    // Create icon.png (1024x1024)
    await createImage('icon.png', 1024, 1024, '#3498db');

    // Create adaptive-icon.png (1024x1024)
    await createImage('adaptive-icon.png', 1024, 1024, '#3498db');

    // Create splash.png (1080x1920)
    const splashSvg = `
      <svg width="1080" height="1920" xmlns="http://www.w3.org/2000/svg">
        <rect width="1080" height="1920" fill="white"/>
        <text x="540" y="960" font-size="72" fill="#3498db" text-anchor="middle" dominant-baseline="middle" font-family="Arial" font-weight="bold">MacroTracker</text>
      </svg>
    `;
    await sharp(Buffer.from(splashSvg))
      .png()
      .toFile(path.join(assetsDir, 'splash.png'));
    console.log('✓ Created splash.png');

    // Create favicon.png (192x192)
    await createImage('favicon.png', 192, 192, '#3498db');

    console.log('\nAll asset files created successfully!');
  } catch (error) {
    console.error('Error creating assets:', error);
    process.exit(1);
  }
}

main();

