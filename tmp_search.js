const fs=require('fs'); 
const txt=fs.readFileSync('src/data/exerciseLibrary.js','utf8'); 
const i=txt.toLowerCase().indexOf('barbell bench press'); 
console.log('idx',i); 
console.log(txt.slice(i-300,i+500)); 
