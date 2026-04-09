const fs = require('fs');

const parseFile = (file) => {
  const content = fs.readFileSync(file, 'utf8');
  let result = {};
  
  // Custom parsing without regex to avoid nested shell string issues
  const parts = content.split('BodyPartPathData(');
  for (let i = 1; i < parts.length; i++) {
    const part = parts[i];
    const slugMatch = part.match(/slug:\s*\.([a-zA-Z0-9_]+)/);
    if (!slugMatch) continue;
    
    const slug = slugMatch[1];
    
    const leftMatch = part.match(/left:\s*\[([^\]]+)\]/);
    const rightMatch = part.match(/right:\s*\[([^\]]+)\]/);
    
    const getStrings = (str) => {
      if (!str) return [];
      const res = [];
      const re = /"([^"]+)"/g;
      let m;
      while ((m = re.exec(str))) res.push(m[1]);
      return res;
    };
    
    result[slug] = {
      left: getStrings(leftMatch ? leftMatch[1] : ''),
      right: getStrings(rightMatch ? rightMatch[1] : '')
    };
  }
  return result;
};

const front = parseFile('C:/Users/prana/Downloads/MuscleMap/Sources/MuscleMap/Data/MaleFrontPaths.swift');
const back = parseFile('C:/Users/prana/Downloads/MuscleMap/Sources/MuscleMap/Data/MaleBackPaths.swift');

fs.writeFileSync('z:/ironlog/svg_out.json', JSON.stringify({front, back}, null, 2));
console.log('Parsed SVGs:', Object.keys(front).length, 'front,', Object.keys(back).length, 'back regions.');
