from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent
out=root/'build/tests';out.mkdir(parents=True,exist_ok=True)
def run(args):subprocess.run([str(x) for x in args],cwd=root,check=True)
run(['javac','-encoding','UTF-8','--release','8','-d',out,*[root/'src'/n for n in ['BankRules.java','StatementImporter.java','MiExport.java']],*sorted((root/'tests').glob('*.java'))])
for name in ['TestBankRules','TestStatementImport','TestSyntheticFormats']:run(['java','-cp',out,name])
run(['node','assets/local-ledger/test-parser.mjs'])
run(['node','tests/test-multiple.mjs'])
print('PASS all public synthetic regression tests')
