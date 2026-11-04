"""Live smoke against the device's saved polygon. Never prints the backend token."""
import argparse
import json
import subprocess
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urljoin, urlsplit

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--configure', action='store_true', help='Configure local backend only when no existing config')
p.add_argument('--start', default='2026-09-13')
p.add_argument('--end', default='2026-10-13')
a = p.parse_args()
adb = str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')
pkg = 'com.example.test_tablet'
out = Path(__file__).resolve().parents[1] / 'app/build/outputs/region-analysis'
out.mkdir(parents=True, exist_ok=True)
def device(*args, **kwargs):
    return subprocess.run([adb, '-s', a.serial, *args], check=True, capture_output=True, **kwargs).stdout

raw = device('shell', 'run-as', pkg, 'cat', 'files/vwing_data.json')
(out / 'device-data-before.json').write_bytes(raw)
garden = json.loads(raw)
region = next(f for f in garden['features'] if f['kind'] == 'BOUNDARY')
token = (Path('E:/New folder/SmartFarm') / '.satellite-token').read_text().strip()
base = 'http://127.0.0.1:8000'
with urlopen(base + '/v1/health', timeout=5) as response:
    assert json.load(response)['mode'] == 'live', 'Live verification cannot use mock data'
ring = [[point['lng'], point['lat']] for point in region['points']]
headers = {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'}
records = []
for index in ('NDVI', 'EVI', 'NDMI'):
    data = json.dumps({'region': {'ring': ring}, 'index': index, 'start': a.start, 'end': a.end, 'max_cloud_pct': 80}).encode()
    with urlopen(Request(base + '/v1/satellite/index', data=data, headers=headers), timeout=240) as response:
        result = json.load(response)
    assert result['simulated'] is False
    (out / (index + '.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    if result.get('image_url'):
        image = urljoin(base + '/', result['image_url'])
        parsed = urlsplit(image)
        same = (parsed.scheme, parsed.netloc) == ('http', '127.0.0.1:8000')
        assert same or (parsed.scheme == 'https' and parsed.hostname == 'earthengine.googleapis.com')
        with urlopen(Request(image, headers={'Authorization': 'Bearer ' + token} if same else {}), timeout=120) as response:
            png = response.read(12 * 1024 * 1024 + 1)
        assert png.startswith(b'\x89PNG') and len(png) <= 12 * 1024 * 1024
        (out / (index + '.png')).write_bytes(png)
    records.append({k: result.get(k) for k in ('index', 'mean', 'area_ha', 'scene_count', 'valid_coverage_pct', 'simulated')})

if a.configure:
    existing = subprocess.run([adb, '-s', a.serial, 'shell', 'run-as', pkg, 'cat', 'files/vwing_satellite.json'], capture_output=True)
    if existing.returncode == 0 and json.loads(existing.stdout).get('url'):
        print('Existing satellite connection preserved')
    else:
        device('reverse', 'tcp:8000', 'tcp:8000')
        config = json.dumps({'url': base, 'token': token}).encode()
        device('shell', 'run-as', pkg, 'sh', '-c', "'cat > files/vwing_satellite.json'", input=config)
        device('shell', 'run-as', pkg, 'chmod', '600', 'files/vwing_satellite.json')
        print('Local live backend connection configured')
print(json.dumps({'region': region['name'], 'region_source': region.get('source'), 'results': records}, ensure_ascii=False))
