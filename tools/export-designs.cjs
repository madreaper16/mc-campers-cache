const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {readNbt}=require('./inspect-world.cjs');
const root=path.resolve(__dirname,'..'),world=process.argv[2];
const dimension=path.join(world,'dimensions/minecraft/overworld');
function chunks(dir){
 const out=[];for(const f of fs.readdirSync(dir).filter(f=>f.endsWith('.mca'))){const b=fs.readFileSync(path.join(dir,f));
 for(let i=0;i<1024;i++){const off=(b.readUInt32BE(i*4)>>>8)*4096;if(!off)continue;const raw=b.subarray(off+5,off+4+b.readUInt32BE(off));out.push(readNbt(b[off+4]===2?zlib.inflateSync(raw):b[off+4]===1?zlib.gunzipSync(raw):raw));}}
 return out;
}
const defs=[{name:'camp',min:[-7,37,-6],max:[1,42,8]},{name:'supply_shack',min:[-38,37,-8],max:[-29,44,5]},{name:'hidden_storehouse',min:[-90,37,-6],max:[-77,45,7]}];
const inside=(p,d)=>p.every((v,i)=>v>=d.min[i]&&v<=d.max[i]);
const relative=(p,d)=>p.map((v,i)=>v-d.min[i]);
const allEntities=chunks(path.join(dimension,'entities')).flatMap(c=>c.Entities||[]);
// NBT needs the original number and list types.
const tag=(type,value)=>({type,value}),int=v=>tag(3,v),byte=v=>tag(1,v),string=v=>tag(8,v),compound=v=>tag(10,v),list=(type,values)=>tag(9,{type,values}),ints=v=>list(3,v),intArray=v=>tag(11,v);
function text(s){const b=Buffer.from(s);const n=Buffer.alloc(2);n.writeUInt16BE(b.length);return Buffer.concat([n,b]);}
function payload(t,v){
 if(t===1)return Buffer.from([v&255]);if(t===3){const b=Buffer.alloc(4);b.writeInt32BE(v);return b;}if(t===6){const b=Buffer.alloc(8);b.writeDoubleBE(v);return b;}if(t===8)return text(v);
 if(t===9)return Buffer.concat([Buffer.from([v.type]),payload(3,v.values.length),...v.values.map(x=>payload(v.type,x))]);
 if(t===11)return Buffer.concat([payload(3,v.length),...v.map(x=>payload(3,x))]);
 if(t===10)return Buffer.concat([...Object.entries(v).map(([k,x])=>Buffer.concat([Buffer.from([x.type]),text(k),payload(x.type,x.value)])),Buffer.from([0])]);
 throw Error('Unsupported output NBT type '+t);
}
function blockEntity(e){
 const base={id:string(e.id),components:compound({})};
 if(e.id==='minecraft:chest')base.Items=list(10,[]);
 else if(e.id==='minecraft:campfire'){base.Items=list(10,[]);base.CookingTimes=intArray(e.CookingTimes);base.CookingTotalTimes=intArray(e.CookingTotalTimes);}
 else if(e.id==='minecraft:sign'){
  base.is_waxed=byte(e.is_waxed);for(const side of ['front_text','back_text'])base[side]=compound({color:string(e[side].color),messages:list(8,e[side].messages),has_glowing_text:byte(e[side].has_glowing_text)});
 }else throw Error('Review unsupported block entity '+e.id);
 return base;
}
const records=defs.map(d=>({...d,blocks:[],blockEntities:[],guardMarkers:[],oreMarkers:[],entities:allEntities.filter(e=>inside(e.Pos,d))}));
// Read only the chunks containing the builds.
for(const f of fs.readdirSync(path.join(dimension,'region')).filter(f=>f.endsWith('.mca'))){
 const [,rx,rz]=f.match(/^r\.(-?\d+)\.(-?\d+)\.mca$/).map(Number);
 if(!defs.some(d=>Math.floor(d.min[0]/512)<=rx&&Math.floor(d.max[0]/512)>=rx&&Math.floor(d.min[2]/512)<=rz&&Math.floor(d.max[2]/512)>=rz))continue;
 const b=fs.readFileSync(path.join(dimension,'region',f));
 for(let slot=0;slot<1024;slot++){
  const cx=rx*32+slot%32,cz=rz*32+Math.floor(slot/32);
  const targets=records.filter(d=>cx>=Math.floor(d.min[0]/16)&&cx<=Math.floor(d.max[0]/16)&&cz>=Math.floor(d.min[2]/16)&&cz<=Math.floor(d.max[2]/16));if(!targets.length)continue;
  const off=(b.readUInt32BE(slot*4)>>>8)*4096;if(!off)continue;const raw=b.subarray(off+5,off+4+b.readUInt32BE(off));const c=readNbt(zlib.inflateSync(raw));
  for(const d of targets){d.blockEntities.push(...(c.block_entities||[]).filter(e=>inside([e.x,e.y,e.z],d)));
   for(const s of c.sections||[]){if(s.Y*16>d.max[1]||s.Y*16+15<d.min[1])continue;const st=s.block_states;if(!st)continue;
    const palette=st.palette.map(p=>typeof p==='string'?{id:p}:p.id?p:{id:p['']});
    const bits=Math.max(4,Math.ceil(Math.log2(palette.length))),per=Math.floor(64/bits),mask=(1n<<BigInt(bits))-1n;
    for(let i=0;i<4096;i++){const pos=[cx*16+i%16,s.Y*16+Math.floor(i/256),cz*16+Math.floor(i/16)%16];if(!inside(pos,d))continue;
     const state=palette[st.data?Number((st.data[Math.floor(i/per)]>>BigInt(i%per*bits))&mask):0];
     if(state.id==='minecraft:yellow_wool')d.guardMarkers.push({world:pos,local:relative(pos,d)});
     if(state.id==='minecraft:pink_wool')d.oreMarkers.push({world:pos,local:relative(pos,d)});
     d.blocks.push({pos:relative(pos,d),state});
    }
   }
  }
 }
}
const outDir=path.join(root,'design/templates');fs.mkdirSync(outDir,{recursive:true});
const manifest=[];
for(const d of records){
 if(d.entities.length)throw Error('Review decorative entities before exporting '+d.name+': '+d.entities.map(e=>e.id));
 const palette=[],paletteMap=new Map();const blocks=d.blocks.map(b=>{
  // Keep ore markers for the configured ore rolls.
  // Ore types are chosen later, during world generation.
  const state=b.state.id==='minecraft:yellow_wool'?{id:'minecraft:air'}:b.state.id==='minecraft:pink_wool'?{id:'minecraft:deepslate'}:b.state;
  const key=JSON.stringify(state);if(!paletteMap.has(key)){paletteMap.set(key,palette.length);palette.push(state);}
  const p={pos:ints(b.pos),state:int(paletteMap.get(key))};const worldPos=b.pos.map((v,i)=>v+d.min[i]);
  const be=d.blockEntities.find(e=>e.x===worldPos[0]&&e.y===worldPos[1]&&e.z===worldPos[2]);if(be)p.nbt=compound(blockEntity(be));return p;
 });
 const size=d.max.map((v,i)=>v-d.min[i]+1);
 const rootTag={DataVersion:int(5023),size:ints(size),palette:list(10,palette.map(p=>({id:string(p.id),...(p.properties?{properties:compound(Object.fromEntries(Object.entries(p.properties).map(([k,v])=>[k,string(v)])))}:{})}))),blocks:list(10,blocks),entities:list(10,[])};
 const nbt=zlib.gzipSync(Buffer.concat([Buffer.from([10]),text(''),payload(10,rootTag)]));const dest=path.join(outDir,d.name+'.nbt');fs.writeFileSync(dest,nbt);
 const check=readNbt(zlib.gunzipSync(fs.readFileSync(dest)));if(check.blocks.length!==size.reduce((a,b)=>a*b,1)||check.palette.some(p=>['minecraft:yellow_wool','minecraft:pink_wool'].includes(p.id)))throw Error('Export validation failed');
 for(let i=0;i<d.blocks.length;i++){
  const original=d.blocks[i],saved=check.blocks[i],state=check.palette[saved.state];
  const expected=original.state.id==='minecraft:yellow_wool'?{id:'minecraft:air'}:original.state.id==='minecraft:pink_wool'?{id:'minecraft:deepslate'}:original.state;
  if(JSON.stringify(saved.pos)!==JSON.stringify(original.pos)||JSON.stringify(state)!==JSON.stringify(expected))throw Error('Export state mismatch in '+d.name);
 }
 manifest.push({name:d.name,sourceBounds:{min:d.min,max:d.max},size,guardMarkers:d.guardMarkers,oreMarkers:d.oreMarkers,...(d.oreMarkers.length?{oreConfig:'config/defaults.json',orePlaceholder:'minecraft:deepslate'}:{}),blockEntities:d.blockEntities.map(e=>({id:e.id,world:[e.x,e.y,e.z],local:relative([e.x,e.y,e.z],d)})),file:path.relative(root,dest),status:'Source template. The build adds entities, loot and ores.'});
}
fs.writeFileSync(path.join(root,'design/structures.json'),JSON.stringify(manifest,null,2));console.log(JSON.stringify(manifest.map(d=>({name:d.name,size:d.size,guards:d.guardMarkers.length,orePositions:d.oreMarkers.length,chestBlocks:d.blockEntities.filter(e=>e.id==='minecraft:chest').length})),null,2));
