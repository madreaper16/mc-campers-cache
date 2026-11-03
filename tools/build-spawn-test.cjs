const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {readNbt}=require('./inspect-world.cjs');
const root=path.resolve(__dirname,'..');
const asMod=process.argv.includes('--mod');
const out=path.join(root,asMod?'build/generated/worldgen':'dist/campers-cache-spawn-test');
const structures=JSON.parse(fs.readFileSync(path.join(root,'design/structures.json'),'utf8'));
const weights=JSON.parse(fs.readFileSync(path.join(root,'config/defaults.json'),'utf8')).storehouseOres.weights;
const total=Object.values(weights).reduce((a,b)=>a+b,0);
if(!Number.isFinite(total)||total<=0||Object.values(weights).some(w=>!Number.isFinite(w)||w<0))throw Error('Invalid ore weights');
function file(p,body){const dest=path.join(out,p);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,typeof body==='string'||Buffer.isBuffer(body)?body:JSON.stringify(body,null,2)+'\n');}
if(asMod)for(const [name,table] of Object.entries(require('./loot-tables.cjs')))
 file('data/campers_cache/loot_table/chests/'+name+'.json',table);
// Keep the NBT number types needed by entities.
const T=(t,v)=>({t,v}),I=v=>T(3,v),B=v=>T(1,v),F=v=>T(5,v),D=v=>T(6,v),S=v=>T(8,v),C=v=>T(10,v),L=(t,v)=>T(9,{t,v});
function str(v){const b=Buffer.from(v),len=Buffer.alloc(2);len.writeUInt16BE(b.length);return Buffer.concat([len,b]);}
function payload(t,v){
 if(t===1)return Buffer.from([v&255]);
 if(t===3||t===5||t===6){const b=Buffer.alloc(t===6?8:4);b[t===3?'writeInt32BE':t===5?'writeFloatBE':'writeDoubleBE'](v);return b;}
 if(t===8)return str(v);
 if(t===9)return Buffer.concat([Buffer.from([v.t]),payload(3,v.v.length),...v.v.map(x=>payload(v.t,x))]);
 if(t===11)return Buffer.concat([payload(3,v.length),...v.map(x=>payload(3,x))]);
 if(t===10)return Buffer.concat([...Object.entries(v).map(([k,x])=>Buffer.concat([Buffer.from([x.t]),str(k),payload(x.t,x.v)])),Buffer.from([0])]);
 throw Error('Unsupported NBT type '+t);
}
function generic(v,key=''){
 if(typeof v==='string')return S(v);
 if(typeof v==='number')return ['is_waxed','has_glowing_text','keepPacked'].includes(key)?B(v):I(v);
 if(Array.isArray(v))return ['CookingTimes','CookingTotalTimes'].includes(key)?T(11,v):L(v.length?generic(v[0]).t:(key==='Items'?10:3),v.map(x=>generic(x).v));
 return C(Object.fromEntries(Object.entries(v).map(([k,x])=>[k,generic(x,k)])));
}
function entity(id,pos){
 const isCamper=id==='minecraft:villager';
 const nbt={id:S(id),Pos:L(6,pos),Motion:L(6,[0,0,0]),Rotation:L(5,[0,0]),PersistenceRequired:B(1),Tags:L(8,[isCamper?'campers_cache_test_camper':'campers_cache_test_guard']),Health:F(isCamper?20:24)};
 if(isCamper){nbt.VillagerData=C({type:S('minecraft:plains'),profession:S('minecraft:none'),level:I(1)});nbt.CustomName=S(asMod?'Camper':'Camper (test placeholder)');if(asMod)nbt.id=S('campers_cache:camper');}
 return {pos:L(6,pos),blockPos:L(3,pos.map(Math.floor)),nbt:C(nbt)};
}
if(!asMod)file('pack.mcmeta',{pack:{description:'Camper\u2019s Cache: spawn test v2 (generation fix)',min_format:121,max_format:121}});
const biomeValues=['minecraft:plains','minecraft:sunflower_plains','minecraft:forest','minecraft:flower_forest','minecraft:birch_forest','minecraft:old_growth_birch_forest','minecraft:dark_forest','minecraft:taiga','minecraft:old_growth_pine_taiga','minecraft:old_growth_spruce_taiga','minecraft:meadow','minecraft:cherry_grove','minecraft:grove','minecraft:windswept_hills','minecraft:windswept_forest','minecraft:stony_peaks','minecraft:jagged_peaks','minecraft:frozen_peaks'];
file('data/campers_cache/tags/worldgen/biome/test_sites.json',{replace:false,values:biomeValues});
if(asMod)file('data/campers_cache/tags/worldgen/biome/cache_sites.json',{replace:false,values:[...biomeValues,
 'minecraft:desert','minecraft:jungle','minecraft:sparse_jungle','minecraft:bamboo_jungle','minecraft:savanna',
 'minecraft:savanna_plateau','minecraft:windswept_savanna','minecraft:badlands','minecraft:wooded_badlands',
 'minecraft:eroded_badlands','minecraft:snowy_plains','minecraft:ice_spikes','minecraft:snowy_taiga',
 'minecraft:snowy_slopes','minecraft:stony_shore','minecraft:beach','minecraft:snowy_beach','minecraft:pale_garden']});
