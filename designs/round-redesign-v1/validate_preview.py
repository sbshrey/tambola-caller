from pathlib import Path
from PIL import Image, ImageChops, ImageStat
import subprocess, json
from render_preview import scene, T1, T2, ROOT, imageio_ffmpeg

checks=[]
for ticket in (T1,T2):
    assert len(ticket)==3 and all(len(row)==9 for row in ticket)
    assert all(sum(n>0 for n in row)==5 for row in ticket)
    numbers=[n for row in ticket for n in row if n]
    assert len(numbers)==len(set(numbers))==15
    for col in range(9):
        values=[row[col] for row in ticket if row[col]]
        assert values==sorted(values) and values
        assert all((1 if col==0 else col*10)<=n<=(90 if col==8 else col*10+9) for n in values)
checks.append('Both tickets obey 3x9, 15 unique numbers, five per row and valid sorted column ranges')
# Prove the design does not alter or reposition untouched cells when the fifth mark lands.
before=scene(2.4);after=scene(3.6)
assert ImageChops.difference(before.crop((308,294,945,501)),after.crop((308,294,945,501))).getbbox() is None
change=ImageChops.difference(before.crop((308,76,945,283)),after.crop((308,76,945,283))).getbbox()
assert change and change[0]>=148 and change[2]<=214 and change[1]>=93 and change[3]<=142
checks.append('Only the fifth-mark cell changes within Ticket 1; Ticket 2 is pixel-identical')
assert ImageChops.difference(scene(7.8).crop((308,76,945,501)),scene(8.2).crop((308,76,945,501))).getbbox() is None
assert '6 prizes left' in scene(14,True) and '5 prizes left' in scene(15.5,True)
checks.append('Claim sheet persists across the eight-second call; prize count changes only after confirmation')
for power in ['SHIELD','BONUS']:
    assert 'Armed for Ticket 1' in scene(6,True,power)
    assert ImageChops.difference(scene(4,power=power).crop((320,118,933,274)),scene(6,power=power).crop((320,118,933,274))).getbbox() is None
checks.append('Shield and bonus arm without automatically marking ticket numbers')
frames=[]
for i,t in enumerate([2.4,3.6,7.8,8.2,14,15.5]):
    path=ROOT/f'video-validated-{i}.png'
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-ss',str(t),'-i',str(ROOT/'round-preview.mp4'),'-frames:v','1',str(path)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    frame=Image.open(path).convert('RGB')
    error=sum(ImageStat.Stat(ImageChops.difference(frame,scene(t))).mean)/3
    assert error<4, (t,error)
    frames.append(frame.resize((480,270)))
checks.append('Six decoded MP4 frames match intended scene states within compression tolerance')
sheet=Image.new('RGB',(1440,540))
for i,frame in enumerate(frames): sheet.paste(frame,((i%3)*480,(i//3)*270))
sheet.save(ROOT/'validated-contact-sheet.png')
(ROOT/'validation.json').write_text(json.dumps({'passed':True,'checks':checks,'scope':'Design preview only; not native layout, Android performance or server behavior'},indent=2)+'\n')
print('PASS:',len(checks),'design checks')
