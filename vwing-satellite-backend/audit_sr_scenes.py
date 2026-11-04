"""Read-only live scene audit; writes no credentials and does not run inference."""
from pathlib import Path
from datetime import date
import json
import argparse
from app import ee_service, sr_engine, geo
import ee

folder = Path(__file__).parent / 'artifacts/verification/audit-20261020'
parser = argparse.ArgumentParser()
parser.add_argument('--start', default='2026-09-20', type=date.fromisoformat)
parser.add_argument('--end', default='2026-10-20', type=date.fromisoformat)
args = parser.parse_args()
data = json.loads((folder / 'vwing_data.json').read_text(encoding='utf-8'))
f = next(f for f in data['features'] if f['kind'] == 'BOUNDARY')
ring = geo.validate_ring([[p['lng'], p['lat']] for p in f['points']])
ee_service.init('gen-lang-client-0598596007')
geom = ee_service._geom(ring)
col = ee_service._collection(geom, args.start, args.end,80).sort('CLOUDY_PIXEL_PERCENTAGE')
def score(scene):
    valid = ee_service._scl_mask(scene).select('B4').mask().unmask(0).rename('valid')
    cov = valid.reduceRegion(ee.Reducer.mean(),geom,10,maxPixels=1_000_000).get('valid')
    return scene.set('audit',ee.Dictionary({'scene':scene.get('system:index'),'tile_cloud_pct':scene.get('CLOUDY_PIXEL_PERCENTAGE'),'clear_fraction':cov}))
scores = col.map(score).aggregate_array('audit').getInfo()
out={'start':args.start.isoformat(),'end_exclusive':args.end.isoformat(),'area_ha':geo.area_ha(ring),'candidate_count':len(scores),'scenes_sorted_by_tile_cloud':scores}
(folder/f'scene-audit-{args.start}-{args.end}.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False),flush=True)