if(asMod){
 const cacheBiomes=JSON.parse(fs.readFileSync(path.join(out,'data/campers_cache/tags/worldgen/biome/cache_sites.json'),'utf8')).values;
 file('assets/campers_cache/config_defaults.json',require('./config-defaults.cjs')(biomeValues,cacheBiomes));
 // The config decides which surface biomes are allowed.
 file('data/campers_cache/tags/worldgen/biome/all_sites.json',{replace:false,values:[...new Set([...cacheBiomes,'minecraft:swamp','minecraft:mangrove_swamp','minecraft:mushroom_fields','minecraft:ocean','minecraft:deep_ocean','minecraft:river','minecraft:frozen_river','minecraft:warm_ocean','minecraft:lukewarm_ocean','minecraft:deep_lukewarm_ocean','minecraft:cold_ocean','minecraft:deep_cold_ocean','minecraft:frozen_ocean','minecraft:deep_frozen_ocean'])]});
}
const rules=[];let remaining=total;
for(const [id,weight] of Object.entries(weights).filter(([,w])=>w>0)){
 rules.push({input_predicate:weight===remaining?{predicate_type:'minecraft:block_match',block:'minecraft:pink_wool'}:{predicate_type:'minecraft:random_block_match',block:'minecraft:pink_wool',probability:weight/remaining},location_predicate:{predicate_type:'minecraft:always_true'},output_state:id});remaining-=weight;
}
file('data/campers_cache/worldgen/processor_list/storehouse_ores.json',{processors:asMod?[{processor_type:'campers_cache:ores'}]:[{processor_type:'minecraft:rule',rules}]});
for(let index=0;index<structures.length;index++){
 const d=structures[index],template=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(root,d.file))));
 const pink=template.palette.length;template.palette.push({id:'minecraft:pink_wool'});
 if(asMod&&d.name!=='camp'){
  const chests=template.blocks.filter(b=>template.palette[b.state].id==='minecraft:chest');
  if(chests.length!==(d.name==='hidden_storehouse'?2:1))throw Error('Unexpected cache chest count: '+d.name);
  chests.forEach((b,i)=>{b.nbt={id:'minecraft:chest',components:{},LootTable:'campers_cache:chests/'+(i===0?d.name:'storehouse_supplies')};});
 }
 for(const marker of d.oreMarkers){const block=template.blocks.find(b=>b.pos.every((v,i)=>v===marker.local[i]));if(!block)throw Error('Missing ore marker');block.state=pink;}
 const tree=generic(template).v;
 if(asMod&&d.name==='hidden_storehouse'){
  // Keep natural terrain around the building and above its roof.
  const addState=state=>{const index=template.palette.findIndex(p=>JSON.stringify(p)===JSON.stringify(state));if(index>=0)return index;template.palette.push(state);return template.palette.length-1;};
  const voidState=addState({id:'minecraft:structure_void'});
  const [sx,sy,sz]=template.size;
  for(const b of template.blocks){const [x,y,z]=b.pos;if(template.palette[b.state].id==='minecraft:air'&&(x===0||x===sx-1||z===0||z===sz-1||y===sy-1))b.state=voidState;}
  tree.size=L(3,template.size);tree.blocks=generic(template.blocks);
  tree.palette=generic(template.palette);
 }
 const entities=d.guardMarkers.map(m=>entity('minecraft:pillager',[m.local[0]+0.5,m.local[1],m.local[2]+0.5]));
 if(d.name==='camp')entities.push(entity('minecraft:villager',[5.5,1,8.5]));
 if(asMod)for(const e of entities)e.nbt.v.CampersCacheSite=S(d.name);
 tree.entities=L(10,entities);
 const encoded=zlib.gzipSync(Buffer.concat([Buffer.from([10]),str(''),payload(10,tree)]));
 const decoded=readNbt(zlib.gunzipSync(encoded));if(decoded.entities.length!==entities.length)throw Error('Entity export mismatch');
 file('data/campers_cache/structure/'+d.name+'.nbt',encoded);
 file('data/campers_cache/worldgen/template_pool/'+d.name+'.json',{fallback:'minecraft:empty',elements:[{weight:1,element:{element_type:'minecraft:single_pool_element',location:'campers_cache:'+d.name,processors:d.oreMarkers.length?'campers_cache:storehouse_ores':{processors:[]},projection:'rigid'}}]});
 file('data/campers_cache/worldgen/structure/'+d.name+'.json',asMod?
  {type:'campers_cache:linked_site',site:d.name,biomes:'#campers_cache:all_sites',step:'surface_structures',spawn_overrides:{},terrain_adaptation:d.name==='hidden_storehouse'?'none':'beard_thin'}:
  {type:'minecraft:jigsaw',biomes:'#campers_cache:test_sites',step:'surface_structures',spawn_overrides:{},terrain_adaptation:'beard_thin',start_pool:'campers_cache:'+d.name,size:1,start_height:{absolute:0},project_start_to_heightmap:'WORLD_SURFACE_WG',max_distance_from_center:32,use_expansion_hack:false});
 file('data/campers_cache/worldgen/structure_set/'+d.name+'.json',{structures:[{structure:'campers_cache:'+d.name,weight:1}],placement:asMod?{type:'campers_cache:linked_site',site:d.name}:{type:'minecraft:random_spread',spacing:12,separation:5,salt:3918200+index}});
}
const testCommands=[
 'scoreboard objectives add cc_test dummy',
 `tellraw @s {"text":"${asMod?"Camper's Cache alpha":"Spawn test v2"}: placing three sites east of you. Use a fresh test world.","color":"yellow"}`
];
for(const [name,offset] of [['camp',24],['supply_shack',56],['hidden_storehouse',96]]){
 testCommands.push(`execute store success score @s cc_test run execute positioned ~${offset} ~ ~ run place structure campers_cache:${name} ~ ~ ~`);
 testCommands.push(`execute if score @s cc_test matches 1 run tellraw @s {"text":"PASS: ${name} placed near ${offset} blocks east.","color":"green"}`);
 testCommands.push(`execute unless score @s cc_test matches 1 run tellraw @s {"text":"FAILED: ${name}. Run /place structure campers_cache:${name} directly to see its error.","color":"red"}`);
}
testCommands.push(`tellraw @s {"text":"Successful sites should contain 1 ${asMod?'Camper':'camp villager'}, 4 shack guards, 8 storehouse guards and 52 ore blocks. ${asMod?'All three sites contain loot.':'Chests are empty.'}","color":"gray"}`);
file('data/campers_cache/function/test_all.mcfunction',testCommands.join('\n')+'\n');
if(asMod){
 // Model previews do not use natural spacing.
 file('data/campers_cache/function/test_models.mcfunction',testCommands.join('\n')+'\n');
 file('data/campers_cache/function/test_all.mcfunction','tellraw @s {"text":"Spacing test: locate a natural camp first, then run /camperscache layout there for its two distant destinations. Compact model previews are available separately through /function campers_cache:test_models.","color":"yellow"}\n');
}
file('data/campers_cache/function/info.mcfunction','tellraw @s {"text":"Camper\u2019s Cache spawn test loaded. Use /function campers_cache:test_all in a fresh Creative test world, or /locate structure campers_cache:camp.","color":"green"}\n');
if(!asMod){file('README.txt','Camper\u2019s Cache 26.3 spawn test. Add this datapack when creating a NEW Creative world with cheats. Use Normal difficulty for pillagers. Run /function campers_cache:test_all in an open area, then inspect the three sites east of you. Also test /locate structure campers_cache:camp, supply_shack, hidden_storehouse. Natural generation has accelerated TEST spacing, not release rarity. No custom Camper behavior, maps, loot, theft, config screen, biome weighting or 1000-block map pairing yet. Ore weights are baked from config/defaults.json when this pack is built. Use /place structure, not /place template, to apply the ore processor.\n');
fs.writeFileSync(path.join(root,'testing/test-pack-summary.json'),JSON.stringify({minecraft:'26.3',structures:structures.map(d=>({name:d.name,guards:d.guardMarkers.length,ores:d.oreMarkers.length})),oreWeights:weights},null,2));}
console.log('Built '+(asMod?'mod worldgen resources':'spawn test datapack')+': '+out);
