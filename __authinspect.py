from pathlib import Path 
import sys 
path=Path(r'node_modules/expo-auth-session/build/AuthSession.d.ts') 
lines=path.read_text(encoding='utf-8', errors='ignore').splitlines() 
for i in range(1,240): sys.stdout.buffer.write((f'{i}: {lines[i-1]}\n').encode('utf-8')) 
