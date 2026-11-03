const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
function readNbt(b) {
  let o=0;
  const n=(k)=>{const v=b[k](o);o+=({readInt8:1,readInt16BE:2,readInt32BE:4,readBigInt64BE:8,readFloatBE:4,readDoubleBE:8})[k];return v;};
  const str=()=>{const l=b.readUInt16BE(o);o+=2;const s=b.toString('utf8',o,o+l);o+=l;return s;};
  function value(t){switch(t){
    case 1:return n('readInt8');case 2:return n('readInt16BE');case 3:return n('readInt32BE');case 4:return n('readBigInt64BE');case 5:return n('readFloatBE');case 6:return n('readDoubleBE');
    case 7:{const l=n('readInt32BE');const a=b.subarray(o,o+l);o+=l;return a;}
    case 8:return str();
    case 9:{const t=n('readInt8'),l=n('readInt32BE');return Array.from({length:l},()=>value(t));}
    case 10:{const a={};while(true){const t=n('readInt8');if(!t)return a;const k=str();a[k]=value(t);}}
    case 11:return Array.from({length:n('readInt32BE')},()=>n('readInt32BE'));
    case 12:return Array.from({length:n('readInt32BE')},()=>BigInt.asUintN(64,n('readBigInt64BE')));
    default:throw Error('Unknown NBT '+t);
  }}
  const type=n('readInt8');str();return value(type);
}
if(require.main===module){
const world=process.argv[2];
if(world==='--reference'){const b=fs.readFileSync(process.argv[3]);console.log(JSON.stringify(readNbt(zlib.gunzipSync(b)),(_,v)=>typeof v==='bigint'?v.toString():v,2));process.exit(0);}
const region=path.join(world,'dimensions/minecraft/overworld/region');
const ignored=new Set(['minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:grass_block','minecraft:dirt','minecraft:bedrock','minecraft:stone','minecraft:water','minecraft:deepslate','minecraft:granite','minecraft:diorite','minecraft:andesite','minecraft:tuff','minecraft:gravel','minecraft:lava']);
const blocks=[],entities=[];let chunks=0;const versions=new Set();
for(const file of fs.readdirSync(region).filter(x=>x.endsWith('.mca'))){
 const b=fs.readFileSync(path.join(region,file));
 for(let slot=0;slot<1024;slot++){
  const loc=b.readUInt32BE(slot*4),off=(loc>>>8)*4096;if(!off)continue;
  const len=b.readUInt32BE(off),compression=b[off+4];const raw=b.subarray(off+5,off+4+len);
  const c=readNbt(compression===2?zlib.inflateSync(raw):compression===1?zlib.gunzipSync(raw):raw);chunks++;versions.add(c.DataVersion);
  if(process.argv.includes('--sample')&&c.xPos===-1&&c.zPos===0){console.log(JSON.stringify({x:c.xPos,z:c.zPos,sections:(c.sections||[]).filter(s=>s.Y===2).map(s=>({y:s.Y,palette:s.block_states?.palette}))}));process.exit(0);}
  for(const s of c.sections||[]){const st=s.block_states;if(!st)continue;
   st.palette=st.palette.map(p=>{if(typeof p==='object'){if(p.Name)return p;if(p.id)return {Name:p.id,...(p.properties?{Properties:p.properties}:{})};p=p[''];}const [Name,props]=p.split('[');return {Name,...(props?{Properties:Object.fromEntries(props.replace(/\]$/,'').split(',').map(v=>v.split('=')))}:{})};});
   if(st.palette.every(x=>ignored.has(x.Name)))continue;
   const bits=Math.max(4,Math.ceil(Math.log2(st.palette.length))),per=Math.floor(64/bits),mask=(1n<<BigInt(bits))-1n;
   for(let i=0;i<4096;i++){
    const index=st.data?Number((st.data[Math.floor(i/per)]>>BigInt((i%per)*bits))&mask):0;
    const p=st.palette[index];if(!p)throw Error('Bad palette index');if(ignored.has(p.Name))continue;
    blocks.push({x:c.xPos*16+(i%16),y:s.Y*16+Math.floor(i/256),z:c.zPos*16+(Math.floor(i/16)%16),...p});
   }
  }
  entities.push(...(c.block_entities||[]));
 }
}
fs.mkdirSync(path.join(__dirname,'../design'),{recursive:true});
const out=path.join(__dirname,'../design/world-inspection.json');
fs.writeFileSync(out,JSON.stringify({versions:[...versions],chunks,blocks,blockEntities:entities},(_,v)=>typeof v==='bigint'?v.toString():v,2));
const counts={};for(const b of blocks)counts[b.Name]=(counts[b.Name]||0)+1;
console.log(JSON.stringify({versions:[...versions],chunks,blocks:blocks.length,counts,markers:blocks.filter(b=>/yellow_wool|pink_wool|chest|bed$|sign$/.test(b.Name)),blockEntities:entities},(_,v)=>typeof v==='bigint'?v.toString():v,2));
}
module.exports={readNbt};
