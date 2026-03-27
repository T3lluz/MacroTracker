import urllib.request
import re
import os

lucide_url = 'https://unpkg.com/lucide-static@0.344.0/icons/'
icons = {
  'sun': 'ic_weather_sun',
  'cloud': 'ic_weather_cloud',
  'cloud-sun': 'ic_weather_cloud_sun',
  'cloud-fog': 'ic_weather_fog',
  'cloud-lightning': 'ic_weather_storm',
  'zap': 'ic_weather_lightning',
  'cloud-rain': 'ic_weather_rain',
  'cloud-snow': 'ic_weather_snow',
  'flame': 'ic_flame',
  'pizza': 'ic_carbs',
  'beef': 'ic_protein',
  'droplet': 'ic_fat',
  'utensils': 'ic_meal',
  'heart': 'ic_heart',
  'moon': 'ic_sleep',
  'activity': 'ic_energy',
  'bar-chart': 'ic_stats',
  'footprints': 'ic_steps',
  'trending-down': 'ic_chart_down'
}

def svg_to_vectordrawable(svg, name):
    d_paths = set(re.findall(r'<path[^>]*d=\"([^\"]+)\"[^>]*>', svg))
    lines = re.findall(r'<line[^>]*x1=\"([^\"]+)\" y1=\"([^\"]+)\" x2=\"([^\"]+)\" y2=\"([^\"]+)\"[^>]*>', svg)
    for x1, y1, x2, y2 in lines:
        d_paths.add(f'M{x1},{y1} L{x2},{y2}')
    rects = re.findall(r'<rect[^>]*x=\"([^\"]+)\" y=\"([^\"]+)\" width=\"([^\"]+)\" height=\"([^\"]+)\"[^>]*>', svg)
    for x, y, w, h in rects:
        d_paths.add(f'M{x},{y} h{w} v{h} h-{w} Z')
    circles = re.findall(r'<circle[^>]*cx=\"([^\"]+)\" cy=\"([^\"]+)\" r=\"([^\"]+)\"[^>]*>', svg)
    for cx, cy, r in circles:
        d_paths.add(f'M{float(cx)-float(r)},{cy} a{r},{r} 0 1,0 {float(r)*2},0 a{r},{r} 0 1,0 -{float(r)*2},0')
    polylines = re.findall(r'<polyline[^>]*points=\"([^\"]+)\"[^>]*>', svg)
    for pts in polylines:
        coords = re.split(r'[ ,]+', pts.strip())
        if len(coords) >= 2:
            path_str = f'M{coords[0]},{coords[1]}'
            for i in range(2, len(coords), 2):
                if i+1 < len(coords):
                    path_str += f' L{coords[i]},{coords[i+1]}'
            d_paths.add(path_str)
        
    path_data = []
    for p in d_paths:
        path_data.append(f'        <path android:pathData=\"{p}\"\n              android:strokeWidth=\"2\"\n              android:strokeColor=\"#FF000000\"\n              android:strokeLineCap=\"round\"\n              android:strokeLineJoin=\"round\" />')
    
    xml = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="28"
    android:viewportHeight="28">
    <group
        android:translateX="2"
        android:translateY="2">
{"\n".join(path_data)}
    </group>
</vector>'''
    return xml

for icon, name in icons.items():
    try:
        url = lucide_url + icon + '.svg'
        req = urllib.request.urlopen(url)
        svg = req.read().decode('utf-8')
        vd = svg_to_vectordrawable(svg, name)
        with open('app/src/main/res/drawable/' + name + '.xml', 'w') as f:
            f.write(vd)
        print(f'Generated {name}.xml')
    except Exception as e:
        print(f'Failed {name}: {e}')
