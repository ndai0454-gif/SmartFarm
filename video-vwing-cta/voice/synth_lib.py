import os, numpy as np, sherpa_onnx
D=os.path.expanduser('~/work_video/tts')
def mk(model_dir,onnx,threads=2):
    v=sherpa_onnx.OfflineTtsVitsModelConfig(model=f'{D}/{model_dir}/{onnx}',tokens=f'{D}/{model_dir}/tokens.txt',data_dir=f'{D}/{model_dir}/espeak-ng-data')
    cfg=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=v,num_threads=threads,provider='cpu'))
    return sherpa_onnx.OfflineTts(cfg)
_vi=_en=None
def vi():
    global _vi
    if _vi is None: _vi=mk('vits-piper-vi_VN-vais1000-medium','vi_VN-vais1000-medium.onnx')
    return _vi
def en(name='lessac'):
    global _en
    if _en is None:
        _en=mk('vits-piper-en_US-lessac-medium','en_US-lessac-medium.onnx') if name=='lessac' else mk('vits-piper-en_US-ryan-high','en_US-ryan-high.onnx')
    return _en
def gen(tts,text,speed=1.0):
    a=tts.generate(text,sid=0,speed=speed)
    return np.array(a.samples,dtype=np.float32),a.sample_rate
