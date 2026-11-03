// Default loot tables for Minecraft 26.3.
const uniform = (min,max) => min===max ? min : {type:'minecraft:uniform',min,max};
const item = (name,min=1,max=min,weight=1) => ({type:'minecraft:item',name:'minecraft:'+name,weight,
 ...(min===1&&max===1 ? {} : {modifier:{type:'minecraft:set_count',count:uniform(min,max)}})});
const enchants = values => ({type:'minecraft:set_enchantments',enchantments:Object.fromEntries(
 Object.entries(values).map(([name,level])=>['minecraft:'+name,level]))});
const pool = (min,max,entries) => ({rolls:uniform(min,max),entries});
const gear = strong => {
 const protection=strong?3:2, unbreaking=strong?3:2, efficiency=strong?4:2;
 return [
  ['helmet',{protection,unbreaking}], ['chestplate',{protection,unbreaking}],
  ['leggings',{protection,unbreaking}], ['boots',{protection,unbreaking,feather_falling:strong?3:2}],
  ['pickaxe',{efficiency,unbreaking}], ['axe',{efficiency,unbreaking}],
  ['sword',{sharpness:strong?4:2,unbreaking}]
 ].map(([name,values])=>({...item('diamond_'+name),modifier:enchants(values)}));
};
const books = strong => Object.entries(strong ? {
 protection:4,unbreaking:3,efficiency:4,sharpness:4,fortune:3,looting:3,feather_falling:4,power:4,mending:1
} : {
 protection:3,unbreaking:2,efficiency:3,sharpness:3,fortune:2,looting:2,feather_falling:3,power:3,mending:1
}).map(([name,level])=>({...item('book',1,1,name==='mending'?1:3),modifier:enchants({[name]:level})}));
const valuables = strong => strong ? [item('iron_ingot',12,24,5),item('diamond',3,6,3),item('emerald',8,16,4),item('gold_ingot',6,12,2)]
 : [item('iron_ingot',6,12,5),item('diamond',1,3,2),item('emerald',3,8,4),item('gold_ingot',3,6,2)];
const food = strong => [item('cooked_beef',strong?8:4,strong?16:8),item('cooked_porkchop',strong?8:4,strong?16:8),
 item('cooked_chicken',strong?8:4,strong?16:8),item('baked_potato',strong?12:6,strong?24:12)];
const materials = strong => [item('torch',strong?24:12,strong?48:24,4),item('coal',strong?12:6,strong?24:12,3),
 item('oak_log',strong?16:8,strong?32:16,3),item('spruce_log',strong?16:8,strong?32:16,3),
 item('glass',strong?16:8,strong?32:16,2),item('cobblestone',strong?32:16,64,2),
 item('gunpowder',strong?8:4,strong?16:8,2),item('leather',strong?6:3,strong?12:6,2),
 item('string',strong?8:4,strong?16:8,2),item('arrow',strong?16:8,strong?32:16,2)];
const table = (name,pools) => ({type:'minecraft:chest',pools,random_sequence:'campers_cache:chests/'+name});
module.exports = {
 supply_shack:table('supply_shack',[
  pool(1,2,gear(false)),pool(1,2,books(false)),pool(3,4,valuables(false)),pool(1,2,food(false)),pool(2,4,materials(false))
 ]),
 // Roll rare rewards once, in one half of the double chest.
 hidden_storehouse:table('hidden_storehouse',[
  pool(3,4,gear(true)),pool(2,3,books(true)),pool(4,5,valuables(true)),pool(1,2,food(true)),pool(2,4,materials(true)),
  {...pool(1,1,[{...item('diamond_chestplate'),modifier:[enchants({unbreaking:3,mending:1,protection:5}),
   {type:'minecraft:set_name',name:{text:'God Armor',color:'gold',italic:false},target:'item_name'}]}]),
   condition:{type:'minecraft:random_chance',chance:0.015}},
  {...pool(1,1,[item('netherite_scrap',1,2)]),condition:{type:'minecraft:random_chance',chance:0.25}}
 ]),
 storehouse_supplies:table('storehouse_supplies',[
  pool(2,4,valuables(true)),pool(1,2,food(true)),pool(3,4,materials(true)),pool(1,1,[item('experience_bottle',8,16)])
 ])
};
