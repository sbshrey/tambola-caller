from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from html import escape
import math, subprocess, sys
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent.parent/'full-game/.test-workspace/design-runtime'))
import imageio_ffmpeg
W,H=960,540
C={'bg':'#141421','panel':'#222235','raised':'#303047','muted':'#AAA9C0','cream':'#FFF6DD','coral':'#FF8968','mint':'#9CE5CE','gold':'#FFD57B','ink':'#292437'}
fonts={}
def font(size,bold=False):
 k=(size,bold)
 if k not in fonts: fonts[k]=ImageFont.truetype('C:/Windows/Fonts/'+('segoeuib.ttf' if bold else 'segoeui.ttf'),size)
 return fonts[k]
T1=[[3,0,22,0,45,0,61,0,82],[0,14,27,36,0,56,0,74,0],[8,19,0,0,49,0,68,0,89]]
T2=[[1,0,20,0,41,0,60,0,80],[0,11,0,34,0,54,0,73,86],[7,18,29,0,48,0,69,0,0]]
def scene(t,svg=False,power="AUTO-DAB"):
 im=Image.new('RGB',(W,H),C['bg']);d=ImageDraw.Draw(im); elements=[]
 def rect(x,y,w,h,fill,r=0,stroke=None,sw=1):
  d.rounded_rectangle((x,y,x+w,y+h),r,fill=fill,outline=stroke,width=sw)
  elements.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"'+(f' stroke="{stroke}" stroke-width="{sw}"' if stroke else '')+'/>')
 def txt(x,y,s,size=14,fill=None,bold=False):
  fill=fill or C['cream'];d.text((x,y),s,font=font(size,bold),fill=fill)
  elements.append(f'<text x="{x}" y="{y+size}" font-family="Segoe UI,Roboto,sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{fill}">{escape(s)}</text>')
 def circle(x,y,r,fill):
  d.ellipse((x-r,y-r,x+r,y+r),fill=fill);elements.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}"/>')
 def line(x1,y1,x2,y2,fill,width=2):
  d.line((x1,y1,x2,y2),fill=fill,width=width);elements.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{fill}" stroke-width="{width}"/>')
 ready=t>=3.0; active=t>=5.2; claimed=t>=13.4; confirmed=t>=15.2
 rect(16,14,928,48,C['panel'],16)
 txt(30,23,'TAMBOLA',19,bold=True);txt(170,28,'24 players',13,C['muted']);circle(265,38,2,C['muted']);txt(280,28,'5 prizes left' if confirmed else '6 prizes left',13,C['muted'])
 # Next / active power occupies the top right only.
 glow=ready and not active
 if glow:
  for inset in (6,4,2):
   rect(722-inset,16-inset,216+2*inset,44+2*inset,C['panel'],14, C['mint'],1+int((math.sin(t*8)+1)/2))
 rect(724,18,210,40,C['mint'] if ready else C['raised'],12)
 bolt=([(747,23),(737,39),(745,39),(741,51),(756,33),(748,33)] if power=='AUTO-DAB' else ([(737,25),(747,22),(757,25),(755,40),(747,49),(739,40)] if power=='SHIELD' else [(744,25),(750,25),(750,33),(758,33),(758,39),(750,39),(750,47),(744,47),(744,39),(736,39),(736,33),(744,33)]))
 d.polygon(bolt,fill=C['ink'] if ready else C['mint']);elements.append('<polygon points="'+ ' '.join(str(x)+','+str(y) for x,y in bolt)+'" fill="'+(C['ink'] if ready else C['mint'])+'"/>')
 txt(766,22,power+' · T1' if active else (power+' READY' if ready else 'NEXT · '+power),12,C['ink'] if ready else C['cream'],True)
 txt(766,39,(('Active · '+str(max(0,15-int(t-5.2)))+'s') if power=='AUTO-DAB' else 'Armed for Ticket 1') if active else ('Tap to use on Ticket 1' if ready else '4 / 5 correct marks'),10,C['ink'] if ready else C['muted'])
 # Board / call pane
 rect(16,76,278,424,C['panel'],18)
 txt(32,88,'LIVE CALL',11,C['muted'],True)
 txt(30,103,'27' if t<8 else '36',58,C['gold'],True)
 txt(131,111,'Next call',12,C['muted']);txt(131,130,f'{max(0,8-(t%8)):.1f}s',30,bold=True)
 rect(32,174,246,5,C['raised'],2);rect(32,174,max(1,246*(1-(t%8)/8)),5,C['coral'],2)
 txt(32,192,'RECENT',10,C['muted'],True)
 for i,n in enumerate(([14,22,45,61,27] if t<8 else [22,45,61,27,36])):
  rect(85+i*38,188,32,28,C['raised'],8);txt(91+i*38,192,str(n),14)
 txt(32,228,'NUMBER BOARD',10,C['muted'],True)
 called={3,14,22,27,45,61,72,82}
 if t>=8: called.add(36)
 for n in range(1,91):
  x=32+((n-1)%10)*24.7;y=252+((n-1)//10)*25.5
  if n in called: rect(x,y,22,23,C['gold'] if n==(27 if t<8 else 36) else C['raised'],5)
  txt(x+3,y+3,str(n),11,C['ink'] if n==(27 if t<8 else 36) else (C['cream'] if n in called else '#73738D'),n in called)
 # Stable tickets; only touched cell is animated.
 for idx,(cells,y) in enumerate([(T1,76),(T2,294)]):
  rect(308,y,636,206,C['cream'],16, C['mint'] if idx==0 and active else C['cream'],2)
  txt(323,y+10,f'TICKET {idx+1}',12,C['ink'],True)
  txt(405,y+10,(power.title()+' active' if power=='AUTO-DAB' else power.title()+' armed') if idx==0 and active else ('Last played' if idx==0 else 'Ready to mark'),11,'#62596A')
  rect(824,y+5,108,30,C['coral'],10);txt(845,y+11,'Claim prize',12,C['ink'],True)
  marks={3,14,22,45} if idx==0 else set()
  if idx==0 and ready: marks.add(27)
  if idx==0 and active and power=='AUTO-DAB': marks.update({61,82})
  if idx==0 and t>=8.3 and power=='AUTO-DAB': marks.add(36)
  for rr,row in enumerate(cells):
   for cc,n in enumerate(row):
    x=320+cc*68;ycell=y+42+rr*51
    fill='#EDE3CC' if not n else '#FAF0D7'
    rect(x,ycell,65,48,fill,6)
    if n in marks:
     circle(x+32,ycell+24,20,C['mint'])
     if n==27 and 3<=t<3.5:
      radius=20+10*(t-3)/.5;d.ellipse((x+32-radius,ycell+24-radius,x+32+radius,ycell+24+radius),outline=C['coral'],width=2)
    if n: txt(x+(24 if n<10 else 17),ycell+6,str(n),24,C['ink'],True)
 # Tiny caption is outside game frame and used only in preview.
 caption='Compact two-ticket round · Design preview'
 if 2<t<5: caption='Fifth correct mark: only the tapped cell responds; power glows once ready'
 elif 5<=t<7.5: caption='One tap activates the random power on the last-played ticket'
 elif t>=7.5: caption='Claim stays in place while the next call arrives'
 txt(20,517,caption,12,C['muted'])
 # Cursor / touch preview
 if 2.7<t<3.4: circle(488,193,7,C['coral'])
 if 4.9<t<5.5: circle(840,39,7,C['coral'])
 # Claim sheet in-ticket area, board remains visible.
 if 7.5<=t<13.4:
  rect(308,76,636,424,C['panel'],18)
  txt(328,94,'Claim · Ticket 1',23,bold=True);txt(835,100,'Close',13,C['muted'])
  labels=[('Early five','300'),('Four corners','300'),('Top line','300'),('Middle line','300'),('Bottom line','300'),('Full house','1,500')]
  for i,(label,amount) in enumerate(labels):
   x=328+(i%2)*302;y=143+(i//2)*102
   rect(x,y,286,86,C['raised'],12,C['mint'] if i==0 and t>12 else None)
   txt(x+16,y+13,label,17,bold=True);txt(x+16,y+45,amount+' coins',13,C['gold'])
   for rr in range(3):
    for cc in range(9):
     on = (i==0 and (rr,cc) in {(0,0),(0,4),(1,2),(2,5),(2,8)}) or (i==1 and rr in (0,2) and cc in (0,8)) or (i in (2,3,4) and rr==i-2) or i==5
     rect(x+202+cc*6,y+20+rr*7,4,5,C['mint'] if on else '#484860',1)
  txt(328,464,'Choose a prize to submit your claim',12,C['muted'])
 if claimed:
  rect(412,238,430,64,C['mint'],16);txt(435,249,('Early five · confirmed' if confirmed else 'Early five · claim sent'),19,C['ink'],True);txt(435,277,('Prize recorded by server' if confirmed else 'Waiting for server confirmation'),11,C['ink'])
 if svg:
  return '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540" viewBox="0 0 960 540"><rect width="960" height="540" fill="'+C['bg']+'"/>'+''.join(elements)+'</svg>'
 return im
def render_assets():
 for name,t in [('round',1),('power-ready',4),('power-active',6),('claim',11)]:
  scene(t).save(ROOT/(name+'.png'))
  (ROOT/(name+'.svg')).write_text(scene(t,True),encoding='utf-8')
 for power in ['SHIELD','BONUS']:
  for state,t in [('ready',4),('armed',6)]:
   name=power.lower()+'-'+state
   scene(t,power=power).save(ROOT/(name+'.png'))
   (ROOT/(name+'.svg')).write_text(scene(t,True,power),encoding='utf-8')
 ff=imageio_ffmpeg.get_ffmpeg_exe()
 p=subprocess.Popen([ff,'-y','-f','rawvideo','-vcodec','rawvideo','-s','960x540','-pix_fmt','rgb24','-r','24','-i','-','-an','-vcodec','libx264','-pix_fmt','yuv420p','-crf','19','-movflags','+faststart',str(ROOT/'round-preview.mp4')],stdin=subprocess.PIPE,stderr=subprocess.PIPE)
 for i in range(16*24): p.stdin.write(scene(i/24).tobytes())
 p.stdin.close();err=p.stderr.read();code=p.wait()
 if code: raise RuntimeError(err.decode())
 print('Rendered four editable SVGs, four PNGs, and 16-second MP4')

if __name__ == "__main__": render_assets()
