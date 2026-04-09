const fs=require('fs'); 
const path=require('path'); 
function walk(d){for(const e of fs.readdirSync(d,{withFileTypes:true})){const p=path.join(d,e.name); if(e.isDirectory()) walk(p); else if(e.name.endsWith('.js')){const t=fs.readFileSync(p,'utf8').toLowerCase(); if(t.includes('haptic')) console.log(p);}}} 
walk('src');
