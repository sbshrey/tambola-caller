"""Original vector review screens. No downloaded artwork, fonts or player data."""
from pathlib import Path
from html import escape
import json
import re
import math
from itertools import product, combinations

ROOT = Path(__file__).resolve().parent
(ROOT / 'frames').mkdir(exist_ok=True)
W, H = 1280, 720
NIGHT = dict(bg='#19172F', panel='#272341', soft='#383253', ink='#FFF9EF', muted='#B8AECF', accent='#FF8665', gold='#FFDA7B', mint='#9FE2CB')
DAY = dict(bg='#F7EEDB', panel='#FFF9EF', soft='#E6DCC6', ink='#253E36', muted='#65736B', accent='#F88064', gold='#E5B743', mint='#A7D9BC')

def text(x,y,s,size=22,fill=None,weight=600,anchor='start',extra=''):
    return f'<text x="{x}" y="{y}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{weight}" fill="{fill or T["ink"]}" text-anchor="{anchor}" {extra}>{escape(str(s))}</text>'
def rect(x,y,w,h,fill,rx=20,stroke='none',sw=1,extra=''):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>'
def circle(x,y,r,fill,extra=''):
    return f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}" {extra}/>'
def timer_arc(x,y,r,fraction,color,name,width):
    angle=min(fraction,.9999)*2*math.pi
    return f'<path id="{name}" d="M {x} {y-r} A {r} {r} 0 {int(fraction>.5)} 1 {x+r*math.sin(angle):.3f} {y-r*math.cos(angle):.3f}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round"/>'
def group(name,body,hot=None):
    return f'<g id="{name}"'+(f' data-action="{hot}" role="button" tabindex="0" aria-label="{name.replace("-"," ")}"' if hot else '')+'>'+body+'</g>'
def pill(x,y,w,label,color=None):
    return rect(x,y,w,36,T['soft'],18)+text(x+w/2,y+24,label,15,color or T['muted'],700,'middle')
def button(x,y,w,label,action='lobby',color=None,sub=None):
    c=color or T['accent']
    return group(label.replace(' ','-'),rect(x,y+7,w,64,'#B64A42',21)+rect(x,y,w,64,c,21)+text(x+w/2,y+41,label,24,'#24192E',900,'middle'),action)+(text(x+w/2,y+95,sub,16,T['muted'],500,'middle') if sub else '')
def ball(x,y,r,n,color='coral',rot=0):
    return f'<g transform="rotate({rot} {x} {y})" class="float-ball">'+f'<ellipse cx="{x+5}" cy="{y+r+18}" rx="{r*.74}" ry="{r*.19}" fill="#060515" opacity=".18"/>'+circle(x,y,r,f'url(#{color})')+circle(x-r*.11,y-r*.08,r*.63,'#FFF5E6')+text(x-r*.11,y+r*.18,n,int(r*.74),'#34243D',900,'middle')+f'<ellipse cx="{x-r*.3}" cy="{y-r*.67}" rx="{r*.34}" ry="{r*.13}" fill="#FFFFFF" opacity=".45" transform="rotate(-28 {x-r*.3} {y-r*.67})"/></g>'
def coin(x,y,r=17):
    return circle(x,y+3,r,'#AA7426')+circle(x,y,r,'#FFDC7C')+circle(x,y,r*.73,'none', 'stroke="#DDA743" stroke-width="2"')+text(x,y+r*.32,'T',int(r*.9),'#B37D24',900,'middle')
def star(x,y,s=12,color=None):
    return f'<path d="M{x} {y-s} Q{x+2} {y-2} {x+s} {y} Q{x+2} {y+2} {x} {y+s} Q{x-2} {y+2} {x-s} {y} Q{x-2} {y-2} {x} {y-s}Z" fill="{color or T["gold"]}"/>'
def avatar(x,y,color='#9FE2CB',name='YOU',size=30):
    return circle(x,y,size,color)+circle(x-size*.23,y-size*.05,2.5,'#25323D')+circle(x+size*.23,y-size*.05,2.5,'#25323D')+f'<path d="M{x-size*.24} {y+size*.24} Q{x} {y+size*.43} {x+size*.24} {y+size*.24}" fill="none" stroke="#25323D" stroke-width="3" stroke-linecap="round"/>'+text(x,y+size+24,name,14,T['muted'],700,'middle')
