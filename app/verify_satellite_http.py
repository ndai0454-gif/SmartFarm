"""Exercise the same HTTP/PNG contract used by Android. No credentials logged."""
import argparse
import os
from pathlib import Path
import secrets
import subprocess
import time
import requests
from urllib.parse import urljoin


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=['live', 'mock'], default='mock')
    args = parser.parse_args()
    backend = Path(__file__).resolve().parent.parent / 'vwing-satellite-backend'
    secret = secrets.token_urlsafe(32)
    base = 'http://127.0.0.1:18765'
    env = dict(os.environ, VWING_SAT_TOKEN=secret, VWING_EE_MODE=args.mode,
               EE_PROJECT='gen-lang-client-0598596007', VWING_PUBLIC_URL=base)
    child = subprocess.Popen([str(backend / '.venv/Scripts/python.exe'), '-m', 'uvicorn',
                              'app.main:app', '--host', '127.0.0.1', '--port', '18765'],
                             cwd=backend, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(60):
            if child.poll() is not None:
                raise RuntimeError('Backend exited before becoming ready')
            try:
                if requests.get(base + '/v1/health', timeout=1).json()['mode'] == args.mode:
                    break
            except (requests.RequestException, ValueError, KeyError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError('Backend did not become ready')
        headers = {'Authorization': 'Bearer ' + secret}
        region = {'ring': [[107.8,11.55],[107.801,11.55],[107.801,11.551],[107.8,11.551],[107.8,11.55]]}
        assert requests.post(base + '/v1/satellite/index', json={}, timeout=10).status_code == 401
        for index in ['NDVI', 'EVI', 'NDMI', 'water']:
            body = {'region': region}
            if index == 'water':
                endpoint = '/v1/satellite/water-change'
                body.update(baseline_start='2026-08-01',baseline_end='2026-08-20',event_start='2026-09-01',event_end='2026-09-10')
            else:
                endpoint = '/v1/satellite/index'
                body.update(index=index,start='2026-08-01',end='2026-09-01',max_cloud_pct=80)
            response = requests.post(base + endpoint, headers=headers, json=body, timeout=240)
            response.raise_for_status()
            result = response.json()
            assert result['simulated'] == (args.mode == 'mock')
            assert result['bounds']['west'] == 107.8
            image_url = urljoin(base, result['image_url'])
            assert image_url.startswith(base + '/') or image_url.startswith('https://earthengine.googleapis.com/')
            image = requests.get(image_url, headers=headers if image_url.startswith(base + '/') else {}, timeout=90)
            image.raise_for_status()
            assert image.content.startswith(b'\x89PNG\r\n\x1a\n')
            print(f'PASS {index}: authenticated HTTP, bounds, simulated flag, PNG ({len(image.content)} bytes)', flush=True)
    finally:
        child.terminate()
        try:
            child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()


if __name__ == '__main__':
    main()
