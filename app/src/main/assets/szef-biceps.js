/* Szef Biceps — wspólna maskotka dla Post Przerywany i Dziennik FBW.
   Silnik nie wie nic o aplikacji: postać, dymek, nastroje, limity i pamięć „już powiedziane”.
   Aplikacja podaje paczkę tekstów i ustawienia przez SzefBiceps.init({...}).

   init({
     lines:    { klucz: {mood:'happy', soft:[...], sharp:[...]} },   // {x} w tekście = ctx.x
     settings: () => ({mode:'off'|'rare'|'normal'|'often', tone:'soft'|'sharp', peek:true}),
     canShow:  () => true,          // np. false, gdy otwarty jest arkusz/dialog
     peekHere: () => true,          // czy na tym ekranie może wyglądać zza krawędzi
     haptic:   ms => {},
     tapKey:   'tap'                // klucz tekstów po stuknięciu w Szefa
   })
   say(key, {ctx, mood, once, every, auto, force}) -> true, jeśli powiedział
     once:  id — powie tylko raz dla tego id (np. id postu)
     every: ms — ten klucz nie częściej niż co tyle
     auto:  podlega globalnemu limitowi z ustawienia częstotliwości
   refresh() — po zmianie ustawień / ekranu
*/
(function(){
'use strict';
const LSK='szef-biceps-v1', COOL={rare:3*3600e3, normal:45*60e3, often:10*60e3}, KEEP=40*864e5;
let cfg=null, log={once:{},at:{},last:{},lastAuto:0}, root, bub, txt, hideT=0, showing=false;
try{const o=JSON.parse(localStorage.getItem(LSK)); if(o) log=Object.assign(log,o)}catch(e){}
const saveLog=()=>{try{localStorage.setItem(LSK,JSON.stringify(log))}catch(e){}};
const now=()=>Date.now();
const st=()=>Object.assign({mode:'normal',tone:'sharp',peek:true},cfg&&cfg.settings?cfg.settings():{});

const CSS=`
.sb{position:fixed;right:4px;bottom:calc(var(--sb-bottom,84px) + env(safe-area-inset-bottom,0px));z-index:30;width:96px;height:118px;pointer-events:none}
.sb[hidden]{display:none}
.sb .chef{position:absolute;right:0;bottom:0;width:96px;height:118px;pointer-events:auto;cursor:pointer;
  transform:translateX(130%);transition:transform .45s cubic-bezier(.3,1.5,.5,1);-webkit-tap-highlight-color:transparent}
.sb.peek .chef{transform:translateX(64%) rotate(-16deg)}
.sb.on .chef{transform:none}
.sb.on .chef svg{animation:sbHop .5s ease-out}
.sb .chef svg{width:100%;height:100%;overflow:visible;filter:drop-shadow(0 4px 8px rgba(0,0,0,.35))}
.sb .bub{position:absolute;right:86px;bottom:60px;width:max-content;max-width:min(250px,calc(100vw - 100px));pointer-events:auto;
  background:var(--surface2,#fff);color:var(--fg,#16171c);border:1px solid var(--line,#d9d9de);border-radius:16px 16px 4px 16px;
  padding:10px 30px 10px 13px;font-size:14px;line-height:1.4;box-shadow:0 8px 24px rgba(0,0,0,.28);
  opacity:0;transform:translateY(8px) scale(.9);transform-origin:100% 100%;transition:opacity .2s,transform .25s;visibility:hidden}
.sb.on .bub{opacity:1;transform:none;visibility:visible;transition-delay:.18s}
.sb .bub b{font-weight:700}
.sb .bub .nm{display:block;font-size:11px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:var(--acc,#e8663a);margin-bottom:2px}
.sb .bub .x{position:absolute;top:4px;right:4px;width:24px;height:24px;border:0;background:none;color:var(--muted,#888);font-size:18px;line-height:1;cursor:pointer}
.sb [data-m]{display:none}
.sb[data-mood="happy"] [data-m~="happy"],.sb[data-mood="flex"] [data-m~="flex"],.sb[data-mood="shock"] [data-m~="shock"],
.sb[data-mood="angry"] [data-m~="angry"],.sb[data-mood="sleepy"] [data-m~="sleepy"]{display:inline}
.sb .eyes{transform-box:view-box;transform-origin:190px 123px;animation:sbBlink 4.6s infinite}
.sb .arm{transform-box:view-box}
.sb .arm.l{transform-origin:135px 180px}.sb .arm.r{transform-origin:245px 180px}
.sb.on[data-mood="flex"] .arm.l{animation:sbFlexL .7s ease-in-out 3}
.sb.on[data-mood="flex"] .arm.r{animation:sbFlexR .7s ease-in-out 3}
.sb.on[data-mood="flex"] .bi{transform-box:fill-box;transform-origin:center;animation:sbPump .7s ease-in-out 3}
.sb.on[data-mood="shock"] .chef svg{animation:sbShake .4s ease-in-out 2}
.sb.on[data-mood="angry"] .hat{transform-box:view-box;transform-origin:190px 100px;animation:sbHat .5s ease-in-out 2}
@keyframes sbHop{0%{transform:translateY(0)}35%{transform:translateY(-10px)}70%{transform:translateY(2px)}100%{transform:none}}
@keyframes sbBlink{0%,93%,100%{transform:scaleY(1)}96%{transform:scaleY(.1)}}
@keyframes sbFlexL{50%{transform:rotate(10deg)}}
@keyframes sbFlexR{50%{transform:rotate(-10deg)}}
@keyframes sbPump{50%{transform:scale(1.28)}}
@keyframes sbShake{25%{transform:rotate(-6deg)}75%{transform:rotate(6deg)}}
@keyframes sbHat{50%{transform:translateY(-7px) rotate(-6deg)}}
@media (prefers-reduced-motion:reduce){.sb *{animation:none!important;transition:none!important}}
`;

const SKIN='#E9B48C', LINE='#3a2a20';
const SVG=`<svg viewBox="70 30 250 300" aria-hidden="true"><g stroke="${LINE}" stroke-width="2.4">
<g class="arm l"><path d="M135 180 L100 195 L95 135" fill="none" stroke="${SKIN}" stroke-width="26" stroke-linecap="round" stroke-linejoin="round"/>
 <ellipse class="bi" cx="112" cy="172" rx="17" ry="13" fill="${SKIN}" stroke="#9a6a48"/>
 <line x1="95" y1="118" x2="95" y2="78" stroke="#555" stroke-width="5"/><rect x="84" y="56" width="22" height="24" rx="3" fill="#9aa3ab"/>
 <circle cx="95" cy="125" r="12" fill="${SKIN}"/></g>
<g class="arm r"><path d="M245 180 L280 195 L285 135" fill="none" stroke="${SKIN}" stroke-width="26" stroke-linecap="round" stroke-linejoin="round"/>
 <ellipse class="bi" cx="268" cy="172" rx="17" ry="13" fill="${SKIN}" stroke="#9a6a48"/>
 <line x1="266" y1="125" x2="304" y2="125" stroke="#444" stroke-width="5"/>
 <rect x="260" y="108" width="9" height="34" rx="2" fill="#2f2f2f"/><rect x="301" y="108" width="9" height="34" rx="2" fill="#2f2f2f"/>
 <circle cx="285" cy="125" r="12" fill="${SKIN}"/></g>
<rect x="158" y="268" width="30" height="42" rx="4" fill="#33363b"/><rect x="192" y="268" width="30" height="42" rx="4" fill="#33363b"/>
<ellipse cx="170" cy="313" rx="16" ry="6" fill="#1f1f1f"/><ellipse cx="210" cy="313" rx="16" ry="6" fill="#1f1f1f"/>
<path d="M128 168 L252 168 L232 272 L148 272 Z" fill="#FAFAF7"/>
<path d="M128 168 L148 168 L140 192 L122 186 Z" fill="#FAFAF7"/><path d="M252 168 L232 168 L240 192 L258 186 Z" fill="#FAFAF7"/>
<rect x="160" y="200" width="60" height="74" rx="4" fill="#D9473B"/>
<line x1="176" y1="226" x2="204" y2="226" stroke="#FAFAF7" stroke-width="4"/>
<rect x="170" y="218" width="6" height="16" fill="#FAFAF7" stroke="none"/><rect x="204" y="218" width="6" height="16" fill="#FAFAF7" stroke="none"/>
<rect x="180" y="152" width="20" height="18" fill="${SKIN}"/>
<circle cx="190" cy="128" r="30" fill="${SKIN}"/>
<g stroke-width="4" stroke-linecap="round">
 <g data-m="happy flex"><line x1="172" y1="113" x2="185" y2="117"/><line x1="208" y1="113" x2="195" y2="117"/></g>
 <g data-m="shock"><path d="M171 110 Q178 104 185 109" fill="none"/><path d="M195 109 Q202 104 209 110" fill="none"/></g>
 <g data-m="angry"><line x1="171" y1="110" x2="186" y2="119"/><line x1="209" y1="110" x2="194" y2="119"/></g>
 <g data-m="sleepy"><line x1="172" y1="116" x2="185" y2="116"/><line x1="195" y1="116" x2="208" y2="116"/></g>
</g>
<g class="eyes" data-m="happy flex angry shock" stroke="none"><circle cx="179" cy="123" r="3.2" fill="#2b2b2b"/><circle cx="201" cy="123" r="3.2" fill="#2b2b2b"/></g>
<g data-m="sleepy" stroke-width="2.6" fill="none" stroke-linecap="round"><path d="M174 124 Q179 127 184 124"/><path d="M196 124 Q201 127 206 124"/></g>
<path d="M190 136 C180 132 170 136 166 144 C174 141 182 142 190 140 C198 142 206 141 214 144 C210 136 200 132 190 136 Z" fill="#5a3a22"/>
<path data-m="happy flex" d="M180 148 Q190 158 200 148" fill="none" stroke-width="2.6"/>
<ellipse data-m="shock" cx="190" cy="151" rx="5" ry="6" fill="#5a2a22"/>
<line data-m="angry" x1="182" y1="151" x2="198" y2="151" stroke-width="2.6" stroke-linecap="round"/>
<path data-m="sleepy" d="M185 150 Q190 153 195 150" fill="none" stroke-width="2.4"/>
<g class="hat"><rect x="166" y="76" width="48" height="24" rx="3" fill="#FFFFFF"/>
 <circle cx="170" cy="70" r="18" fill="#FFFFFF"/><circle cx="210" cy="70" r="18" fill="#FFFFFF"/><circle cx="190" cy="58" r="22" fill="#FFFFFF"/></g>
</g></svg>`;

function build(){
  const s=document.createElement('style'); s.textContent=CSS; document.head.appendChild(s);
  root=document.createElement('div'); root.className='sb'; root.dataset.mood='happy';
  root.innerHTML=`<div class="bub" role="status" aria-live="polite"><span class="nm">Szef Biceps</span><span class="tx"></span><button type="button" class="x" aria-label="Zamknij">×</button></div>
    <div class="chef" role="button" tabindex="0" aria-label="Szef Biceps">${SVG}</div>`;
  document.body.appendChild(root);
  bub=root.querySelector('.bub'); txt=root.querySelector('.tx');
  root.querySelector('.x').addEventListener('click',e=>{e.stopPropagation();hide()});
  bub.addEventListener('click',e=>{e.stopPropagation();hide()});
  root.querySelector('.chef').addEventListener('click',e=>{e.stopPropagation();
    if(!say(cfg.tapKey||'tap',{force:true})) hide();});
}

function fill(t,ctx){return String(t).replace(/\{(\w+)\}/g,(m,k)=>ctx&&ctx[k]!=null?esc(ctx[k]):'')}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function pick(key){
  const p=cfg.lines[key]; if(!p) return null;
  const tone=st().tone; let a=p[tone]||p.sharp||p.soft||p; if(!Array.isArray(a)) a=[a]; if(!a.length) return null;
  let i=Math.floor(Math.random()*a.length); const lk=key+'|'+tone;
  if(a.length>1&&i===log.last[lk]) i=(i+1+Math.floor(Math.random()*(a.length-1)))%a.length;
  log.last[lk]=i; return {t:a[i],mood:p.mood};
}

function say(key,o){
  o=o||{}; if(!cfg||!root) return false;
  const s=st(), t=now(); if(s.mode==='off') return false;
  const oid=o.once!=null?key+':'+o.once:null;
  if(!o.force){
    if(oid&&log.once[oid]) return false;
    if(o.every&&log.at[key]&&t-log.at[key]<o.every) return false;
    if(o.auto&&(showing||t-(log.lastAuto||0)<(COOL[s.mode]||COOL.normal))) return false;
    if(cfg.canShow&&!cfg.canShow()) return false;
  }
  const l=pick(key); if(!l) return false;
  show(fill(l.t,o.ctx),o.mood||l.mood||'happy');
  if(oid) log.once[oid]=t; log.at[key]=t; if(o.auto) log.lastAuto=t;
  for(const k in log.once) if(t-log.once[k]>KEEP) delete log.once[k];
  saveLog(); return true;
}

function show(html,mood){
  clearTimeout(hideT);
  const re=showing; root.classList.remove('on'); root.dataset.mood=mood; txt.innerHTML=html;
  if(re) void root.offsetWidth;
  root.classList.add('on'); showing=true;
  try{cfg.haptic&&cfg.haptic(20)}catch(e){}
  const len=txt.textContent.length; hideT=setTimeout(hide,Math.min(12000,4500+len*55));
}
function hide(){clearTimeout(hideT); showing=false; if(root){root.classList.remove('on'); refresh()}}
function refresh(){
  if(!root) return; const s=st();
  root.hidden=s.mode==='off';
  root.classList.toggle('peek',!showing&&s.peek&&(!cfg.peekHere||cfg.peekHere()));
}

window.SzefBiceps={
  init(c){cfg=c; if(!root){if(document.body) build(); else document.addEventListener('DOMContentLoaded',()=>{build();refresh()})} refresh()},
  say, hide, refresh,
  isShowing:()=>showing
};
})();
