# تشغيل:  pip install flask yt-dlp   (ويفضل ffmpeg للصوت والدمج)
#         python app.py   ثم افتح http://localhost:5000 من المتصفح
import os, shutil, tempfile
from flask import Flask, request, send_file, jsonify, after_this_request, render_template_string
import yt_dlp

APP_NAME = "Pluck"  # غيّره بعد ما تختار الاسم النهائي
app = Flask(__name__)

PAGE = """<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>{{n}}</title>
<style>
body{margin:0;background:#0B0718;color:#fff;font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}
.c{width:min(92vw,420px);text-align:center}h1{color:#C6FF00;font-size:2.2rem;margin:6px 0 4px}
p{color:#9a94b8;margin:0 0 20px}input,select,button{width:100%;box-sizing:border-box;padding:14px;margin:6px 0;border-radius:12px;border:1px solid #2A1F5C;background:#120B2E;color:#fff;font-size:1rem;font-family:inherit}
button{background:#C6FF00;color:#120B2E;font-weight:700;border:0;cursor:pointer}button.s{background:#120B2E;color:#fff;border:1px solid #2A1F5C;font-weight:500}
img{width:100%;border-radius:12px;margin-top:10px}#m{min-height:24px;color:#FF2E93;margin-top:8px}
</style></head><body><div class="c">
<svg width="76" height="76" viewBox="110 30 160 160"><rect x="110" y="30" width="160" height="160" rx="42" fill="#120B2E" stroke="#2A1F5C" stroke-width="2"/><g transform="translate(10,5)" fill="none" stroke="#C6FF00" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"><path d="M158 66 H188 a29 29 0 0 1 0 58 H158"/><path d="M158 66 V150"/><path d="M142 134 L158 150 L174 134"/></g><circle cx="199" cy="100" r="8" fill="#FF2E93"/></svg>
<h1>{{n}}</h1><p>انسخ الرابط، الصقه، ونزّل</p>
<input id="u" placeholder="https://..." inputmode="url" dir="ltr">
<button class="s" onclick="paste()">لصق من الحافظة</button>
<select id="q"><option value="video">فيديو (MP4)</option><option value="audio">صوت فقط (MP3)</option></select>
<button onclick="go()">تحميل</button><div id="m"></div><div id="p"></div></div>
<script>
const $=i=>document.getElementById(i);
async function paste(){try{$('u').value=await navigator.clipboard.readText()}catch(e){$('m').textContent='الصق الرابط يدوياً'}}
async function go(){
 const u=$('u').value.trim();if(!u)return $('m').textContent='ضع الرابط أولاً';
 $('m').textContent='جاري الفحص...';$('p').innerHTML='';
 const r=await fetch('/api/info?url='+encodeURIComponent(u));const d=await r.json();
 if(!r.ok)return $('m').textContent=d.error;
 $('p').innerHTML=(d.thumbnail?'<img src="'+d.thumbnail+'">':'')+'<p>'+d.title+'</p>';
 $('m').textContent='جاري التحميل... انتظر شوية';
 location.href='/api/download?q='+$('q').value+'&url='+encodeURIComponent(u);
}
</script></body></html>"""

def valid(u):
    return u.startswith(("http://", "https://"))

@app.get("/")
def home():
    return render_template_string(PAGE, n=APP_NAME)

@app.get("/api/info")
def info():
    u = request.args.get("url", "")
    if not valid(u):
        return jsonify(error="الرابط غير صحيح"), 400
    try:
        with yt_dlp.YoutubeDL({"quiet": True, "noplaylist": True}) as y:
            d = y.extract_info(u, download=False)
        return jsonify(title=d.get("title", ""), thumbnail=d.get("thumbnail"))
    except Exception:
        return jsonify(error="مقدرتش أقرأ الفيديو من الرابط ده"), 422

@app.get("/api/download")
def download():
    u, q = request.args.get("url", ""), request.args.get("q", "video")
    if not valid(u):
        return jsonify(error="الرابط غير صحيح"), 400
    tmp = tempfile.mkdtemp()
    opts = {"quiet": True, "noplaylist": True, "outtmpl": os.path.join(tmp, "%(title).80s.%(ext)s")}
    if q == "audio":
        opts["format"] = "bestaudio/best"
        opts["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3"}]
    else:
        opts["format"] = "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b"
        opts["merge_output_format"] = "mp4"
    try:
        with yt_dlp.YoutubeDL(opts) as y:
            y.download([u])
        f = os.path.join(tmp, os.listdir(tmp)[0])
    except Exception:
        shutil.rmtree(tmp, ignore_errors=True)
        return jsonify(error="فشل التحميل"), 500

    @after_this_request
    def clean(resp):
        resp.call_on_close(lambda: shutil.rmtree(tmp, ignore_errors=True))
        return resp
    return send_file(f, as_attachment=True)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5000)))
