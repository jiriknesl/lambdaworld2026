/* Procedural chart geometry only. All algorithmic states come from Clojure. */
'use strict';
function oceanSurface(time){
  const phase=reducedMotion?0:time*.00012;
  const fog=ctx.createLinearGradient(0,0,0,H);fog.addColorStop(0,'#07131f');fog.addColorStop(.48,'#0d2d3c');fog.addColorStop(1,'#07151f');ctx.fillStyle=fog;ctx.fillRect(0,0,W,H);
  // Long wave contours give the camera a continuous sea to travel over.
  for(let row=-11;row<=12;row++){
    const points=[];for(let col=-32;col<=32;col++){
      const x=col*100,z=row*92+Math.sin(col*.3+row*.48+phase)*15;
      points.push([x,-20+Math.sin(col*.25+phase)*2,z]);
    }
    line(points,row%3===0?'#72b8ce17':'#72b8ce09',1);
  }
  const shores=[
    [[-2900,-1050],[-2300,-810],[-2040,-660],[-1700,-680],[-1420,-825],[-1000,-760],[-700,-930],[100,-1100]],
    [[620,-1150],[990,-780],[1270,-630],[1600,-710],[1840,-520],[2160,-620],[2440,-970],[2900,-1150]],
    [[-2800,950],[-2150,760],[-1830,805],[-1630,1070],[-2200,1390]],
    [[650,1150],[1240,895],[1680,970],[2050,870],[2740,1130]]
  ];
  for(const shore of shores){
    polygon(shore.map(([x,z])=>[x,-12,z]),'#193d42','#75a99355');
    for(let j=1;j<=3;j++)line(shore.map(([x,z])=>[x,-12,z+(z<0?j*16:-j*16)]),'#70adad16');
  }
  // Sparse glints, deterministic and decorative; they do not represent targets.
  for(let i=0;i<34;i++){
    const x=-2600+fract(Math.sin(i*43.1)*29873)*5200,z=-600+fract(Math.cos(i*37)*3971)*1200;
    const p=project(x,-14,z),a=.04+(Math.sin(phase*3+i)*.5+.5)*.1;
    ctx.strokeStyle=`rgba(162,214,225,${a})`;ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(p.x-4*p.s,p.y);ctx.lineTo(p.x+4*p.s,p.y);ctx.stroke();
  }
}
function rescueBeacon(x,z,time){
  const phase=reducedMotion?.35:(time*.00025)%1;
  ring(x,0,z,20+phase*85,alpha('#f2b376',(1-phase)*.45),1.5);
  ring(x,0,z,22,'#f2b376aa',2);
  const p=project(x,20,z),size=clamp(12*p.s,5,17);
  ctx.strokeStyle='#f5bc87';ctx.lineWidth=3;ctx.beginPath();ctx.arc(p.x,p.y,size*.7,0,Math.PI*2);ctx.stroke();
  ctx.fillStyle='#fbe4c6';ctx.beginPath();ctx.arc(p.x,p.y-size*.13,size*.22,0,Math.PI*2);ctx.fill();
  ctx.strokeStyle='#fbe4c6';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(p.x-size*.38,p.y+size*.27);ctx.lineTo(p.x+size*.38,p.y+size*.27);ctx.stroke();
}
function currentArrows(anchor,time){
  const phase=reducedMotion?0:(time*.000015)%1;
  for(let i=0;i<4;i++){
    const x=anchor-300+i*170+phase*35,z=205+i%2*35;
    line([[x-20,0,z],[x+20,0,z]],'#88bcce3b',1.2);
    line([[x+10,0,z-7],[x+20,0,z],[x+10,0,z+7]],'#88bcce3b',1.2);
  }
}
function mapCompass(){
  if(W<700)return;const x=W-70,y=H-115;if(entered&&codeVisible&&!stageMode)return;
  ctx.save();ctx.translate(x,y);ctx.strokeStyle='#9abcc866';ctx.lineWidth=1;ctx.beginPath();ctx.arc(0,0,23,0,Math.PI*2);ctx.moveTo(0,-32);ctx.lineTo(0,32);ctx.moveTo(-32,0);ctx.lineTo(32,0);ctx.stroke();ctx.fillStyle='#d2e2dc';ctx.font='10px "Rescue Mono"';ctx.textAlign='center';ctx.fillText('N',0,-39);ctx.restore();
}
function sectorOverlay(data){
  if(!data?.frames)return;const frame=data.frames[Math.floor(clamp(cursor,0,data.frames.length-1))],event=frame.inspection.event;
  if(!event)return;const ranges={west:[0,4],central:[4,5],east:[5,6]},[a,b]=ranges[event.action];
  polygon([[(a-3)*76-38,-2,-190],[(b-3)*76+38,-2,-190],[(b-3)*76+38,-2,190],[(a-3)*76-38,-2,190]],'#e3bb8510','#e3bb8588');
  boat((a+b-6)*38,20,220,Math.PI,'#e3bb85');
  label('OBSERVED SEARCH / '+event.action.toUpperCase(),(a+b-6)*38,0,263,'#e3bb85',13);
}
function coverageMap(data){
  if(!data?.coverage)return;const n=Math.floor(clamp(cursor,0,data.orders.length*4-1)),team=new Set(data.orders[Math.floor(n/4)].trace[n%4].coalition);
  // A second, small chart ties coalition membership to its frozen probability map.
  const ox=anchors[2]+260,size=28;
  for(const cell of data.coverage){
    const [gx,gy]=cell.state,x=ox+(gx-3)*size,z=-190+(gy-2)*size;
    const active=cell.sensors.filter(s=>team.has(s.boat)&&s.detection>0);
    polygon([[x-16,0,z-16],[x+16,0,z-16],[x+16,0,z+16],[x-16,0,z+16]],active.length?'#d7b57945':'#7cacc21a',active.length?'#e3bb8599':'#a7ccdb22');
    if(cell.belief>.035)dot(x,6,z,Math.max(2,Math.sqrt(cell.belief)*14),'#b2d1ef');
  }
  label('FROZEN BELIEF / COVERED CELLS',ox,0,-290,'#b2d1ef',11);
}
