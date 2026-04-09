from pathlib import Path 
import sys 
path=Path(r'src/screens/HomeScreen.js') 
lines=path.read_text(encoding='utf-8', errors='ignore').splitlines() 
for i in range(100,320): sys.stdout.buffer.write((f'{i}: {lines[i-1]}\n').encode('utf-8')) 
