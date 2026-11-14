import subprocess, numpy as np, wave, os
SR=44100; DUR=42.0; N=int(DUR*SR)
A=os.path.expanduser('~/mnt/SmartFarm/vwing-remotion-motion/public/audio')
def load(name,af=None):
    cmd=['ffmpeg','-v','error','-i',f'{A}/{name}']+(['-af',af] if af else [])+['-f','f32le','-ac','2','-ar',str(SR),'-']
    return np.frombuffer(subprocess.run(cmd,capture_output=True).stdout,dtype=np.float32).reshape(-1,2).copy()
# music: slow 124 -> 120 BPM, drop (track 15.50s) lands at video 9.0s
a=120/124.0
bgm=load('bgm-tech-house.mp3',f'atempo={a:.6f}')
start=int((15.50/a-9.0)*SR)
seg=bgm[start:start+N].copy()
env=np.ones(N); fo=int(1.8*SR); env[-fo:]=np.linspace(1,0,fo)**1.5
fi=int(0.4*SR); env[:fi]=np.linspace(0,1,fi)
V=np.frombuffer(subprocess.run(['ffmpeg','-v','error','-i',os.path.expanduser('~/work_video/tts/voice.wav'),'-f','f32le','-ac','2','-ar',str(SR),'-'],capture_output=True).stdout,dtype=np.float32).reshape(-1,2)
V=np.pad(V,((0,max(0,N-len(V))),(0,0)))[:N]
venv=np.convolve(np.abs(V).mean(axis=1),np.ones(int(0.12*SR))/int(0.12*SR),'same')
gate=np.clip(venv/0.03,0,1); gate=np.convolve(gate,np.ones(int(0.25*SR))/int(0.25*SR),'same')
duck=1-0.6*gate
musicpart=seg*env[:,None]*duck[:,None]*0.62
buf=musicpart.copy()
def add(name,t,vol,maxdur=None,peak_at=None,fade=None):
    x=load(name)
    if peak_at is not None:
        sm=np.convolve(np.abs(x).mean(axis=1),np.ones(2205)/2205,'same'); pk=np.argmax(sm)/SR
        t=peak_at-pk; print(name,'peak at',round(pk,2),'s -> start',round(t,2))
    if maxdur:
        m=int(maxdur*SR); x=x[:m]; f=int((fade if fade is not None else min(0.6,maxdur/2))*SR); x[-f:]*=np.linspace(1,0,f)[:,None]
    i=int(max(0,t)*SR)
    if t<0: x=x[int(-t*SR):]
    j=min(N,i+len(x)); buf[i:j]+=x[:j-i]*vol
for t in (0.15,2.65,5.15): add('pop.mp3',t,0.7)
for t in (2.5,5.0,7.5): add('swoosh-quick.mp3',t,0.6)
add('riser-cine.mp3',7.5,0.5,maxdur=1.5,fade=0.06)
add('impact-cine.mp3',0,0.7,maxdur=2.4,peak_at=9.0)
add('sparkle.mp3',9.85,0.45,maxdur=2.5)
for t in (11.5,15.0,18.5,22.0,26.0,30.0): add('whoosh-fast.mp3',t,0.5)
for t in (12.05,12.45,12.7,12.95,13.2,13.55,17.3,19.15,19.75,20.45,22.35,23.15,26.35,26.9,30.35,30.9): add('pop.mp3',t,0.55)
add('click-camera.mp3',16.75,0.8)
add('sparkle.mp3',20.8,0.4,maxdur=2.0)
add('whoosh-big.mp3',34.0,0.6); add('transition-snap.mp3',34.0,0.5)
add('sparkle.mp3',34.9,0.4,maxdur=2.5)
for t in (36.0,38.0,40.0): add('pop.mp3',t,0.6)
sfxpart=buf-musicpart
buf=musicpart+sfxpart*(1-0.6*gate)[:,None]
buf+=V*1.3
sp=venv>0.03
print('speech-window rms  voice %.3f  music %.3f  (ratio %.1f dB)'%(np.sqrt((V[sp]*1.3)**2).mean() if False else float(np.sqrt(((V[sp]*1.3)**2).mean())),float(np.sqrt((musicpart[sp]**2).mean())),20*np.log10(float(np.sqrt(((V[sp]*1.3)**2).mean()))/float(np.sqrt((musicpart[sp]**2).mean())))))
pk=np.abs(buf).max(); print('peak before',round(float(pk),2))
buf=np.tanh(buf*0.9)/np.tanh(0.9) if pk>1.0 else buf
buf=buf*(0.95/max(1e-9,np.abs(buf).max()))
pcm=(buf.clip(-1,1)*32767).astype(np.int16)
with wave.open('mix_voice.wav','wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print('mix_voice.wav written',len(pcm)/SR,'s')