def brand(x=48,y=49):
    return circle(x+19,y-6,19,T['accent'])+text(x+19,y+1,'T',22,'#24192E',900,'middle')+text(x+49,y+2,'tambola',27,T['ink'],900)+text(x+50,y+20,'T O G E T H E R',8,T['muted'],700)
def header(balance=1500):
    return brand()+group('Your-profile',avatar(897,41,T['mint'],'',22),'profile')+group('Wallet',pill(942,20,188,f'{balance:,}  coins',T['gold']),'profile')+group('Sound',rect(1150,20,48,40,T['soft'],15)+text(1174,47,'♪',27,T['ink'],600,'middle'),'sound')+group('Settings',rect(1212,20,40,40,T['soft'],15)+text(1232,47,'···',24,T['ink'],900,'middle'),'settings')
def background():
    return rect(0,0,W,H,T['bg'],0)+f'<path d="M780 0 Q510 250 1280 620 L1280 0Z" fill="{T["panel"]}"/>'+f'<path d="M0 630 Q600 500 1280 610 V720 H0Z" fill="{T["panel"]}" opacity=".4"/>'+''.join(star(x,y,s,T['soft']) for x,y,s in [(54,360,9),(732,109,8),(1190,184,13),(598,546,8),(85,657,9)])
def tiny_ticket(x,y,scale=1,angle=-10):
    body=rect(0,8,286,154,'#050512',19,extra='opacity=".18"')+rect(0,0,286,154,'#FFF4DA',19)+rect(0,0,286,34,'#FBD276',16)+text(22,24,'YOUR LUCKY TICKET',13,'#775C31',800)
    vals=[3,12,22,31,45,57,61,72,88]
    for r in range(3):
        for c in range(6):
            xx,yy=18+c*42,45+r*31
            body+=rect(xx,yy,36,26,'#E7DCC4' if (r+c)%3==1 else '#FFF9EF',5)
            if (r+c)%3!=1: body+=text(xx+18,yy+19,vals[(r*3+c)%9],14,'#554A42',700,'middle')
    return f'<g transform="translate({x} {y}) rotate({angle}) scale({scale})">{body}</g>'
def welcome():
    out=background()+brand()+pill(1080,24,140,'EN  /  हिन्दी')
    out+=pill(56,146,216,'A LITTLE LUCK. A LOT OF FUN.',T['mint'])
    out+=text(54,267,'Good times.',86,T['ink'],900)+text(54,355,'Great calls.',86,T['gold'],900)
    out+=text(59,408,'Your next Tambola table is one tap away.',23,T['muted'],500)
    out+=coin(81,468,21)+text(116,475,'1,500 free coins to get you started',22,T['ink'],700)
    out+=button(58,527,346,'LET’S PLAY  →','lobby',sub='Guest play. No sign-up form.')
    out+=group('Choose-your-look',text(459,569,'Make it yours',20,T['muted'],700),'profile')
    out+=tiny_ticket(782,327,1.25,13)+ball(963,221,115,22,'coral',12)+ball(751,460,68,7,'mint',-13)+ball(1136,496,83,90,'gold',14)
    out+=star(783,179,23)+star(1147,332,15)+text(64,687,'Free virtual coins · No cash prizes',14,T['muted'],500)
    return out
