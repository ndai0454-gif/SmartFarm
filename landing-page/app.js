const indexData = {
  NDVI:{label:'NDVI TRUNG BÌNH',value:'0,702',copy:'Tín hiệu độ xanh của tán.<br>Không phải kết luận sức khỏe từng cây.',low:'−0,2',high:'0,9',title:'Độ xanh của tán',colors:'#a1784b,#e6c846,#8cc850,#1e8232',source:'SENTINEL-2 · 08/2026'},
  EVI:{label:'EVI TRUNG BÌNH',value:'0,657',copy:'Bổ sung quan sát vùng tán dày.<br>Cần đối chiếu ảnh và thực địa.',low:'−0,2',high:'1,0',title:'Chỉ số thực vật tăng cường',colors:'#a1784b,#e6c846,#8cc850,#1e8232',source:'SENTINEL-2 · 08/2026'},
  NDMI:{label:'NDMI TRUNG BÌNH',value:'0,247',copy:'Tín hiệu liên quan nước trong tán.<br>Không đo trực tiếp độ ẩm đất.',low:'−0,4',high:'0,6',title:'Trạng thái nước trong tán',colors:'#b45f3c,#e6c846,#78c8a0,#1d6fa5',source:'SENTINEL-2 · 08/2026'},
  water:{label:'DIỆN TÍCH NƯỚC MỚI',value:'0,00 ha',copy:'Vùng mẫu không ghi nhận nước mới.<br>Không phải kết luận về ngập toàn vùng.',low:'Nước thường xuyên',high:'Nước đã rút',title:'Nước mới',colors:'#8a9a8f,#1e78b8,#e0a030',source:'SENTINEL-1 · 09/2026'}
};
function selectIndex(button){
  const key=button.dataset.index,d=indexData[key];
  document.querySelectorAll('[data-index]').forEach(b=>{const selected=b===button;b.setAttribute('aria-selected',String(selected));b.tabIndex=selected?0:-1});
  document.querySelector('#index-panel').setAttribute('aria-labelledby',button.id);
  const img=document.querySelector('#sat-image');img.src=`assets/${key}.png`;img.alt=`Ảnh phân tích ${key==='water'?'nước mặt Sentinel-1':key} của vùng mẫu 1,21 ha`;
  document.querySelector('#metric-label').textContent=d.label;document.querySelector('#metric-value').textContent=d.value;document.querySelector('#metric-copy').innerHTML=d.copy;
  document.querySelector('#scale-low').textContent=d.low;document.querySelector('#scale-high').textContent=d.high;document.querySelector('#scale-title').textContent=d.title;
  document.querySelector('#color-scale').style.background=`linear-gradient(90deg,${d.colors})`;
  document.querySelector('.image-badge').textContent=d.source;
  document.querySelector('#data-note').textContent=key==='water'?'Vùng mẫu Bảo Lộc · Kỳ nền 01–20/08/2026; sự kiện 01–10/09/2026 (ngày cuối không gồm). Ảnh trong suốt khi không có lớp nước. Cần kiểm tra thực địa.':'Vùng mẫu Bảo Lộc · 01/08–01/09/2026 (không gồm ngày cuối). Không phải dữ liệu vườn của bạn.';
}
const tabs=[...document.querySelectorAll('[data-index]')];
tabs.forEach((button,i)=>{button.addEventListener('click',()=>selectIndex(button));button.addEventListener('keydown',e=>{let next;if(e.key==='ArrowRight')next=(i+1)%tabs.length;else if(e.key==='ArrowLeft')next=(i+tabs.length-1)%tabs.length;else if(e.key==='Home')next=0;else if(e.key==='End')next=tabs.length-1;else return;e.preventDefault();selectIndex(tabs[next]);tabs[next].focus()})});
const menu=document.querySelector('.menu'),navigation=document.querySelector('.nav nav');
menu.addEventListener('click',()=>{const open=menu.getAttribute('aria-expanded')!=='true';menu.setAttribute('aria-expanded',String(open));menu.setAttribute('aria-label',open?'Đóng điều hướng':'Mở điều hướng');navigation.classList.toggle('mobile-open',open)});
navigation.querySelectorAll('a').forEach(a=>a.addEventListener('click',()=>{navigation.classList.remove('mobile-open');menu.setAttribute('aria-expanded','false');menu.setAttribute('aria-label','Mở điều hướng')}));
const dialog=document.querySelector('#demo-dialog');
document.querySelectorAll('[data-demo]').forEach(button=>button.addEventListener('click',()=>dialog.showModal()));
document.querySelector('.close-dialog').addEventListener('click',()=>dialog.close());
dialog.addEventListener('click',e=>{if(e.target===dialog){const box=dialog.getBoundingClientRect();if(e.clientX<box.left||e.clientX>box.right||e.clientY<box.top||e.clientY>box.bottom)dialog.close()}});
const grid=document.querySelector('.demo-grid');
for(let i=1;i<=12;i++){
  const code=`A-${String(i).padStart(2,'0')}`,button=document.createElement('button');button.innerHTML=`<svg aria-hidden="true"><use href="#leaf"/></svg><span>${code}</span>`;button.setAttribute('aria-label',`Xem hồ sơ cây ${code}`);button.setAttribute('aria-pressed',String(i===4));
  button.addEventListener('click',()=>{grid.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));document.querySelector('#demo-tree').textContent=`Sầu riêng ${code}`;document.querySelector('#demo-description').textContent='Khu A · Hồ sơ gắn với ID cố định của cây.';document.querySelector('#demo-event-title').textContent=i%2?'Kiểm tra lá':'Tưới nước';document.querySelector('#demo-event-text').textContent=`${i%2?'11':'12'}/10/2026 · Ghi nhận thủ công từ nhật ký mẫu.`});grid.appendChild(button);
}
