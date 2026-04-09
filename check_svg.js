const fs = require('fs');

const files = [
    'C:/Users/prana/Downloads/Male_Front.svg',
    'C:/Users/prana/Downloads/Male_Back.svg',
    'C:/Users/prana/Downloads/Female_Front.svg',
    'C:/Users/prana/Downloads/Female_Back.svg'
];

files.forEach(filename => {
    try {
        const data = fs.readFileSync(filename, 'utf8');
        console.log('---');
        console.log('File:', filename, 'Length:', data.length);
        const matches = data.match(/<path[^>]+>/g);
        if (!matches) {
            console.log('No paths found');
            return;
        }
        console.log('Paths count:', matches.length);
        matches.slice(0, 5).forEach(m => {
            const str = m.replace(/d="[^"]+"/, 'd="..."');
            console.log(str);
        });
    } catch(e) {
        console.log('Error reading', filename, e.message);
    }
});
