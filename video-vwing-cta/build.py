#!/usr/bin/env python3
# VWing 42s vertical promo (Problem -> Solution -> USP -> CTA). PIL + ffmpeg.
import os, sys, math, random, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1080, 1920, 30
HOME = os.path.expanduser('~')
ROOT = f'{HOME}/mnt/SmartFarm/vwing-remotion-motion/public'
FONT_DIR = f'{HOME}/work_fonts'
OUT = f'{HOME}/work_video/scenes'
os.makedirs(OUT, exist_ok=True)

# palette
WHITE=(246,250,247); MUTED=(168,184,176); LIME=(63,192,122); LIME_B=(130,235,160)
DARK=(12,34,25); AMBER=(245,165,36); RED=(240,84,84); YEL=(255,214,10)

# ---------- helpers ----------
_fc={}
def F(weight,size):
    k=(weight,size)
    if k not in _fc: _fc[k]=ImageFont.truetype(f'{FONT_DIR}/BeVietnamPro-{weight}.ttf',size)
    return _fc[k]
def fit(weight,s,size,maxw=980):
    while size>24:
        f=F(weight,size)
        if f.getlength(s)<=maxw: return f
        size-=2
    return F(weight,24)
def clamp(x,a=0.0,b=1.0): return max(a,min(b,x))
def eo3(x): x=clamp(x); return 1-(1-x)**3
def eob(x,s=1.70158): x=clamp(x); return 1+(s+1)*(x-1)**3+s*(x-1)**2
def prog(t,t0,d): return clamp((t-t0)/d)

def rr_mask(w,h,r,ss=3):
    m=Image.new('L',(w*ss,h*ss),0)
    ImageDraw.Draw(m).rounded_rectangle((0,0,w*ss-1,h*ss-1),r*ss,fill=255)
    return m.resize((w,h),Image.LANCZOS)

_bg={}
def grad(top,bot):
    k=(top,bot)
    if k not in _bg:
        a=np.linspace(0,1,H)[:,None,None]
        arr=(np.array(top,float)[None,None,:]*(1-a)+np.array(bot,float)[None,None,:]*a)*np.ones((1,W,1))
        _bg[k]=Image.fromarray(arr.astype(np.uint8),'RGB')
    return _bg[k].copy()

def radial(size,color,power=2.2):
    yy,xx=np.mgrid[0:size,0:size]
    r=np.hypot(xx-size/2,yy-size/2)/(size/2)
    a=np.clip(1-r,0,1)**power
    arr=np.zeros((size,size,4),np.uint8); arr[...,:3]=color; arr[...,3]=(a*255).astype(np.uint8)
    return Image.fromarray(arr,'RGBA')

_tl={}
def text_layer(s,font,fill):
    k=(s,id(font),fill)
    if k in _tl: return _tl[k]
    d0=ImageDraw.Draw(Image.new('L',(1,1)))
    l,t,r,b=d0.textbbox((0,0),s,font=font,anchor='ls')
    pad=24; w=r-l+2*pad; h=b-t+2*pad
    lay=Image.new('RGBA',(w,h),(0,0,0,0)); d=ImageDraw.Draw(lay)
    d.text((pad-l,pad-t+7),s,font=font,fill=(0,0,0,120),anchor='ls')
    d.text((pad-l,pad-t),s,font=font,fill=fill,anchor='ls')
    _tl[k]=lay; return lay

def put(img,lay,cx,cy,scale=1.0,alpha=1.0,rot=0):
    if alpha<=0.01: return
    if scale!=1.0:
        lay=lay.resize((max(1,int(lay.width*scale)),max(1,int(lay.height*scale))),Image.BILINEAR)
    if rot: lay=lay.rotate(rot,resample=Image.BILINEAR,expand=True)
    if alpha<1.0:
        a=lay.getchannel('A').point(lambda v:int(v*alpha)); lay=lay.copy(); lay.putalpha(a)
    img.paste(lay,(int(cx-lay.width/2),int(cy-lay.height/2)),lay)

def slam(img,s,weight,size,fill,cx,cy,t,t0,maxw=980):
    if t<t0: return
    p=prog(t,t0,0.30)
    f=fit(weight,s,size,maxw)
    put(img,text_layer(s,f,fill),cx,cy,scale=1+0.45*(1-eo3(p)),alpha=clamp(p*4))
