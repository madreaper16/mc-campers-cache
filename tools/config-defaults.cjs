const tables=require('./loot-tables.cjs');
const item=(id,min,max,weight=1,enchantments=[])=>({enabled:true,item:'minecraft:'+id,min,max,weight,chance:1,enchantments});
const enchant=(id,level)=>({id:'minecraft:'+id,min:level,max:level,chance:1});
const pool=(name,min,max,entries,chance=1)=>({name,enabled:true,min,max,chance,entries});
module.exports=(campBiomes,cacheBiomes)=>{
 const loot={};
 for(const [name,table] of Object.entries(tables)){
  loot[name]={enabled:true,pools:table.pools.filter(p=>!p.entries.some(e=>e.modifier?.some?.(m=>m.type==='minecraft:set_name'))).map((p,i)=>
   pool(['Gear','Enchanted Books','Valuables','Cooked Food','Materials','Bonus Supplies','Rare Supplies'][i]||'Supplies',p.rolls.min??p.rolls,p.rolls.max??p.rolls,p.entries.map(e=>{
    const modifiers=Array.isArray(e.modifier)?e.modifier:[e.modifier].filter(Boolean);
    const count=modifiers.find(m=>m.type==='minecraft:set_count')?.count??1;
    const values=modifiers.find(m=>m.type==='minecraft:set_enchantments')?.enchantments??{};
    return item(e.name.slice(10),count.min??count,count.max??count,e.weight??1,Object.entries(values).map(([id,level])=>enchant(id.slice(10),level)));
   }),p.condition?.chance??1))};
 }
 loot.storehouse_supplies.pools.forEach((p,i)=>p.name=['Extra Valuables','Extra Food','Extra Materials','Experience Bottles'][i]);
 const gear=['helmet','chestplate','leggings','boots','pickaxe','axe'].map(type=>item('diamond_'+type,1,1,1,[enchant('unbreaking',2),enchant('vanishing_curse',1)]));
 loot.camp={enabled:true,pools:[pool('Gunpowder',1,1,[item('gunpowder',6,16)]),pool('Gold Nuggets',1,1,[item('gold_nugget',8,24)]),pool('Emeralds',1,1,[item('emerald',2,6)]),pool('Bones',1,1,[item('bone',3,12)]),pool('Saddle',1,1,[item('saddle',1,1)],1/3),pool('Gear',1,1,gear)]};
 return {version:2,preset:'Generous',basis:'Generous',enabled:true,camperEnabled:true,guardsEnabled:true,oresEnabled:true,camperRadius:20,
  regionChunks:192,destinationDistance:944,familyChance:1,shackGuards:4,storehouseGuards:8,
  godArmorEnabled:true,godArmorChance:0.015,sites:Object.fromEntries(['camp','supply_shack','hidden_storehouse'].map(name=>[name,{enabled:true,biomes:name==='camp'?campBiomes:cacheBiomes,biomeChances:{}}])),
  storehouseOreWeights:{iron:14,coal:13,copper:13,diamond:30,emerald:30},loot};
};
