var fs = require('fs');
var p = 'C:/Users/prana/.claude/projects/Z--ironlog/2a85ce97-c76c-43e8-962b-7c1764769b67/tool-results/mcp-Desktop_Commander-start_process-1777969533407.txt';
var d = JSON.parse(fs.readFileSync(p, 'utf8'));
var t = d.map(function(x) { return x.text; }).join('');
var lines = t.split('\n');
console.log(lines.slice(-10).join('\n'));
