const fs=require('fs'); 
const x=fs.readFileSync('window_dump_current.xml','utf8'); 
const re=/text=\\\"([^\\\"]*)\\\"[^>]*clickable=\\\"([\\\"]*)\\\"[>]*bounds=\\\"\[([^\]]+)\]\[([^\]]+)\]\\\"/g; 
let m; 