def fade_text(img,s,weight,size,fill,cx,cy,t,t0,maxw=980):
    if t<t0: return
    p=prog(t,t0,0.35); f=fit(weight,s,size,maxw)
    put(img,text_layer(s,f,fill),cx,cy+24*(1-eo3(p)),alpha=p)

def ripple(img,cx,cy,t,t0,maxr=80,color=LIME,dur=0.55):
    p=(t-t0)/dur
    if p<0 or p>1: return
    r=int(14+maxr*eo3(p)); a=int(255*(1-p)); sz=2*maxr+50; c=sz//2
    lay=Image.new('RGBA',(sz,sz),(0,0,0,0)); d=ImageDraw.Draw(lay)
    d.ellipse((c-r,c-r,c+r,c+r),outline=color+(a,),width=7)
    d.ellipse((c-15,c-15,c+15,c+15),fill=(255,255,255,int(a*0.85)))
    img.paste(lay,(int(cx-c),int(cy-c)),lay)

def glow_rect(img,box,r,color,alpha,width=5,fill_alpha=0.0):
    x0,y0,x1,y1=[int(v) for v in box]; pad=width+2
    w,h=x1-x0+2*pad,y1-y0+2*pad
    lay=Image.new('RGBA',(w,h),(0,0,0,0)); d=ImageDraw.Draw(lay)
    d.rounded_rectangle((pad,pad,w-pad-1,h-pad-1),r,fill=(color+(int(fill_alpha*255),)) if fill_alpha else None,outline=color+(int(alpha*255),),width=width)
    img.paste(lay,(x0-pad,y0-pad),lay)

