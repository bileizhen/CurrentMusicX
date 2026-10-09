"""Encode actual UiAutomation frames with recorded monotonic intervals, without interpolation."""
import argparse, json, subprocess
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('directory',type=Path); p.add_argument('output',type=Path); p.add_argument('--ffmpeg'); a=p.parse_args()
if a.ffmpeg: encoder=a.ffmpeg
else:
 try:
  import imageio_ffmpeg
  encoder=imageio_ffmpeg.get_ffmpeg_exe()
 except ImportError:
  encoder='ffmpeg'
rows=[json.loads(x) for x in (a.directory/'frames.jsonl').read_text(encoding='utf-8').splitlines()]
if len(rows)<2: raise SystemExit('Insufficient actual device frames; no video evidence produced')
lines=['ffconcat version 1.0']
for i,r in enumerate(rows):
 path=(a.directory/r['file']).resolve().as_posix()
 if "'" in path: raise SystemExit('Unsupported quote in frame path')
 lines.append("file '"+path+"'")
 lines.append('option framerate 1000') # Millisecond time base; default image2 25Hz would quantize capture intervals.
 if i+1<len(rows): lines.append('duration %.6f'%max(.001,(rows[i+1]['elapsedMs']-r['elapsedMs'])/1000))
manifest=a.directory/'capture.ffconcat'; manifest.write_text('\n'.join(lines)+'\n',encoding='utf-8')
subprocess.run([encoder,'-y','-hide_banner','-loglevel','error','-safe','0','-f','concat','-i',str(manifest),'-fps_mode','vfr','-c:v','libx264','-threads','2','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart',str(a.output)],check=True)
print(json.dumps({'frames':len(rows),'elapsedSeconds':(rows[-1]['elapsedMs']-rows[0]['elapsedMs'])/1000,'positiveEnvelopeRequests':sum(r['glitchAtRequest']>0 for r in rows),'output':str(a.output)},ensure_ascii=False))
