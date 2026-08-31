// Explicit maintenance command, never run by CI/build. Original licensed files
// only; no backup/user data is read. All sources are pinned to this revision.
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
const revision = 'ade3d1533e06b2b1462ffcde8e08b129627ca360';
const root = resolve(import.meta.dirname, '..');
const native = process.argv[2] ? resolve(process.argv[2]) : undefined;
const families = [
  ['inter','Inter','Inter[opsz,wght].ttf'], ['manrope','Manrope','Manrope[wght].ttf'],
  ['dmsans','DM Sans','DMSans[opsz,wght].ttf'], ['plusjakartasans','Plus Jakarta Sans','PlusJakartaSans[wght].ttf'],
  ['outfit','Outfit','Outfit[wght].ttf'], ['sora','Sora','Sora[wght].ttf'],
  ['urbanist','Urbanist','Urbanist[wght].ttf'], ['nunito','Nunito','Nunito[wght].ttf'],
  ['nunitosans','Nunito Sans','NunitoSans[YTLC,opsz,wdth,wght].ttf'], ['rubik','Rubik','Rubik[wght].ttf'],
  ['worksans','Work Sans','WorkSans[wght].ttf'], ['publicsans','Public Sans','PublicSans[wght].ttf'],
  ['figtree','Figtree','Figtree[wght].ttf'], ['assistant','Assistant','Assistant[wght].ttf'],
  ['mulish','Mulish','Mulish[wght].ttf'], ['quicksand','Quicksand','Quicksand[wght].ttf'],
  ['raleway','Raleway','Raleway[wght].ttf'], ['montserrat','Montserrat','Montserrat[wght].ttf'],
  ['exo2','Exo 2','Exo2[wght].ttf'], ['sourcesans3','Source Sans 3','SourceSans3[wght].ttf'],
];
const sha = b => createHash('sha256').update(b).digest('hex');
function weightRange(bytes) {
  if (bytes.readUInt32BE(0) !== 0x10000) throw Error('Not an upright TrueType font');
  for(let i=0;i<bytes.readUInt16BE(4);i++) {
    const entry=12+i*16;
    if(bytes.toString('ascii',entry,entry+4)!=='fvar') continue;
    const start=bytes.readUInt32BE(entry+8);
    const offset=bytes.readUInt16BE(start+4), count=bytes.readUInt16BE(start+8), size=bytes.readUInt16BE(start+10);
    for(let n=0;n<count;n++) {
      const axis=start+offset+n*size;
      if(bytes.toString('ascii',axis,axis+4)==='wght') return [bytes.readInt32BE(axis+4)/65536,bytes.readInt32BE(axis+12)/65536];
    }
  }
  throw Error('Missing variable weight axis');
}
async function download(id,file) {
  const url=`https://raw.githubusercontent.com/google/fonts/${revision}/ofl/${id}/${encodeURIComponent(file)}`;
  const response=await fetch(url);
  if(!response.ok) throw Error(`${response.status}: ${url}`);
  return {url, bytes:Buffer.from(await response.arrayBuffer())};
}
await mkdir(resolve(root,'public/fonts'),{recursive:true});
await mkdir(resolve(root,'public/licenses/fonts'),{recursive:true});
if(native) {
  await mkdir(resolve(native,'app/src/main/res/font'),{recursive:true});
  await mkdir(resolve(native,'app/src/main/assets/font_licenses'),{recursive:true});
}
const rows=[];
for(const [id,name,file] of families) {
  const [{url,bytes},license]=await Promise.all([download(id,file),download(id,'OFL.txt')]);
  if(!license.bytes.toString().includes('SIL OPEN FONT LICENSE')) throw Error(`Unrecognized license: ${id}`);
  const [minWeight,maxWeight]=weightRange(bytes);
  await writeFile(resolve(root,`public/fonts/${id}_variable.ttf`),bytes);
  await writeFile(resolve(root,`public/licenses/fonts/${id}.txt`),license.bytes);
  if(native) {
    await writeFile(resolve(native,`app/src/main/res/font/${id}_variable.ttf`),bytes);
    await writeFile(resolve(native,`app/src/main/assets/font_licenses/${id}.txt`),license.bytes);
  }
  rows.push({id,name,file:`fonts/${id}_variable.ttf`,minWeight,maxWeight,bytes:bytes.length,sha256:sha(bytes),source:url,license:`licenses/fonts/${id}.txt`,licenseSha256:sha(license.bytes)});
  console.log(`${name}: ${minWeight}–${maxWeight}, ${bytes.length} bytes`);
}
const lexend=await readFile(resolve(root,'public/assets/lexend_variable.ttf'));
if(native) await writeFile(resolve(native,'app/src/main/assets/font_licenses/lexend.txt'),await readFile(resolve(root,'public/licenses/Lexend-OFL.txt')));
const [minWeight,maxWeight]=weightRange(lexend);
rows.unshift({id:'lexend',name:'Lexend',file:'assets/lexend_variable.ttf',minWeight,maxWeight,bytes:lexend.length,sha256:sha(lexend),source:'Existing native res/font/lexend_variable.ttf; see native-provenance.json',license:'licenses/Lexend-OFL.txt'});
await writeFile(resolve(root,'src/generated/fonts.json'),JSON.stringify({revision,fonts:rows},null,2)+'\n');
console.log(`21 families; ${(rows.reduce((n,f)=>n+f.bytes,0)/1024/1024).toFixed(2)} MiB total, unmodified TTFs.`);