_pl={}
def pill_layer(s,font,fg,bg,icon=False,padx=46,pady=24):
    k=(s,id(font),fg,bg,icon)
    if k in _pl: return _pl[k]
    d0=ImageDraw.Draw(Image.new('L',(1,1)))
    l,t,r,b=d0.textbbox((0,0),s,font=font,anchor='ls')
    tw,th=r-l,b-t; ic=(th+8) if icon else 0; gap=22 if icon else 0
    w=tw+2*padx+ic+gap; h=th+2*pady; pad=40
    lay=Image.new('RGBA',(w+2*pad,h+2*pad),(0,0,0,0))
    sh=Image.new('RGBA',(w,h),(0,0,0,150)); lay.paste(sh,(pad,pad+12),rr_mask(w,h,h//2))
    lay=lay.filter(ImageFilter.GaussianBlur(14))
    lay.paste(Image.new('RGBA',(w,h),bg+(255,)),(pad,pad),rr_mask(w,h,h//2))
    d=ImageDraw.Draw(lay)
    x=pad+padx
    if icon:
        cx,cy=x+ic//2,pad+h//2; rr=ic//2
        d.ellipse((cx-rr,cy-rr,cx+rr,cy+rr),fill=fg)
        d.line((cx-rr*0.45,cy+rr*0.02,cx-rr*0.1,cy+rr*0.38,cx+rr*0.5,cy-rr*0.35),fill=bg,width=max(4,rr//4),joint='curve')
        x+=ic+gap
    d.text((x-l,pad+h//2-(t+b)/2+0),s,font=font,fill=fg,anchor='ls')
    _pl[k]=lay; return lay
def pill(img,s,cx,cy,t,t0,weight='ExtraBold',size=80,fg=DARK,bg=LIME,icon=False,maxw=900):
    if t<t0: return
    p=prog(t,t0,0.38)
    put(img,pill_layer(s,fit(weight,s,size,maxw),fg,bg,icon),cx,cy,scale=0.55+0.45*eob(p),alpha=clamp(p*4))

def flash(img,a):
    if a<=0: return img
    return Image.blend(img,Image.new('RGB',img.size,(255,255,255)),a)

def make_logo(size):
    S4=4; s=size*S4
    im=Image.new('RGBA',(s,s),(0,0,0,0)); d=ImageDraw.Draw(im)
    d.rounded_rectangle((0,0,s-1,s-1),int(s*0.24),fill=(15,41,30,255),outline=(63,192,122,255),width=int(s*0.035))
    A=(0.27*s,0.73*s); B=(0.73*s,0.27*s)
    def bez(P0,P1,P2,n=40):
        return [((1-u)**2*P0[0]+2*(1-u)*u*P1[0]+u*u*P2[0],(1-u)**2*P0[1]+2*(1-u)*u*P1[1]+u*u*P2[1]) for u in [k/n for k in range(n+1)]]
    up=bez(A,(0.28*s,0.26*s),B); lo=bez(A,(0.74*s,0.72*s),B)
    d.polygon(up+lo[::-1],fill=(63,192,122,255))
    d.line((0.30*s,0.70*s,0.62*s,0.38*s),fill=(15,41,30,255),width=int(s*0.03))
    d.line((0.22*s,0.78*s,0.30*s,0.70*s),fill=(63,192,122,255),width=int(s*0.04))
    return im.resize((size,size),Image.LANCZOS)
_lg={}
def logo(size):
    if size not in _lg: _lg[size]=make_logo(size)
    return _lg[size]

def brand_tag(img):
    lg=logo(58); img.paste(lg,(52,96),lg)
    ImageDraw.Draw(img).text((124,125),'VWing',font=F('Bold',40),fill=WHITE,anchor='lm')

# ---------- phone mockup ----------
SW=740; S=SW/1080; BEZ=16
PX=(W-(SW+2*BEZ))//2
PHONE_Y=430
ORIG={}; SCR={}
def load_assets():
    PH=f'{ROOT}/exports/phone'; SC=f'{ROOT}/screens'
    src={'map':f'{PH}/05_map_gee_boundary.png','qr':f'{PH}/06_qr_scanner_3modes.png','log':f'{PH}/22_log_garden_activity.png',
         'home':f'{PH}/03_overview_pro_aurora.png','tree':f'{SC}/08_tree_profile_from_qr.png'}
    for k,v in src.items():
        im=Image.open(v).convert('RGB'); assert im.size==(1080,2400),(k,im.size)
        ORIG[k]=im; SCR[k]=im.resize((SW,int(im.height*S)),Image.LANCZOS)
load_assets()
SHH=SCR['map'].height; PW=SW+2*BEZ; PHH=SHH+2*BEZ
_m_out=rr_mask(PW,PHH,70); _m_in=rr_mask(PW-4,PHH-4,68); _m_scr=rr_mask(SW,SHH,56)
_body=Image.new('RGBA',(PW,PHH),(0,0,0,0))
_body.paste(Image.new('RGBA',(PW,PHH),(78,92,86,255)),(0,0),_m_out)
_body.paste(Image.new('RGBA',(PW-4,PHH-4),(16,20,18,255)),(2,2),_m_in)
_sh=Image.new('RGBA',(PW+160,PHH+160),(0,0,0,0)); _sh.paste(Image.new('RGBA',(PW,PHH),(0,0,0,190)),(80,95),_m_out)
_sh=_sh.filter(ImageFilter.GaussianBlur(34))
def place_phone(img,scr,py):
    img.paste(_sh,(PX-80,py-80),_sh)
    b=_body.copy(); b.paste(scr,(BEZ,BEZ),_m_scr)
    img.paste(b,(PX,py),b)
def P(x,y,py): return (PX+BEZ+x*S, py+BEZ+y*S)

_pbg=None
def phone_bg():
    global _pbg
    if _pbg is None:
        im=grad((9,36,26),(4,15,12)); g=radial(1500,(40,170,100),2.0); g=g.resize((1500,1500))
        im.paste(g,(W//2-750,1150-750),g.point(lambda v:int(v*0.35)) if False else g.getchannel('A').point(lambda v:int(v*0.42)))
        _pbg=im
    return _pbg.copy()

# ---------- scenes ----------
RND=np.random.RandomState(7)
RAIN=[(RND.uniform(-300,W),RND.uniform(1700,2900),RND.uniform(60,150),RND.uniform(0,H+300),int(RND.randint(70,160)),int(RND.choice((2,3,4)))) for _ in range(120)]

def scene_rain(t,i,n):
    img=grad((22,31,46),(8,12,20)); d=ImageDraw.Draw(img,'RGBA')
    for x0,sp,L,ph,al,wd in RAIN:
        y=((t*sp+ph)%(H+300))-150; x=x0+y*0.16
        d.line((x,y,x+L*0.16,y+L),fill=(175,205,240,al),width=wd)
    bob=14*math.sin(t*3.0); cx,cy=540,560+bob
    lay=Image.new('RGBA',(700,420),(0,0,0,0)); dl=ImageDraw.Draw(lay)
    for (ex,ey,er) in [(210,230,120),(350,170,150),(480,240,115),(340,260,130)]:
        dl.ellipse((ex-er,ey-er*0.78,ex+er,ey+er*0.78),fill=(196,208,224,255))
    dl.rounded_rectangle((100,240,590,340),50,fill=(196,208,224,255))
    put(img,lay,cx,cy)
    if (9<=i<=12) or (38<=i<=42):
        d.polygon([(590,700),(520,840),(566,840),(500,1000),(650,800),(596,800),(660,700)],fill=(255,232,120,255))
        img=flash(img,0.28)
    slam(img,'Phun thuốc xong…','ExtraBold',96,WHITE,540,1180,t,0.15)
    slam(img,'mưa rửa trôi?','ExtraBold',124,AMBER,540,1345,t,0.75)
    fade_text(img,'Mất công, mất cả tiền thuốc','Medium',52,MUTED,540,1500,t,1.5)
    return img

def tree(d,cx,cy,r,col,hi):
    d.rectangle((cx-9,cy+r*0.55,cx+9,cy+r*1.35),fill=(96,68,44))
    d.ellipse((cx-r,cy-r,cx+r,cy+r),fill=col)
    d.ellipse((cx-r*0.62,cy-r*0.78,cx+r*0.05,cy-r*0.18),fill=hi)
WEAK={(1,1),(4,2),(2,4),(0,3)}
def scene_trees(t,i,n):
    img=grad((14,34,28),(7,16,14)); d=ImageDraw.Draw(img,'RGBA')
    slam(img,'Cây nào đang yếu?','ExtraBold',100,WHITE,540,380,t,0.05)
    for r_ in range(5):
        for c_ in range(6):
            tt=0.18+(r_+c_)*0.03
            if t<tt: continue
            sc=eob(prog(t,tt,0.3))
            x=140+c_*160; y=650+r_*150
            weak=(c_,r_) in WEAK
            rad=int(58*sc*(1+(0.07*math.sin(t*9+c_) if weak and t>1.0 else 0)))
            if rad<4: continue
            if weak and t>0.9: col,hi=(224,150,40),(247,196,84)
            else: col,hi=(44,148,92),(88,190,122)
            tree(d,x,y,rad,col,hi)
            if weak and t>1.0:
                q=text_layer('?',F('ExtraBold',72),AMBER); put(img,q,x,y-100-10*abs(math.sin(t*6+r_)),alpha=clamp((t-1.0)*5))
    mx=540+330*math.sin(t*2.3+0.4); my=900+270*math.sin(t*1.7)
    d.ellipse((mx-105,my-105,mx+105,my+105),outline=(255,255,255,235),width=11)
    d.line((mx+74,my+74,mx+150,my+150),fill=(255,255,255,235),width=18)
    fade_text(img,'Đi cả rẫy mới biết','ExtraBold',88,AMBER,540,1500,t,0.8)
    return img

def make_notebook():
    w,h=720,930
    lay=Image.new('RGBA',(w,h),(0,0,0,0))
    lay.paste(Image.new('RGBA',(w,h),(243,236,219,255)),(0,0),rr_mask(w,h,40))
    d=ImageDraw.Draw(lay)
    for y in range(160,h-30,78): d.line((50,y,w-40,y),fill=(160,178,200,255),width=3)
    d.line((112,0,112,h),fill=(226,120,120,255),width=3)
    for y in range(70,h-30,110): d.ellipse((22,y,48,y+26),fill=(52,52,58,255))
    d.text((135,108),'Sổ vườn',font=F('Bold',58),fill=(30,40,70,255),anchor='ls')
    rows=[('A-01','54'),('A-02','48'),('A-03','51'),('B-01','47'),('B-02','??'),('Tổng','1?3')]
    f=F('Medium',50)
    for k,(a,b) in enumerate(rows):
        y=160+k*78-16
        d.text((135,y),a,font=f,fill=(36,44,70,255),anchor='ls'); d.text((w-80,y),b,font=f,fill=(36,44,70,255),anchor='rs')
    return lay
NB=make_notebook()
_stain=Image.new('RGBA',(720,930),(0,0,0,0))
_sd=ImageDraw.Draw(_stain)
for (x,y,r) in [(250,300,120),(480,520,150),(220,700,100),(520,820,90)]:
    _sd.ellipse((x-r,y-r,x+r,y+r),fill=(70,120,190,80))
_stain=_stain.filter(ImageFilter.GaussianBlur(26))
_nbmask=rr_mask(720,930,40)
def scene_notebook(t,i,n):
    img=grad((38,29,23),(14,11,10))
    slam(img,'Sổ giấy ghi tay','ExtraBold',92,WHITE,540,340,t,0.05)
    lay=NB.copy()
    if t>1.1:
        st=_stain.copy(); st.putalpha(ImageChops_mul(st.getchannel('A'),_nbmask,clamp((t-1.1)/0.6)))
        lay.alpha_composite(st)
    d=ImageDraw.Draw(lay)
    for k in range(5):
        p=prog(t,0.7+k*0.17,0.16)
        if p>0:
            y=160+k*78-34
            d.line((125,y+8,125+(580-125)*p,y-4),fill=RED+(255,),width=11)
    bob=8*math.sin(t*2.4)
    put(img,lay,540,930+bob,rot=-5,alpha=clamp(t*8))
    slam(img,'Ướt – mất – cộng sai','ExtraBold',100,RED,540,1560,t,0.55)
    return img
def ImageChops_mul(a,b,k):
    arr=(np.asarray(a,np.float32)*np.asarray(b,np.float32)/255.0*k).clip(0,255).astype(np.uint8)
    return Image.fromarray(arr,'L')

def scene_predrop(t,i,n):
    img=grad((6,8,10),(10,22,16)); d=ImageDraw.Draw(img,'RGBA')
    for k in range(3):
        ph=((t*2.0)+k/3)%1.0; r=int(60+620*ph)
        d.ellipse((540-r,900-r,540+r,900+r),outline=LIME+(int(200*(1-ph)),),width=8)
    slam(img,'Cho đến khi…','ExtraBold',116,WHITE,540,900,t,0.1)
    if i>=n-8: img=flash(img,(i-(n-8)+1)/8*0.95)
    return img

def scene_logo(t,i,n):
    img=grad((10,44,30),(5,18,14))
    g=radial(1300,(60,200,120),2.0); img.paste(g,(540-650,780-650),g.getchannel('A').point(lambda v:int(v*0.55)))
    d=ImageDraw.Draw(img,'RGBA')
    for k in range(40):
        a=k*2.399; sp=260+(k%7)*70; p=eo3(t/1.1)
        x=540+math.cos(a)*sp*p; y=780+math.sin(a)*sp*p; r=int(5+(k%4)*3)
        d.ellipse((x-r,y-r,x+r,y+r),fill=(130,235,160,int(220*(1-clamp(t/1.2)))))
    s=eob(prog(t,0.0,0.55)); lg=logo(340); put(img,lg,540,780,scale=max(0.05,s))
    slam(img,'VWing','ExtraBold',176,WHITE,540,1090,t,0.35)
    fade_text(img,'Sổ vườn số cho nhà nông','Bold',64,LIME_B,540,1235,t,0.85)
    fade_text(img,'Hiểu từng cây. Chăm cả khu vườn.','Medium',46,MUTED,540,1330,t,1.35)
    return img

def header(img,t,l1,l2):
    brand_tag(img)
    slam(img,l1,'ExtraBold',86,WHITE,540,225,t,0.0)
    slam(img,l2,'Bold',54,LIME_B,540,330,t,0.12)

def scene_map(t,i,n):
    img=phone_bg(); py=int(PHONE_Y+300*(1-eo3(t/0.4)))
    header(img,t,'Chấm 4 góc vườn','Tự ra số sào, không cần cộng tay')
    place_phone(img,SCR['map'],py)
    cx,cy=P(296,1524,py); ripple(img,cx,cy,t,0.55,60)
    pts=[(230,930),(850,880),(900,1330),(260,1400)]; times=[0.95,1.2,1.45,1.7]
    sh=[P(x,y,py) for (x,y),tt in zip(pts,times) if t>=tt]
    d=ImageDraw.Draw(img,'RGBA')
    if len(sh)>=3: d.polygon(sh,fill=YEL+(70,))
    if len(sh)>=2: d.line(sh+([sh[0]] if len(sh)==4 else []),fill=YEL+(255,),width=7,joint='curve')
    for (x,y),tt in zip(pts,times):
        if t>=tt:
            px,py2=P(x,y,py); r=int(17*eob(prog(t,tt,0.25)))
            d.ellipse((px-r,py2-r,px+r,py2+r),fill=(255,255,255,255),outline=LIME+(255,),width=5)
            ripple(img,px,py2,t,tt,40,YEL,0.4)
    if t>=2.0:
        x0,y0=P(24,1850,py); x1,y1=P(1056,1940,py)
        glow_rect(img,(x0,y0,x1,y1),14,LIME_B,0.55+0.4*math.sin(t*9),5)
        pill(img,'≈ 29,1 sào',540,1480,t,2.05,size=100)
    return img

def scene_qr(t,i,n):
    img=phone_bg(); py=int(PHONE_Y+300*(1-eo3(t/0.4)))
    header(img,t,'Quét mã QR trên cây','Hồ sơ hiện ngay, không cần gõ')
    if t<1.85: scr=SCR['qr']
    elif t<2.15: scr=Image.blend(SCR['qr'],SCR['tree'],(t-1.85)/0.3)
    else: scr=SCR['tree']
    place_phone(img,scr,py)
    x0,y0=P(318,912,py); x1,y1=P(762,1356,py)
    if t<2.0:
        ph=(t*1.9)%2; yy=ph if ph<1 else 2-ph; ly=y0+(y1-y0)*(0.08+0.84*yy)
        d=ImageDraw.Draw(img,'RGBA')
        d.rectangle((x0+6,ly-34,x1-6,ly),fill=LIME+(55,)); d.line((x0+6,ly,x1-6,ly),fill=LIME_B+(255,),width=7)
        glow_rect(img,(x0,y0,x1,y1),10,LIME_B,0.5+0.4*math.sin(t*10),4)
    if 1.6<=t<2.0:
        a=clamp((t-1.6)/0.1)*(1-clamp((t-1.75)/0.25))
        glow_rect(img,(x0,y0,x1,y1),10,(255,255,255),a,6,fill_alpha=0.45*a)
        ripple(img,(x0+x1)/2,(y0+y1)/2,t,1.7,150,LIME_B,0.45)
    pill(img,'Hồ sơ Cây A-03',540,1500,t,2.3,size=84,icon=True)
    return img

CHIPS={'tuoi':(512,1321,(372,1260,652,1382)),'bon':(812,1321,(676,1260,948,1382))}
def scene_log(t,i,n):
    img=phone_bg(); py=int(PHONE_Y+300*(1-eo3(t/0.4)))
    header(img,t,'Ghi sổ một chạm','Tưới, bón, phun – xong trong vài giây')
    place_phone(img,SCR['log'],py)
    for key,t0 in (('tuoi',0.65),('bon',1.25)):
        cx,cy,box=CHIPS[key]
        if t>=t0:
            a,b=P(box[0],box[1],py); c,dd=P(box[2],box[3],py)
            glow_rect(img,(a,b,c,dd),22,LIME_B,0.95,5,fill_alpha=0.38)
        px,pyy=P(cx,cy,py); ripple(img,px,pyy,t,t0,70)
    sx,sy=P(848,705,py); ripple(img,sx,sy,t,1.95,80)
    if t>=1.95:
        a,b=P(701,643,py); c,dd=P(996,768,py); glow_rect(img,(a,b,c,dd),22,LIME_B,0.9*(1-prog(t,2.1,0.3)),5)
    pill(img,'Đã lưu vào sổ vườn',540,py+330+int(30*(1-eo3(prog(t,2.3,0.3)))),t,2.3,size=62,icon=True)
    return img

def usp_bg(): 
    im=grad((8,30,22),(4,14,11)); g=radial(1400,(40,170,100),2.0)
    im.paste(g,(W//2-700,1000-700),g.getchannel('A').point(lambda v:int(v*0.30))); return im
def usp_head(img,t,k,l1,l2):
    brand_tag(img)
    fade_text(img,f'Vì sao chọn VWing  ·  {k}/3','Medium',38,MUTED,540,190,t,0.0)
    slam(img,l1,'ExtraBold',88,WHITE,540,315,t,0.05); slam(img,l2,'ExtraBold',88,LIME_B,540,425,t,0.17)

_cards={}
def card(key,box,width,r=34):
    k=(key,box,width)
    if k in _cards: return _cards[k]
    im=ORIG[key].crop(box); h=int(im.height*width/im.width); im=im.resize((width,h),Image.LANCZOS)
    pad=60; lay=Image.new('RGBA',(width+2*pad,h+2*pad),(0,0,0,0))
    lay.paste(Image.new('RGBA',(width,h),(0,0,0,170)),(pad,pad+16),rr_mask(width,h,r))
    lay=lay.filter(ImageFilter.GaussianBlur(24))
    lay.paste(im,(pad,pad),rr_mask(width,h,r))
    ImageDraw.Draw(lay).rounded_rectangle((pad,pad,pad+width-1,pad+h-1),r,outline=(120,230,160,210),width=3)
    _cards[k]=lay; return lay
def card_in(img,lay,cx,cy,t,t0):
    p=prog(t,t0,0.42)
    put(img,lay,cx,cy+(1-eo3(p))*150+6*math.sin(t*2.2),alpha=clamp(p*3))
def chevron(img,cx,cy,t,t0):
    if t<t0: return
    a=clamp((t-t0)*4); b=10*math.sin(t*7)
    d=ImageDraw.Draw(img,'RGBA')
    d.line((cx-46,cy-16+b,cx,cy+22+b,cx+46,cy-16+b),fill=LIME_B+(int(255*a),),width=18,joint='curve')

def scene_usp1(t,i,n):
    img=usp_bg(); usp_head(img,t,1,'Thời tiết dặn thẳng','việc cần làm')
    card_in(img,card('home',(60,680,1020,910),960),540,780,t,0.35)
    chevron(img,540,985,t,0.95)
    card_in(img,card('home',(60,910,1020,1200),960),540,1190,t,1.15)
    fade_text(img,'Báo mưa: khoan phun thuốc','Bold',50,WHITE,540,1470,t,1.8)
    fade_text(img,'và chỉ sẵn cây cần thăm','Bold',50,LIME_B,540,1545,t,2.1)
    return img
def scene_usp2(t,i,n):
    img=usp_bg(); usp_head(img,t,2,'Chữ to, nút lớn','có nghe đọc to')
    card_in(img,card('home',(60,570,640,700),840),540,790,t,0.35)
    card_in(img,card('log',(60,1230,1020,1560),960),540,1150,t,0.9)
    fade_text(img,'Thiết kế cho tay dính đất,','Bold',50,WHITE,540,1470,t,1.7)
    fade_text(img,'đứng ngoài nắng vẫn bấm trúng','Bold',50,LIME_B,540,1545,t,2.0)
    return img
def scene_usp3(t,i,n):
    img=usp_bg(); usp_head(img,t,3,'Mỗi cây một hồ sơ','quét QR là thấy')
    card_in(img,card('tree',(24,520,1056,945),960),540,800,t,0.35)
    card_in(img,card('tree',(60,1520,1020,1730),960),540,1185,t,0.9)
    fade_text(img,'Trái, tình trạng, lịch sử chăm sóc','Bold',50,WHITE,540,1440,t,1.7)
    fade_text(img,'– gọn trong lòng bàn tay','Bold',50,LIME_B,540,1515,t,2.0)
    return img

def scene_cta(t,i,n):
    img=grad((9,40,28),(4,16,12)); pulse=0.5+0.5*math.sin(t*4.0)
    g=radial(1500,(60,200,120),2.0); img.paste(g,(540-750,1000-750),g.getchannel('A').point(lambda v:int(v*(0.30+0.12*pulse))))
    lg=logo(230); put(img,lg,540,420,scale=eob(prog(t,0.0,0.5))*(1+0.02*math.sin(t*4)))
    slam(img,'Ghi sổ vườn số','ExtraBold',112,WHITE,540,700,t,0.35)
    slam(img,'ngay hôm nay','ExtraBold',112,LIME_B,540,830,t,0.5)
    sc=1+0.035*math.sin(t*5.0)
    if t>=0.9:
        p=prog(t,0.9,0.45); put(img,pill_layer('TẢI VWING',F('ExtraBold',96),DARK,LIME,False,padx=120,pady=50),540,1090,scale=(0.5+0.5*eob(p))*sc,alpha=clamp(p*4))
        for tt in (2.0,4.0,6.0): ripple(img,540,1090,t,tt,170,LIME_B,0.7)
    fade_text(img,'Link tải ở phần mô tả','Bold',54,WHITE,540,1290,t,1.5)
    fade_text(img,'VWing – Sổ vườn nông dân','Medium',44,MUTED,540,1440,t,2.0)
    if i>=n-10: img=flash(Image.blend(img,Image.new('RGB',img.size,(0,0,0)),(i-(n-10)+1)/10),0)
    return img

SCENES={'rain':(scene_rain,75),'trees':(scene_trees,75),'notebook':(scene_notebook,75),'predrop':(scene_predrop,45),
        'logo':(scene_logo,75),'map':(scene_map,105),'qr':(scene_qr,105),'log':(scene_log,105),
        'usp1':(scene_usp1,120),'usp2':(scene_usp2,120),'usp3':(scene_usp3,120),'cta':(scene_cta,240)}
ORDER=['rain','trees','notebook','predrop','logo','map','qr','log','usp1','usp2','usp3','cta']
FLASH={'predrop':[],'logo':[0.95,0.8,0.55,0.35,0.2,0.1]}

def post(name,img,i):
    fl=FLASH.get(name,[0.5,0.3,0.15,0.06])
    if i<len(fl): img=flash(img,fl[i])
    if i<6 and name!='predrop':
        s=1+0.05*(1-i/6); w2,h2=int(W*s),int(H*s)
        im2=img.resize((w2,h2),Image.BILINEAR); l=(w2-W)//2; tp=(h2-H)//2
        img=im2.crop((l,tp,l+W,tp+H))
    return img

def render_scene(name):
    fn,n=SCENES[name]
    tmp=f'{OUT}/{name}.tmp.mp4'; fin=f'{OUT}/{name}.mp4'
    cmd=['ffmpeg','-y','-v','error','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}','-r',str(FPS),'-i','-',
         '-c:v','libx264','-preset','veryfast','-crf','18','-pix_fmt','yuv420p','-r',str(FPS),'-an',tmp]
    p=subprocess.Popen(cmd,stdin=subprocess.PIPE)
    for i in range(n):
        img=post(name,fn(i/FPS,i,n),i); p.stdin.write(img.convert('RGB').tobytes())
    p.stdin.close(); rc=p.wait()
    if rc==0: os.replace(tmp,fin); print('done',name,n,'frames',flush=True)
    else: print('FAIL',name,rc,flush=True)

def preview(out):
    picks=[('rain',1.2),('trees',1.6),('notebook',2.0),('predrop',0.9),('logo',1.6),('map',2.6),
           ('qr',1.5),('qr',2.8),('log',3.0),('usp1',3.0),('usp2',3.0),('usp3',3.0),('cta',2.6)]
    cols=5; tw,th=324,576; rows=(len(picks)+cols-1)//cols
    sheet=Image.new('RGB',(cols*tw,rows*th),(30,30,30))
    for k,(nm,tt) in enumerate(picks):
        fn,n=SCENES[nm]; i=min(n-1,int(tt*FPS)); img=post(nm,fn(i/FPS,i,n),i)
        sheet.paste(img.resize((tw,th),Image.LANCZOS),((k%cols)*tw,(k//cols)*th))
    sheet.save(out,quality=88); print('preview',out)

if __name__=='__main__':
    cmd=sys.argv[1]
    if cmd=='preview': preview(sys.argv[2])
    elif cmd=='frame':
        nm,tt,out=sys.argv[2],float(sys.argv[3]),sys.argv[4]; fn,n=SCENES[nm]; i=min(n-1,int(tt*FPS))
        post(nm,fn(i/FPS,i,n),i).save(out)
    elif cmd=='render':
        from multiprocessing import Pool
        names=sys.argv[2:] or ORDER
        with Pool(2) as pl: pl.map(render_scene,names)
