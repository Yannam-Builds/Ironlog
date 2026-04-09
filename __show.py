from pathlib import Path 
import sys 
for p in ['App.js','src/navigation/index.js','src/screens/StatsScreen.js','src/screens/VolumeAnalyticsScreen.js','src/screens/BodyWeightScreen.js','src/screens/CreateExerciseScreen.js']: 
  path=Path(p); print('---',p) 
  lines=path.read_text(encoding='utf-8', errors='ignore').splitlines() 
  for i,line in enumerate(lines[:220],1): sys.stdout.buffer.write((f'{i}: {line}\n').encode('utf-8')) 
