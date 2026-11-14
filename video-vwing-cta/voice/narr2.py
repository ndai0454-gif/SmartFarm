import sys, os, re, hashlib, difflib, wave, time, numpy as np, sherpa_onnx
sys.path.insert(0,'.')
D=os.path.expanduser('~/work_video/tts'); SR=22050; N_CAND=int(os.environ.get('NC','5')); VI_SPEED=0.9
os.makedirs(f'{D}/cache',exist_ok=True)
exec(open(f'{D}/narr.py').read().split("def trim")[0].split("LINES=",1)[0].replace("import sys, wave, numpy as np","import numpy as np"))  # imports only
LINES=eval("["+open(f'{D}/narr.py').read().split("LINES=[",1)[1].split("\n]\n",1)[0]+"\n]")
def norm(s): s=s.lower().replace('4','bốn'); s=re.sub(r'[^\w\s]',' ',s); return re.sub(r'\s+',' ',s).strip()
def trim(x,th=0.012):
    idx=np.where(np.abs(x)>th)[0]; return x[idx[0]:idx[-1]+1] if len(idx) else x
def level(x,target=0.11):
    act=x[np.abs(x)>0.02]; r=np.sqrt((act**2).mean()) if len(act) else 1; return x*(target/r)
t0=time.time()
rec=sherpa_onnx.OfflineRecognizer.from_whisper(encoder=f'{D}/sherpa-onnx-whisper-small/small-encoder.int8.onnx',decoder=f'{D}/sherpa-onnx-whisper-small/small-decoder.int8.onnx',tokens=f'{D}/sherpa-onnx-whisper-small/small-tokens.txt',language='vi',task='transcribe',num_threads=2)
def mk(md,onnx,ns,nw):
    v=sherpa_onnx.OfflineTtsVitsModelConfig(model=f'{D}/{md}/{onnx}',tokens=f'{D}/{md}/tokens.txt',data_dir=f'{D}/{md}/espeak-ng-data',noise_scale=ns,noise_scale_w=nw)
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=v,num_threads=2,provider='cpu')))
TV=mk('vits-piper-vi_VN-vais1000-medium','vi_VN-vais1000-medium.onnx',0.4,0.5)
TE=mk('vits-piper-en_US-hfc_female-medium','en_US-hfc_female-medium.onnx',0.667,0.8)   # vi voice is female (f0~256Hz) -> female English voice
def best_vi(text):
    f=f'{D}/cache/vi_{hashlib.md5(text.encode()).hexdigest()}.npy'
    if os.path.exists(f): return np.load(f)
    best=None
    for k in range(16 if len(text.split())<=3 else N_CAND):
        a=TV.generate(text,sid=0,speed=VI_SPEED); x=np.array(a.samples,np.float32)
        y=np.concatenate([np.zeros(4000,np.float32),x,np.zeros(4000,np.float32)])
        s=rec.create_stream(); s.accept_waveform(a.sample_rate,y); rec.decode_stream(s)
        h=norm(s.result.text); e=norm(text)
        sc=difflib.SequenceMatcher(None,e.replace(' ',''),h.replace(' ','')).ratio()
        if best is None or sc>best[0]: best=(sc,x,h)
    np.save(f,best[1]); print(f'  [{best[0]:.2f}] {text!r} <- {best[2]!r}',flush=True); return best[1]
rec_en=sherpa_onnx.OfflineRecognizer.from_whisper(encoder=f'{D}/sherpa-onnx-whisper-small/small-encoder.int8.onnx',decoder=f'{D}/sherpa-onnx-whisper-small/small-decoder.int8.onnx',tokens=f'{D}/sherpa-onnx-whisper-small/small-tokens.txt',language='en',task='transcribe',num_threads=2)
def en_seg(text):
    key=text.strip('. ').lower(); pat=r'^(v|vee|vi)[\s\-,.]*wing' if key.startswith('vee') else r'^(q[\s\.\-]*r|qr|cue[\s\-]*are|queue)'
    f=f'{D}/cache/en2_{hashlib.md5(key.encode()).hexdigest()}.npy'
    if os.path.exists(f): return np.load(f)
    fallback=None
    for k in range(24):
        a=TE.generate(key.title() if key.startswith('vee') else 'Q R',sid=0,speed=0.78); x=np.array(a.samples,np.float32)
        y=np.concatenate([np.zeros(3000,np.float32),x,np.zeros(3000,np.float32)])
        st=rec_en.create_stream(); st.accept_waveform(a.sample_rate,y); rec_en.decode_stream(st); h=st.result.text.strip()
        if fallback is None: fallback=x
        if re.match(pat,h.lower()):
            np.save(f,x); print(f'  [EN ok after {k+1}] {text!r} <- {h!r}',flush=True); return x
    np.save(f,fallback); print(f'  [EN no match] {text!r}',flush=True); return fallback
total=int(42*SR); out=np.zeros(total,np.float32); gap=np.zeros(int(0.05*SR),np.float32)
for st,segs,mx in LINES:
    if time.time()-t0>150: print('TIME BUDGET - rerun to continue'); sys.exit(3)
    parts=[]
    for lang,text in segs: parts+=[level(trim(best_vi(text) if lang=='vi' else en_seg(text)),0.11 if lang=='vi' else 0.14),gap]
    line=np.concatenate(parts[:-1]); end=st+len(line)/SR
    print(f'{st:5.2f}-{end:5.2f}s [{mx:4.1f}] {"OVER" if end>mx else "ok"}  {" ".join(t for _,t in segs)}',flush=True)
    i=int(st*SR); out[i:i+len(line)]+=line[:total-i]
out=out*(0.9/np.abs(out).max())
with wave.open('voice.wav','wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((out.clip(-1,1)*32767).astype(np.int16).tobytes())
print('voice.wav written (best-of-%d)'%N_CAND)
