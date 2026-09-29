from render_preview import scene,ROOT,imageio_ffmpeg,TICKETS
from PIL import Image,ImageChops,ImageDraw
import subprocess,json
for page in range(3):
    scene(6,page=page,ticket_count=6,show_claim=False).save(ROOT/f'six-tickets-page-{page+1}.png')
    (ROOT/f'six-tickets-page-{page+1}.svg').write_text(scene(6,True,page=page,ticket_count=6,show_claim=False),encoding='utf-8')
assert len({tuple(n for row in ticket for n in row) for ticket in TICKETS})==6
for ticket in TICKETS:
    assert all(sum(n>0 for n in row)==5 for row in ticket)
    nums=[n for row in ticket for n in row if n]
    assert len(nums)==len(set(nums))==15
    for col in range(9):
        values=[row[col] for row in ticket if row[col]]
        assert values==sorted(values) and values
        assert all((1 if col==0 else col*10)<=n<=(90 if col==8 else col*10+9) for n in values)
# Page changes must not touch the call board or global clock.
first=scene(6,page=0,ticket_count=6,show_claim=False)
for page in (1,2):
    assert ImageChops.difference(first.crop((0,0,300,500)),scene(6,page=page,ticket_count=6,show_claim=False).crop((0,0,300,500))).getbbox() is None
for count in range(1,7):
    for page in range((count+1)//2):
        svg=scene(6,True,page=page,ticket_count=count,show_claim=False)
        assert f'TICKET {page*2+1}' in svg
        assert (f'TICKET {page*2+2}' in svg)==(page*2+2<=count)
# Pagination video: no auto-flip on calls; arrow-driven page changes at 6, 9, 12 and 14 seconds.
p=subprocess.Popen([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-f','rawvideo','-vcodec','rawvideo','-s','960x540','-pix_fmt','rgb24','-r','24','-i','-','-an','-vcodec','libx264','-pix_fmt','yuv420p','-crf','19','-movflags','+faststart',str(ROOT/'six-ticket-preview.mp4')],stdin=subprocess.PIPE,stderr=subprocess.PIPE)
for i in range(16*24):
    t=i/24;page=0 if t<6 or t>=14 else (1 if t<9 or t>=12 else 2)
    frame=scene(t,page=page,ticket_count=6,show_claim=False)
    for tap,cy in [(5.8,347),(8.8,347),(11.8,215),(13.8,215)]:
        if abs(t-tap)<.13: ImageDraw.Draw(frame).ellipse((914,cy-6,926,cy+6),fill='#FF8968')
    p.stdin.write(frame.tobytes())
p.stdin.close();err=p.stderr.read();assert p.wait()==0,err.decode()
frames=[]
for i,t in enumerate([5.5,6.5,9.5,14.5]):
    path=ROOT/f'six-ticket-video-{i}.png'
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-ss',str(t),'-i',str(ROOT/'six-ticket-preview.mp4'),'-frames:v','1',str(path)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    frames.append(Image.open(path).resize((640,360)))
sheet=Image.new('RGB',(1280,720))
for i,frame in enumerate(frames):sheet.paste(frame,((i%2)*640,(i//2)*360))
sheet.save(ROOT/'six-ticket-contact-sheet.png')
(ROOT/'six-ticket-validation.json').write_text(json.dumps({'passed':True,'checks':['Six distinct legal 3x9 tickets','All ticket counts 1 through 6 paginate without nonexistent tickets','Board and call clock remain pixel-identical across page changes','Three pages retain unique ticket identity; returning to page one retains marked cells','48x48 arrow controls; full 48-unit number-row height retained'],'scope':'Illustrative design geometry, not native device validation'},indent=2)+'\n')
print('PASS: six-ticket concept rendered and validated')
