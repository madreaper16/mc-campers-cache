const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {readNbt}=require('./inspect-world.cjs');
const world=process.argv[2],x=Number(process.argv[3]),z=Number(process.argv[4]);
function readChunk(cx,cz){
 const file=path.join(world,'dimensions/minecraft/overworld/region',`r.${Math.floor(cx/32)}.${Math.floor(cz/32)}.mca`);
 if(!fs.existsSync(file))return null;
 const b=fs.readFileSync(file),i=((cz%32+32)%32)*32+(cx%32+32)%32,o=(b.readUInt32BE(i*4)>>>8)*4096;
 if(!o)return null;const raw=b.subarray(o+5,o+4+b.readUInt32BE(o));return readNbt(b[o+4]===2?zlib.inflateSync(raw):raw);
}
const c=readChunk(Math.floor(x/16),Math.floor(z/16));
console.log(JSON.stringify({keys:Object.keys(c),structures:c.structures},(_,v)=>typeof v==='bigint'?v.toString():v,2));
const gen=path.join(world,'data/minecraft/world_gen_settings.dat');
if(fs.existsSync(gen)){const b=fs.readFileSync(gen);console.log('WORLDGEN',JSON.stringify(readNbt(zlib.gunzipSync(b)),(_,v)=>typeof v==='bigint'?v.toString():v));}
