import {createOcr} from './ppocr.mjs';
let model;
const ready=createOcr({precision:'int8',wasmUrl:new URL('./ppocr_rs.wasm',import.meta.url)}).then(m=>{model=m;postMessage({type:'ready',info:m.info});}).catch(e=>{postMessage({type:'fatal',error:String(e.message)});throw e;});
self.onmessage=async ({data})=>{try{await ready;postMessage({id:data.id,result:model.ocr(data.bytes)});}catch(e){postMessage({id:data.id,error:String(e.message)});}};