def lobby():
    out=background()+header()+text(56,124,'Hey, Player 07',20,T['muted'])
    out+=text(54,209,'Make your',65,T['ink'],900)+text(54,281,'next call count.',65,T['gold'],900)
    out+=pill(58,318,142,'QUICK PLAY',T['mint'])+text(216,343,'5s calls  ·  6–8 prizes',19,T['muted'],500)
    out+=tiny_ticket(140,426,1.05,-8)+ball(416,422,77,66,'coral',11)+ball(85,529,46,5,'mint',-8)+star(459,565,17)
    out+=rect(639,124,584,515,T['panel'],34, T['soft'])+text(681,179,'Your tickets',33,T['ink'],900)+text(1181,177,'100 coins each',17,T['muted'],600,'end')
    out+=text(681,218,'Pick your hand. We’ll find your table.',19,T['muted'],500)
    for i in range(1,7):
        x=680+(i-1)*85
        body=rect(x,257,72,100,T['accent'] if i==3 else T['soft'],17)+circle(x,299,7,T['panel'])+circle(x+72,299,7,T['panel'])+text(x+36,319,i,38,'#24192E' if i==3 else T['ink'],900,'middle')
        out+=group(f'{i}-tickets',body,f'tickets-{i}')
    out+=text(681,403,'3 tickets',22,T['ink'],700,extra='id="ticket-count-label"')+text(1177,403,'300 coins',24,T['gold'],800,'end',extra='id="ticket-cost-label"')
    out+=button(680,434,501,'PLAY  ·  300','ready',sub='Coins are free. Your wins stay in your wallet.')
    out+=text(681,591,'EARLY 5',12,T['muted'],700)+text(799,591,'CORNERS',12,T['muted'],700)+text(933,591,'LINES',12,T['muted'],700)+text(1040,591,'FULL HOUSE',12,T['muted'],700)
    out+=text(58,683,'A real table. A little friendly competition.',16,T['muted'],500)
    return out
def ready():
    out=background()+header(1200)+text(640,143,'Your table is filling up',40,T['ink'],900,'middle')
    out+=text(640,187,'3 tickets in hand',22,T['muted'],600,'middle')
    out+=circle(640,317,101,T['soft'])+timer_arc(640,317,101,10/12,T['accent'],'lobby-ring',7)+circle(640,317,83,T['panel'])+text(640,336,'10',69,T['gold'],900,'middle',extra='id="countdown-value"')+text(640,372,'STARTS IN',13,T['muted'],700,'middle')
    for index,(x,c,n) in enumerate([(278,T['mint'],'YOU'),(422,T['accent'],'ChaiChamp'),(858,'#BCADF5','NeonNinja'),(1002,T['gold'],'LuckyMango')]):
        out+=f'<g id="seat-{index}" visibility="{"hidden" if index<2 else "visible"}">'+circle(x,321,40,'none',f'stroke="{T["soft"]}" stroke-width="3" stroke-dasharray="7 6"')+text(x,330,'+',29,T['muted'],600,'middle')+text(x,383,'Joining…',14,T['muted'],600,'middle')+'</g>'
        out+=f'<g id="arrival-{index}" class="seat-arrival" visibility="{"visible" if index<2 else "hidden"}">'+avatar(x,321,c,n,38)+(text(x,406,'COMPUTER',11,T['muted'],600,'middle') if index>=2 else '')+'</g>'
    out+=rect(325,466,630,87,T['panel'],25)+text(362,498,'ROUND POOL',13,T['muted'],700)+coin(381,526,16)+text(410,534,'1,200',29,T['gold'],900,extra='id="round-pool"')+text(920,502,'7 prizes',24,T['ink'],800,'end',extra='id="prize-count"')+text(920,533,'Sales close before the first call',16,T['muted'],500,'end')
    out+=text(640,613,'ChaiChamp joined your table',23,T['mint'],700,'middle',extra='id="join-status"')+group('Leave-table',text(85,641,'← Leave table',17,T['muted'],600),'lobby')
    return out

