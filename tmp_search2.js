const fs=require('fs'); 
const txt=fs.readFileSync('src/data/exerciseLibrary.js','utf8'); 
['Barbell Squat','Tricep Pushdown','Overhead Press','Deadlift'].forEach(k= i=txt.toLowerCase().indexOf(k.toLowerCase());console.log(k,i); if(i console.log(txt.slice(i-120,i+220));}); 
