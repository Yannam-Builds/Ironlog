const fs=require('fs'); 
const txt=fs.readFileSync('src/data/exerciseLibrary.js','utf8').toLowerCase(); 
const key='tricep pushdown'; 
console.log(txt.indexOf(key)); 