def settings():
    out=background()+brand()+text(64,142,'Settings',44,T['ink'],900)+group('Back-to-lobby',rect(1052,101,174,56,T['soft'],18)+text(1139,138,'Back to game',18,T['ink'],700,'middle'),'lobby')
    out+=rect(58,180,566,375,T['panel'],28)+rect(648,180,576,375,T['panel'],28)
    out+=text(90,225,'SOUND',15,T['muted'],800)+text(680,225,'PLAY',15,T['muted'],800)
    for key,label,x,y,on in [('voice','Number voice',90,291,True),('music','Music',90,386,False),('effects','Game sounds',90,481,True),('haptics','Vibration',680,291,True),('motion','Reduced motion',680,386,False)]:
        body=rect(x-10,y-45,518,72,'transparent',12)+text(x,y,label,25,T['ink'],700)+rect(x+412,y-32,76,42,T['mint'] if on else T['soft'],21,extra=f'id="setting-track-{key}"')+circle(x+467 if on else x+433,y-11,15,T['bg'] if on else T['muted'],extra=f'id="setting-thumb-{key}"')
        out+=group('setting-'+key,body,'toggle-'+key)
    out+=text(680,458,'LANGUAGE',13,T['muted'],700)
    for index,label in enumerate(['English','हिन्दी','Hinglish']):
        x=680+index*163
        out+=group('language-'+str(index),rect(x,481,149,48,T['soft'],14)+text(x+74,512,label,18,T['ink'],700,'middle'),'language-'+str(index))
    out+=group('Your-game-data',text(64,626,'Your game data  ↗',18,T['muted'],600),'privacy')+text(64,677,'Free virtual coins · No cash value',14,T['muted'],500)
    return out
