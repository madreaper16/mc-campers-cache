const fs=require('node:fs'),path=require('node:path');
const launcher=path.join(process.env.APPDATA,'ATLauncher');
const version=JSON.parse(fs.readFileSync(path.join(launcher,'configs/json/minecraft/26.3.json'),'utf8'));
const libs=version.libraries.map(l=>l.downloads?.artifact?.path).filter(Boolean).map(p=>path.join(launcher,'libraries',p)).filter(p=>fs.existsSync(p));
const client=path.join(launcher,'libraries/net/minecraft/client/26.3/client-26.3.jar');
const dir=path.resolve(__dirname,'../testing');fs.mkdirSync(dir,{recursive:true});
const cp=[client,...libs].join(path.delimiter);
fs.writeFileSync(path.join(dir,'java.args'),['-Xmx2G','-Djava.awt.headless=true','-cp',JSON.stringify(cp)].join('\n'));
console.log('Prepared classpath with '+libs.length+' installed libraries.');
