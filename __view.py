import pathlib,sys 
sys.stdout.reconfigure(encoding='utf-8') 
p=pathlib.Path(r'Z:/ironlog/src/utils/intelligenceEngine.js') 
txt=p.read_text(encoding='utf-8').splitlines() 
start=357; end=392 
print('\\n'.join(f'{i}: {line}' for i,line in enumerate(txt[start-1:end], start))) 
