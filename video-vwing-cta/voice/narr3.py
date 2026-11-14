import sys, os, re, hashlib, difflib, wave, time, numpy as np, sherpa_onnx
sys.path.insert(0,'.')
D=os.path.expanduser('~/work_video/tts'); SR=22050; N_CAND=int(os.environ.get('NC','5')); VI_SPEED=0.9
os.makedirs(f'{D}/cache',exist_ok=True)
exec(open(f'{D}/narr.py').read().split("def trim")[0].split("LINES=",1)[0].replace("import sys, wave, numpy as np","import numpy as np"))  # imports only
LINES=[
 (0.25,[('vi','Phun thuốc xong, mưa rửa trôi.')],2.5),
 (2.75,[('vi','Cây nào yếu? Đi cả rẫy mới biết.')],5.0),
 (5.25,[('vi','Sổ giấy ướt, mất, cộng sai.')],7.5),
 (7.65,[('vi','Cho đến khi…')],8.9),
 (9.35,[('vi','Vi Uing. Sổ vườn số cho nhà nông.')],11.55),
 (11.7,[('vi','Chấm bốn góc, tự ra số sào.')],15.0),
 (15.2,[('vi','Quét Kiu A trên cây, hồ sơ hiện ngay.')],18.5),
 (18.7,[('vi','Ghi sổ một chạm: tưới, bón, phun.')],22.0),
 (22.2,[('vi','Thời tiết dặn thẳng việc cần làm.')],26.0),
 (26.2,[('vi','Chữ to, nút lớn, có nghe đọc to.')],30.0),
 (30.2,[('vi','Mỗi cây một hồ sơ, quét Kiu A là thấy.')],34.0),
 (34.4,[('vi','Ghi sổ vườn số, ngay hôm nay.')],36.6),
 (36.7,[('vi','Tải Vi Uing, lin ở phần mô tả.')],41.0),
]
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
TE=None   # vi voice is female (f0~256Hz) -> female English voice
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
