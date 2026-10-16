function moodPrepare(row, signature, config) {
 var processed=load(957).preprocess(row), input=processed.input;
 config=config||{};
 var overrides=config.emotionTagOverride||{};
 Object.keys(overrides).some(function(label){
   if(Array.isArray(overrides[label])&&overrides[label].indexOf(row.song_id)>=0&&load(945).isEmotionLabel(label)){
     input.emotion_label=label;input.emotion_idx=load(945).EMOTION_INDEX[label];input.emotion_source='config_override';return true;
   }return false;
 });
 var force=Array.isArray(config.defaultTemplateSongIds)&&config.defaultTemplateSongIds.indexOf(row.song_id)>=0;
 var template=load(872).resolveModel64Template(force?'default':input.emotion_label,input.family);
 var payload=Object.assign({},input,{module_switches:template.moduleSwitches,base_extra_aev2:load(939).buildBaseExtraAev2FromTemplate(template.effectParams)});
 var canInfer=processed.availability.hasAllAudioFeatures&&!processed.fallbackReason&&!force;
 return {input:input,template:template,tensors:canInfer?load(953).encodeEmoFX(payload,signature):[],infer:!!canInfer,
    signature:signature,config:config,source:canInfer?'model':'preset_default',fallbackReason:processed.fallbackReason||(!canInfer?'missing audio features':undefined)};
}
function moodFinish(prepared, output) {
 var fx=load(939), input=prepared.input, params;
 if(prepared.infer){
  var decoded=load(952).decodeEmoFX([{name:'aev2',dtype:'float32',shape:[1,32],data:output}],prepared.signature,prepared.template.moduleSwitches);
  params=fx.mergeDecodedAev2IntoTemplate(prepared.template.effectParams,decoded);
 } else params=fx.prepareEffectTemplate(prepared.template.effectParams);
 params=fx.applySshaperRule(params,input.chorus_loudness).params;
 if(prepared.config.supportStereo===true)params=fx.applyMultichannelPostProcess(params,input.emotion_idx);
 if(Array.isArray(prepared.config.degradeSongIds)&&prepared.config.degradeSongIds.indexOf(prepared.config.songId)>=0)params=fx.applyDegradation(params);
 var visual=load(963).getVisualExtraStrategy().compute(input,params,{debug:function(){}});
 return {aev2:params,source:prepared.source,emotion:input.emotion_label,family:input.family,fallbackReason:prepared.fallbackReason,visualExtra:visual};
}
if(typeof module!=='undefined')module.exports={load:load,moodPrepare:moodPrepare,moodFinish:moodFinish};
