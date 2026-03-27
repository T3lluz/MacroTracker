import glob
import os

colors = {
    'sun': '#FFF9A826',
    'cloud': '#FF90A4AE',
    'cloud_sun': '#FF4FC3F7',
    'fog': '#FFB0BEC5',
    'lightning': '#FFF9A826',
    'rain': '#FF29B6F6',
    'snow': '#FF81D4FA',
    'storm': '#FF5C6BC0'
}

files = glob.glob('app/src/main/res/drawable/ic_weather_*.xml')
for f in files:
    name = os.path.basename(f).replace('ic_weather_', '').replace('.xml', '')
    color = colors.get(name)
    if color:
        with open(f, 'r') as file:
            content = file.read()
        content = content.replace('#FF000000', color)
        with open(f, 'w') as file:
            file.write(content)
print('Done!')
