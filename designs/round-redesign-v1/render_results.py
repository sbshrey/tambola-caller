"""Fixed results actions and a ranked, scrolling fictional roster focused on You."""
from render_preview import ROOT, C, font, imageio_ffmpeg
from PIL import Image, ImageDraw
from html import escape
import subprocess
import json

PLAYERS = [('You', 'Early five · Top line', 200), ('Player 2', 'Full house', 500),
           ('Computer 1', 'Full house', 500), ('Player 4', 'Full house', 500),
           ('Player 5', 'Corners · Middle line', 200), ('Computer 2', 'Bottom line', 100)]
PLAYERS += [(f'Player {n}', 'No prizes this round', 0) for n in range(7, 25)]
PLAYERS.sort(key=lambda player: -player[2])  # Stable tie order; equal totals share rank.
RANKS = [1 + sum(other[2] > player[2] for other in PLAYERS) for player in PLAYERS]
OWN_INDEX = next(i for i, player in enumerate(PLAYERS) if player[0] == 'You')
MAX_SCROLL = len(PLAYERS)*69-337
OWN_SCROLL = max(0, min(MAX_SCROLL, OWN_INDEX*69-138))

def results(scroll=OWN_SCROLL, svg=False):
    im = Image.new('RGB', (960, 540), C['bg'])
    draw = ImageDraw.Draw(im)
    elements = []
    def rect(x, y, w, h, color, radius=14):
        draw.rounded_rectangle((x,y,x+w,y+h),radius,fill=color)
        elements.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{color}"/>')
    def text(x,y,value,size=16,color=None,bold=False):
        color = color or C['cream']
        draw.text((x,y),value,font=font(size,bold),fill=color)
        elements.append(f'<text x="{x}" y="{y+size}" font-family="Segoe UI,Roboto,sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}">{escape(value)}</text>')
    text(24,18,'ROUND COMPLETE',23,bold=True)
    text(660,25,'24 players · Results confirmed',14,C['muted'])
    rect(24,68,260,378,C['panel'])
    text(44,90,'YOUR WINNINGS',13,C['muted'],True)
    text(44,117,'200 coins',34,C['gold'],True)
    text(44,175,'Early five',17);text(224,175,'100',16,C['mint'])
    text(44,207,'Top line',17);text(224,207,'100',16,C['mint'])
    text(44,264,'Your share of each prize',14,C['muted'])
    text(44,294,f'Rank #{RANKS[OWN_INDEX]} · 6 tickets',16)
    text(44,391,'Thanks for playing!',18,C['mint'],True)
    text(308,69,'RANK · PLAYER · PRIZES',13,C['muted'],True)
    text(807,69,'COINS WON',13,C['muted'],True)
    # Render into a clipped surface so scrolling never covers the fixed actions/header.
    roster = Image.new('RGB',(960,540),C['bg'])
    draw = ImageDraw.Draw(roster)
    elements.append('<defs><clipPath id="roster"><rect x="308" y="98" width="632" height="337"/></clipPath></defs><g clip-path="url(#roster)">')
    for index, (name, prizes, amount) in enumerate(PLAYERS):
        y=98+index*69-scroll
        mine = name=='You'
        rect(308,y,618,61,C['mint'] if mine else C['panel'])
        text(321,y+18,f'#{RANKS[index]}',16,C['ink'] if mine else C['muted'],True)
        text(370,y+7,name,16,C['ink'] if mine else C['cream'],True)
        text(370,y+33,prizes,13,C['ink'] if mine else C['muted'])
        text(825,y+18,str(amount),19,C['ink'] if mine else C['gold'],True)
    elements.append('</g>')
    im.paste(roster.crop((308,98,940,435)),(308,98))
    draw = ImageDraw.Draw(im)
    rect(934,98,5,337,C['raised'],2)
    rect(934,98+(337-68)*scroll/MAX_SCROLL,5,68,C['muted'],2)
    rect(24,466,260,54,C['raised']);text(92,481,'Back to lobby',18,bold=True)
    rect(308,466,632,54,C['coral']);text(490,480,'Play again · 6 tickets',20,C['ink'],True)
    if svg: return '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540" viewBox="0 0 960 540"><rect width="960" height="540" fill="'+C['bg']+'"/>'+''.join(elements)+'</svg>'
    return im

if __name__ == '__main__':
    for name, scroll in [('your-rank',OWN_SCROLL),('leaders',0),('rest',600)]:
        results(scroll).save(ROOT/f'results-{name}.png')
        (ROOT/f'results-{name}.svg').write_text(results(scroll,True),encoding='utf-8')
    process = subprocess.Popen([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-f','rawvideo','-vcodec','rawvideo','-s','960x540','-pix_fmt','rgb24','-r','24','-i','-','-an','-vcodec','libx264','-pix_fmt','yuv420p','-crf','19',str(ROOT/'results-preview.mp4')],stdin=subprocess.PIPE,stderr=subprocess.PIPE)
    for frame in range(10*24):
        t=frame/24
        scroll = OWN_SCROLL if t<2 else (OWN_SCROLL*(1-min(1,(t-2)/2)) if t<4 else (600*min(1,(t-4)/3) if t<7 else 600+(OWN_SCROLL-600)*min(1,(t-7)/2)))
        process.stdin.write(results(scroll).tobytes())
    process.stdin.close(); error=process.stderr.read(); assert process.wait()==0,error.decode()
    # Static actions/personal winnings remain identical while the ranked list scrolls.
    from PIL import ImageChops
    for scroll in (0,300,MAX_SCROLL):
        assert ImageChops.difference(results().crop((0,466,960,540)),results(scroll).crop((0,466,960,540))).getbbox() is None
        assert ImageChops.difference(results().crop((0,68,284,446)),results(scroll).crop((0,68,284,446))).getbbox() is None
    assert len(PLAYERS)==24 and all(PLAYERS[i][2]>=PLAYERS[i+1][2] for i in range(23))
    assert 0 <= OWN_INDEX*69-OWN_SCROLL <= 337-61
    (ROOT/'results-validation.json').write_text(json.dumps({'passed':True,'scope':'Illustrative geometry, not native settlement or replay validation','checks':['24 players sorted by descending winnings with shared ranks for ties','Initial scroll reveals highlighted own row without reordering ranks','Replay and lobby actions remain fixed','Personal result remains fixed while scrolling','Computer players explicitly labelled']},indent=2))
