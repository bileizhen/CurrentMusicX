if(!Object.entries)Object.entries=function(o){return Object.keys(o).map(function(k){return [k,o[k]];});};
var NeteaseAiMaster = (function() {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = function(target, all) {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = function(to, from, except, desc) {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: function() {
            return from[key];
          }, enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = function(mod) {
    return __copyProps(__defProp({}, "__esModule", { value: true }), mod);
  };

  // .verification/sound-effects/ai-master/index.ts
  var index_exports = {};
  __export(index_exports, {
    composeEffect: function() {
      return composeEffect;
    }
  });

  // .verification/sound-effects/ai-master/logger-adapter.ts
  var logger = { info() {
  }, warn() {
  }, error() {
  }, stepStart() {
  }, stepEnd() {
  }, logRule() {
  }, logClosingRule() {
  }, logMergeRule() {
  } };

  // .verification/sound-effects/ai-master/validator.ts
  function parseJsonOrObject(data, typeName) {
    try {
      if (typeof data === "string") {
        var parsed = JSON.parse(data);
        if (!parsed || typeof parsed !== "object") {
          logger.error(`${typeName} \u89E3\u6790\u5931\u8D25\uFF1A\u4E0D\u662F\u6709\u6548\u7684\u5BF9\u8C61`, { data });
          return null;
        }
        return parsed;
      } else if (typeof data === "object" && data !== null) {
        return data;
      } else {
        logger.error(`${typeName} \u7C7B\u578B\u9519\u8BEF\uFF1A\u5FC5\u987B\u662F\u5B57\u7B26\u4E32\u6216\u5BF9\u8C61`, { data });
        return null;
      }
    } catch (error) {
      logger.error(`${typeName} \u89E3\u6790\u5931\u8D25\uFF1A\u65E0\u6548\u7684 JSON \u683C\u5F0F`, { data, error });
      return null;
    }
  }
  function validateInputs(params) {
    var errors = [];
    if (!params) {
      errors.push("\u8F93\u5165 ComposeEffectDataParams \u7C7B\u578B\u7684\u53C2\u6570\u4E0D\u80FD\u4E3A\u7A7A");
      logger.warn("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", { errors });
      return {
        isValid: false,
        errors
      };
    }
    if (!params.baseJson && !params.songJson) {
      errors.push("baseJson \u548C songJson \u81F3\u5C11\u9700\u8981\u4E00\u4E2A");
      logger.warn("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", { errors });
      return {
        isValid: false,
        errors
      };
    }
    if (params.songJson && params.diyJson) {
      var songEffect = parseJsonOrObject(params.songJson, "songJson");
      var diyEffect = parseJsonOrObject(params.diyJson, "diyJson");
      if (!songEffect) {
        errors.push("songJson \u683C\u5F0F\u4E0D\u6B63\u786E");
      }
      if (!diyEffect) {
        errors.push("diyJson \u683C\u5F0F\u4E0D\u6B63\u786E");
      }
      if (errors.length === 0) {
        logger.info("\u8F93\u5165\u9A8C\u8BC1\u901A\u8FC7");
      } else {
        logger.warn("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", { errors });
      }
      return {
        isValid: errors.length === 0,
        errors
      };
    }
    if (params.baseJson && params.diyJson) {
      var baseEffect = parseJsonOrObject(params.baseJson, "baseJson");
      var diyEffect = parseJsonOrObject(params.diyJson, "diyJson");
      if (!baseEffect) {
        errors.push("baseJson \u683C\u5F0F\u4E0D\u6B63\u786E");
      }
      if (!diyEffect) {
        errors.push("diyJson \u683C\u5F0F\u4E0D\u6B63\u786E");
      }
      if (errors.length === 0) {
        logger.info("\u8F93\u5165\u9A8C\u8BC1\u901A\u8FC7");
      } else {
        logger.warn("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", { errors });
      }
      return {
        isValid: errors.length === 0,
        errors
      };
    }
    if (params.supportStereo !== void 0 && params.supportStereo !== null) {
      if (typeof params.supportStereo !== "boolean") {
        errors.push("supportStereo \u5FC5\u987B\u662F\u5E03\u5C14\u7C7B\u578B");
      }
      if (errors.length === 0) {
        logger.info("\u8F93\u5165\u9A8C\u8BC1\u901A\u8FC7");
      } else {
        logger.warn("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", { errors });
      }
      return {
        isValid: errors.length === 0,
        errors
      };
    }
    if (errors.length === 0) {
      logger.info("\u8F93\u5165\u9A8C\u8BC1\u901A\u8FC7");
    } else {
      logger.warn("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", { errors });
    }
    return {
      isValid: errors.length === 0,
      errors
    };
  }

  // .verification/sound-effects/ai-master/closingRules.ts
  function processClosingRules(input) {
    logger.stepStart("\u5173\u95ED\u89C4\u5219\u5904\u7406", "\u6839\u636E\u89C4\u52191\u5904\u7406 diyJson \u7684\u5173\u95ED\u903B\u8F91");
    var { baseJson, songJson, diyJson, supportStereo } = input;
    var filteredDiyJson = {};
    var closedKeys = [];
    if (supportStereo) {
      logger.logClosingRule("\u5168\u90E8 Diy \u97F3\u6548", true, "supportStereo \u4E3A true\uFF0C\u5173\u95ED\u6240\u6709 diy \u97F3\u6548");
      logger.stepEnd("\u5173\u95ED\u89C4\u5219\u5904\u7406", {
        filteredDiyJson: {},
        closedKeys: Object.keys(diyJson),
        reason: "supportStereo=true"
      });
      return {
        baseJson,
        songJson,
        filteredDiyJson: {},
        closedKeys: Object.keys(diyJson)
      };
    }
    var sourceData = songJson || baseJson;
    var isSongMode = !!songJson;
    Object.entries(diyJson).forEach(function([key, value]) {
      var effectKey = key;
      switch (effectKey) {
        case "en_bass":
          if (shouldCloseBass(sourceData, isSongMode)) {
            closedKeys.push(key);
            logger.logClosingRule(key, true, "\u547D\u4E2D\u4F4E\u9891\u589E\u5F3A\u5173\u95ED\u6761\u4EF6");
          } else {
            filteredDiyJson[effectKey] = value;
            logger.logClosingRule(key, false, "\u672A\u547D\u4E2D\u4F4E\u9891\u589E\u5F3A\u5173\u95ED\u6761\u4EF6");
          }
          break;
        case "en_treble":
          if (shouldCloseTreble(sourceData, isSongMode)) {
            closedKeys.push(key);
            logger.logClosingRule(key, true, "\u547D\u4E2D\u9AD8\u9891\u589E\u5F3A\u5173\u95ED\u6761\u4EF6");
          } else {
            filteredDiyJson[effectKey] = value;
            logger.logClosingRule(key, false, "\u672A\u547D\u4E2D\u9AD8\u9891\u589E\u5F3A\u5173\u95ED\u6761\u4EF6");
          }
          break;
        case "en_vocal":
          if (shouldCloseVocal(sourceData, isSongMode)) {
            closedKeys.push(key);
            logger.logClosingRule(key, true, "\u547D\u4E2D\u4EBA\u58F0\u589E\u5F3A\u5173\u95ED\u6761\u4EF6");
          } else {
            filteredDiyJson[effectKey] = value;
            logger.logClosingRule(key, false, "\u672A\u547D\u4E2D\u4EBA\u58F0\u589E\u5F3A\u5173\u95ED\u6761\u4EF6");
          }
          break;
        case "en_vibe":
        case "en_live":
          break;
        default:
          filteredDiyJson[effectKey] = value;
          logger.logClosingRule(key, false, "\u672A\u77E5\u97F3\u6548\u7C7B\u578B\uFF0C\u9ED8\u8BA4\u4FDD\u7559");
      }
    });
    processVibeAndLive(diyJson, filteredDiyJson, closedKeys, sourceData, isSongMode);
    logger.stepEnd("\u5173\u95ED\u89C4\u5219\u5904\u7406", {
      originalKeys: Object.keys(diyJson),
      filteredKeys: Object.keys(filteredDiyJson),
      closedKeys
    });
    return {
      baseJson,
      songJson,
      filteredDiyJson,
      closedKeys
    };
  }
  function shouldCloseBass(sourceData, isSongMode) {
    var _a, _b, _c;
    if (!sourceData) return false;
    if (!isSongMode) {
      logger.logRule("1a-base", true, "baseJson + diyJson \u878D\u5408\uFF0C\u76F4\u63A5\u5173\u95ED\u4F4E\u9891\u589E\u5F3A");
      return true;
    }
    var feature = sourceData.feature;
    var aev2 = sourceData.aev2;
    var drumRatio = (_a = feature == null ? void 0 : feature.vocal_drum_bass_other_ratio) == null ? void 0 : _a[1];
    if (drumRatio !== void 0 && drumRatio < 0.05) {
      logger.logRule("1a-1", true, `drum ratio (${drumRatio}) < 0.05`);
      return true;
    }
    var eqs = (_b = aev2 == null ? void 0 : aev2.eq) == null ? void 0 : _b.eqs;
    var lowRatio = (_c = feature == null ? void 0 : feature.low_mid_high_ratio) == null ? void 0 : _c[0];
    if (eqs && Array.isArray(eqs) && eqs.length >= 3 && lowRatio !== void 0) {
      var eqsFirst3Avg = (eqs[0] + eqs[1] + eqs[2]) / 3;
      var condition = eqsFirst3Avg + (1 - lowRatio) * 6;
      if (condition > 6) {
        logger.logRule("1a-2", true, `eqs\u524D\u4E09\u6BB5\u5747\u503C(${eqsFirst3Avg}) + (1-low_ratio(${lowRatio})) * 6 = ${condition} > 6`);
        return true;
      }
    }
    logger.logRule("1a", false, "\u4F4E\u9891\u589E\u5F3A\u5173\u95ED\u6761\u4EF6\u672A\u547D\u4E2D", {
      drumRatio,
      eqsFirst3: eqs == null ? void 0 : eqs.slice(0, 3),
      lowRatio
    });
    return false;
  }
  function shouldCloseTreble(sourceData, isSongMode) {
    var _a, _b, _c;
    if (!sourceData) return false;
    var feature = sourceData.feature;
    var aev2 = sourceData.aev2;
    if (!isSongMode) {
      var eqs = (_b = (_a = sourceData == null ? void 0 : sourceData.effect) == null ? void 0 : _a.eq) == null ? void 0 : _b.eqs;
      if (eqs && Array.isArray(eqs) && eqs.length >= 3) {
        var lastIndex = eqs.length - 1;
        var eqsLast3Avg = (eqs[lastIndex] + eqs[lastIndex - 1] + eqs[lastIndex - 2]) / 3;
        if (eqsLast3Avg > 5) {
          logger.logRule("1b-base", true, `baseJson + diyJson \u878D\u5408\uFF0Ceqs\u540E\u4E09\u6BB5\u5747\u503C (${eqsLast3Avg}) > 5`);
          return true;
        }
      }
      logger.logRule("1b-base", false, "baseJson + diyJson \u878D\u5408\uFF0C\u4E0D\u5B58\u5728 eqs \u5B57\u6BB5\u6216\u5173\u95ED\u6761\u4EF6\u672A\u547D\u4E2D\uFF0C\u9ED8\u8BA4\u5F00\u542F", {
        eqsLast3: eqs == null ? void 0 : eqs.slice(-3),
        hasEqs: !!eqs
      });
      return false;
    }
    var vocalMainFreq = feature == null ? void 0 : feature.vocal_main_freq;
    if (vocalMainFreq !== void 0 && vocalMainFreq > 600) {
      logger.logRule("1b-1", true, `vocal_main_freq (${vocalMainFreq}) > 600`);
      return true;
    }
    var spectralCentroid = feature == null ? void 0 : feature.audio_spectral_centroid;
    if (spectralCentroid !== void 0 && spectralCentroid > 4e3) {
      logger.logRule("1b-2", true, `audio_spectral_centroid (${spectralCentroid}) > 4000`);
      return true;
    }
    var eqs = (_c = aev2 == null ? void 0 : aev2.eq) == null ? void 0 : _c.eqs;
    if (eqs && Array.isArray(eqs) && eqs.length >= 3) {
      var lastIndex = eqs.length - 1;
      var eqsLast3Avg = (eqs[lastIndex] + eqs[lastIndex - 1] + eqs[lastIndex - 2]) / 3;
      if (eqsLast3Avg > 5) {
        logger.logRule("1b-3", true, `eqs\u540E\u4E09\u6BB5\u5747\u503C (${eqsLast3Avg}) > 5`);
        return true;
      }
    }
    logger.logRule("1b", false, "\u9AD8\u9891\u589E\u5F3A\u5173\u95ED\u6761\u4EF6\u672A\u547D\u4E2D", {
      vocalMainFreq,
      spectralCentroid,
      eqsLast3: eqs == null ? void 0 : eqs.slice(-3)
    });
    return false;
  }
  function shouldCloseVocal(sourceData, isSongMode) {
    var _a;
    if (!sourceData) return false;
    if (!isSongMode) {
      logger.logRule("1c-base", true, "baseJson + diyJson \u878D\u5408\uFF0C\u76F4\u63A5\u5173\u95ED\u4EBA\u58F0\u589E\u5F3A");
      return true;
    }
    var feature = sourceData.feature;
    var vocalRatio = (_a = feature == null ? void 0 : feature.vocal_drum_bass_other_ratio) == null ? void 0 : _a[0];
    if (vocalRatio !== void 0 && vocalRatio < 0.2) {
      logger.logRule("1c", true, `vocal ratio (${vocalRatio}) < 0.2`);
      return true;
    }
    logger.logRule("1c", false, "\u4EBA\u58F0\u589E\u5F3A\u5173\u95ED\u6761\u4EF6\u672A\u547D\u4E2D", { vocalRatio });
    return false;
  }
  function processVibeAndLive(originalDiyJson, filteredDiyJson, closedKeys, sourceData, isSongMode) {
    var _a, _b;
    var hasVibe = "en_vibe" in originalDiyJson;
    var hasLive = "en_live" in originalDiyJson;
    if (!hasVibe && !hasLive) {
      return;
    }
    if (!hasVibe || !hasLive) {
      if (hasVibe && originalDiyJson.en_vibe) {
        filteredDiyJson.en_vibe = originalDiyJson.en_vibe;
        logger.logClosingRule("en_vibe", false, "\u53EA\u6709\u6C1B\u56F4\u611F\u589E\u5F3A\uFF0C\u76F4\u63A5\u4FDD\u7559");
      }
      if (hasLive && originalDiyJson.en_live) {
        filteredDiyJson.en_live = originalDiyJson.en_live;
        logger.logClosingRule("en_live", false, "\u53EA\u6709\u73B0\u573A\u611F\u589E\u5F3A\uFF0C\u76F4\u63A5\u4FDD\u7559");
      }
      return;
    }
    if (isSongMode) {
      var rvbOn = (_b = (_a = sourceData == null ? void 0 : sourceData.aev2) == null ? void 0 : _a.rvb) == null ? void 0 : _b.on;
      if (rvbOn !== true) {
        filteredDiyJson.en_live = originalDiyJson.en_live;
        closedKeys.push("en_vibe");
        logger.logClosingRule("en_vibe", true, "songJson \u4E2D\u6DF7\u54CD\u672A\u5F00\u542F\uFF0C\u9009\u62E9\u73B0\u573A\u611F\u589E\u5F3A");
        logger.logClosingRule("en_live", false, "songJson \u4E2D\u6DF7\u54CD\u672A\u5F00\u542F\uFF0C\u9009\u62E9\u73B0\u573A\u611F\u589E\u5F3A");
      } else {
        filteredDiyJson.en_vibe = originalDiyJson.en_vibe;
        closedKeys.push("en_live");
        logger.logClosingRule("en_vibe", false, "songJson \u4E2D\u6DF7\u54CD\u5DF2\u5F00\u542F\uFF0C\u9009\u62E9\u6C1B\u56F4\u611F\u589E\u5F3A");
        logger.logClosingRule("en_live", true, "songJson \u4E2D\u6DF7\u54CD\u5DF2\u5F00\u542F\uFF0C\u9009\u62E9\u6C1B\u56F4\u611F\u589E\u5F3A");
      }
    } else {
      filteredDiyJson.en_vibe = originalDiyJson.en_vibe;
      closedKeys.push("en_live");
      logger.logClosingRule("en_vibe", false, "baseJson \u878D\u5408\uFF0C\u4EC5\u5F00\u542F\u6C1B\u56F4\u611F\u589E\u5F3A");
      logger.logClosingRule("en_live", true, "baseJson \u878D\u5408\uFF0C\u4EC5\u5F00\u542F\u6C1B\u56F4\u611F\u589E\u5F3A");
    }
  }

  // .verification/sound-effects/ai-master/mergeRules.ts
  function countEnabledEffects(diyJson) {
    var count = 0;
    if (diyJson.en_bass) count++;
    if (diyJson.en_treble) count++;
    if (diyJson.en_vocal) count++;
    if (diyJson.en_vibe || diyJson.en_live) count++;
    return count;
  }
  function processBassAndTreble(mergedJson, diyJson, isSongMode) {
    var hasBass = !!diyJson.en_bass;
    var hasTreble = !!diyJson.en_treble;
    if (!hasBass && !hasTreble) {
      return;
    }
    logger.info("\u878D\u5408\u89C4\u5219 - BT\u53E0\u52A0: \u5904\u7406\u4F4E\u9891/\u9AD8\u9891\u589E\u5F3A", { hasBass, hasTreble });
    var mergedBt;
    if (hasBass && !hasTreble) {
      mergedBt = diyJson.en_bass.bt;
      logger.logMergeRule("Bass\u76F4\u63A5\u8D4B\u503C", "\u4EC5\u6709\u4F4E\u9891\u589E\u5F3A\uFF0C\u76F4\u63A5\u4F7F\u7528 en_bass.bt", {}, mergedBt);
    } else if (!hasBass && hasTreble) {
      mergedBt = diyJson.en_treble.bt;
      logger.logMergeRule("Treble\u76F4\u63A5\u8D4B\u503C", "\u4EC5\u6709\u9AD8\u9891\u589E\u5F3A\uFF0C\u76F4\u63A5\u4F7F\u7528 en_treble.bt", {}, mergedBt);
    } else {
      mergedBt = { on: true };
      var bassBt = diyJson.en_bass.bt;
      mergedBt.bass = bassBt.bass || 0;
      mergedBt.bassFreq = bassBt.bassFreq || 0;
      mergedBt.bassQ = bassBt.bassQ || 0;
      var trebleBt = diyJson.en_treble.bt;
      mergedBt.treble = trebleBt.treble || 0;
      mergedBt.trebleFreq = trebleBt.trebleFreq || 0;
      mergedBt.trebleQ = trebleBt.trebleQ || 0;
      logger.logMergeRule("BT\u9009\u62E9\u6027\u5408\u5E76", "\u540C\u65F6\u5B58\u5728\u4F4E\u9891\u548C\u9AD8\u9891\u589E\u5F3A\uFF0C\u9009\u62E9\u6027\u5408\u5E76", {}, mergedBt);
    }
    if (isSongMode) {
      if (!mergedJson.aev2) {
        mergedJson.aev2 = {};
      }
      mergedJson.aev2.bt = mergedBt;
    } else {
      if (!mergedJson.effect) {
        mergedJson.effect = {};
      }
      mergedJson.effect.bt = mergedBt;
    }
    logger.logMergeRule("BT\u6700\u7EC8", "\u4F4E\u9891\u9AD8\u9891\u589E\u5F3A\u878D\u5408\u5B8C\u6210", {}, mergedBt);
  }
  function processVocal(mergedJson, diyJson, isSongMode) {
    if (!diyJson.en_vocal) {
      return;
    }
    logger.info("\u5904\u7406\u4EBA\u58F0\u589E\u5F3A\u7684 peq \u5B57\u6BB5");
    var peq = diyJson.en_vocal.peq;
    if (isSongMode) {
      if (!mergedJson.aev2) {
        mergedJson.aev2 = {};
      }
      mergedJson.aev2.peq = peq;
    } else {
      if (!mergedJson.effect) {
        mergedJson.effect = {};
      }
      mergedJson.effect.peq = peq;
    }
    logger.logMergeRule("\u4EBA\u58F0\u589E\u5F3A", "\u6DFB\u52A0 peq \u5B57\u6BB5", {}, peq);
  }
  function processVibe(mergedJson, diyJson, isSongMode) {
    var _a;
    if (!diyJson.en_vibe) {
      return;
    }
    logger.info("\u5904\u7406\u6C1B\u56F4\u611F\u589E\u5F3A");
    var vibe = diyJson.en_vibe;
    if (isSongMode) {
      if (!mergedJson.aev2) {
        mergedJson.aev2 = {};
      }
      if (((_a = mergedJson.aev2.rvb) == null ? void 0 : _a.on) === true) {
        mergedJson.aev2.rvb.on = false;
        logger.logMergeRule("\u6C1B\u56F4\u611F-\u6DF7\u54CD", "\u5173\u95ED\u539F\u6709\u6DF7\u54CD", { "rvb.on": true }, { "rvb.on": false });
      }
      mergedJson.aev2.se = vibe.se;
      logger.logMergeRule("\u6C1B\u56F4\u611F-SE", "\u6DFB\u52A0 se \u5B57\u6BB5", {}, vibe.se);
      if (vibe.cmp) {
        if (!mergedJson.aev2.cmp) {
          mergedJson.aev2.cmp = {
            attack: 0,
            on: false,
            // 最后会根据 pregain 值调整
            pregain: 0,
            release: 0,
            range: 0,
            ratio: 1,
            postgain: 0,
            threshold: 0
          };
          logger.logMergeRule("CMP\u9ED8\u8BA4\u503C", "\u8BBE\u7F6E\u9ED8\u8BA4 cmp \u5B57\u6BB5", {}, mergedJson.aev2.cmp);
        }
        var originalPregain = mergedJson.aev2.cmp.pregain || 0;
        var additionalPregain = vibe.cmp.pregain || 0;
        mergedJson.aev2.cmp.pregain = originalPregain + additionalPregain;
        logger.logMergeRule(
          "\u6C1B\u56F4\u611F-CMP",
          "\u53E0\u52A0 pregain",
          { pregain: originalPregain },
          { pregain: originalPregain + additionalPregain }
        );
      }
    } else {
      if (!mergedJson.effect) {
        mergedJson.effect = {};
      }
      mergedJson.effect.se = vibe.se;
      logger.logMergeRule("\u6C1B\u56F4\u611F-SE", "\u6DFB\u52A0 se \u5B57\u6BB5\u5230 effect", {}, vibe.se);
      if (vibe.cmp) {
        if (!mergedJson.effect.cmp) {
          mergedJson.effect.cmp = {
            attack: 0,
            on: false,
            // 最后会根据 pregain 值调整
            pregain: 0,
            release: 0,
            range: 0,
            ratio: 1,
            postgain: 0,
            threshold: 0
          };
          logger.logMergeRule("CMP\u9ED8\u8BA4\u503C", "\u8BBE\u7F6E\u9ED8\u8BA4 cmp \u5B57\u6BB5", {}, mergedJson.effect.cmp);
        }
        var originalPregain = mergedJson.effect.cmp.pregain || 0;
        var additionalPregain = vibe.cmp.pregain || 0;
        mergedJson.effect.cmp.pregain = originalPregain + additionalPregain;
        logger.logMergeRule(
          "\u6C1B\u56F4\u611F-CMP",
          "\u53E0\u52A0 pregain \u5230 effect",
          { pregain: originalPregain },
          { pregain: originalPregain + additionalPregain }
        );
      }
    }
  }
  function processLive(mergedJson, diyJson, isSongMode) {
    if (!diyJson.en_live) {
      return;
    }
    logger.info("\u5904\u7406\u73B0\u573A\u611F\u589E\u5F3A");
    var live = diyJson.en_live;
    if (isSongMode) {
      if (!mergedJson.aev2) {
        mergedJson.aev2 = {};
      }
      var originalRvb = mergedJson.aev2.rvb;
      mergedJson.aev2.rvb = live.rvb;
      logger.logMergeRule("\u73B0\u573A\u611F\u589E\u5F3A", "\u66FF\u6362 rvb \u5B57\u6BB5", originalRvb, live.rvb);
    } else {
      if (!mergedJson.effect) {
        mergedJson.effect = {};
      }
      var originalRvb = mergedJson.effect.rvb;
      mergedJson.effect.rvb = live.rvb;
      logger.logMergeRule("\u73B0\u573A\u611F\u589E\u5F3A", "\u66FF\u6362 effect.rvb \u5B57\u6BB5", originalRvb, live.rvb);
    }
  }
  function adjustPregain(mergedJson, enabledEffectsCount, isSongMode) {
    if (enabledEffectsCount === 0) {
      return;
    }
    logger.info(`\u8C03\u6574 pregain\uFF0C\u5F00\u542F\u7684\u97F3\u6548\u6570\u91CF: ${enabledEffectsCount}`);
    var pregainAdjustment = 0;
    if (enabledEffectsCount === 1) {
      pregainAdjustment = -0.5;
    } else if (enabledEffectsCount === 2) {
      pregainAdjustment = -1.2;
    } else if (enabledEffectsCount === 3) {
      pregainAdjustment = -2.1;
    } else if (enabledEffectsCount === 4) {
      pregainAdjustment = -3;
    }
    if (isSongMode) {
      if (!mergedJson.aev2) {
        mergedJson.aev2 = {};
      }
      if (!mergedJson.aev2.cmp) {
        mergedJson.aev2.cmp = {
          attack: 0,
          on: true,
          pregain: 0,
          release: 0,
          range: 0,
          ratio: 1,
          postgain: 0,
          threshold: 0
        };
        logger.logMergeRule("CMP\u9ED8\u8BA4\u503C", "\u8BBE\u7F6E\u9ED8\u8BA4 cmp \u5B57\u6BB5", {}, mergedJson.aev2.cmp);
      }
      var originalPregain = mergedJson.aev2.cmp.pregain || 0;
      mergedJson.aev2.cmp.pregain = originalPregain + pregainAdjustment;
      logger.logMergeRule(
        "Pregain\u8C03\u6574",
        "aev2.cmp.pregain \u8C03\u6574",
        { pregain: originalPregain },
        { pregain: mergedJson.aev2.cmp.pregain, adjustment: pregainAdjustment }
      );
    } else {
      if (!mergedJson.effect) {
        mergedJson.effect = {};
      }
      if (!mergedJson.effect.cmp) {
        mergedJson.effect.cmp = {
          attack: 0,
          on: true,
          pregain: 0,
          release: 0,
          range: 0,
          ratio: 1,
          postgain: 0,
          threshold: 0
        };
        logger.logMergeRule("CMP\u9ED8\u8BA4\u503C", "\u8BBE\u7F6E\u9ED8\u8BA4 effect.cmp \u5B57\u6BB5", {}, mergedJson.effect.cmp);
      }
      var originalPregain = mergedJson.effect.cmp.pregain || 0;
      mergedJson.effect.cmp.pregain = originalPregain + pregainAdjustment;
      logger.logMergeRule(
        "Pregain\u8C03\u6574",
        "effect.cmp.pregain \u8C03\u6574",
        { pregain: originalPregain },
        { pregain: mergedJson.effect.cmp.pregain, adjustment: pregainAdjustment }
      );
    }
  }
  function applyStereoAdjustment(mergedJson, isSongMode) {
    var _a, _b, _c, _d;
    logger.info("\u5E94\u7528\u7ACB\u4F53\u58F0\u8C03\u6574\uFF0Ceqs \u6570\u7EC4\u6240\u6709\u5143\u7D20\u4E58 0.8");
    if (isSongMode) {
      var eqs = (_b = (_a = mergedJson == null ? void 0 : mergedJson.aev2) == null ? void 0 : _a.eq) == null ? void 0 : _b.eqs;
      if (eqs && Array.isArray(eqs)) {
        var originalEqs = eqs.slice();
        mergedJson.aev2.eq.eqs = eqs.map(function(value) {
          return value * 0.8;
        });
        logger.logMergeRule(
          "\u7ACB\u4F53\u58F0\u8C03\u6574",
          "aev2.eq.eqs \u4E58 0.8",
          { eqs: originalEqs },
          { eqs: mergedJson.aev2.eq.eqs }
        );
      }
    } else {
      var eqs = (_d = (_c = mergedJson == null ? void 0 : mergedJson.effect) == null ? void 0 : _c.eq) == null ? void 0 : _d.eqs;
      if (eqs && Array.isArray(eqs)) {
        var originalEqs = eqs.slice();
        mergedJson.effect.eq.eqs = eqs.map(function(value) {
          return value * 0.8;
        });
        logger.logMergeRule(
          "\u7ACB\u4F53\u58F0\u8C03\u6574",
          "effect.eq.eqs \u4E58 0.8",
          { eqs: originalEqs },
          { eqs: mergedJson.effect.eq.eqs }
        );
      }
    }
  }
  function adjustCmpOnBasedOnPregain(mergedJson, isSongMode) {
    var _a, _b;
    if (isSongMode) {
      var cmp = (_a = mergedJson == null ? void 0 : mergedJson.aev2) == null ? void 0 : _a.cmp;
      if (cmp && typeof cmp.pregain === "number") {
        var originalOn = cmp.on;
        var newOn = cmp.pregain !== 0;
        if (originalOn !== newOn) {
          cmp.on = newOn;
          logger.logMergeRule(
            "CMP\u72B6\u6001\u8C03\u6574",
            `pregain\u4E3A${cmp.pregain}\uFF0C${newOn ? "\u5F00\u542F" : "\u5173\u95ED"}aev2.cmp`,
            { pregain: cmp.pregain, on: originalOn },
            { pregain: cmp.pregain, on: newOn }
          );
        }
      }
    } else {
      var cmp = (_b = mergedJson == null ? void 0 : mergedJson.effect) == null ? void 0 : _b.cmp;
      if (cmp && typeof cmp.pregain === "number") {
        var originalOn = cmp.on;
        var newOn = cmp.pregain !== 0;
        if (originalOn !== newOn) {
          cmp.on = newOn;
          logger.logMergeRule(
            "CMP\u72B6\u6001\u8C03\u6574",
            `pregain\u4E3A${cmp.pregain}\uFF0C${newOn ? "\u5F00\u542F" : "\u5173\u95ED"}effect.cmp`,
            { pregain: cmp.pregain, on: originalOn },
            { pregain: cmp.pregain, on: newOn }
          );
        }
      }
    }
  }
  function standardizeOutputStructure(mergedJson, isSongMode) {
    if (isSongMode) {
      return { aev2: mergedJson.aev2 };
    } else {
      if (mergedJson.effect) {
        logger.logMergeRule(
          "\u7ED3\u6784\u6807\u51C6\u5316",
          "\u5C06 baseJson \u7684 effect \u7ED3\u6784\u8F6C\u6362\u4E3A aev2 \u7ED3\u6784",
          { structure: "effect" },
          { structure: "aev2" }
        );
        return { aev2: mergedJson.effect };
      } else {
        logger.warn("baseJson \u6A21\u5F0F\u4E0B\u6CA1\u6709\u627E\u5230 effect \u5B57\u6BB5\uFF0C\u8FD4\u56DE\u7A7A\u7684 aev2 \u7ED3\u6784");
        return { aev2: {} };
      }
    }
  }
  function processMergeRules(input) {
    logger.stepStart("\u878D\u5408\u89C4\u5219\u5904\u7406", "\u6839\u636E\u89C4\u52192\u5904\u7406 diyJson \u7684\u53E0\u52A0\u903B\u8F91");
    var { baseJson, songJson, diyJson, supportStereo } = input;
    var mergedJson;
    var isSongMode = !!songJson;
    if (isSongMode) {
      mergedJson = JSON.parse(JSON.stringify(songJson));
      logger.info("\u4F7F\u7528 songJson \u4F5C\u4E3A\u878D\u5408\u57FA\u7840");
    } else if (baseJson) {
      var baseKeys = Object.keys(baseJson);
      if (baseKeys.length === 1) {
        var baseKey = baseKeys[0];
        mergedJson = JSON.parse(JSON.stringify(baseJson[baseKey]));
        logger.info(`\u4F7F\u7528 baseJson.${baseKey} \u4F5C\u4E3A\u878D\u5408\u57FA\u7840`);
      } else if (baseKeys.length > 1) {
        logger.error("baseJson \u5305\u542B\u591A\u4E2A\u97F3\u6548\u914D\u7F6E\uFF0C\u65E0\u6CD5\u786E\u5B9A\u4F7F\u7528\u54EA\u4E2A\u4F5C\u4E3A\u878D\u5408\u57FA\u7840", { baseKeys });
        mergedJson = {};
        return mergedJson;
      } else {
        mergedJson = {};
        logger.info("baseJson \u4E3A\u7A7A\uFF0C\u4F7F\u7528\u7A7A\u5BF9\u8C61\u4F5C\u4E3A\u878D\u5408\u57FA\u7840");
      }
    } else {
      mergedJson = {};
      logger.warn("\u6CA1\u6709\u53EF\u7528\u7684\u57FA\u7840 JSON\uFF0C\u4F7F\u7528\u7A7A\u5BF9\u8C61");
    }
    var enabledEffectsCount = countEnabledEffects(diyJson);
    logger.info(`\u5F00\u542F\u7684\u589E\u5F3A\u97F3\u6548\u6570\u91CF: ${enabledEffectsCount}`, Object.keys(diyJson));
    processBassAndTreble(mergedJson, diyJson, isSongMode);
    processVocal(mergedJson, diyJson, isSongMode);
    processVibe(mergedJson, diyJson, isSongMode);
    processLive(mergedJson, diyJson, isSongMode);
    adjustPregain(mergedJson, enabledEffectsCount, isSongMode);
    if (supportStereo) {
      applyStereoAdjustment(mergedJson, isSongMode);
    }
    adjustCmpOnBasedOnPregain(mergedJson, isSongMode);
    var standardizedJson = standardizeOutputStructure(mergedJson, isSongMode);
    logger.stepEnd("\u878D\u5408\u89C4\u5219\u5904\u7406", { mergedJson: standardizedJson });
    return { mergedJson: standardizedJson };
  }

  // .verification/sound-effects/ai-master/index.ts
  function composeEffect(params) {
    logger.info("\u5F00\u59CB\u97F3\u6548\u878D\u5408\u5904\u7406", params);
    try {
      var validation = validateInputs(params);
      if (!validation.isValid) {
        logger.error("\u8F93\u5165\u9A8C\u8BC1\u5931\u8D25", validation.errors);
        return {
          mergedJson: {},
          usedDiyConfig: [],
          success: false,
          errorMsg: validation.errors.join("; "),
          isUseZoneEffect: false
        };
      }
      var { baseJson, songJson, diyJson, supportStereo } = params;
      logger.info("\u8F93\u5165\u9A8C\u8BC1\u901A\u8FC7\uFF0C\u5F00\u59CB\u5904\u7406\u878D\u5408\u89C4\u5219");
      var baseEffect = null;
      if (typeof baseJson === "string") {
        baseEffect = baseJson.trim() === "" ? null : JSON.parse(baseJson);
      } else {
        baseEffect = baseJson;
      }
      var songEffect = null;
      if (typeof songJson === "string") {
        songEffect = songJson.trim() === "" ? null : JSON.parse(songJson);
      } else {
        songEffect = songJson;
      }
      var diyEffect = {};
      if (typeof diyJson === "string") {
        diyEffect = diyJson.trim() === "" ? {} : JSON.parse(diyJson);
      } else {
        diyEffect = diyJson;
      }
      var closingResult = processClosingRules({
        baseJson: baseEffect,
        songJson: songEffect,
        diyJson: diyEffect,
        supportStereo
      });
      logger.info("\u5173\u95ED\u89C4\u5219\u5904\u7406\u5B8C\u6210", {
        originalDiyKeys: Object.keys(diyJson || {}),
        filteredDiyKeys: Object.keys(closingResult.filteredDiyJson),
        closedKeys: closingResult.closedKeys
      });
      var mergeResult = processMergeRules({
        baseJson: closingResult.baseJson,
        songJson: closingResult.songJson,
        diyJson: closingResult.filteredDiyJson,
        supportStereo
      });
      logger.info("\u878D\u5408\u89C4\u5219\u5904\u7406\u5B8C\u6210", mergeResult);
      var usedDiyConfig = Object.keys(closingResult.filteredDiyJson);
      var isUseZoneEffect = checkZoneEffect(mergeResult.mergedJson);
      var result = {
        mergedJson: mergeResult.mergedJson,
        usedDiyConfig,
        success: true,
        errorMsg: "",
        isUseZoneEffect
      };
      logger.info("\u97F3\u6548\u878D\u5408\u5904\u7406\u5B8C\u6210", result);
      return result;
    } catch (error) {
      var errorMsg = error instanceof Error ? error.message : "\u672A\u77E5\u9519\u8BEF";
      logger.error("\u97F3\u6548\u878D\u5408\u5904\u7406\u5F02\u5E38", errorMsg);
      return {
        mergedJson: {},
        usedDiyConfig: [],
        success: false,
        errorMsg: `\u97F3\u6548\u878D\u5408\u5904\u7406\u5931\u8D25: ${errorMsg}`,
        isUseZoneEffect: false
      };
    }
  }
  function checkZoneEffect(mergedJson) {
    var _a, _b;
    logger.info("\u68C0\u67E5\u7A7A\u95F4\u97F3\u6548\u4F7F\u7528\u60C5\u51B5", mergedJson);
    try {
      var aev2 = mergedJson == null ? void 0 : mergedJson.aev2;
      if (!aev2) {
        logger.info("\u672A\u627E\u5230 aev2 \u5B57\u6BB5\uFF0C\u7A7A\u95F4\u97F3\u6548\u672A\u4F7F\u7528");
        return false;
      }
      var isRvbOn = ((_a = aev2.rvb) == null ? void 0 : _a.on) === true;
      var isSeOn = ((_b = aev2.se) == null ? void 0 : _b.on) === true;
      var result = isRvbOn || isSeOn;
      logger.info("\u7A7A\u95F4\u97F3\u6548\u68C0\u67E5\u7ED3\u679C", {
        "aev2.rvb.on": isRvbOn,
        "aev2.se.on": isSeOn,
        isUseZoneEffect: result
      });
      return result;
    } catch (error) {
      logger.error("\u68C0\u67E5\u7A7A\u95F4\u97F3\u6548\u65F6\u53D1\u751F\u9519\u8BEF", error);
      return false;
    }
  }
  return __toCommonJS(index_exports);
})();
