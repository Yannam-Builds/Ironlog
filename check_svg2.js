const fs = require('fs');

const files = [
    'C:/Users/prana/Downloads/Male_Front.svg',
    'C:/Users/prana/Downloads/Male_Back.svg',
    'C:/Users/prana/Downloads/Female_Front.svg',
    'C:/Users/prana/Downloads/Female_Back.svg'
];

let result = '';

files.forEach(filename => {
    try {
        const data = fs.readFileSync(filename, 'utf8');
        result += `--- ${filename} ---\n`;
        result += `Length: ${data.length}\n`;
        
        // Count tags
        const paths = data.match(/<path/g) || [];
        const polys = data.match(/<polygon/g) || [];
        const groups = data.match(/<g/g) || [];
        result += `Tags: paths=${paths.length}, polygons=${polys.length}, groups=${groups.length}\n`;
        
        // Print first 5 groups if any
        const gMatch = data.match(/<g[^>]*>/g);
        if (gMatch) {
            result += `Groups:\n${gMatch.slice(0, 5).join('\n')}\n`;
        }
        
        // Find ids
        const ids = data.match(/id="([^"]+)"/g) || [];
        result += `IDs (first 10): ${ids.slice(0, 10).join(', ')}\n`;
        
    } catch(e) {
        result += `Error: ${e.message}\n`;
    }
});

fs.writeFileSync('z:/ironlog/svg_analysis.txt', result);