CARDS=[[[3,0,22,0,44,0,61,0,82],[0,12,0,31,45,0,0,72,88],[5,17,28,0,0,57,69,0,0]],[[8,0,25,0,40,0,62,0,85],[0,13,0,36,46,0,0,74,89],[9,19,29,0,0,58,67,0,0]],[[1,0,23,0,41,0,63,0,80],[0,10,0,34,48,0,0,70,87],[6,16,27,0,0,55,68,0,0]]]
def complete_demo_strip():
    used={n for card in CARDS for row in card for n in row if n}
    columns=[[n for n in range(1,91) if min(n//10,8)==c and n not in used] for c in range(9)]
    def allocation(col,totals,rows):
        if col==9:return rows if totals==[15]*3 else None
        for counts in product(range(1,4),repeat=3):
            if sum(counts)!=len(columns[col]):continue
            after=[a+b for a,b in zip(totals,counts)]
            if any(a+(8-col)>15 or a+3*(8-col)<15 for a in after):continue
            found=allocation(col+1,after,rows+[counts])
            if found:return found
    counts=allocation(0,[0]*3,[])
    assert counts
    for ticket in range(3):
        def masks(c,totals,placed):
            if c==9:return placed if totals==[5]*3 else None
            for slots in combinations(range(3),counts[c][ticket]):
                after=[v+(r in slots) for r,v in enumerate(totals)]
                if max(after)>5:continue
                found=masks(c+1,after,placed+[slots])
                if found:return found
        positions=masks(0,[0]*3,[])
        assert positions
        card=[[0]*9 for _ in range(3)]
        for c,slots in enumerate(positions):
            start=sum(counts[c][:ticket])
            for r,n in zip(slots,columns[c][start:start+len(slots)]):card[r][c]=n
        CARDS.append(card)
    assert sorted(n for card in CARDS for row in card for n in row if n)==list(range(1,91))
    assert all(sum(n!=0 for n in row)==5 for card in CARDS for row in card)
complete_demo_strip()
CALLED={3,12,22,44,57,69,72,82,8,13,25,40,58,62,74,85,7,18,23,37,46}
def gameplay(page=1,quantity=3):
    out=rect(0,0,W,H,T['bg'],0)+rect(0,0,W,112,T['panel'],0)
    out+=group('Lobby',text(40,57,'‹',48,T['muted'],600),'lobby')+f'<g id="live-call">'+ball(136,57,39,46,'coral')+'</g>'+timer_arc(136,57,46,1,T['mint'],'call-ring',4)
    for index,(x,n) in enumerate(zip([245,313,381,449],[37,23,18,7])): out+=group(f'recent-slot-{index}',circle(x,56,26,T['soft'])+text(x,64,n,22,T['muted'],800,'middle',extra=f'id="recent-{index}"'))
    out+=text(532,47,'21 / 90',20,T['ink'],800)+text(532,76,'called',14,T['muted'],500)+text(743,47,'3 tickets',20,T['ink'],800)+text(743,76,'yours',14,T['muted'],500)+coin(964,52,20)+text(1000,60,'1,200',25,T['gold'],900,extra='id="round-pool"')+group('Sound',text(1220,63,'♪',28,T['muted'],600),'sound')
    out+=text(33,165,'PRIZES',15,T['muted'],800)
    for i,(label,amount) in enumerate([('Early 5',120),('Corners',120),('Top line',120),('Middle line',120),('Bottom line',120),('Full house',420),('2nd house',180)]):
        y=183+i*53
        out+=group('prize-'+str(i),rect(24,y,205,43,T['panel'],13)+text(40,y+28,label,16,T['ink'],700)+text(214,y+28,amount,16,T['gold'],800,'end'))
    out+=group('Players',rect(24,594,205,83,T['soft'],20)+text(42,624,'4 at the table',17,T['ink'],800)+text(42,651,'2 computers',14,T['muted'],500),'players')
    for slot,card in enumerate(CARDS[(page-1)*2:min(page*2,quantity)]):
        ordinal=(page-1)*2+slot
        top=153+slot*251
        out+=f'<g id="ticket-{ordinal+1}">'
        out+=rect(257,top,899,226,'#FFF3D8',24)+text(281,top+34,f'TICKET 0{ordinal+1}',15,'#7C6952',800)
        for r,row in enumerate(card):
            for c,n in enumerate(row):
                x,y=278+c*76,top+49+r*52
                marked=n in CALLED and n!=46
                cell=rect(x,y,70,46,'#F2B878' if marked else '#E6D9BB' if n==0 else '#FFF9EF',9)
                if n: cell+=text(x+35,y+33,n,27,'#463B35',900,'middle')
                if marked: cell+=circle(x+58,y+9,4,'#B85A35')
                out+=group(f'Number-{n}' if n else f'Blank-{ordinal}-{r}-{c}',cell,f'dab-{n}' if n else None)
        out+=group(f'Claim-ticket-{ordinal+1}',rect(986,top+64,146,106,T['accent'],19)+text(1059,top+104,'✦',27,'#593C34',600,'middle')+text(1059,top+139,'CLAIM',21,'#24192E',900,'middle'),'claim')
        out+='</g>'
    out+=group('Previous-tickets',rect(1174,199,78,84,T['soft'],20)+text(1213,253,'↑',36,T['ink'],600,'middle'),'page-up')
    out+=text(1213,335,'1–2' if page==1 else '3',22,T['ink'],800,'middle',extra='id="page-range"')+text(1213,364,'of 3',16,T['muted'],600,'middle',extra='id="page-total"')
    out+=group('Next-tickets',rect(1174,401,78,84,T['soft'],20)+text(1213,455,'↓',36,T['ink'],600,'middle'),'page-down')
    out+=text(283,682,'YOUR TICKETS',13,T['muted'],700)+text(1157,682,'Next call  03',17,T['gold'],700,'end')
    return out
def claim():
    out=gameplay()+rect(0,0,W,H,'#0A091D',0,extra='opacity=".75"')+rect(362,98,560,532,T['panel'],30,T['soft'])
    out+=text(406,157,'What’s your win?',35,T['ink'],900)+text(407,194,'TICKET 01',15,T['muted'],700,extra='id="claim-ticket-label"')+group('Close-claim',text(877,150,'×',33,T['muted'],500,'middle'),'game')
    labels=['Early 5','Corners','Top line','Middle line','Bottom line','Full house']
    for i,label in enumerate(labels):
        x,y=404+(i%2)*242,225+(i//2)*91
        out+=group('Claim-'+label.replace(' ','-'),rect(x,y,228,73,T['soft'],16,T['mint'] if i==0 else 'none',2)+text(x+20,y+32,label,20,T['ink'],800)+text(x+20,y+57,'120 coins' if i<5 else '420 coins',14,T['gold'],600),'claim-win')
    out+=group('next-house',rect(404,510,470,59,T['bg'],16)+text(427,548,'2nd house',19,T['muted'],700)+text(850,547,'NEXT',12,T['muted'],700,'end'))
    return out
def results():
    out=background()+header(1620)+text(640,167,'THAT’S A GREAT CALL',16,T['mint'],800,'middle')
    out+=text(640,261,'Winner at your table.',67,T['ink'],900,'middle')+coin(492,362,39)+text(552,390,'+420',94,T['gold'],900,extra='id="won-value"')
    out+=group('Won-prize',pill(513,435,255,'FULL HOUSE · TICKET 01',T['ink']))+button(441,511,398,'PLAY AGAIN  ·  300','ready')
    out+=group('Back-to-lobby',text(640,620,'Back to lobby',20,T['muted'],600,'middle'),'lobby')
    for x,y,s in [(190,233,17),(1060,170,13),(308,432,15),(1034,459,24),(377,179,11),(925,324,13)]:out+=star(x,y,s)
    out+=ball(191,490,72,22,'coral',-12)+ball(1075,352,83,90,'mint',12)
    return out
DEFS='''<defs>
<radialGradient id="coral" cx=".28" cy=".18" r=".95"><stop stop-color="#FFCF97"/><stop offset=".6" stop-color="#FC8464"/><stop offset="1" stop-color="#CD4A63"/></radialGradient>
<radialGradient id="mint" cx=".3" cy=".2" r=".95"><stop stop-color="#DCF7BA"/><stop offset=".65" stop-color="#89D6BE"/><stop offset="1" stop-color="#42A69D"/></radialGradient>
<radialGradient id="gold" cx=".3" cy=".2" r=".95"><stop stop-color="#FFF0B3"/><stop offset=".65" stop-color="#FFD477"/><stop offset="1" stop-color="#DF9952"/></radialGradient>
</defs>'''
def svg(body, title, width=W,height=H):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}"><title>{title}</title>{DEFS}{body}</svg>'
T=NIGHT
SCREENS={name:func() for name,func in [('welcome',welcome),('lobby',lobby),('ready',ready),('game',gameplay),('claim',claim),('results',results)]}
for i,(name,body) in enumerate(SCREENS.items(),1):
    (ROOT/'frames'/f'{i:02}-{name}.svg').write_text(svg(body,name),encoding='utf-8')
T=DAY
SCREENS['daylight']=lobby()
(ROOT/'frames'/'07-daylight-direction.svg').write_text(svg(SCREENS['daylight'],'Daylight direction'),encoding='utf-8')
T=NIGHT
SCREENS['game2']=gameplay(2)
(ROOT/'frames'/'08-game-page2.svg').write_text(svg(SCREENS['game2'],'Ticket page 2'),encoding='utf-8')
SCREENS['settings']=settings()
(ROOT/'frames'/'09-settings.svg').write_text(svg(SCREENS['settings'],'Minimal settings'),encoding='utf-8')
board=rect(0,0,2768,4134,'#E9E7ED',0)+text(64,74,'TAMBOLA TOGETHER — LANDSCAPE DESIGN REVIEW',34,'#292237',900)+text(64,116,'Direction A · Game night / Direction B · Daylight club · Original vector artwork · Demo data',22,'#736A83',500)
for i,(name,body) in enumerate(SCREENS.items()):
    x,y=64+(i%2)*1340,204+(i//2)*784
    body=re.sub(r'id="([^"]+)"',lambda m:f'id="{name}-{m.group(1)}"',body)
    board+=text(x,y-20,f'{i+1:02} / {name.upper()}',20,'#5D556D',800)+f'<g id="{i+1:02}-{name}" transform="translate({x} {y})">{body}</g>'
(ROOT/'Tambola-Landscape-Review.svg').write_text(svg(board,'Tambola landscape review',2768,4134),encoding='utf-8')
print(f'Created {len(SCREENS)} editable vector screen sources and review board')
for page in range(1,4):SCREENS[f'play{page}']=gameplay(page,6)
SCREENS['claim-overlay']=claim()[len(gameplay()):]
(ROOT/'screens.json').write_text(json.dumps(SCREENS,ensure_ascii=False),encoding='utf-8')
(ROOT/'cards.json').write_text(json.dumps(CARDS),encoding='utf-8')
