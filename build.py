from pathlib import Path
import argparse,subprocess,zipfile,os,hashlib,json
p=argparse.ArgumentParser(description="Build unsigned standalone local ledger APK. Sign with your own Android signing key.")
p.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME','')))
p.add_argument('--build-tools',default='35.0.0');p.add_argument('--platform',default='android-35')
p.add_argument('--android-jar');p.add_argument('--aapt');p.add_argument('--d8-jar');a=p.parse_args()
root=Path(__file__).resolve().parent;sdk=Path(a.sdk);bt=sdk/'build-tools'/a.build_tools
jar=Path(a.android_jar) if a.android_jar else sdk/'platforms'/a.platform/'android.jar'
aapt=Path(a.aapt) if a.aapt else bt/('aapt.exe' if os.name=='nt' else 'aapt')
d8=Path(a.d8_jar) if a.d8_jar else bt/'lib/d8.jar'
for f in [jar,aapt,d8]:
 if not f.is_file():raise SystemExit('Missing Android SDK build input: '+str(f))
model=root/'assets/local-ledger/ppocr_rs.wasm';metadata=json.loads((root/'assets/local-ledger/ARTIFACT.json').read_text())
assert hashlib.sha256(model.read_bytes()).hexdigest()==metadata['sha256'],'OCR artifact checksum mismatch'
out=root/'build';out.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
def run(args):subprocess.run([str(x) for x in args],check=True,cwd=root)
run(['javac','-encoding','UTF-8','--release','8','-classpath',jar,'-d',classes,*sorted((root/'src').glob('*.java'))])
appjar=out/'app.jar';run(['jar','cf',appjar,'-C',classes,'.'])
run(['java','-cp',d8,'com.android.tools.r8.D8','--lib',jar,'--min-api','26','--output',dex,appjar])
apk=out/'local-ledger-unsigned.apk';run([aapt,'package','-f','-M',root/'AndroidManifest.xml','-S',root/'res','-A',root/'assets','-I',jar,'-F',apk])
with zipfile.ZipFile(apk,'a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
print('Unsigned APK:',apk);print('SHA256:',hashlib.sha256(apk.read_bytes()).hexdigest())
