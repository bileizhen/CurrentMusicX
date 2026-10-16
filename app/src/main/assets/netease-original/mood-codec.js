function originalConstruct(fn,args){while(args.length&&args[args.length-1]===undefined)args.pop();return Reflect.construct(fn,args);}
var global = (function(){return this})(); var factories = {}, cache = {};
factories[872]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a1;
        r1 = a5;
        r4 = a6;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: l, environment: r2
            _fun9232: for(var _fun9232_ip = 0; ; ) switch(_fun9232_ip) {
case 0:
                r2 = a0;
                r1 = ':';
                r2 = r2 + r1;
                r1 = a1;
                r2 = r2 + r1;
                r1 = _closure1_slot1;
                r1 = r1.MODEL64_TEMPLATE_DATA;
                r1 = r1[r2];
                r0 = null;
                if(!(r0 != r1)) { _fun9232_ip = 44; continue _fun9232 }
case 41:
                r0 = r1;
case 44:
                return r0;
            }
        };
        var _closure1_slot2 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: t, environment: r2
            _fun9233: for(var _fun9233_ip = 0; ; ) switch(_fun9233_ip) {
case 0:
                r0 = a0;
                r2 = null;
                r1 = undefined;
                if(!(r2 != r0)) { _fun9233_ip = 16; continue _fun9233 }
case 11:
                r1 = r0.on;
case 16:
                r2 = 'boolean';
                r1 = typeof r1;
                if(!(r2 === r1)) { _fun9233_ip = 34; continue _fun9233 }
case 27:
                r0 = r0.on;
                return r0;
case 34:
                r0 = global;
                r2 = r0.Error;
                r1 = a1;
                r0 = ' 缺失或不是 boolean';
                r3 = r1 + r0;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r4 = r1;
                r0 = originalConstruct(r2, [r3, r2]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        var _closure1_slot3 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: u, environment: r2
            _fun9234: for(var _fun9234_ip = 0; ; ) switch(_fun9234_ip) {
case 0:
                r0 = a0;
                r5 = undefined;
                r4 = _closure1_slot3;
                r3 = r0.rvb;
                r2 = 'template.rvb.on';
                r3 = r4.bind(r5)(r3, r2);
                r4 = r0.dly;
                r6 = null;
                r2 = undefined;
                if(!(r6 != r4)) { _fun9234_ip = 47; continue _fun9234 }
case 42:
                r2 = r4.on;
case 47:
                r4 = 'boolean';
                r2 = typeof r2;
                r2 = r4 === r2;
                if(!r2) { _fun9234_ip = 72; continue _fun9234 }
case 61:
                r4 = r0.dly;
                r2 = r4.on;
case 72:
                r4 = _closure1_slot3;
                r1 = r0.se;
                r0 = 'template.se.on';
                r1 = r4.bind(r5)(r1, r0);
                r0 = {};
                r0['reverb'] = r3;
                r0['delay'] = r2;
                r0['stereo_enhance'] = r1;
                return r0;
            }
        };
        var _closure1_slot4 = r0;
        r0 = global;
        r7 = r0.Object;
        r6 = r7.defineProperty;
        r5 = {};
        r0 = true;
        r5['value'] = r0;
        r0 = '__esModule';
        r0 = r6.bind(r7)(r1, r0, r5);
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9235: for(var _fun9235_ip = 0; ; ) switch(_fun9235_ip) {
case 0:
                r6 = a0;
                r4 = a1;
                r1 = _closure1_slot2;
                r3 = undefined;
                r1 = r1.bind(r3)(r6, r4);
                if(r1) { _fun9235_ip = 74; continue _fun9235 }
case 24:
                r2 = global;
                r5 = r2.Error;
                r2 = 'missing model64 template: ';
                r6 = r2 + r6;
                r2 = ':';
                r2 = r6 + r2;
                r8 = r2 + r4;
                r4 = r5.prototype;
                r4 = Object.create(r4, {constructor: {value: r5}});
                r9 = r4;
                r2 = originalConstruct(r5, [r8, r7]);
                r2 = r2 instanceof Object ? r2 : r4;
                throw r2;
case 74:
                r2 = _closure1_slot0;
                r2 = r2.deepClone;
                r2 = r2.bind(r3)(r1);
                r0 = _closure1_slot4;
                r1 = r0.bind(r3)(r1);
                r0 = {};
                r0['effectParams'] = r2;
                r0['moduleSwitches'] = r1;
                return r0;
            }
        };
        r1['resolveModel64Template'] = r0;
        r0 = 0;
        r1 = r4[r0];
        r0 = undefined;
        r1 = r3.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 1;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot1 = r1;
        return r0;
    };
factories[873]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r5 = a1;
        r3 = a5;
        r6 = a6;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: u, environment: r2
            _fun9237: for(var _fun9237_ip = 0; ; ) switch(_fun9237_ip) {
case 0:
                r1 = a0;
                r6 = 'unknown';
                r5 = undefined;
                r2 = undefined;
                r3 = arguments.length;
                r0 = 1;
                r4 = r6;
                if(!(r3 > r0)) { _fun9237_ip = 39; continue _fun9237 }
case 24:
                r3 = arguments[r0];
                r4 = r6;
                if(!(r5 !== r3)) { _fun9237_ip = 39; continue _fun9237 }
case 35:
                r4 = arguments[r0];
case 39:
                r0 = global;
                r0 = r0.Error;
                r0 = r1 instanceof r0;
                if(!r0) { _fun9237_ip = 62; continue _fun9237 }
case 54:
                r0 = r1.message;
                if(r0) { _fun9237_ip = 95; continue _fun9237 }
case 62:
                r3 = 'string';
                r2 = typeof r1;
                r0 = r4;
                if(!(r3 === r2)) { _fun9237_ip = 93; continue _fun9237 }
case 76:
                r3 = r1.length;
                r2 = 0;
                r0 = r4;
                if(!(r3 > r2)) { _fun9237_ip = 93; continue _fun9237 }
case 90:
                r0 = r1;
case 93:
                _fun9237_ip = 100; continue _fun9237;
case 95:
                r0 = r1.message;
case 100:
                return r0;
            }
        };
        var _closure1_slot2 = r1;
        r0 = 0;
        r4 = r6[r0];
        r0 = undefined;
        r4 = r5.bind(r0)(r4);
        r7 = global;
        r10 = r7.Object;
        r9 = r10.defineProperty;
        r8 = {};
        r7 = true;
        r8['value'] = r7;
        r7 = '__esModule';
        r7 = r9.bind(r10)(r3, r7, r8);
        r3['RANGE_EPSILON'] = r0;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9238: for(var _fun9238_ip = 0; ; ) switch(_fun9238_ip) {
case 0:
                r0 = a0;
                r2 = 'number';
                r1 = typeof r0;
                if(!(r2 === r1)) { _fun9238_ip = 38; continue _fun9238 }
case 14:
                r1 = global;
                r2 = r1.Number;
                r1 = r2.isFinite;
                r1 = r1.bind(r2)(r0);
                if(!r1) { _fun9238_ip = 38; continue _fun9238 }
case 36:
                return r0;
case 38:
                r0 = global;
                r2 = r0.Error;
                r1 = a1;
                r0 = ' 必须为有限数值';
                r3 = r1 + r0;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r4 = r1;
                r0 = originalConstruct(r2, [r3, r2]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        r3['assertFiniteNumber'] = r7;
        r7 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r0 = global;
            r3 = r0.Math;
            r2 = r3.min;
            r5 = r0.Math;
            r4 = r5.max;
            r1 = a0;
            r0 = a1;
            r1 = r4.bind(r5)(r1, r0);
            r0 = a2;
            r0 = r2.bind(r3)(r1, r0);
            return r0;
        };
        r3['clamp'] = r7;
        r7 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r0 = global;
            r2 = r0.JSON;
            r1 = r2.parse;
            r4 = r0.JSON;
            r3 = r4.stringify;
            r0 = a0;
            r0 = r3.bind(r4)(r0);
            r0 = r1.bind(r2)(r0);
            return r0;
        };
        r3['deepClone'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: n, environment: r2
            _fun9241: for(var _fun9241_ip = 0; ; ) switch(_fun9241_ip) {
case 0:
                r4 = a0;
                r1 = a1;
                r2 = null;
                r0 = undefined;
                r13 = undefined;
                r14 = undefined;
                r7 = undefined;
                r5 = undefined;
                r15 = undefined;
                r16 = undefined;
                if(!(r4 !== r1)) { _fun9241_ip = 436; continue _fun9241 }
case 29:
                if(!(r2 != r4)) { _fun9241_ip = 404; continue _fun9241 }
case 36:
                if(!(r2 != r1)) { _fun9241_ip = 404; continue _fun9241 }
case 43:
                r8 = 'object';
                r3 = typeof r4;
                if(!(r8 === r3)) { _fun9241_ip = 404; continue _fun9241 }
case 57:
                r3 = typeof r1;
                if(!(r8 === r3)) { _fun9241_ip = 404; continue _fun9241 }
case 67:
                r3 = global;
                r11 = r3.Array;
                r8 = r11.isArray;
                r8 = r8.bind(r11)(r4);
                if(!r8) { _fun9241_ip = 110; continue _fun9241 }
case 88:
                r11 = r3.Array;
                r8 = r11.isArray;
                r8 = r8.bind(r11)(r1);
                if(r8) { _fun9241_ip = 312; continue _fun9241 }
case 110:
                r13 = r4;
                r14 = r1;
                r17 = r3.Set;
                r19 = new Array(0);
                r18 = r19.concat;
                r8 = _closure1_slot0;
                r11 = r8.default;
                r20 = r3.Object;
                r8 = r20.keys;
                r8 = r8.bind(r20)(r4);
                r11 = r11.bind(r0)(r8);
                r8 = _closure1_slot0;
                r20 = r8.default;
                r21 = r3.Object;
                r8 = r21.keys;
                r8 = r8.bind(r21)(r1);
                r8 = r20.bind(r0)(r8);
                r23 = r18.bind(r19)(r11, r8);
                r11 = r17.prototype;
                r11 = Object.create(r11, {constructor: {value: r17}});
                r24 = r11;
                r8 = originalConstruct(r17, [r23, r22]);
                r17 = r8 instanceof Object ? r8 : r11;
                r7 = {};
                r5 = false;
                r8 = r17;
                var __iterator_values_1=Array.from(r8), __iterator_index_1=0; r11={return:function(){}}; r8=function(){if(__iterator_index_1>=__iterator_values_1.length){r11=undefined;return {value:undefined}}return {value:__iterator_values_1[__iterator_index_1++]}};
case 230:
                r20 = r8().value;
                r17 = r11;
                if(!(r17 !== r0)) { _fun9241_ip = 299; continue _fun9241 }
case 241: // try_start_0
                r15 = r20;
                r19 = _closure1_slot3;
                r17 = r13;
                r18 = r17[r20];
                r17 = r14;
                r17 = r17[r20];
                r17 = r19.bind(r0)(r18, r17);
                r16 = r17;
                if(!(r0 !== r17)) { _fun9241_ip = 290; continue _fun9241 }
case 275:
                r19 = r7;
                r18 = r15;
                r17 = r16;
                r19[r18] = r17;
                r5 = true;
case 290: // try_end0
                _fun9241_ip = 230; continue _fun9241;
case 292: // catch_target0
                CatchBlockStart(arg_register=8);
                r11.return();
                throw r8;
case 299:
                r8 = r5;
                r5 = undefined;
                if(!r8) { _fun9241_ip = 310; continue _fun9241 }
case 307:
                r5 = r7;
case 310:
                return r5;
case 312:
                r8 = r3.Math;
                r7 = r8.max;
                r5 = r4.length;
                r3 = r1.length;
                r8 = r7.bind(r8)(r5, r3);
                r5 = new Array(0);
                r3 = false;
                r6 = false;
                r10 = 0;
                if(!(r10 < r8)) { _fun9241_ip = 394; continue _fun9241 }
case 356:
                r13 = _closure1_slot3;
                r12 = r4[r10];
                r11 = r1[r10];
                r11 = r13.bind(r0)(r12, r11);
                if(!(r0 !== r11)) { _fun9241_ip = 384; continue _fun9241 }
case 378:
                r5[r10] = r11;
                r3 = true;
case 384:
                r10 = r10 + 1;
                r6 = r3;
                if(r10 < r8) { _fun9241_ip = 356; continue _fun9241 }
case 394:
                r3 = undefined;
                if(!r6) { _fun9241_ip = 402; continue _fun9241 }
case 399:
                r3 = r5;
case 402:
                return r3;
case 404:
                r3 = null;
                if(!(r2 != r4)) { _fun9241_ip = 413; continue _fun9241 }
case 410:
                r3 = r4;
case 413:
                r2 = null;
                if(!(r2 != r1)) { _fun9241_ip = 422; continue _fun9241 }
case 419:
                r2 = r1;
case 422:
                r1 = {};
                r1['_from'] = r3;
                r1['_to'] = r2;
                return r1;
case 436:
                return r0;
            }
        };
        var _closure1_slot3 = r7;
        r3['deepDiff'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9242: for(var _fun9242_ip = 0; ; ) switch(_fun9242_ip) {
case 0:
                r1 = a0;
                r3 = a1;
                r4 = 'number';
                r2 = typeof r1;
                r0 = r3;
                if(!(r4 === r2)) { _fun9242_ip = 48; continue _fun9242 }
case 20:
                r2 = global;
                r4 = r2.Number;
                r2 = r4.isFinite;
                r2 = r2.bind(r4)(r1);
                r0 = r3;
                if(!r2) { _fun9242_ip = 48; continue _fun9242 }
case 45:
                r0 = r1;
case 48:
                return r0;
            }
        };
        r3['finiteOr'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r0 = global;
            r2 = r0.Math;
            r1 = r2.abs;
            r3 = a0;
            r0 = a1;
            r0 = r3 - r0;
            r1 = r1.bind(r2)(r0);
            r0 = _closure1_slot1;
            r0 = r1 < r0;
            return r0;
        };
        r3['floatEqual'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r1 = _closure1_slot1;
            r0 = a1;
            r1 = r0 + r1;
            r0 = a0;
            r0 = r0 > r1;
            return r0;
        };
        r3['floatGt'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r1 = _closure1_slot1;
            r0 = a1;
            r1 = r0 - r1;
            r0 = a0;
            r0 = r0 >= r1;
            return r0;
        };
        r3['floatGte'] = r7;
        r7 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r0 = global;
            r2 = r0.Math;
            r1 = r2.abs;
            r0 = a0;
            r1 = r1.bind(r2)(r0);
            r0 = _closure1_slot1;
            r0 = r1 < r0;
            return r0;
        };
        r3['floatIsZero'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r1 = _closure1_slot1;
            r0 = a1;
            r1 = r0 - r1;
            r0 = a0;
            r0 = r0 < r1;
            return r0;
        };
        r3['floatLt'] = r7;
        r7 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9248: for(var _fun9248_ip = 0; ; ) switch(_fun9248_ip) {
case 0:
                r5 = 'unknown';
                r4 = undefined;
                r1 = undefined;
                r2 = arguments.length;
                r0 = 2;
                r3 = r5;
                if(!(r2 > r0)) { _fun9248_ip = 36; continue _fun9248 }
case 21:
                r2 = arguments[r0];
                r3 = r5;
                if(!(r4 !== r2)) { _fun9248_ip = 36; continue _fun9248 }
case 32:
                r3 = arguments[r0];
case 36:
                r1 = a0;
                r0 = ': ';
                r1 = r1 + r0;
                r2 = _closure1_slot2;
                r0 = a1;
                r0 = r2.bind(r4)(r0, r3);
                r0 = r1 + r0;
                return r0;
            }
        };
        r3['formatStepError'] = r7;
        r3['getErrorText'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r3 = a0;
            r2 = r3.reduce;
            r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                r1 = a0;
                r0 = a1;
                r0 = r1 * r0;
                return r0;
            };
            r0 = 1;
            r0 = r2.bind(r3)(r1, r0);
            return r0;
        };
        r3['getTensorSize'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9251: for(var _fun9251_ip = 0; ; ) switch(_fun9251_ip) {
case 0:
                r2 = a0;
                r1 = 'number';
                r0 = typeof r2;
                r0 = r1 === r0;
                if(!r0) { _fun9251_ip = 36; continue _fun9251 }
case 17:
                r1 = global;
                r3 = r1.Number;
                r1 = r3.isInteger;
                r0 = r1.bind(r3)(r2);
case 36:
                if(!r0) { _fun9251_ip = 45; continue _fun9251 }
case 39:
                r1 = 0;
                r0 = r2 > r1;
case 45:
                return r0;
            }
        };
        r3['isPositiveInteger'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9252: for(var _fun9252_ip = 0; ; ) switch(_fun9252_ip) {
case 0:
                r1 = a0;
                r4 = 'number';
                r2 = typeof r1;
                r0 = 0;
                if(!(r4 === r2)) { _fun9252_ip = 43; continue _fun9252 }
case 16:
                r2 = global;
                r4 = r2.Number;
                r2 = r4.isFinite;
                r2 = r2.bind(r4)(r1);
                r0 = 0;
                if(!r2) { _fun9252_ip = 43; continue _fun9252 }
case 40:
                r0 = r1;
case 43:
                return r0;
            }
        };
        r3['optionalFiniteNumber'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9253: for(var _fun9253_ip = 0; ; ) switch(_fun9253_ip) {
case 0:
                r0 = a0;
                r1 = null;
                if(!(r1 != r0)) { _fun9253_ip = 11; continue _fun9253 }
case 9:
                return r0;
case 11:
                r0 = global;
                r2 = r0.Error;
                r1 = a1;
                r0 = ' 缺失';
                r3 = r1 + r0;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r4 = r1;
                r0 = originalConstruct(r2, [r3, r2]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        r3['requireField'] = r1;
        r1 = 1;
        r1 = r6[r1];
        r1 = r5.bind(r0)(r1);
        r1 = r4.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 0.0001;
        r3['RANGE_EPSILON'] = r1;
        var _closure1_slot1 = r1;
        return r0;
    };
factories[874]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r67 = a1;
        r2 = a5;
        r68 = a6;
        r0 = 0;
        r1 = r68[r0];
        r0 = undefined;
        r66 = r67.bind(r0)(r1);
        r1 = global;
        r5 = r1.Object;
        r4 = r5.defineProperty;
        r3 = {};
        r1 = true;
        r3['value'] = r1;
        r1 = '__esModule';
        r1 = r4.bind(r5)(r2, r1, r3);
        r2['MODEL64_TEMPLATE_DATA'] = r0;
        r1 = 1;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r65 = r66.bind(r0)(r1);
        r1 = 2;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r64 = r66.bind(r0)(r1);
        r1 = 3;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r63 = r66.bind(r0)(r1);
        r1 = 4;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r62 = r66.bind(r0)(r1);
        r1 = 5;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r61 = r66.bind(r0)(r1);
        r1 = 6;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r60 = r66.bind(r0)(r1);
        r1 = 7;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r59 = r66.bind(r0)(r1);
        r1 = 8;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r58 = r66.bind(r0)(r1);
        r1 = 9;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r57 = r66.bind(r0)(r1);
        r1 = 10;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r56 = r66.bind(r0)(r1);
        r1 = 11;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r55 = r66.bind(r0)(r1);
        r1 = 12;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r54 = r66.bind(r0)(r1);
        r1 = 13;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r53 = r66.bind(r0)(r1);
        r1 = 14;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r52 = r66.bind(r0)(r1);
        r1 = 15;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r51 = r66.bind(r0)(r1);
        r1 = 16;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r50 = r66.bind(r0)(r1);
        r1 = 17;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r49 = r66.bind(r0)(r1);
        r1 = 18;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r48 = r66.bind(r0)(r1);
        r1 = 19;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r47 = r66.bind(r0)(r1);
        r1 = 20;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r46 = r66.bind(r0)(r1);
        r1 = 21;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r45 = r66.bind(r0)(r1);
        r1 = 22;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r44 = r66.bind(r0)(r1);
        r1 = 23;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r43 = r66.bind(r0)(r1);
        r1 = 24;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r42 = r66.bind(r0)(r1);
        r1 = 25;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r41 = r66.bind(r0)(r1);
        r1 = 26;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r40 = r66.bind(r0)(r1);
        r1 = 27;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r39 = r66.bind(r0)(r1);
        r1 = 28;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r38 = r66.bind(r0)(r1);
        r1 = 29;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r37 = r66.bind(r0)(r1);
        r1 = 30;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r36 = r66.bind(r0)(r1);
        r1 = 31;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r35 = r66.bind(r0)(r1);
        r1 = 32;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r34 = r66.bind(r0)(r1);
        r1 = 33;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r33 = r66.bind(r0)(r1);
        r1 = 34;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r32 = r66.bind(r0)(r1);
        r1 = 35;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r31 = r66.bind(r0)(r1);
        r1 = 36;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r30 = r66.bind(r0)(r1);
        r1 = 37;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r29 = r66.bind(r0)(r1);
        r1 = 38;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r28 = r66.bind(r0)(r1);
        r1 = 39;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r27 = r66.bind(r0)(r1);
        r1 = 40;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r26 = r66.bind(r0)(r1);
        r1 = 41;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r25 = r66.bind(r0)(r1);
        r1 = 42;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r24 = r66.bind(r0)(r1);
        r1 = 43;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r23 = r66.bind(r0)(r1);
        r1 = 44;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r22 = r66.bind(r0)(r1);
        r1 = 45;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r21 = r66.bind(r0)(r1);
        r1 = 46;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r20 = r66.bind(r0)(r1);
        r1 = 47;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r19 = r66.bind(r0)(r1);
        r1 = 48;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r18 = r66.bind(r0)(r1);
        r1 = 49;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r17 = r66.bind(r0)(r1);
        r1 = 50;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r16 = r66.bind(r0)(r1);
        r1 = 51;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r15 = r66.bind(r0)(r1);
        r1 = 52;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r14 = r66.bind(r0)(r1);
        r1 = 53;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r13 = r66.bind(r0)(r1);
        r1 = 54;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r12 = r66.bind(r0)(r1);
        r1 = 55;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r11 = r66.bind(r0)(r1);
        r1 = 56;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r10 = r66.bind(r0)(r1);
        r1 = 57;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r9 = r66.bind(r0)(r1);
        r1 = 58;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r8 = r66.bind(r0)(r1);
        r1 = 59;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r7 = r66.bind(r0)(r1);
        r1 = 60;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r6 = r66.bind(r0)(r1);
        r1 = 61;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r5 = r66.bind(r0)(r1);
        r1 = 62;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r4 = r66.bind(r0)(r1);
        r1 = 63;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r3 = r66.bind(r0)(r1);
        r1 = 64;
        r1 = r68[r1];
        r1 = r67.bind(r0)(r1);
        r1 = r66.bind(r0)(r1);
        r66 = r65.default;
        r65 = r64.default;
        r64 = r63.default;
        r63 = r62.default;
        r62 = r61.default;
        r61 = r60.default;
        r60 = r59.default;
        r59 = r58.default;
        r58 = r57.default;
        r57 = r56.default;
        r56 = r55.default;
        r55 = r54.default;
        r54 = r53.default;
        r53 = r52.default;
        r52 = r51.default;
        r51 = r50.default;
        r50 = r49.default;
        r49 = r48.default;
        r48 = r47.default;
        r47 = r46.default;
        r46 = r45.default;
        r45 = r44.default;
        r44 = r43.default;
        r43 = r42.default;
        r42 = r41.default;
        r41 = r40.default;
        r40 = r39.default;
        r39 = r38.default;
        r38 = r37.default;
        r37 = r36.default;
        r36 = r35.default;
        r35 = r34.default;
        r34 = r33.default;
        r33 = r32.default;
        r32 = r31.default;
        r31 = r30.default;
        r30 = r29.default;
        r29 = r28.default;
        r28 = r27.default;
        r27 = r26.default;
        r26 = r25.default;
        r25 = r24.default;
        r24 = r23.default;
        r23 = r22.default;
        r22 = r21.default;
        r21 = r20.default;
        r20 = r19.default;
        r19 = r18.default;
        r18 = r17.default;
        r17 = r16.default;
        r16 = r15.default;
        r15 = r14.default;
        r14 = r13.default;
        r13 = r12.default;
        r12 = r11.default;
        r11 = r10.default;
        r10 = r9.default;
        r9 = r8.default;
        r8 = r7.default;
        r7 = r6.default;
        r6 = r5.default;
        r5 = r4.default;
        r4 = r3.default;
        r3 = r1.default;
        r1 = {};
        r1['bitter:folk-family'] = r66;
        r1['bitter:instrumental-family'] = r65;
        r1['bitter:pop-family'] = r64;
        r1['bitter:rhythm-family'] = r63;
        r1['calm:folk-family'] = r62;
        r1['calm:instrumental-family'] = r61;
        r1['calm:pop-family'] = r60;
        r1['calm:rhythm-family'] = r59;
        r1['confident:folk-family'] = r58;
        r1['confident:instrumental-family'] = r57;
        r1['confident:pop-family'] = r56;
        r1['confident:rhythm-family'] = r55;
        r1['default:folk-family'] = r54;
        r1['default:instrumental-family'] = r53;
        r1['default:pop-family'] = r52;
        r1['default:rhythm-family'] = r51;
        r1['depressed:folk-family'] = r50;
        r1['depressed:instrumental-family'] = r49;
        r1['depressed:pop-family'] = r48;
        r1['depressed:rhythm-family'] = r47;
        r1['ecstatic:folk-family'] = r46;
        r1['ecstatic:instrumental-family'] = r45;
        r1['ecstatic:pop-family'] = r44;
        r1['ecstatic:rhythm-family'] = r43;
        r1['energetic:folk-family'] = r42;
        r1['energetic:instrumental-family'] = r41;
        r1['energetic:pop-family'] = r40;
        r1['energetic:rhythm-family'] = r39;
        r1['healing:folk-family'] = r38;
        r1['healing:instrumental-family'] = r37;
        r1['healing:pop-family'] = r36;
        r1['healing:rhythm-family'] = r35;
        r1['joyful:folk-family'] = r34;
        r1['joyful:instrumental-family'] = r33;
        r1['joyful:pop-family'] = r32;
        r1['joyful:rhythm-family'] = r31;
        r1['lonely:folk-family'] = r30;
        r1['lonely:instrumental-family'] = r29;
        r1['lonely:pop-family'] = r28;
        r1['lonely:rhythm-family'] = r27;
        r1['nostalgic:folk-family'] = r26;
        r1['nostalgic:instrumental-family'] = r25;
        r1['nostalgic:pop-family'] = r24;
        r1['nostalgic:rhythm-family'] = r23;
        r1['passionate:folk-family'] = r22;
        r1['passionate:instrumental-family'] = r21;
        r1['passionate:pop-family'] = r20;
        r1['passionate:rhythm-family'] = r19;
        r1['relaxed:folk-family'] = r18;
        r1['relaxed:instrumental-family'] = r17;
        r1['relaxed:pop-family'] = r16;
        r1['relaxed:rhythm-family'] = r15;
        r1['romantic:folk-family'] = r14;
        r1['romantic:instrumental-family'] = r13;
        r1['romantic:pop-family'] = r12;
        r1['romantic:rhythm-family'] = r11;
        r1['sad:folk-family'] = r10;
        r1['sad:instrumental-family'] = r9;
        r1['sad:pop-family'] = r8;
        r1['sad:rhythm-family'] = r7;
        r1['sweet:folk-family'] = r6;
        r1['sweet:instrumental-family'] = r5;
        r1['sweet:pop-family'] = r4;
        r1['sweet:rhythm-family'] = r3;
        r2['MODEL64_TEMPLATE_DATA'] = r1;
        return r0;
    };
factories[875]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r0 = [0.4, 0.4, 0.8, 1.3, 0.8, 0];
        r2 = -0.3;
        r0[6] = r2;
        r2 = -0.8;
        r0[7] = r2;
        r3 = -1.5;
        r0[8] = r3;
        r0[9] = r2;
        r10 = {};
        r2 = true;
        r10['on'] = r2;
        r10['eqs'] = r0;
        r0 = new Array(9);
        r2 = {'band': 0, 'type': 6, 'gain': 1.8, 'freq': 400, 'q': 0.5, 'on': true};
        r0[0] = r2;
        r2 = {'band': 1, 'type': 6, 'gain': 1, 'freq': 2500, 'q': 1, 'on': true};
        r0[1] = r2;
        r2 = {'band': 2, 'type': 6, 'gain': 1, 'freq': 750, 'q': 0.7, 'on': true};
        r0[2] = r2;
        r2 = {'band': 3, 'type': 5, 'gain': -0.8, 'freq': 4500, 'q': 1, 'on': true};
        r0[3] = r2;
        r2 = {'on': false, 'band': 4};
        r0[4] = r2;
        r2 = {'on': false, 'band': 5};
        r0[5] = r2;
        r2 = {'on': false, 'band': 6};
        r0[6] = r2;
        r2 = {'on': false, 'band': 7};
        r0[7] = r2;
        r2 = {'on': false, 'band': 8};
        r0[8] = r2;
        r9 = {'on': true, 'gain': -1};
        r9['f'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r11;
        r1['eq'] = r10;
        r1['peq'] = r9;
        r1['rvb'] = r8;
        r1['limiter'] = r7;
        r7 = {'on': true, 'presence': 0, 'stereoizer': 1.8, 'sshaper': true, 'ambience': 0};
        r1['se'] = r7;
        r1['bt'] = r6;
        r1['rotate'] = r5;
        r5 = {'on': true, 'upmix': 1, 'lronoff': 0, 'lrangle': 10, 'otheronoff': 0, 'global': -0.1, 'center': 0.8, 'lfe': 0, 'fl': -0.3, 'fr': -0.3, 'left': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'ur': 0};
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '苦情-pop';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[876]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r0 = [0.5, 0.5, 1, 1.5, 0.8, 0];
        r2 = -0.6;
        r0[6] = r2;
        r3 = -0.8;
        r0[7] = r3;
        r3 = -1.4;
        r0[8] = r3;
        r0[9] = r2;
        r10 = {};
        r2 = true;
        r10['on'] = r2;
        r10['eqs'] = r0;
        r0 = new Array(9);
        r2 = {'band': 0, 'type': 6, 'gain': 0.5, 'freq': 420, 'q': 1, 'on': true};
        r0[0] = r2;
        r2 = {'band': 1, 'type': 6, 'gain': 1.5, 'freq': 1800, 'q': 0.7, 'on': true};
        r0[1] = r2;
        r2 = {'band': 2, 'type': 5, 'gain': -0.7, 'freq': 3500, 'q': 0.6, 'on': true};
        r0[2] = r2;
        r2 = {'on': false, 'band': 3};
        r0[3] = r2;
        r2 = {'on': false, 'band': 4};
        r0[4] = r2;
        r2 = {'on': false, 'band': 5};
        r0[5] = r2;
        r2 = {'on': false, 'band': 6};
        r0[6] = r2;
        r2 = {'on': false, 'band': 7};
        r0[7] = r2;
        r2 = {'on': false, 'band': 8};
        r0[8] = r2;
        r9 = {'on': true, 'gain': -0.5};
        r9['f'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r11;
        r1['eq'] = r10;
        r1['peq'] = r9;
        r1['rvb'] = r8;
        r1['limiter'] = r7;
        r7 = {'on': true, 'presence': 0, 'stereoizer': 2.8, 'sshaper': true, 'ambience': 0};
        r1['se'] = r7;
        r1['bt'] = r6;
        r1['rotate'] = r5;
        r5 = {'on': true, 'upmix': 1, 'lronoff': 0, 'lrangle': 10, 'otheronoff': 0, 'global': 0, 'center': 0.3, 'lfe': 0, 'fl': -0.2, 'fr': -0.2, 'left': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'ur': 0};
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '苦情-instru';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[877]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r0 = [0.6, 0.6, 1.1, 1.6, 0.6, 0];
        r2 = -0.4;
        r0[6] = r2;
        r2 = -1;
        r0[7] = r2;
        r3 = -2;
        r0[8] = r3;
        r0[9] = r2;
        r10 = {'eqs': null, 'on': true, 'useable': true};
        r10['eqs'] = r0;
        r0 = new Array(9);
        r2 = {'band': 0, 'freq': 450, 'gain': 2, 'on': true, 'q': 0.6, 'type': 6};
        r0[0] = r2;
        r2 = {'band': 1, 'freq': 2200, 'gain': 0.8, 'on': true, 'q': 1.2, 'type': 6};
        r0[1] = r2;
        r2 = {'band': 2, 'freq': 800, 'gain': 1.2, 'on': true, 'q': 0.8, 'type': 6};
        r0[2] = r2;
        r2 = {'band': 3, 'freq': 4000, 'gain': -1, 'on': true, 'q': 1, 'type': 5};
        r0[3] = r2;
        r2 = {'on': false, 'band': 4};
        r0[4] = r2;
        r2 = {'on': false, 'band': 5};
        r0[5] = r2;
        r2 = {'on': false, 'band': 6};
        r0[6] = r2;
        r2 = {'on': false, 'band': 7};
        r0[7] = r2;
        r2 = {'on': false, 'band': 8};
        r0[8] = r2;
        r9 = {'f': null, 'gain': -1.25, 'on': true, 'useable': true};
        r9['f'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r11;
        r1['eq'] = r10;
        r1['peq'] = r9;
        r1['rvb'] = r8;
        r1['limiter'] = r7;
        r7 = {'ambience': 0, 'on': true, 'presence': 0, 'sshaper': true, 'stereoizer': 1.5, 'useable': true};
        r1['se'] = r7;
        r1['bt'] = r6;
        r1['rotate'] = r5;
        r5 = {'center': 1, 'fl': -0.4, 'fr': -0.4, 'global': -0.1, 'left': 0, 'lfe': 0, 'lrangle': 10, 'lronoff': 0, 'on': true, 'otheronoff': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'upmix': 1, 'ur': 0, 'useable': true};
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[878]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r0 = [0.8, 0.8, 0.8, 1.2, 0.4, 0];
        r2 = -0.5;
        r0[6] = r2;
        r2 = -1;
        r0[7] = r2;
        r2 = -1.8;
        r0[8] = r2;
        r2 = -0.8;
        r0[9] = r2;
        r10 = {};
        r2 = true;
        r10['on'] = r2;
        r10['eqs'] = r0;
        r0 = new Array(9);
        r2 = {'band': 0, 'type': 6, 'gain': 1.5, 'freq': 500, 'q': 0.7, 'on': true};
        r0[0] = r2;
        r2 = {'band': 1, 'type': 6, 'gain': 0.6, 'freq': 2000, 'q': 1.5, 'on': true};
        r0[1] = r2;
        r2 = {'band': 2, 'type': 6, 'gain': 1, 'freq': 850, 'q': 0.9, 'on': true};
        r0[2] = r2;
        r2 = {'band': 3, 'type': 5, 'gain': -1, 'freq': 3500, 'q': 1, 'on': true};
        r0[3] = r2;
        r2 = {'on': false, 'band': 4};
        r0[4] = r2;
        r2 = {'on': false, 'band': 5};
        r0[5] = r2;
        r2 = {'on': false, 'band': 6};
        r0[6] = r2;
        r2 = {'on': false, 'band': 7};
        r0[7] = r2;
        r2 = {'on': false, 'band': 8};
        r0[8] = r2;
        r9 = {'on': true, 'gain': -1};
        r9['f'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r11;
        r1['eq'] = r10;
        r1['peq'] = r9;
        r1['rvb'] = r8;
        r1['limiter'] = r7;
        r7 = {'on': true, 'presence': 0, 'stereoizer': 1, 'sshaper': true, 'ambience': 0};
        r1['se'] = r7;
        r1['bt'] = r6;
        r1['rotate'] = r5;
        r5 = {'on': true, 'upmix': 1, 'lronoff': 0, 'lrangle': 10, 'otheronoff': 0, 'global': -0.1, 'center': 1, 'lfe': 0, 'fl': -0.3, 'fr': -0.3, 'left': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'ur': 0};
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '苦情-rhythm';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[879]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'gain': 1.6, 'freq': 90, 'band': 0, 'type': 6, 'on': true, 'q': 4.5};
        r1[0] = r2;
        r2 = {'gain': -1.5, 'freq': 480, 'band': 1, 'type': 6, 'on': true, 'q': 2};
        r1[1] = r2;
        r2 = {'gain': 0.5, 'freq': 3500, 'band': 2, 'type': 6, 'on': true, 'q': 2};
        r1[2] = r2;
        r2 = {'gain': -0.4, 'freq': 1500, 'band': 3, 'type': 6, 'on': true, 'q': 2};
        r1[3] = r2;
        r2 = {'gain': 0.1, 'freq': 9000, 'band': 4, 'type': 5, 'on': true, 'q': 0.1};
        r1[4] = r2;
        r2 = {'gain': 0.5, 'freq': 6000, 'band': 5, 'type': 6, 'on': true, 'q': 1.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r14 = {};
        r14['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -8, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7000, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r15 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 160, 'q': 10, 'diffusion': 80, 'on': true, 'dtime': 2};
        r0['rvb'] = r15;
        r0['on'] = r1;
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -27};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '中性';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0, 'sshaper': true, 'ambience': 5, 'stereoizer': 0.2, 'on': true};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[880]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'gain': 1.6, 'freq': 90, 'band': 0, 'type': 6, 'on': true, 'q': 4.5};
        r1[0] = r2;
        r2 = {'gain': -1.5, 'freq': 480, 'band': 1, 'type': 6, 'on': true, 'q': 2};
        r1[1] = r2;
        r2 = {'gain': 0.8, 'freq': 3500, 'band': 2, 'type': 6, 'on': true, 'q': 2};
        r1[2] = r2;
        r2 = {'gain': -0.4, 'freq': 1500, 'band': 3, 'type': 6, 'on': true, 'q': 2};
        r1[3] = r2;
        r2 = {'gain': 0.1, 'freq': 9000, 'band': 4, 'type': 5, 'on': true, 'q': 0.1};
        r1[4] = r2;
        r2 = {'gain': 0.5, 'freq': 6000, 'band': 5, 'type': 6, 'on': true, 'q': 1.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r14 = {};
        r14['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -8, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7000, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r15 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 160, 'q': 10, 'diffusion': 80, 'on': true, 'dtime': 2};
        r0['rvb'] = r15;
        r0['on'] = r1;
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -24};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '中性';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0.1, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.2};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[881]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 90, 'band': 0, 'type': 6, 'q': 4.5, 'gain': 1.6};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'gain': -1.5};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'gain': 1};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 1500, 'band': 3, 'type': 6, 'q': 2, 'gain': -0.4};
        r1[3] = r2;
        r2 = {'on': true, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'gain': 0.1};
        r1[4] = r2;
        r2 = {'on': true, 'freq': 6000, 'band': 5, 'type': 6, 'q': 1.5, 'gain': 0.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r14 = {};
        r14['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -8, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7000, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r15 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 160, 'q': 10, 'diffusion': 80, 'on': true, 'dtime': 2};
        r0['rvb'] = r15;
        r0['on'] = r1;
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -26};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '中性';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0, 'sshaper': true, 'stereoizer': 0.2, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[882]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'gain': 1.6, 'freq': 90, 'band': 0, 'type': 6, 'on': true, 'q': 4.5};
        r1[0] = r2;
        r2 = {'gain': -1.5, 'freq': 480, 'band': 1, 'type': 6, 'on': true, 'q': 2};
        r1[1] = r2;
        r2 = {'gain': 0.8, 'freq': 3500, 'band': 2, 'type': 6, 'on': true, 'q': 2};
        r1[2] = r2;
        r2 = {'gain': -0.4, 'freq': 1500, 'band': 3, 'type': 6, 'on': true, 'q': 2};
        r1[3] = r2;
        r2 = {'gain': 0.1, 'freq': 9000, 'band': 4, 'type': 5, 'on': true, 'q': 0.1};
        r1[4] = r2;
        r2 = {'gain': 0.5, 'freq': 6000, 'band': 5, 'type': 6, 'on': true, 'q': 1.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r14 = {};
        r14['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -8, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7000, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r15 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 160, 'q': 10, 'diffusion': 80, 'on': true, 'dtime': 2};
        r0['rvb'] = r15;
        r0['on'] = r1;
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -28};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '中性';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0, 'sshaper': true, 'ambience': 5, 'stereoizer': 0.2, 'on': true};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[883]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 35, 'band': 0, 'q': 0.8, 'type': 1, 'gain': 0};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 250, 'band': 1, 'q': 1, 'type': 6, 'gain': -0.2};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3000, 'band': 2, 'q': 1, 'type': 6, 'gain': 1.8};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 5000, 'band': 3, 'q': 0.9, 'type': 6, 'gain': 0.8};
        r1[3] = r2;
        r2 = {'band': 4, 'on': false};
        r1[4] = r2;
        r2 = {'band': 5, 'on': false};
        r1[5] = r2;
        r2 = {'band': 6, 'on': false};
        r1[6] = r2;
        r2 = {'band': 7, 'on': false};
        r1[7] = r2;
        r2 = {'band': 8, 'on': false};
        r1[8] = r2;
        r4 = {'f': null, 'gain': 0, 'on': true};
        r4['f'] = r1;
        r3 = {};
        r3['on'] = r0;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -2, 'freq': 180, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -2, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 15, 'hfdamping': 4.5, 'rshape': 52, 'density': 52, 'swidth': 95, 'q': 4, 'diffusion': 56, 'on': true, 'dtime': 0.7};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 13, 'rsize': 8, 'on': true, 'sdelay': 6};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': -96.3};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -28, 'rvb': -32};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': -96.3, 'front': 0, 'rear': -5, 'center': -1};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r7 = {'treble': 0.8, 'trebleFreq': 6000, 'on': true, 'bass': 0.8, 'trebleQ': 0.7, 'bassQ': 0.7, 'bassFreq': 200};
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '自信-国风';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1, 'sshaper': true, 'ambience': 1, 'on': true, 'stereoizer': 1.2};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[884]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r0 = new Array(9);
        r2 = {'on': true, 'freq': 35, 'band': 0, 'q': 0.8, 'type': 1, 'gain': 0};
        r0[0] = r2;
        r2 = {'on': true, 'freq': 250, 'band': 1, 'q': 1, 'type': 6, 'gain': -0.4};
        r0[1] = r2;
        r2 = {'on': true, 'freq': 3000, 'band': 2, 'q': 1, 'type': 6, 'gain': 1.4};
        r0[2] = r2;
        r2 = {'on': true, 'freq': 5000, 'band': 3, 'q': 0.9, 'type': 6, 'gain': 0.6};
        r0[3] = r2;
        r2 = {'band': 4, 'on': false};
        r0[4] = r2;
        r2 = {'band': 5, 'on': false};
        r0[5] = r2;
        r2 = {'band': 6, 'on': false};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r2 = {'band': 8, 'on': false};
        r0[8] = r2;
        r4 = {'f': null, 'gain': 0, 'on': true};
        r4['f'] = r0;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r7 = {'treble': 0.4, 'trebleFreq': 6000, 'on': true, 'bass': 0.2, 'trebleQ': 0.7, 'bassQ': 0.7, 'bassFreq': 200};
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '自信-古典';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1, 'sshaper': true, 'stereoizer': 1, 'on': true, 'ambience': 1};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[885]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 35, 'band': 0, 'q': 0.8, 'type': 1, 'gain': 0};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 250, 'band': 1, 'q': 1, 'type': 6, 'gain': -0.4};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3000, 'band': 2, 'q': 1, 'type': 6, 'gain': 2.4};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 5000, 'band': 3, 'q': 0.9, 'type': 6, 'gain': 1};
        r1[3] = r2;
        r2 = {'band': 4, 'on': false};
        r1[4] = r2;
        r2 = {'band': 5, 'on': false};
        r1[5] = r2;
        r2 = {'band': 6, 'on': false};
        r1[6] = r2;
        r2 = {'band': 7, 'on': false};
        r1[7] = r2;
        r2 = {'band': 8, 'on': false};
        r1[8] = r2;
        r4 = {'f': null, 'gain': 0, 'on': true};
        r4['f'] = r1;
        r3 = {};
        r3['on'] = r0;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -2, 'freq': 180, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -2, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 15, 'hfdamping': 4.5, 'rshape': 52, 'density': 52, 'swidth': 95, 'q': 4, 'diffusion': 56, 'on': true, 'dtime': 0.7};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 13, 'rsize': 8, 'on': true, 'sdelay': 6};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': -96.3};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -28, 'rvb': -32};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': -96.3, 'front': 0, 'rear': -5, 'center': -1};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r7 = {'treble': 1.2, 'trebleFreq': 6000, 'on': true, 'bass': 0.8, 'trebleQ': 0.7, 'bassQ': 0.7, 'bassFreq': 200};
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '自信-去糊';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': true, 'ambience': 1, 'on': true, 'stereoizer': 1.2};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[886]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 35, 'band': 0, 'q': 0.8, 'type': 1, 'gain': 0};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 250, 'band': 1, 'q': 1, 'type': 6, 'gain': -0.6};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3000, 'band': 2, 'q': 1, 'type': 6, 'gain': 2.4};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 5000, 'band': 3, 'q': 0.9, 'type': 6, 'gain': 1.2};
        r1[3] = r2;
        r2 = {'band': 4, 'on': false};
        r1[4] = r2;
        r2 = {'band': 5, 'on': false};
        r1[5] = r2;
        r2 = {'band': 6, 'on': false};
        r1[6] = r2;
        r2 = {'band': 7, 'on': false};
        r1[7] = r2;
        r2 = {'band': 8, 'on': false};
        r1[8] = r2;
        r4 = {'f': null, 'gain': 0, 'on': true};
        r4['f'] = r1;
        r3 = {};
        r3['on'] = r0;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -2, 'freq': 180, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -2, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 15, 'hfdamping': 4.5, 'rshape': 52, 'density': 52, 'swidth': 95, 'q': 4, 'diffusion': 56, 'on': true, 'dtime': 0.6};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 13, 'rsize': 8, 'on': true, 'sdelay': 6};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': -96.3};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -28, 'rvb': -32};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': -96.3, 'front': 0, 'rear': -5, 'center': -1};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r7 = {'treble': 1.2, 'trebleFreq': 6000, 'on': true, 'bass': 0.4, 'trebleQ': 0.7, 'bassQ': 0.7, 'bassFreq': 200};
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '自信-电子';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 2, 'sshaper': true, 'ambience': 1, 'on': true, 'stereoizer': 1.5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[887]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r0 = new Array(9);
        r2 = {'on': true, 'freq': 80, 'band': 0, 'type': 10, 'q': 1, 'gain': 0.5};
        r0[0] = r2;
        r2 = {'on': true, 'freq': 460, 'band': 1, 'type': 6, 'q': 3.5, 'gain': -0.5};
        r0[1] = r2;
        r2 = {'on': true, 'freq': 4000, 'band': 2, 'type': 8, 'q': 2, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'on': true, 'freq': 8500, 'band': 3, 'type': 6, 'q': 0.8, 'gain': 0.4};
        r0[3] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[4] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[5] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[6] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[7] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[8] = r2;
        r5 = {'f': null, 'gain': 0, 'on': true};
        r5['f'] = r0;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r16 = {};
        r16['on'] = r1;
        r0 = new Array(3);
        r2 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -9, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r2;
        r2 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r2;
        r2 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r2;
        r15 = {};
        r15['f'] = r0;
        r0 = true;
        r15['on'] = r0;
        r2 = {};
        r17 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 2};
        r2['rvb'] = r17;
        r2['on'] = r0;
        r2['er'] = r16;
        r2['tc'] = r15;
        r15 = {'center': 0, 'lfe': 0};
        r2['il'] = r15;
        r15 = {'dry': 0, 'er': -30, 'rvb': -30};
        r2['ol'] = r15;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '保底';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name', 'noise'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[888]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r0 = new Array(9);
        r2 = {'on': true, 'freq': 80, 'band': 0, 'type': 10, 'q': 1, 'gain': 0.5};
        r0[0] = r2;
        r2 = {'on': true, 'freq': 460, 'band': 1, 'type': 6, 'q': 3.5, 'gain': -0.9};
        r0[1] = r2;
        r2 = {'on': true, 'freq': 4000, 'band': 2, 'type': 8, 'q': 2, 'gain': -0.4};
        r0[2] = r2;
        r2 = {'on': true, 'freq': 8500, 'band': 3, 'type': 6, 'q': 0.8, 'gain': 0.4};
        r0[3] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[4] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[5] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[6] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[7] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[8] = r2;
        r5 = {'f': null, 'gain': 0, 'on': true};
        r5['f'] = r0;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r16 = {};
        r16['on'] = r1;
        r0 = new Array(3);
        r2 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -9, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r2;
        r2 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r2;
        r2 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r2;
        r15 = {};
        r15['f'] = r0;
        r0 = true;
        r15['on'] = r0;
        r2 = {};
        r17 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 2};
        r2['rvb'] = r17;
        r2['on'] = r0;
        r2['er'] = r16;
        r2['tc'] = r15;
        r15 = {'center': 0, 'lfe': 0};
        r2['il'] = r15;
        r15 = {'dry': 0, 'er': -30, 'rvb': -27};
        r2['ol'] = r15;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '保底';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name', 'noise'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[889]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r0 = new Array(9);
        r2 = {'on': true, 'freq': 80, 'band': 0, 'type': 10, 'q': 1, 'gain': 0.5};
        r0[0] = r2;
        r2 = {'on': true, 'freq': 460, 'band': 1, 'type': 6, 'q': 3.5, 'gain': -0.5};
        r0[1] = r2;
        r2 = {'on': true, 'freq': 4000, 'band': 2, 'type': 8, 'q': 2, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'on': true, 'freq': 8500, 'band': 3, 'type': 6, 'q': 0.8, 'gain': 0.4};
        r0[3] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[4] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[5] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[6] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[7] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[8] = r2;
        r5 = {'f': null, 'gain': 0, 'on': true};
        r5['f'] = r0;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r15 = {};
        r15['on'] = r1;
        r0 = new Array(3);
        r2 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -9, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r2;
        r2 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r2;
        r2 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r2;
        r14 = {};
        r14['f'] = r0;
        r0 = true;
        r14['on'] = r0;
        r2 = {};
        r16 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 2};
        r2['rvb'] = r16;
        r2['on'] = r0;
        r2['er'] = r15;
        r2['tc'] = r14;
        r14 = {'center': 0, 'lfe': 0};
        r2['il'] = r14;
        r14 = {'dry': 0, 'er': -30, 'rvb': -30};
        r2['ol'] = r14;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = 'default';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[890]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r0 = new Array(9);
        r2 = {'on': true, 'freq': 80, 'band': 0, 'type': 10, 'q': 1, 'gain': 0.5};
        r0[0] = r2;
        r2 = {'on': true, 'freq': 460, 'band': 1, 'type': 6, 'q': 3.5, 'gain': -0.5};
        r0[1] = r2;
        r2 = {'on': true, 'freq': 4000, 'band': 2, 'type': 8, 'q': 2, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'on': true, 'freq': 8500, 'band': 3, 'type': 6, 'q': 0.8, 'gain': 0.4};
        r0[3] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[4] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[5] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[6] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[7] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[8] = r2;
        r5 = {'f': null, 'gain': 0, 'on': true};
        r5['f'] = r0;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r16 = {};
        r16['on'] = r1;
        r0 = new Array(3);
        r2 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -9, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r2;
        r2 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r2;
        r2 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r2;
        r15 = {};
        r15['f'] = r0;
        r0 = true;
        r15['on'] = r0;
        r2 = {};
        r17 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 2};
        r2['rvb'] = r17;
        r2['on'] = r0;
        r2['er'] = r16;
        r2['tc'] = r15;
        r15 = {'center': 0, 'lfe': 0};
        r2['il'] = r15;
        r15 = {'dry': 0, 'er': -30, 'rvb': -30};
        r2['ol'] = r15;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '保底';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name', 'noise'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[891]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 90, 'band': 0, 'type': 6, 'q': 3.5, 'gain': 1};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'gain': -1.5};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'gain': -2.2};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 1500, 'band': 3, 'type': 6, 'q': 2, 'gain': -1};
        r1[3] = r2;
        r2 = {'on': true, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'gain': 0.2};
        r1[4] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -5, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 3};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 15, 'rsize': 100, 'sdelay': 50, 'on': true};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -30};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '音效配置 3-24 10:16';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 5, 'sshaper': true, 'stereoizer': 0.2, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[892]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 90, 'band': 0, 'type': 6, 'q': 3.5, 'gain': 1};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'gain': -2};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'gain': -2.4};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 1500, 'band': 3, 'type': 6, 'q': 2, 'gain': -1};
        r1[3] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[4] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -5, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 3};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 15, 'rsize': 100, 'sdelay': 50, 'on': true};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -27};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '音效配置 3-24 10:16';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 6, 'sshaper': true, 'stereoizer': 0.2, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[893]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 90, 'band': 0, 'type': 6, 'q': 3.5, 'gain': 1};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'gain': -1.5};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'gain': -2};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 1500, 'band': 3, 'type': 6, 'q': 2, 'gain': -1};
        r1[3] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[4] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -5, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 3};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 15, 'rsize': 100, 'sdelay': 50, 'on': true};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -25};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '负向';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 7, 'sshaper': true, 'stereoizer': 0.2, 'useable': true, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[894]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 90, 'band': 0, 'type': 6, 'q': 3.5, 'gain': 1};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'gain': -1.5};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'gain': -2};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 1500, 'band': 3, 'type': 6, 'q': 2, 'gain': -1};
        r1[3] = r2;
        r2 = {'on': true, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'gain': -0.4};
        r1[4] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 2, 'gain': -5, 'freq': 700, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 2, 'gain': -8, 'freq': 7800, 'curve': 2, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 0, 'band': 3, 'q': 0.5, 'gain': -4, 'freq': 8000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r13 = {};
        r13['f'] = r0;
        r1 = true;
        r13['on'] = r1;
        r0 = {};
        r14 = {'pdelay': 80, 'hfdamping': 0.5, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 3};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 15, 'rsize': 100, 'sdelay': 50, 'on': true};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -32};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '音效配置 3-24 10:16';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 5, 'sshaper': true, 'stereoizer': 0.2, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[895]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r0 = new Array(8);
        r2 = {'gain': 1.6, 'freq': 90, 'band': 0, 'type': 6, 'q': 4.5, 'on': true};
        r0[0] = r2;
        r2 = {'gain': -1.5, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'on': true};
        r0[1] = r2;
        r2 = {'gain': 1, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'on': true};
        r0[2] = r2;
        r2 = {'band': 3, 'on': false};
        r0[3] = r2;
        r2 = {'gain': 0.2, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'on': true};
        r0[4] = r2;
        r2 = {'band': 5, 'on': false};
        r0[5] = r2;
        r2 = {'band': 6, 'on': false};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r3 = {'f': null, 'gain': -0.2, 'on': true};
        r3['f'] = r0;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '2';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.1};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[896]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r0 = new Array(8);
        r2 = {'gain': 1.6, 'freq': 90, 'band': 0, 'type': 6, 'q': 4.5, 'on': true};
        r0[0] = r2;
        r2 = {'gain': -1.5, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'on': true};
        r0[1] = r2;
        r2 = {'gain': 1, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'on': true};
        r0[2] = r2;
        r2 = {'band': 3, 'on': false};
        r0[3] = r2;
        r2 = {'gain': 0.2, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'on': true};
        r0[4] = r2;
        r2 = {'band': 5, 'on': false};
        r0[5] = r2;
        r2 = {'band': 6, 'on': false};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r3 = {'f': null, 'gain': -0.2, 'on': true};
        r3['f'] = r0;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '2';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.2};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[897]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r0 = new Array(9);
        r2 = {'on': true, 'freq': 90, 'band': 0, 'type': 6, 'q': 4.5, 'gain': 1.6};
        r0[0] = r2;
        r2 = {'on': true, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'gain': -1.5};
        r0[1] = r2;
        r2 = {'on': true, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'gain': 0.5};
        r0[2] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[3] = r2;
        r2 = {'on': true, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'gain': 0.2};
        r0[4] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[5] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[6] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[7] = r2;
        r2 = {};
        r2['on'] = r1;
        r0[8] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r0;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0.5, 'infiniteRatio': true, 'ratio': 2, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '2';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0.5, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.2};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[898]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r1 = false;
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r0 = new Array(8);
        r2 = {'gain': 1.6, 'freq': 90, 'band': 0, 'type': 6, 'q': 4.5, 'on': true};
        r0[0] = r2;
        r2 = {'gain': -1.5, 'freq': 480, 'band': 1, 'type': 6, 'q': 2, 'on': true};
        r0[1] = r2;
        r2 = {'gain': 1, 'freq': 3500, 'band': 2, 'type': 6, 'q': 2, 'on': true};
        r0[2] = r2;
        r2 = {'band': 3, 'on': false};
        r0[3] = r2;
        r2 = {'gain': 0.2, 'freq': 9000, 'band': 4, 'type': 5, 'q': 0.1, 'on': true};
        r0[4] = r2;
        r2 = {'band': 5, 'on': false};
        r0[5] = r2;
        r2 = {'band': 6, 'on': false};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r3 = {'f': null, 'gain': -0.2, 'on': true};
        r3['f'] = r0;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '2';
        r1['effect_name'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 0.5, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.3};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[899]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r1 = [0, 0, 0, 0, 0, 0.6, 0.6, 0];
        r2 = -0.4;
        r1[8] = r2;
        r2 = -1;
        r1[9] = r2;
        r8 = {};
        r8['eqs'] = r1;
        r1 = true;
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r2 = new Array(9);
        r3 = {'gain': 0.5, 'freq': 63, 'band': 0, 'type': 6, 'on': true, 'q': 2};
        r2[0] = r3;
        r3 = {'gain': 3, 'freq': 1000, 'band': 1, 'type': 6, 'on': true, 'q': 2};
        r2[1] = r3;
        r3 = {'gain': 2, 'freq': 2000, 'band': 2, 'type': 6, 'on': true, 'q': 2};
        r2[2] = r3;
        r3 = {'gain': 2, 'freq': 4000, 'band': 3, 'type': 6, 'on': true, 'q': 2};
        r2[3] = r3;
        r3 = {'gain': 1.5, 'freq': 8000, 'band': 4, 'type': 5, 'on': true, 'q': 2};
        r2[4] = r3;
        r3 = {'gain': 2.5, 'freq': 16000, 'band': 5, 'type': 5, 'on': true, 'q': 2};
        r2[5] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[8] = r3;
        r3 = {'f': null, 'gain': 0.5, 'on': true};
        r3['f'] = r2;
        r2 = {};
        r2['on'] = r0;
        r13 = {};
        r13['on'] = r0;
        r0 = {};
        r14 = {'pdelay': 55, 'swidth': 110, 'hfdamping': 5.5, 'density': 48, 'rshape': 38, 'q': 4, 'diffusion': 55, 'dtime': 0.9};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 10, 'on': true, 'sdelay': 8, 'rsize': 45};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -24.2};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '活力民谣';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'alc', 'effect_name', 'noise'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': false, 'ambience': 5, 'on': true, 'stereoizer': 3};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[900]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'gain': 0.5, 'freq': 63, 'band': 0, 'type': 6, 'on': true, 'q': 2};
        r1[0] = r2;
        r2 = {'gain': 3, 'freq': 1000, 'band': 1, 'type': 6, 'on': true, 'q': 2};
        r1[1] = r2;
        r2 = {'gain': 2, 'freq': 2000, 'band': 2, 'type': 6, 'on': true, 'q': 2};
        r1[2] = r2;
        r2 = {'gain': 2, 'freq': 4000, 'band': 3, 'type': 6, 'on': true, 'q': 2};
        r1[3] = r2;
        r2 = {'gain': 1.5, 'freq': 8000, 'band': 4, 'type': 5, 'on': true, 'q': 2};
        r1[4] = r2;
        r2 = {'gain': 2.5, 'freq': 16000, 'band': 5, 'type': 5, 'on': true, 'q': 2};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': 0.5, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r13 = {};
        r13['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 55, 'hfdamping': 5.5, 'rshape': 38, 'density': 48, 'swidth': 130, 'q': 4, 'diffusion': 60, 'on': true, 'dtime': 1.2};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r14 = {'pattern': 10, 'on': true, 'sdelay': 8, 'rsize': 45};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -23};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '活力纯音乐';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': false, 'ambience': 5, 'on': true, 'stereoizer': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[901]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 63, 'band': 0, 'type': 6, 'q': 2, 'gain': 0.5};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 1000, 'band': 1, 'type': 6, 'q': 2, 'gain': 3};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 2000, 'band': 2, 'type': 6, 'q': 2, 'gain': 2};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 4000, 'band': 3, 'type': 6, 'q': 2, 'gain': 2};
        r1[3] = r2;
        r2 = {'on': true, 'freq': 8000, 'band': 4, 'type': 5, 'q': 2, 'gain': 1.5};
        r1[4] = r2;
        r2 = {'on': true, 'freq': 16000, 'band': 5, 'type': 5, 'q': 2, 'gain': 2.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': 0.5, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r13 = {};
        r13['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 55, 'swidth': 110, 'hfdamping': 5.5, 'density': 48, 'rshape': 38, 'q': 4, 'diffusion': 55, 'dtime': 0.9};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r14 = {'pattern': 10, 'rsize': 45, 'sdelay': 8, 'on': true};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -32, 'rvb': -26};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '活力';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'alc', 'effect_name', 'noise'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': false, 'stereoizer': 4, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[902]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r0 = false;
        r12['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r1 = [0, 1, 0, 0, 0, 0, 0, 0, 0.8, 0.8];
        r8['eqs'] = r1;
        r1 = true;
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r2 = new Array(9);
        r3 = {'gain': 0.5, 'freq': 63, 'band': 0, 'type': 6, 'on': true, 'q': 2};
        r2[0] = r3;
        r3 = {'gain': 3, 'freq': 1000, 'band': 1, 'type': 6, 'on': true, 'q': 2};
        r2[1] = r3;
        r3 = {'gain': 2, 'freq': 2000, 'band': 2, 'type': 6, 'on': true, 'q': 2};
        r2[2] = r3;
        r3 = {'gain': 2, 'freq': 4000, 'band': 3, 'type': 6, 'on': true, 'q': 2};
        r2[3] = r3;
        r3 = {'gain': 1.5, 'freq': 8000, 'band': 4, 'type': 5, 'on': true, 'q': 2};
        r2[4] = r3;
        r3 = {'gain': 2.5, 'freq': 16000, 'band': 5, 'type': 5, 'on': true, 'q': 2};
        r2[5] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[8] = r3;
        r3 = {'f': null, 'gain': 0.5, 'on': true};
        r3['f'] = r2;
        r2 = {};
        r2['on'] = r0;
        r13 = {};
        r13['on'] = r0;
        r0 = {};
        r14 = {'pdelay': 55, 'hfdamping': 5.5, 'rshape': 38, 'density': 48, 'swidth': 110, 'q': 4, 'diffusion': 55, 'on': true, 'dtime': 0.7};
        r0['rvb'] = r14;
        r0['on'] = r1;
        r14 = {'pattern': 10, 'on': true, 'sdelay': 8, 'rsize': 45};
        r0['er'] = r14;
        r0['tc'] = r13;
        r13 = {'center': 0, 'lfe': 0};
        r0['il'] = r13;
        r13 = {'dry': 0, 'er': -30, 'rvb': -28};
        r0['ol'] = r13;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '活力节奏';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': false, 'ambience': 5, 'on': true, 'stereoizer': 2.5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[903]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r0 = false;
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r1 = [0, 0, 0, 0.6, 0.4, 1, 1, 0, 0, 0];
        r7['eqs'] = r1;
        r1 = true;
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r2 = new Array(9);
        r3 = {'on': true, 'freq': 63, 'band': 0, 'type': 6, 'q': 2, 'gain': 0};
        r2[0] = r3;
        r3 = {'on': true, 'freq': 125, 'band': 1, 'type': 6, 'q': 2, 'gain': 1};
        r2[1] = r3;
        r3 = {'on': true, 'freq': 250, 'band': 2, 'type': 6, 'q': 2, 'gain': 1.5};
        r2[2] = r3;
        r3 = {'on': true, 'freq': 500, 'band': 3, 'type': 6, 'q': 2, 'gain': 1.2};
        r2[3] = r3;
        r3 = {'on': true, 'freq': 1000, 'band': 4, 'type': 6, 'q': 2, 'gain': 1.8};
        r2[4] = r3;
        r3 = {'on': true, 'freq': 2000, 'band': 5, 'type': 6, 'q': 2, 'gain': 1.5};
        r2[5] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[8] = r3;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r2;
        r2 = {};
        r2['on'] = r0;
        r12 = {};
        r12['on'] = r0;
        r0 = {};
        r13 = {'pdelay': 110, 'hfdamping': 4, 'rshape': 50, 'density': 37, 'swidth': 100, 'q': 5, 'diffusion': 60, 'on': true, 'dtime': 1.1};
        r0['rvb'] = r13;
        r0['on'] = r1;
        r13 = {'pattern': 12, 'on': true, 'sdelay': 25, 'rsize': 55};
        r0['er'] = r13;
        r0['tc'] = r12;
        r12 = {'center': 0, 'lfe': 0};
        r0['il'] = r12;
        r12 = {'dry': 0, 'er': -30, 'rvb': -27};
        r0['ol'] = r12;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['noise'] = r9;
        r1['chorus'] = r8;
        r1['eq'] = r7;
        r1['cmp'] = r6;
        r6 = {'treble': 1.5, 'trebleFreq': 6000, 'on': true, 'bass': 1, 'trebleQ': 0.5, 'bassQ': 0.5, 'useable': true, 'bassFreq': 200};
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'core': true, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '治愈民谣';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.2, 'sshaper': false, 'stereoizer': 4.5, 'on': true, 'ambience': 4};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[904]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r0 = false;
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 63, 'band': 0, 'type': 6, 'q': 2, 'gain': 0};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 125, 'band': 1, 'type': 6, 'q': 2, 'gain': 1};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 250, 'band': 2, 'type': 6, 'q': 2, 'gain': 1.5};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 500, 'band': 3, 'type': 6, 'q': 2, 'gain': 1.2};
        r1[3] = r2;
        r2 = {'on': true, 'freq': 1000, 'band': 4, 'type': 6, 'q': 2, 'gain': 1.8};
        r1[4] = r2;
        r2 = {'on': true, 'freq': 2000, 'band': 5, 'type': 6, 'q': 2, 'gain': 1.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r12 = {};
        r12['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 110, 'hfdamping': 4, 'rshape': 50, 'density': 37, 'swidth': 118, 'q': 5, 'diffusion': 68, 'on': true, 'dtime': 1.4};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r13 = {'pattern': 12, 'rsize': 55, 'on': true, 'sdelay': 25};
        r0['er'] = r13;
        r0['tc'] = r12;
        r12 = {'center': 0, 'lfe': 0};
        r0['il'] = r12;
        r12 = {'dry': 0, 'er': -30, 'rvb': -24};
        r0['ol'] = r12;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['noise'] = r9;
        r1['chorus'] = r8;
        r1['eq'] = r7;
        r1['cmp'] = r6;
        r6 = {'treble': 1.5, 'trebleFreq': 6000, 'on': true, 'bass': 1, 'trebleQ': 0.5, 'bassQ': 0.5, 'useable': true, 'bassFreq': 200};
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'core': true, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '治愈';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.2, 'sshaper': false, 'ambience': 4, 'stereoizer': 6, 'on': true};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[905]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r0 = false;
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(9);
        r2 = {'on': true, 'freq': 63, 'band': 0, 'type': 6, 'q': 2, 'gain': 0};
        r1[0] = r2;
        r2 = {'on': true, 'freq': 125, 'band': 1, 'type': 6, 'q': 2, 'gain': 1};
        r1[1] = r2;
        r2 = {'on': true, 'freq': 250, 'band': 2, 'type': 6, 'q': 2, 'gain': 1.5};
        r1[2] = r2;
        r2 = {'on': true, 'freq': 500, 'band': 3, 'type': 6, 'q': 2, 'gain': 1.2};
        r1[3] = r2;
        r2 = {'on': true, 'freq': 1000, 'band': 4, 'type': 6, 'q': 2, 'gain': 1.8};
        r1[4] = r2;
        r2 = {'on': true, 'freq': 2000, 'band': 5, 'type': 6, 'q': 2, 'gain': 1.5};
        r1[5] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[6] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[7] = r2;
        r2 = {};
        r2['on'] = r0;
        r1[8] = r2;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r12 = {};
        r12['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 110, 'hfdamping': 4, 'rshape': 50, 'density': 37, 'swidth': 100, 'q': 5, 'diffusion': 60, 'on': true, 'dtime': 1.1};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r13 = {'pattern': 12, 'on': true, 'sdelay': 25, 'rsize': 55};
        r0['er'] = r13;
        r0['tc'] = r12;
        r12 = {'center': 0, 'lfe': 0};
        r0['il'] = r12;
        r12 = {'dry': 0, 'er': -30, 'rvb': -26};
        r0['ol'] = r12;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['noise'] = r9;
        r1['chorus'] = r8;
        r1['eq'] = r7;
        r1['cmp'] = r6;
        r6 = {'treble': 1.5, 'trebleFreq': 6000, 'on': true, 'bass': 1, 'trebleQ': 0.5, 'bassQ': 0.5, 'useable': true, 'bassFreq': 200};
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'core': true, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '治愈';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.2, 'sshaper': false, 'ambience': 4, 'stereoizer': 5, 'on': true};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[906]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r0 = false;
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r1 = [0, 1, 0, 0, 0, 0, 0, 0, 0, 0];
        r7['eqs'] = r1;
        r1 = true;
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r2 = new Array(9);
        r3 = {'on': true, 'freq': 63, 'band': 0, 'type': 6, 'q': 2, 'gain': 0};
        r2[0] = r3;
        r3 = {'on': true, 'freq': 125, 'band': 1, 'type': 6, 'q': 2, 'gain': 1};
        r2[1] = r3;
        r3 = {'on': true, 'freq': 250, 'band': 2, 'type': 6, 'q': 2, 'gain': 1.5};
        r2[2] = r3;
        r3 = {'on': true, 'freq': 500, 'band': 3, 'type': 6, 'q': 2, 'gain': 1.2};
        r2[3] = r3;
        r3 = {'on': true, 'freq': 1000, 'band': 4, 'type': 6, 'q': 2, 'gain': 1.8};
        r2[4] = r3;
        r3 = {'on': true, 'freq': 2000, 'band': 5, 'type': 6, 'q': 2, 'gain': 1.5};
        r2[5] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r0;
        r2[8] = r3;
        r3 = {'f': null, 'gain': -1, 'on': true};
        r3['f'] = r2;
        r2 = {};
        r2['on'] = r0;
        r12 = {};
        r12['on'] = r0;
        r0 = {};
        r13 = {'pdelay': 110, 'hfdamping': 4, 'rshape': 50, 'density': 37, 'swidth': 85, 'q': 5, 'diffusion': 50, 'on': true, 'dtime': 0.8};
        r0['rvb'] = r13;
        r0['on'] = r1;
        r13 = {'pattern': 12, 'on': true, 'sdelay': 25, 'rsize': 55};
        r0['er'] = r13;
        r0['tc'] = r12;
        r12 = {'center': 0, 'lfe': 0};
        r0['il'] = r12;
        r12 = {'dry': 0, 'er': -30, 'rvb': -27};
        r0['ol'] = r12;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['noise'] = r9;
        r1['chorus'] = r8;
        r1['eq'] = r7;
        r1['cmp'] = r6;
        r6 = {'treble': 1.5, 'trebleFreq': 6000, 'on': true, 'bass': 1, 'trebleQ': 0.5, 'bassQ': 0.5, 'useable': true, 'bassFreq': 200};
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.08, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '治愈';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.2, 'sshaper': false, 'stereoizer': 4, 'on': true, 'ambience': 4};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[907]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r0 = new Array(10);
        r2 = -1;
        r0[0] = r2;
        r3 = -0.5;
        r0[1] = r3;
        r3 = 0;
        r0[2] = r3;
        r0[3] = r2;
        r0[4] = r2;
        r2 = 1;
        r0[5] = r2;
        r2 = 1.5;
        r0[6] = r2;
        r2 = 2;
        r0[7] = r2;
        r3 = 2.5;
        r0[8] = r3;
        r0[9] = r2;
        r8 = {};
        r8['eqs'] = r0;
        r0 = true;
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r8 = {'attack': 0.025, 'on': true, 'pregain': 0, 'release': 0.12, 'range': 0, 'ratio': 1.5, 'postgain': 0, 'threshold': -12};
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '欢快v2-folk se';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'peq', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'eq', 'rvb', 'se', 'bt', 'noise', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 4, 'sshaper': false, 'stereoizer': 3, 'on': true, 'ambience': 1};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[908]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r0 = new Array(10);
        r2 = -1.5;
        r0[0] = r2;
        r2 = -1;
        r0[1] = r2;
        r2 = 0;
        r0[2] = r2;
        r2 = -1.2;
        r0[3] = r2;
        r0[4] = r2;
        r2 = 1.2;
        r0[5] = r2;
        r2 = 1.8;
        r0[6] = r2;
        r2 = 2.2;
        r0[7] = r2;
        r2 = 2.8;
        r0[8] = r2;
        r2 = 2.5;
        r0[9] = r2;
        r8 = {};
        r8['eqs'] = r0;
        r0 = true;
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r8 = {'attack': 0.03, 'on': true, 'pregain': 0, 'release': 0.15, 'range': 0, 'ratio': 1.2, 'postgain': 0, 'threshold': -8};
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '欢快v2-instrumental se';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 2, 'sshaper': false, 'stereoizer': 6, 'on': true, 'ambience': 1};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[909]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r0 = new Array(10);
        r2 = -2;
        r0[0] = r2;
        r2 = -1.5;
        r0[1] = r2;
        r2 = -0.5;
        r0[2] = r2;
        r0[3] = r2;
        r2 = 0;
        r0[4] = r2;
        r2 = 1.5;
        r0[5] = r2;
        r2 = 2;
        r0[6] = r2;
        r2 = 2.5;
        r0[7] = r2;
        r0[8] = r2;
        r0[9] = r2;
        r8 = {};
        r8['eqs'] = r0;
        r0 = true;
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r8 = {'attack': 0.015, 'on': true, 'pregain': 0, 'release': 0.08, 'range': 0, 'ratio': 2.5, 'postgain': 1, 'threshold': -15};
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '欢快-pop V2 se+pre';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 2, 'sshaper': false, 'ambience': 1, 'on': true, 'stereoizer': 3};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[910]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r0 = new Array(10);
        r2 = -0.5;
        r0[0] = r2;
        r2 = 0;
        r0[1] = r2;
        r2 = 0.5;
        r0[2] = r2;
        r2 = -2;
        r0[3] = r2;
        r0[4] = r2;
        r2 = 1.8;
        r0[5] = r2;
        r2 = 2.5;
        r0[6] = r2;
        r2 = 2.8;
        r0[7] = r2;
        r2 = 3.8;
        r0[8] = r2;
        r2 = 3.2;
        r0[9] = r2;
        r8 = {};
        r8['eqs'] = r0;
        r0 = true;
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r12;
        r1['alc'] = r11;
        r1['noise'] = r10;
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r8 = {'attack': 0.008, 'on': true, 'pregain': 0, 'release': 0.04, 'range': 0, 'ratio': 3, 'postgain': 2, 'threshold': -18};
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '欢快v2-rhythm se';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'peq', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'eq', 'rvb', 'se', 'bt', 'noise', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 2, 'sshaper': false, 'ambience': 1, 'on': true, 'stereoizer': 3};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[911]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r10 = {};
        r0 = false;
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r1 = [0, 0, 0, 0.5, 0, 1, 1, 0.6, 0, 0];
        r8['eqs'] = r1;
        r1 = true;
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r2 = new Array(8);
        r3 = {'q': 2, 'freq': 31, 'band': 0, 'type': 6, 'on': true, 'gain': 1.5};
        r2[0] = r3;
        r3 = {'q': 2, 'freq': 63, 'band': 1, 'type': 6, 'on': true, 'gain': 2};
        r2[1] = r3;
        r3 = {'q': 2, 'freq': 125, 'band': 2, 'type': 6, 'on': true, 'gain': 1};
        r2[2] = r3;
        r3 = {'q': 2, 'freq': 250, 'band': 3, 'type': 6, 'on': true, 'gain': 0.5};
        r2[3] = r3;
        r3 = {'q': 2, 'freq': 1000, 'band': 4, 'type': 6, 'on': true, 'gain': 1};
        r2[4] = r3;
        r3 = {'q': 2, 'freq': 2000, 'band': 5, 'type': 6, 'on': true, 'gain': -0.5};
        r2[5] = r3;
        r3 = {'q': 2, 'freq': 4000, 'band': 6, 'type': 6, 'on': true, 'gain': -1.5};
        r2[6] = r3;
        r3 = {'q': 2, 'freq': 8000, 'band': 7, 'type': 5, 'on': true, 'gain': -2};
        r2[7] = r3;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r2;
        r2 = {};
        r2['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r0 = {};
        r12 = {'pdelay': 135, 'hfdamping': 3.5, 'rshape': 58, 'density': 72, 'swidth': 130, 'q': 5, 'diffusion': 42, 'on': true, 'dtime': 1.2};
        r0['rvb'] = r12;
        r0['on'] = r1;
        r12 = {'pattern': 15, 'on': true, 'sdelay': 38, 'rsize': 75};
        r0['er'] = r12;
        r0['tc'] = r11;
        r11 = {'center': 0, 'lfe': 0};
        r0['il'] = r11;
        r11 = {'dry': 0, 'er': -30, 'rvb': -25};
        r0['ol'] = r11;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r10;
        r1['alc'] = r9;
        r9 = {'mix': 0.25, 'voices': 2, 'delay': 15, 'depth': 0.4, 'on': true, 'rate': 0.3};
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = 'lonely-pop';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 3};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[912]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r0 = false;
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(8);
        r2 = {'gain': 1.5, 'freq': 31, 'band': 0, 'q': 2, 'type': 6, 'on': true};
        r1[0] = r2;
        r2 = {'gain': 2, 'freq': 63, 'band': 1, 'q': 2, 'type': 6, 'on': true};
        r1[1] = r2;
        r2 = {'gain': 1, 'freq': 125, 'band': 2, 'q': 2, 'type': 6, 'on': true};
        r1[2] = r2;
        r2 = {'gain': 0.5, 'freq': 250, 'band': 3, 'q': 2, 'type': 6, 'on': true};
        r1[3] = r2;
        r2 = {'gain': 1, 'freq': 1000, 'band': 4, 'q': 2, 'type': 6, 'on': true};
        r1[4] = r2;
        r2 = {'gain': -0.5, 'freq': 2000, 'band': 5, 'q': 2, 'type': 6, 'on': true};
        r1[5] = r2;
        r2 = {'gain': -1.5, 'freq': 4000, 'band': 6, 'q': 2, 'type': 6, 'on': true};
        r1[6] = r2;
        r2 = {'gain': -2, 'freq': 8000, 'band': 7, 'q': 2, 'type': 5, 'on': true};
        r1[7] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r12 = {};
        r12['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 135, 'hfdamping': 3.5, 'rshape': 58, 'density': 72, 'swidth': 140, 'q': 5, 'diffusion': 48, 'on': true, 'dtime': 1.5};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r13 = {'pattern': 15, 'rsize': 75, 'on': true, 'sdelay': 38};
        r0['er'] = r13;
        r0['tc'] = r12;
        r12 = {'center': 0, 'lfe': 0};
        r0['il'] = r12;
        r12 = {'dry': 0, 'er': -30, 'rvb': -23};
        r0['ol'] = r12;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['noise'] = r9;
        r9 = {'mix': 0.25, 'voices': 2, 'delay': 15, 'depth': 0.4, 'on': true, 'rate': 0.3};
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = 'lonely-pop';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name', 'noise'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': true, 'stereoizer': 4.5, 'on': true, 'ambience': 6};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[913]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r11 = {};
        r0 = false;
        r11['on'] = r0;
        r10 = {};
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(8);
        r2 = {'q': 2, 'freq': 31, 'band': 0, 'type': 6, 'on': true, 'gain': 1.5};
        r1[0] = r2;
        r2 = {'q': 2, 'freq': 63, 'band': 1, 'type': 6, 'on': true, 'gain': 2};
        r1[1] = r2;
        r2 = {'q': 2, 'freq': 125, 'band': 2, 'type': 6, 'on': true, 'gain': 1};
        r1[2] = r2;
        r2 = {'q': 2, 'freq': 250, 'band': 3, 'type': 6, 'on': true, 'gain': 0.5};
        r1[3] = r2;
        r2 = {'q': 2, 'freq': 1000, 'band': 4, 'type': 6, 'on': true, 'gain': 1};
        r1[4] = r2;
        r2 = {'q': 2, 'freq': 2000, 'band': 5, 'type': 6, 'on': true, 'gain': -0.5};
        r1[5] = r2;
        r2 = {'q': 2, 'freq': 4000, 'band': 6, 'type': 6, 'on': true, 'gain': -1.5};
        r1[6] = r2;
        r2 = {'q': 2, 'freq': 8000, 'band': 7, 'type': 5, 'on': true, 'gain': -2};
        r1[7] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r12 = {};
        r12['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 135, 'hfdamping': 3.5, 'rshape': 58, 'density': 72, 'swidth': 130, 'q': 5, 'diffusion': 42, 'on': true, 'dtime': 1.2};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r13 = {'pattern': 15, 'on': true, 'sdelay': 38, 'rsize': 75};
        r0['er'] = r13;
        r0['tc'] = r12;
        r12 = {'center': 0, 'lfe': 0};
        r0['il'] = r12;
        r12 = {'dry': 0, 'er': -30, 'rvb': -24};
        r0['ol'] = r12;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r11;
        r1['alc'] = r10;
        r1['noise'] = r9;
        r9 = {'mix': 0.25, 'voices': 2, 'delay': 15, 'depth': 0.4, 'on': true, 'rate': 0.3};
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.12, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'useable': true, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = 'lonely-pop';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name', 'noise'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': true, 'stereoizer': 4, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[914]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r10 = {};
        r0 = false;
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r0;
        r7 = {};
        r7['on'] = r0;
        r6 = {};
        r6['on'] = r0;
        r5 = {};
        r5['on'] = r0;
        r4 = {};
        r4['on'] = r0;
        r1 = new Array(8);
        r2 = {'q': 2, 'freq': 31, 'band': 0, 'type': 6, 'on': true, 'gain': 2};
        r1[0] = r2;
        r2 = {'q': 2, 'freq': 63, 'band': 1, 'type': 6, 'on': true, 'gain': 3};
        r1[1] = r2;
        r2 = {'q': 2, 'freq': 125, 'band': 2, 'type': 6, 'on': true, 'gain': 1};
        r1[2] = r2;
        r2 = {'q': 2, 'freq': 250, 'band': 3, 'type': 6, 'on': true, 'gain': 0.5};
        r1[3] = r2;
        r2 = {'q': 2, 'freq': 1000, 'band': 4, 'type': 6, 'on': true, 'gain': 1};
        r1[4] = r2;
        r2 = {'q': 2, 'freq': 2000, 'band': 5, 'type': 6, 'on': true, 'gain': -0.5};
        r1[5] = r2;
        r2 = {'q': 2, 'freq': 4000, 'band': 6, 'type': 6, 'on': true, 'gain': -0.5};
        r1[6] = r2;
        r2 = {'q': 2, 'freq': 8000, 'band': 7, 'type': 5, 'on': true, 'gain': -1};
        r1[7] = r2;
        r3 = {'f': null, 'gain': 0, 'on': true};
        r3['f'] = r1;
        r2 = {};
        r2['on'] = r0;
        r11 = {};
        r11['on'] = r0;
        r0 = {};
        r1 = {'pdelay': 135, 'hfdamping': 3.5, 'rshape': 58, 'density': 72, 'swidth': 130, 'q': 5, 'diffusion': 35, 'on': true, 'dtime': 0.9};
        r0['rvb'] = r1;
        r1 = true;
        r0['on'] = r1;
        r12 = {'pattern': 15, 'on': true, 'sdelay': 38, 'rsize': 75};
        r0['er'] = r12;
        r0['tc'] = r11;
        r11 = {'center': 0, 'lfe': 0};
        r0['il'] = r11;
        r11 = {'dry': 0, 'er': -30, 'rvb': -26};
        r0['ol'] = r11;
        r0['useable'] = r1;
        r1 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r0['rl'] = r1;
        r1 = {};
        r1['rotate'] = r10;
        r1['alc'] = r9;
        r9 = {'mix': 0.25, 'voices': 2, 'delay': 15, 'depth': 0.4, 'on': true, 'rate': 0.3};
        r1['chorus'] = r9;
        r1['eq'] = r8;
        r1['cmp'] = r7;
        r1['bt'] = r6;
        r1['flanger'] = r5;
        r1['spspatial'] = r4;
        r1['peq'] = r3;
        r3 = {'attack': 0.005, 'on': true, 'release': 0.08, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 2, 'threshold': -0.1};
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = 'lonely-pop';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'effect_name'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 1.5, 'sshaper': true, 'stereoizer': 4, 'on': true, 'ambience': 5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[915]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r2 = [0, 0, 0.6, 0];
        r0 = -0.8;
        r2[4] = r0;
        r0 = -0.6;
        r2[5] = r0;
        r2[6] = r0;
        r0 = 0.2;
        r2[7] = r0;
        r2[8] = r0;
        r2[9] = r0;
        r11 = {};
        r0 = true;
        r11['on'] = r0;
        r11['eqs'] = r2;
        r10 = {};
        r10['on'] = r1;
        r3 = new Array(3);
        r2 = {'name': '滤波器1', 'band': 1, 'insert': 3, 'curve': 0, 'gain': -6, 'freq': 600, 'q': 1};
        r3[0] = r2;
        r2 = {'name': '滤波器2', 'band': 2, 'insert': 3, 'curve': 2, 'gain': -6, 'freq': 7000, 'q': 1};
        r3[1] = r2;
        r2 = {'name': '滤波器3', 'band': 3, 'insert': 0, 'curve': 0, 'gain': 0, 'freq': 1000, 'q': 1};
        r3[2] = r2;
        r2 = {};
        r2['on'] = r0;
        r2['f'] = r3;
        r9 = {};
        r3 = {'pdelay': 20, 'dtime': 2, 'hfdamping': 2.5, 'density': 50, 'rshape': 50, 'q': 6, 'diffusion': 60, 'swidth': 140, 'on': true};
        r9['rvb'] = r3;
        r9['on'] = r0;
        r3 = {'pattern': 20, 'rsize': 60, 'sdelay': 50, 'on': true};
        r9['er'] = r3;
        r9['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r9['il'] = r2;
        r2 = {'dry': 0, 'er': -30, 'rvb': -25};
        r9['ol'] = r2;
        r9['useable'] = r0;
        r0 = {'front': 0, 'rear': 0, 'center': 0, 'lfe': 0};
        r9['rl'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r12;
        r1['eq'] = r11;
        r1['peq'] = r10;
        r1['rvb'] = r9;
        r9 = {'on': true, 'threshold': -1, 'attack': 0.05, 'release': 0.1, 'outputgain': 0.5, 'infiniteRatio': true, 'ratio': 2};
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '思念-folk';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[916]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r2 = [0, 0, 0.6, 0];
        r0 = -0.8;
        r2[4] = r0;
        r0 = -0.6;
        r2[5] = r0;
        r2[6] = r0;
        r0 = 0.2;
        r2[7] = r0;
        r2[8] = r0;
        r2[9] = r0;
        r11 = {};
        r0 = true;
        r11['on'] = r0;
        r11['eqs'] = r2;
        r10 = {};
        r10['on'] = r1;
        r3 = new Array(3);
        r2 = {'name': '滤波器1', 'band': 1, 'insert': 3, 'curve': 0, 'gain': -6, 'freq': 600, 'q': 1};
        r3[0] = r2;
        r2 = {'name': '滤波器2', 'band': 2, 'insert': 3, 'curve': 2, 'gain': -6, 'freq': 7000, 'q': 1};
        r3[1] = r2;
        r2 = {'name': '滤波器3', 'band': 3, 'insert': 0, 'curve': 0, 'gain': 0, 'freq': 1000, 'q': 1};
        r3[2] = r2;
        r2 = {};
        r2['on'] = r0;
        r2['f'] = r3;
        r9 = {};
        r3 = {'pdelay': 20, 'dtime': 2.3, 'hfdamping': 2.3, 'density': 50, 'rshape': 50, 'q': 6, 'diffusion': 80, 'swidth': 160, 'on': true};
        r9['rvb'] = r3;
        r9['on'] = r0;
        r3 = {'pattern': 20, 'rsize': 60, 'sdelay': 50, 'on': true};
        r9['er'] = r3;
        r9['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r9['il'] = r2;
        r2 = {'dry': 0, 'er': -30, 'rvb': -25};
        r9['ol'] = r2;
        r9['useable'] = r0;
        r0 = {'front': 0, 'rear': 0, 'center': 0, 'lfe': 0};
        r9['rl'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r12;
        r1['eq'] = r11;
        r1['peq'] = r10;
        r1['rvb'] = r9;
        r9 = {'on': true, 'threshold': -1, 'attack': 0.05, 'release': 0.1, 'outputgain': 0.5, 'infiniteRatio': true, 'ratio': 2};
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '思念-ins';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[917]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r2 = [0, 0, 0.4, 0];
        r0 = -0.8;
        r2[4] = r0;
        r2[5] = r0;
        r0 = -0.6;
        r2[6] = r0;
        r0 = 0.2;
        r2[7] = r0;
        r2[8] = r0;
        r2[9] = r0;
        r11 = {};
        r0 = true;
        r11['on'] = r0;
        r11['eqs'] = r2;
        r10 = {};
        r10['on'] = r1;
        r3 = new Array(3);
        r2 = {'name': '滤波器1', 'band': 1, 'insert': 3, 'curve': 0, 'gain': -6, 'freq': 600, 'q': 1};
        r3[0] = r2;
        r2 = {'name': '滤波器2', 'band': 2, 'insert': 3, 'curve': 2, 'gain': -6, 'freq': 7000, 'q': 1};
        r3[1] = r2;
        r2 = {'name': '滤波器3', 'band': 3, 'insert': 0, 'curve': 0, 'gain': 0, 'freq': 1000, 'q': 1};
        r3[2] = r2;
        r2 = {};
        r2['on'] = r0;
        r2['f'] = r3;
        r9 = {};
        r3 = {'pdelay': 20, 'dtime': 2.2, 'hfdamping': 2.5, 'density': 50, 'rshape': 50, 'q': 6, 'diffusion': 60, 'swidth': 140, 'on': true};
        r9['rvb'] = r3;
        r9['on'] = r0;
        r3 = {'pattern': 20, 'rsize': 60, 'sdelay': 50, 'on': true};
        r9['er'] = r3;
        r9['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r9['il'] = r2;
        r2 = {'dry': 0, 'er': -30, 'rvb': -23};
        r9['ol'] = r2;
        r9['useable'] = r0;
        r0 = {'front': 0, 'rear': 0, 'center': 0, 'lfe': 0};
        r9['rl'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r12;
        r1['eq'] = r11;
        r1['peq'] = r10;
        r1['rvb'] = r9;
        r9 = {'on': true, 'threshold': -1, 'attack': 0.05, 'release': 0.1, 'outputgain': 0.5, 'infiniteRatio': true, 'ratio': 2};
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '思念-pop';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[918]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r12 = {};
        r1 = false;
        r12['on'] = r1;
        r2 = [0, 0, 0.6, 0];
        r0 = -1;
        r2[4] = r0;
        r0 = -0.8;
        r2[5] = r0;
        r0 = -0.6;
        r2[6] = r0;
        r0 = 0.2;
        r2[7] = r0;
        r2[8] = r0;
        r2[9] = r0;
        r11 = {};
        r0 = true;
        r11['on'] = r0;
        r11['eqs'] = r2;
        r10 = {};
        r10['on'] = r1;
        r3 = new Array(3);
        r2 = {'name': '滤波器1', 'band': 1, 'insert': 3, 'curve': 0, 'gain': -6, 'freq': 600, 'q': 1};
        r3[0] = r2;
        r2 = {'name': '滤波器2', 'band': 2, 'insert': 3, 'curve': 2, 'gain': -6, 'freq': 7000, 'q': 1};
        r3[1] = r2;
        r2 = {'name': '滤波器3', 'band': 3, 'insert': 0, 'curve': 0, 'gain': 0, 'freq': 1000, 'q': 1};
        r3[2] = r2;
        r2 = {};
        r2['on'] = r0;
        r2['f'] = r3;
        r9 = {};
        r3 = {'pdelay': 20, 'dtime': 2.2, 'hfdamping': 2.5, 'density': 50, 'rshape': 50, 'q': 6, 'diffusion': 60, 'swidth': 140, 'on': true};
        r9['rvb'] = r3;
        r9['on'] = r0;
        r3 = {'pattern': 20, 'rsize': 60, 'sdelay': 50, 'on': true};
        r9['er'] = r3;
        r9['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r9['il'] = r2;
        r2 = {'dry': 0, 'er': -30, 'rvb': -25};
        r9['ol'] = r2;
        r9['useable'] = r0;
        r0 = {'front': 0, 'rear': 0, 'center': 0, 'lfe': 0};
        r9['rl'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r12;
        r1['eq'] = r11;
        r1['peq'] = r10;
        r1['rvb'] = r9;
        r9 = {'on': true, 'threshold': -1, 'attack': 0.05, 'release': 0.1, 'outputgain': 0.5, 'infiniteRatio': true, 'ratio': 2};
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r1['spspatial'] = r5;
        r1['alc'] = r4;
        r1['chorus'] = r3;
        r1['flanger'] = r2;
        r1['dly'] = r0;
        r0 = '思念-rhythm';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'peq', 'rvb', 'se', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[919]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r0 = [0, 0];
        r2 = -10;
        r0[2] = r2;
        r0[3] = r2;
        r2 = -20;
        r0[4] = r2;
        r2 = -16;
        r0[5] = r2;
        r2 = -30;
        r0[6] = r2;
        r2 = -25;
        r0[7] = r2;
        r2 = -40;
        r0[8] = r2;
        r2 = -36;
        r0[9] = r2;
        r2 = -50;
        r0[10] = r2;
        r2 = -46;
        r0[11] = r2;
        r2 = -70;
        r0[12] = r2;
        r0[13] = r2;
        r2 = -200;
        r0[14] = r2;
        r0[15] = r2;
        r12 = {'attack': 0.005, 'hold': 0.05, 'on': true, 'lookahead': 0, 'xyknee': 0, 'release': 0.1, 'lt': 0.5, 'type': 0, 'xypoints': null, 'minisize': 64, 'st': 0.02};
        r12['xypoints'] = r0;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {'eqs': null, 'useable': true, 'on': true};
        r0 = [0.2, 2, 2.6, 3, 2.6, 2, 1.6, 2, 2, 0.2];
        r9['eqs'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r15 = {};
        r15['on'] = r1;
        r14 = {};
        r14['on'] = r1;
        r2 = {};
        r0 = {'pdelay': 40, 'hfdamping': 1.8, 'rshape': 80, 'density': 40, 'swidth': 180, 'q': 6, 'diffusion': 30, 'on': true, 'dtime': 1};
        r2['rvb'] = r0;
        r0 = true;
        r2['on'] = r0;
        r2['er'] = r15;
        r2['tc'] = r14;
        r14 = {'center': 0, 'lfe': 0};
        r2['il'] = r14;
        r14 = {'dry': 0, 'er': -30, 'rvb': -25};
        r2['ol'] = r14;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r4 = {'attack': 0.005, 'on': true, 'release': 0.1, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 4, 'threshold': -0.1};
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '热血-民谣';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'se', 'peq', 'rvb', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'noise', 'effect_name'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[920]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {'eqs': null, 'useable': true, 'on': true};
        r0 = [0.2, 2, 2.6, 3, 2.6, 2, 1.6, 2, 2, 0.2];
        r9['eqs'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r4 = {'attack': 0.005, 'on': true, 'release': 0.1, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 4, 'threshold': -0.1};
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '热血-纯音乐';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'se', 'peq', 'rvb', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'noise', 'effect_name'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[921]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r0 = [0, 0];
        r2 = -10;
        r0[2] = r2;
        r2 = -8;
        r0[3] = r2;
        r2 = -20;
        r0[4] = r2;
        r2 = -16;
        r0[5] = r2;
        r2 = -30;
        r0[6] = r2;
        r2 = -25;
        r0[7] = r2;
        r2 = -40;
        r0[8] = r2;
        r2 = -36;
        r0[9] = r2;
        r2 = -50;
        r0[10] = r2;
        r2 = -46;
        r0[11] = r2;
        r2 = -70;
        r0[12] = r2;
        r0[13] = r2;
        r2 = -200;
        r0[14] = r2;
        r0[15] = r2;
        r12 = {'attack': 0.005, 'hold': 0.05, 'on': true, 'lookahead': 0, 'xyknee': 0, 'release': 0.1, 'lt': 0.5, 'type': 0, 'xypoints': null, 'minisize': 64, 'st': 0.02};
        r12['xypoints'] = r0;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {'eqs': null, 'useable': true, 'on': true};
        r0 = [0.2, 2, 2.6, 3, 2.6, 2, 1.6, 2, 2, 0.2];
        r9['eqs'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r4 = {'attack': 0.005, 'on': true, 'release': 0.1, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 4, 'threshold': -0.1};
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '热血-流行';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'se', 'peq', 'rvb', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'noise', 'effect_name'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[922]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r0 = [0, 0];
        r2 = -10;
        r0[2] = r2;
        r0[3] = r2;
        r2 = -20;
        r0[4] = r2;
        r2 = -16;
        r0[5] = r2;
        r2 = -30;
        r0[6] = r2;
        r2 = -25;
        r0[7] = r2;
        r2 = -40;
        r0[8] = r2;
        r2 = -36;
        r0[9] = r2;
        r2 = -50;
        r0[10] = r2;
        r2 = -46;
        r0[11] = r2;
        r2 = -70;
        r0[12] = r2;
        r0[13] = r2;
        r2 = -200;
        r0[14] = r2;
        r0[15] = r2;
        r12 = {'attack': 0.005, 'hold': 0.05, 'on': true, 'lookahead': 0, 'xyknee': 0, 'release': 0.1, 'lt': 0.5, 'type': 0, 'xypoints': null, 'minisize': 64, 'st': 0.02};
        r12['xypoints'] = r0;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r0 = [0.2, 2.4, 3, 3, 2.6, 2, 1.4, 1.8, 1.8, 0.2];
        r9['eqs'] = r0;
        r0 = true;
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r4 = {'attack': 0.005, 'on': true, 'release': 0.1, 'outputgain': 0, 'infiniteRatio': true, 'ratio': 4, 'threshold': -0.2};
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '热血-节奏';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'se', 'peq', 'rvb', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'noise', 'effect_name'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[923]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r0 = [0, 0.6, 0.8, 0];
        r2 = -1;
        r0[4] = r2;
        r2 = 1.1;
        r0[5] = r2;
        r2 = -0.5;
        r0[6] = r2;
        r2 = -0.4;
        r0[7] = r2;
        r0[8] = r2;
        r2 = -0.2;
        r0[9] = r2;
        r9 = {};
        r9['eqs'] = r0;
        r0 = true;
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '轻松';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 4.5, 'sshaper': false, 'ambience': 2, 'on': true, 'stereoizer': 2.5};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[924]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r0 = [0, 0.6, 0.8];
        r2 = -0.2;
        r0[3] = r2;
        r3 = -1.5;
        r0[4] = r3;
        r3 = 0.7;
        r0[5] = r3;
        r3 = -0.5;
        r0[6] = r3;
        r3 = -0.4;
        r0[7] = r3;
        r3 = 0;
        r0[8] = r3;
        r0[9] = r2;
        r9 = {};
        r9['eqs'] = r0;
        r0 = true;
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '轻松';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 4.5, 'sshaper': false, 'ambience': 2, 'on': true, 'stereoizer': 3.1};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[925]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r0 = [0, 0.6, 0.6];
        r2 = -0.2;
        r0[3] = r2;
        r3 = -0.9;
        r0[4] = r3;
        r3 = 1.1;
        r0[5] = r3;
        r3 = -0.5;
        r0[6] = r3;
        r3 = -0.4;
        r0[7] = r3;
        r0[8] = r3;
        r0[9] = r2;
        r9 = {};
        r9['eqs'] = r0;
        r0 = true;
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '轻松';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 5, 'sshaper': false, 'ambience': 2, 'on': true, 'stereoizer': 2.3};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[926]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r10 = {};
        r10['on'] = r1;
        r0 = [0, 0.6, 0.8, 0];
        r2 = -1.3;
        r0[4] = r2;
        r2 = 1.1;
        r0[5] = r2;
        r2 = -0.5;
        r0[6] = r2;
        r2 = -0.4;
        r0[7] = r2;
        r0[8] = r2;
        r2 = -0.2;
        r0[9] = r2;
        r9 = {};
        r9['eqs'] = r0;
        r0 = true;
        r9['on'] = r0;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r13;
        r1['alc'] = r12;
        r1['noise'] = r11;
        r1['chorus'] = r10;
        r1['eq'] = r9;
        r1['cmp'] = r8;
        r1['bt'] = r7;
        r1['flanger'] = r6;
        r1['spspatial'] = r5;
        r1['peq'] = r4;
        r1['limiter'] = r3;
        r1['dly'] = r2;
        r2 = '轻松';
        r1['effect_name'] = r2;
        r2 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r2;
        r1['rvb'] = r0;
        r0 = {'presence': 4, 'sshaper': false, 'ambience': 2, 'on': true, 'stereoizer': 2};
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[927]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r0 = new Array(10);
        r2 = -2;
        r0[0] = r2;
        r2 = 0.8;
        r0[1] = r2;
        r2 = 1;
        r0[2] = r2;
        r3 = 2;
        r0[3] = r3;
        r3 = 1.6;
        r0[4] = r3;
        r0[5] = r2;
        r2 = -1;
        r0[6] = r2;
        r2 = -1.6;
        r0[7] = r2;
        r2 = 1.4;
        r0[8] = r2;
        r2 = 0.2;
        r0[9] = r2;
        r10 = {};
        r10['eqs'] = r0;
        r0 = true;
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r15 = {};
        r15['on'] = r1;
        r2 = {};
        r16 = {'pdelay': 70, 'hfdamping': 3, 'rshape': 100, 'density': 50, 'swidth': 180, 'q': 10, 'diffusion': 100, 'on': true, 'dtime': 2};
        r2['rvb'] = r16;
        r2['on'] = r0;
        r16 = {'pattern': 15, 'on': true, 'sdelay': 30, 'rsize': 100};
        r2['er'] = r16;
        r2['tc'] = r15;
        r15 = {'center': 0, 'lfe': 0};
        r2['il'] = r15;
        r15 = {'dry': 0, 'er': -30, 'rvb': -25};
        r2['ol'] = r15;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '浪漫-民谣';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[928]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r0 = new Array(10);
        r2 = -2;
        r0[0] = r2;
        r2 = 0.8;
        r0[1] = r2;
        r2 = 1;
        r0[2] = r2;
        r3 = 2;
        r0[3] = r3;
        r3 = 1.6;
        r0[4] = r3;
        r0[5] = r2;
        r2 = -1;
        r0[6] = r2;
        r2 = -1.6;
        r0[7] = r2;
        r2 = 1.4;
        r0[8] = r2;
        r2 = 0.2;
        r0[9] = r2;
        r10 = {};
        r10['eqs'] = r0;
        r0 = true;
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '浪漫-纯音乐';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'se', 'peq', 'rvb', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'noise'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[929]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r0 = new Array(10);
        r2 = -2;
        r0[0] = r2;
        r2 = 0.8;
        r0[1] = r2;
        r2 = 1;
        r0[2] = r2;
        r3 = 2;
        r0[3] = r3;
        r3 = 1.6;
        r0[4] = r3;
        r0[5] = r2;
        r2 = -1;
        r0[6] = r2;
        r2 = -1.6;
        r0[7] = r2;
        r2 = 1.4;
        r0[8] = r2;
        r2 = 0.2;
        r0[9] = r2;
        r10 = {};
        r10['eqs'] = r0;
        r0 = true;
        r10['on'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r15 = {};
        r15['on'] = r1;
        r2 = {};
        r16 = {'pdelay': 30, 'hfdamping': 3, 'rshape': 80, 'density': 30, 'swidth': 120, 'q': 10, 'diffusion': 80, 'on': true, 'dtime': 1.2};
        r2['rvb'] = r16;
        r2['on'] = r0;
        r16 = {'pattern': 16, 'on': true, 'sdelay': 30, 'rsize': 80};
        r2['er'] = r16;
        r2['tc'] = r15;
        r15 = {'center': 0, 'lfe': 0};
        r2['il'] = r15;
        r15 = {'dry': 0, 'er': -30, 'rvb': -25};
        r2['ol'] = r15;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '浪漫-流行';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'se', 'peq', 'rvb', 'bt', 'rotate', 'spspatial', 'alc', 'chorus', 'flanger', 'dly', 'noise', 'effect_name'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[930]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r13['on'] = r1;
        r12 = {};
        r12['on'] = r1;
        r11 = {};
        r11['on'] = r1;
        r0 = new Array(10);
        r2 = -2;
        r0[0] = r2;
        r2 = 0.8;
        r0[1] = r2;
        r2 = 1;
        r0[2] = r2;
        r3 = 2;
        r0[3] = r3;
        r3 = 1.6;
        r0[4] = r3;
        r0[5] = r2;
        r2 = -1;
        r0[6] = r2;
        r2 = -1.6;
        r0[7] = r2;
        r2 = 1.4;
        r0[8] = r2;
        r2 = 0.2;
        r0[9] = r2;
        r10 = {'eqs': null, 'useable': true, 'on': true};
        r10['eqs'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r15 = {};
        r15['on'] = r1;
        r2 = {};
        r0 = {'pdelay': 60, 'hfdamping': 2, 'rshape': 80, 'density': 30, 'swidth': 180, 'q': 8, 'diffusion': 100, 'on': true, 'dtime': 1.2};
        r2['rvb'] = r0;
        r0 = true;
        r2['on'] = r0;
        r16 = {'pattern': 16, 'on': true, 'sdelay': 30, 'rsize': 100};
        r2['er'] = r16;
        r2['tc'] = r15;
        r15 = {'center': 0, 'lfe': 0};
        r2['il'] = r15;
        r15 = {'dry': 0, 'er': -30, 'rvb': -25};
        r2['ol'] = r15;
        r2['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r2['rl'] = r0;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rotate'] = r14;
        r1['alc'] = r13;
        r1['noise'] = r12;
        r1['chorus'] = r11;
        r1['eq'] = r10;
        r1['cmp'] = r9;
        r1['bt'] = r8;
        r1['flanger'] = r7;
        r1['spspatial'] = r6;
        r1['peq'] = r5;
        r1['limiter'] = r4;
        r1['dly'] = r3;
        r3 = '浪漫-节奏';
        r1['effect_name'] = r3;
        r3 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r3;
        r1['rvb'] = r2;
        r1['se'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[931]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -5, 'freq': 500, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -3, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r1 = {};
        r1['f'] = r0;
        r0 = true;
        r1['on'] = r0;
        r9 = {};
        r2 = {'pdelay': 60, 'hfdamping': 4, 'rshape': 60, 'density': 64, 'swidth': 120, 'q': 4, 'diffusion': 72, 'on': true, 'dtime': 2};
        r9['rvb'] = r2;
        r9['on'] = r0;
        r2 = {'pattern': 10, 'rsize': 60, 'on': true, 'sdelay': 15};
        r9['er'] = r2;
        r9['tc'] = r1;
        r1 = {'center': 0, 'lfe': -96.3};
        r9['il'] = r1;
        r1 = {'dry': 0, 'er': -30, 'rvb': -23};
        r9['ol'] = r1;
        r9['useable'] = r0;
        r0 = {'lfe': -96.3, 'front': 0, 'rear': -2, 'center': -1};
        r9['rl'] = r0;
        r8 = {};
        r1 = false;
        r8['on'] = r1;
        r0 = new Array(9);
        r2 = {'type': 1, 'freq': 35, 'band': 0, 'q': 0.8, 'on': true, 'gain': 0};
        r0[0] = r2;
        r2 = {'type': 6, 'freq': 100, 'band': 1, 'q': 3, 'on': true, 'gain': 1};
        r0[1] = r2;
        r2 = {'type': 6, 'freq': 200, 'band': 2, 'q': 3, 'on': true, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'type': 6, 'freq': 4000, 'band': 3, 'q': 2.5, 'on': true, 'gain': -2};
        r0[3] = r2;
        r2 = {'type': 6, 'freq': 3000, 'band': 4, 'q': 3, 'on': true, 'gain': -0.2};
        r0[4] = r2;
        r2 = {'type': 6, 'freq': 1500, 'band': 5, 'q': 2, 'on': true, 'gain': 1.3};
        r0[5] = r2;
        r2 = {'type': 6, 'freq': 8000, 'band': 6, 'q': 1, 'on': true, 'gain': -0.4};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r2 = {'band': 8, 'on': false};
        r0[8] = r2;
        r7 = {'f': null, 'gain': 0, 'on': true};
        r7['f'] = r0;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rvb'] = r9;
        r9 = {'presence': 2, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.2};
        r1['se'] = r9;
        r1['rotate'] = r8;
        r1['peq'] = r7;
        r1['limiter'] = r6;
        r1['spspatial'] = r5;
        r1['bt'] = r4;
        r1['alc'] = r3;
        r3 = '悲伤2';
        r1['effect_name'] = r3;
        r1['eq'] = r2;
        r1['cmp'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[932]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -5, 'freq': 500, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -3, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r1 = {};
        r1['f'] = r0;
        r0 = true;
        r1['on'] = r0;
        r9 = {};
        r2 = {'pdelay': 60, 'hfdamping': 4, 'rshape': 60, 'density': 64, 'swidth': 120, 'q': 4, 'diffusion': 72, 'on': true, 'dtime': 2};
        r9['rvb'] = r2;
        r9['on'] = r0;
        r2 = {'pattern': 10, 'rsize': 60, 'on': true, 'sdelay': 15};
        r9['er'] = r2;
        r9['tc'] = r1;
        r1 = {'center': 0, 'lfe': -96.3};
        r9['il'] = r1;
        r1 = {'dry': 0, 'er': -30, 'rvb': -23};
        r9['ol'] = r1;
        r9['useable'] = r0;
        r0 = {'lfe': -96.3, 'front': 0, 'rear': -2, 'center': -1};
        r9['rl'] = r0;
        r8 = {};
        r1 = false;
        r8['on'] = r1;
        r0 = new Array(9);
        r2 = {'type': 1, 'freq': 35, 'band': 0, 'q': 0.8, 'on': true, 'gain': 0};
        r0[0] = r2;
        r2 = {'type': 6, 'freq': 100, 'band': 1, 'q': 3, 'on': true, 'gain': 1};
        r0[1] = r2;
        r2 = {'type': 6, 'freq': 200, 'band': 2, 'q': 3, 'on': true, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'type': 6, 'freq': 4000, 'band': 3, 'q': 2.5, 'on': true, 'gain': -2};
        r0[3] = r2;
        r2 = {'type': 6, 'freq': 3000, 'band': 4, 'q': 3, 'on': true, 'gain': -0.6};
        r0[4] = r2;
        r2 = {'type': 6, 'freq': 1500, 'band': 5, 'q': 2, 'on': true, 'gain': 0.3};
        r0[5] = r2;
        r2 = {'type': 6, 'freq': 8000, 'band': 6, 'q': 1, 'on': true, 'gain': -0.4};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r2 = {'band': 8, 'on': false};
        r0[8] = r2;
        r7 = {'f': null, 'gain': 0, 'on': true};
        r7['f'] = r0;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rvb'] = r9;
        r9 = {'presence': 4, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.5};
        r1['se'] = r9;
        r1['rotate'] = r8;
        r1['peq'] = r7;
        r1['limiter'] = r6;
        r1['spspatial'] = r5;
        r1['bt'] = r4;
        r1['alc'] = r3;
        r3 = '悲伤2';
        r1['effect_name'] = r3;
        r1['eq'] = r2;
        r1['cmp'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[933]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -5, 'freq': 500, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -3, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r1 = {};
        r1['f'] = r0;
        r0 = true;
        r1['on'] = r0;
        r9 = {};
        r2 = {'pdelay': 60, 'hfdamping': 4, 'rshape': 60, 'density': 64, 'swidth': 120, 'q': 4, 'diffusion': 72, 'on': true, 'dtime': 2};
        r9['rvb'] = r2;
        r9['on'] = r0;
        r2 = {'pattern': 10, 'rsize': 60, 'on': true, 'sdelay': 15};
        r9['er'] = r2;
        r9['tc'] = r1;
        r1 = {'center': 0, 'lfe': -96.3};
        r9['il'] = r1;
        r1 = {'dry': 0, 'er': -30, 'rvb': -23};
        r9['ol'] = r1;
        r9['useable'] = r0;
        r0 = {'lfe': -96.3, 'front': 0, 'rear': -2, 'center': -1};
        r9['rl'] = r0;
        r8 = {};
        r1 = false;
        r8['on'] = r1;
        r0 = new Array(9);
        r2 = {'type': 1, 'freq': 35, 'band': 0, 'q': 0.8, 'on': true, 'gain': 0};
        r0[0] = r2;
        r2 = {'type': 6, 'freq': 100, 'band': 1, 'q': 3, 'on': true, 'gain': 1};
        r0[1] = r2;
        r2 = {'type': 6, 'freq': 200, 'band': 2, 'q': 3, 'on': true, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'type': 6, 'freq': 4000, 'band': 3, 'q': 2.5, 'on': true, 'gain': -2};
        r0[3] = r2;
        r2 = {'type': 6, 'freq': 3000, 'band': 4, 'q': 3, 'on': true, 'gain': -0.6};
        r0[4] = r2;
        r2 = {'type': 6, 'freq': 1500, 'band': 5, 'q': 2, 'on': true, 'gain': 1.3};
        r0[5] = r2;
        r2 = {'type': 6, 'freq': 8000, 'band': 6, 'q': 1, 'on': true, 'gain': -0.4};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r2 = {'band': 8, 'on': false};
        r0[8] = r2;
        r7 = {'f': null, 'gain': 0, 'on': true};
        r7['f'] = r0;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rvb'] = r9;
        r9 = {'presence': 3, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.3};
        r1['se'] = r9;
        r1['rotate'] = r8;
        r1['peq'] = r7;
        r1['limiter'] = r6;
        r1['spspatial'] = r5;
        r1['bt'] = r4;
        r1['alc'] = r3;
        r3 = '悲伤2';
        r1['effect_name'] = r3;
        r1['eq'] = r2;
        r1['cmp'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[934]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r0 = new Array(3);
        r1 = {'insert': 3, 'band': 1, 'q': 0.8, 'gain': -5, 'freq': 500, 'curve': 0, 'name': '滤波器1'};
        r0[0] = r1;
        r1 = {'insert': 3, 'band': 2, 'q': 0.8, 'gain': -1, 'freq': 2500, 'curve': 1, 'name': '滤波器2'};
        r0[1] = r1;
        r1 = {'insert': 3, 'band': 3, 'q': 0.7, 'gain': -3, 'freq': 7000, 'curve': 2, 'name': '滤波器3'};
        r0[2] = r1;
        r1 = {};
        r1['f'] = r0;
        r0 = true;
        r1['on'] = r0;
        r9 = {};
        r2 = {'pdelay': 60, 'hfdamping': 4, 'rshape': 60, 'density': 64, 'swidth': 120, 'q': 4, 'diffusion': 72, 'on': true, 'dtime': 2};
        r9['rvb'] = r2;
        r9['on'] = r0;
        r2 = {'pattern': 10, 'rsize': 60, 'on': true, 'sdelay': 15};
        r9['er'] = r2;
        r9['tc'] = r1;
        r1 = {'center': 0, 'lfe': -96.3};
        r9['il'] = r1;
        r1 = {'dry': 0, 'er': -30, 'rvb': -26};
        r9['ol'] = r1;
        r9['useable'] = r0;
        r0 = {'lfe': -96.3, 'front': 0, 'rear': -2, 'center': -1};
        r9['rl'] = r0;
        r8 = {};
        r1 = false;
        r8['on'] = r1;
        r0 = new Array(9);
        r2 = {'type': 1, 'freq': 35, 'band': 0, 'q': 0.8, 'on': true, 'gain': 0};
        r0[0] = r2;
        r2 = {'type': 6, 'freq': 100, 'band': 1, 'q': 3, 'on': true, 'gain': 1};
        r0[1] = r2;
        r2 = {'type': 6, 'freq': 200, 'band': 2, 'q': 3, 'on': true, 'gain': 0.4};
        r0[2] = r2;
        r2 = {'type': 6, 'freq': 4000, 'band': 3, 'q': 2.5, 'on': true, 'gain': -2};
        r0[3] = r2;
        r2 = {'type': 6, 'freq': 3000, 'band': 4, 'q': 3, 'on': true, 'gain': -0.6};
        r0[4] = r2;
        r2 = {'type': 6, 'freq': 1500, 'band': 5, 'q': 2, 'on': true, 'gain': 1.3};
        r0[5] = r2;
        r2 = {'type': 6, 'freq': 8000, 'band': 6, 'q': 1, 'on': true, 'gain': -0.4};
        r0[6] = r2;
        r2 = {'band': 7, 'on': false};
        r0[7] = r2;
        r2 = {'band': 8, 'on': false};
        r0[8] = r2;
        r7 = {'f': null, 'gain': 0, 'on': true};
        r7['f'] = r0;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['rvb'] = r9;
        r9 = {'presence': 2, 'sshaper': true, 'ambience': 5, 'on': true, 'stereoizer': 0.2};
        r1['se'] = r9;
        r1['rotate'] = r8;
        r1['peq'] = r7;
        r1['limiter'] = r6;
        r1['spspatial'] = r5;
        r1['bt'] = r4;
        r1['alc'] = r3;
        r3 = '悲伤2';
        r1['effect_name'] = r3;
        r1['eq'] = r2;
        r1['cmp'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[935]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r0 = true;
        r12['on'] = r0;
        r2 = [0, 0, 1, 0.2, 0, 0.6, 0.6, 1.6, 1.6, 0];
        r12['eqs'] = r2;
        r2 = new Array(9);
        r3 = {'band': 0, 'type': 6, 'gain': 1.5, 'freq': 3000, 'q': 1, 'on': true};
        r2[0] = r3;
        r3 = {'band': 1, 'type': 6, 'gain': 1.2, 'freq': 220, 'q': 0.7, 'on': true};
        r2[1] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[2] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[3] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[4] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[5] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[8] = r3;
        r11 = {'on': true, 'gain': 1.4};
        r11['f'] = r2;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r10 = {};
        r4 = {'pdelay': 21, 'dtime': 1.1, 'hfdamping': 6, 'density': 58, 'rshape': 25, 'q': 6, 'diffusion': 20, 'swidth': 20, 'on': true};
        r10['rvb'] = r4;
        r10['on'] = r0;
        r10['er'] = r3;
        r10['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r10['il'] = r2;
        r2 = {'dry': 0, 'er': -32, 'rvb': -26};
        r10['ol'] = r2;
        r10['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r10['rl'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r13;
        r1['eq'] = r12;
        r1['peq'] = r11;
        r1['rvb'] = r10;
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r6 = {'on': true, 'upmix': 1, 'lronoff': 0, 'lrangle': 10, 'otheronoff': 0, 'global': -0.2, 'center': 0.6, 'lfe': 0, 'fl': -0.2, 'fr': -0.2, 'left': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'ur': 0};
        r1['spspatial'] = r6;
        r1['alc'] = r5;
        r1['chorus'] = r4;
        r1['flanger'] = r3;
        r1['dly'] = r2;
        r1['noise'] = r0;
        r0 = '甜蜜2-folk';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[936]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r14 = {};
        r1 = false;
        r14['on'] = r1;
        r13 = {};
        r0 = true;
        r13['on'] = r0;
        r2 = [0, 0, 0, 0.6, 0, 0.6, 0.4, 1.6, 1.8, 0];
        r13['eqs'] = r2;
        r2 = new Array(9);
        r3 = {'band': 0, 'type': 6, 'gain': 1.5, 'freq': 3000, 'q': 1, 'on': true};
        r2[0] = r3;
        r3 = {'band': 1, 'type': 6, 'gain': 1.2, 'freq': 220, 'q': 0.7, 'on': true};
        r2[1] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[2] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[3] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[4] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[5] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[8] = r3;
        r12 = {'on': true, 'gain': 1.6};
        r12['f'] = r2;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r11 = {};
        r4 = {'pdelay': 30, 'dtime': 1.1, 'hfdamping': 6, 'density': 80, 'rshape': 25, 'q': 6, 'diffusion': 80, 'swidth': 80, 'on': true};
        r11['rvb'] = r4;
        r11['on'] = r0;
        r11['er'] = r3;
        r11['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r11['il'] = r2;
        r2 = {'dry': 0, 'er': -32, 'rvb': -26};
        r11['ol'] = r2;
        r11['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r11['rl'] = r0;
        r10 = {};
        r10['on'] = r1;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r14;
        r1['eq'] = r13;
        r1['peq'] = r12;
        r1['rvb'] = r11;
        r1['limiter'] = r10;
        r1['se'] = r9;
        r1['bt'] = r8;
        r1['rotate'] = r7;
        r1['spspatial'] = r6;
        r1['alc'] = r5;
        r1['chorus'] = r4;
        r1['flanger'] = r3;
        r1['dly'] = r2;
        r1['noise'] = r0;
        r0 = '甜蜜2 inst';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[937]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r12 = {};
        r0 = true;
        r12['on'] = r0;
        r2 = [0, 0, 0, 0.6, 0, 0.6, 0.2, 1.4, 1.4, 0];
        r12['eqs'] = r2;
        r2 = new Array(9);
        r3 = {'band': 0, 'type': 6, 'gain': 1.7, 'freq': 3000, 'q': 1, 'on': true};
        r2[0] = r3;
        r3 = {'band': 1, 'type': 6, 'gain': 1.2, 'freq': 220, 'q': 0.7, 'on': true};
        r2[1] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[2] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[3] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[4] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[5] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[8] = r3;
        r11 = {'on': true, 'gain': 1.2};
        r11['f'] = r2;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r10 = {};
        r4 = {'pdelay': 25, 'hfdamping': 6, 'rshape': 25, 'density': 80, 'swidth': 20, 'q': 6, 'diffusion': 20, 'on': true, 'dtime': 1.1};
        r10['rvb'] = r4;
        r10['on'] = r0;
        r10['er'] = r3;
        r10['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r10['il'] = r2;
        r2 = {'dry': 0, 'er': -32, 'rvb': -26};
        r10['ol'] = r2;
        r10['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r10['rl'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r13;
        r1['eq'] = r12;
        r1['peq'] = r11;
        r1['rvb'] = r10;
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r6 = {'on': true, 'upmix': 1, 'lronoff': 0, 'lrangle': 10, 'otheronoff': 0, 'global': -0.2, 'center': 0.6, 'lfe': 0, 'fl': -0.2, 'fr': -0.2, 'left': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'ur': 0};
        r1['spspatial'] = r6;
        r1['alc'] = r5;
        r1['chorus'] = r4;
        r1['flanger'] = r3;
        r1['dly'] = r2;
        r1['noise'] = r0;
        r0 = '甜蜜2-pop';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[938]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r13 = {};
        r1 = false;
        r13['on'] = r1;
        r2 = [0, 0.8, 0.6, 0.6, 0, 0.6];
        r0 = -0.4;
        r2[6] = r0;
        r0 = 1.8;
        r2[7] = r0;
        r0 = 1.4;
        r2[8] = r0;
        r0 = 0;
        r2[9] = r0;
        r12 = {};
        r0 = true;
        r12['on'] = r0;
        r12['eqs'] = r2;
        r2 = new Array(9);
        r3 = {'band': 0, 'type': 6, 'gain': 1.5, 'freq': 3000, 'q': 1, 'on': true};
        r2[0] = r3;
        r3 = {'band': 1, 'type': 6, 'gain': 1.2, 'freq': 220, 'q': 0.7, 'on': true};
        r2[1] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[2] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[3] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[4] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[5] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[6] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[7] = r3;
        r3 = {};
        r3['on'] = r1;
        r2[8] = r3;
        r11 = {'on': true, 'gain': 1.2};
        r11['f'] = r2;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r10 = {};
        r4 = {'pdelay': 25, 'hfdamping': 6, 'rshape': 25, 'density': 80, 'swidth': 20, 'q': 6, 'diffusion': 20, 'on': true, 'dtime': 1.1};
        r10['rvb'] = r4;
        r10['on'] = r0;
        r10['er'] = r3;
        r10['tc'] = r2;
        r2 = {'center': 0, 'lfe': 0};
        r10['il'] = r2;
        r2 = {'dry': 0, 'er': -32, 'rvb': -26};
        r10['ol'] = r2;
        r10['useable'] = r0;
        r0 = {'lfe': 0, 'front': 0, 'rear': 0, 'center': 0};
        r10['rl'] = r0;
        r9 = {};
        r9['on'] = r1;
        r8 = {};
        r8['on'] = r1;
        r7 = {};
        r7['on'] = r1;
        r6 = {};
        r6['on'] = r1;
        r5 = {};
        r5['on'] = r1;
        r4 = {};
        r4['on'] = r1;
        r3 = {};
        r3['on'] = r1;
        r2 = {};
        r2['on'] = r1;
        r0 = {};
        r0['on'] = r1;
        r1 = {};
        r1['cmp'] = r13;
        r1['eq'] = r12;
        r1['peq'] = r11;
        r1['rvb'] = r10;
        r1['limiter'] = r9;
        r1['se'] = r8;
        r1['bt'] = r7;
        r1['rotate'] = r6;
        r6 = {'on': true, 'upmix': 1, 'lronoff': 0, 'lrangle': 10, 'otheronoff': 0, 'global': -0.2, 'center': 0.6, 'lfe': 0, 'fl': -0.2, 'fr': -0.2, 'left': 0, 'right': 0, 'rl': 0, 'rr': 0, 'ul': 0, 'ur': 0};
        r1['spspatial'] = r6;
        r1['alc'] = r5;
        r1['chorus'] = r4;
        r1['flanger'] = r3;
        r1['dly'] = r2;
        r1['noise'] = r0;
        r0 = '甜蜜2 rythm';
        r1['effect_name'] = r0;
        r0 = ['cmp', 'eq', 'bt', 'peq', 'chorus', 'flanger', 'se', 'rotate', 'spspatial', 'dly', 'rvb', 'noise', 'alc'];
        r1['order'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[939]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r4 = a1;
        r8 = a5;
        r5 = a6;
        var _closure1_slot4 = r8;
        r2 = global;
        r7 = r2.Object;
        r6 = r7.defineProperty;
        r3 = {};
        r0 = true;
        r3['value'] = r0;
        r0 = '__esModule';
        r0 = r6.bind(r7)(r8, r0, r3);
        r0 = 0;
        r3 = r5[r0];
        r0 = undefined;
        r7 = r4.bind(r0)(r3);
        var _closure1_slot0 = r7;
        r6 = r2.Object;
        r3 = r6.keys;
        r7 = r3.bind(r6)(r7);
        r6 = r7.forEach;
        r3 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            _fun9320: for(var _fun9320_ip = 0; ; ) switch(_fun9320_ip) {
case 0:
                r5 = a0;
                r0 = undefined;
                r1 = 'default';
                r1 = r1 !== r5;
                if(!r1) { _fun9320_ip = 26; continue _fun9320 }
case 18:
                r2 = '__esModule';
                r1 = r2 !== r5;
case 26:
                if(r1) { _fun9320_ip = 35; continue _fun9320 }
case 29:
                var _closure2_slot0 = r5;
                _fun9320_ip = 119; continue _fun9320;
case 35:
                r2 = _closure1_slot4;
                r2 = r5 in r2;
                if(!r2) { _fun9320_ip = 69; continue _fun9320 }
case 49:
                r3 = _closure1_slot4;
                r4 = r3[r5];
                r3 = _closure1_slot0;
                r3 = r3[r5];
                r2 = r4 === r3;
case 69:
                _closure2_slot0 = r5;
                if(r2) { _fun9320_ip = 119; continue _fun9320 }
case 76:
                r2 = global;
                r4 = r2.Object;
                r3 = r4.defineProperty;
                r2 = _closure1_slot4;
                r1 = {};
                r7 = true;
                r1['enumerable'] = r7;
                r6 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: get, environment: r6
                    r1 = _closure1_slot0;
                    r0 = _closure2_slot0;
                    r0 = r1[r0];
                    return r0;
                };
                r1['get'] = r6;
                r1 = r3.bind(r4)(r2, r5, r1);
                _fun9320_ip = 119; continue _fun9320;
case 119:
                return r0;
            }
        };
        r3 = r6.bind(r7)(r3);
        r3 = 1;
        r3 = r5[r3];
        r7 = r4.bind(r0)(r3);
        var _closure1_slot1 = r7;
        r6 = r2.Object;
        r3 = r6.keys;
        r7 = r3.bind(r6)(r7);
        r6 = r7.forEach;
        r3 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            _fun9322: for(var _fun9322_ip = 0; ; ) switch(_fun9322_ip) {
case 0:
                r5 = a0;
                r0 = undefined;
                r1 = 'default';
                r1 = r1 !== r5;
                if(!r1) { _fun9322_ip = 26; continue _fun9322 }
case 18:
                r2 = '__esModule';
                r1 = r2 !== r5;
case 26:
                if(r1) { _fun9322_ip = 35; continue _fun9322 }
case 29:
                var _closure2_slot0 = r5;
                _fun9322_ip = 119; continue _fun9322;
case 35:
                r2 = _closure1_slot4;
                r2 = r5 in r2;
                if(!r2) { _fun9322_ip = 69; continue _fun9322 }
case 49:
                r3 = _closure1_slot4;
                r4 = r3[r5];
                r3 = _closure1_slot1;
                r3 = r3[r5];
                r2 = r4 === r3;
case 69:
                _closure2_slot0 = r5;
                if(r2) { _fun9322_ip = 119; continue _fun9322 }
case 76:
                r2 = global;
                r4 = r2.Object;
                r3 = r4.defineProperty;
                r2 = _closure1_slot4;
                r1 = {};
                r7 = true;
                r1['enumerable'] = r7;
                r6 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: get, environment: r6
                    r1 = _closure1_slot1;
                    r0 = _closure2_slot0;
                    r0 = r1[r0];
                    return r0;
                };
                r1['get'] = r6;
                r1 = r3.bind(r4)(r2, r5, r1);
                _fun9322_ip = 119; continue _fun9322;
case 119:
                return r0;
            }
        };
        r3 = r6.bind(r7)(r3);
        r3 = 2;
        r3 = r5[r3];
        r7 = r4.bind(r0)(r3);
        var _closure1_slot2 = r7;
        r6 = r2.Object;
        r3 = r6.keys;
        r7 = r3.bind(r6)(r7);
        r6 = r7.forEach;
        r3 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            _fun9324: for(var _fun9324_ip = 0; ; ) switch(_fun9324_ip) {
case 0:
                r5 = a0;
                r0 = undefined;
                r1 = 'default';
                r1 = r1 !== r5;
                if(!r1) { _fun9324_ip = 26; continue _fun9324 }
case 18:
                r2 = '__esModule';
                r1 = r2 !== r5;
case 26:
                if(r1) { _fun9324_ip = 35; continue _fun9324 }
case 29:
                var _closure2_slot0 = r5;
                _fun9324_ip = 119; continue _fun9324;
case 35:
                r2 = _closure1_slot4;
                r2 = r5 in r2;
                if(!r2) { _fun9324_ip = 69; continue _fun9324 }
case 49:
                r3 = _closure1_slot4;
                r4 = r3[r5];
                r3 = _closure1_slot2;
                r3 = r3[r5];
                r2 = r4 === r3;
case 69:
                _closure2_slot0 = r5;
                if(r2) { _fun9324_ip = 119; continue _fun9324 }
case 76:
                r2 = global;
                r4 = r2.Object;
                r3 = r4.defineProperty;
                r2 = _closure1_slot4;
                r1 = {};
                r7 = true;
                r1['enumerable'] = r7;
                r6 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: get, environment: r6
                    r1 = _closure1_slot2;
                    r0 = _closure2_slot0;
                    r0 = r1[r0];
                    return r0;
                };
                r1['get'] = r6;
                r1 = r3.bind(r4)(r2, r5, r1);
                _fun9324_ip = 119; continue _fun9324;
case 119:
                return r0;
            }
        };
        r3 = r6.bind(r7)(r3);
        r3 = 3;
        r3 = r5[r3];
        r4 = r4.bind(r0)(r3);
        var _closure1_slot3 = r4;
        r3 = r2.Object;
        r2 = r3.keys;
        r3 = r2.bind(r3)(r4);
        r2 = r3.forEach;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            _fun9326: for(var _fun9326_ip = 0; ; ) switch(_fun9326_ip) {
case 0:
                r5 = a0;
                r0 = undefined;
                r1 = 'default';
                r1 = r1 !== r5;
                if(!r1) { _fun9326_ip = 26; continue _fun9326 }
case 18:
                r2 = '__esModule';
                r1 = r2 !== r5;
case 26:
                if(r1) { _fun9326_ip = 35; continue _fun9326 }
case 29:
                var _closure2_slot0 = r5;
                _fun9326_ip = 119; continue _fun9326;
case 35:
                r2 = _closure1_slot4;
                r2 = r5 in r2;
                if(!r2) { _fun9326_ip = 69; continue _fun9326 }
case 49:
                r3 = _closure1_slot4;
                r4 = r3[r5];
                r3 = _closure1_slot3;
                r3 = r3[r5];
                r2 = r4 === r3;
case 69:
                _closure2_slot0 = r5;
                if(r2) { _fun9326_ip = 119; continue _fun9326 }
case 76:
                r2 = global;
                r4 = r2.Object;
                r3 = r4.defineProperty;
                r2 = _closure1_slot4;
                r1 = {};
                r7 = true;
                r1['enumerable'] = r7;
                r6 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: get, environment: r6
                    r1 = _closure1_slot3;
                    r0 = _closure2_slot0;
                    r0 = r1[r0];
                    return r0;
                };
                r1['get'] = r6;
                r1 = r3.bind(r4)(r2, r5, r1);
                _fun9326_ip = 119; continue _fun9326;
case 119:
                return r0;
            }
        };
        r1 = r2.bind(r3)(r1);
        return r0;
    };
factories[940]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a5;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: t, environment: r2
            _fun9329: for(var _fun9329_ip = 0; ; ) switch(_fun9329_ip) {
case 0:
                r3 = a0;
                r0 = r3;
                if(!r3) { _fun9329_ip = 32; continue _fun9329 }
case 9:
                r1 = r3.on;
                r2 = false;
                r0 = r3;
                if(!(r2 === r1)) { _fun9329_ip = 32; continue _fun9329 }
case 23:
                r1 = {};
                r1['on'] = r2;
                r0 = r1;
case 32:
                return r0;
            }
        };
        var _closure1_slot2 = r0;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: p, environment: r2
            r0 = a0;
            r3 = _closure1_slot2;
            r1 = r0.eq;
            r2 = undefined;
            r1 = r3.bind(r2)(r1);
            r0['eq'] = r1;
            r1 = r0.cmp;
            r1 = r3.bind(r2)(r1);
            r0['cmp'] = r1;
            r1 = r0.limiter;
            r1 = r3.bind(r2)(r1);
            r0['limiter'] = r1;
            r1 = r0.bt;
            r1 = r3.bind(r2)(r1);
            r0['bt'] = r1;
            r1 = r0.peq;
            r1 = r3.bind(r2)(r1);
            r0['peq'] = r1;
            r1 = r0.rvb;
            r1 = r3.bind(r2)(r1);
            r0['rvb'] = r1;
            r1 = r0.dly;
            r1 = r3.bind(r2)(r1);
            r0['dly'] = r1;
            r1 = r0.se;
            r1 = r3.bind(r2)(r1);
            r0['se'] = r1;
            r1 = r0.chorus;
            r1 = r3.bind(r2)(r1);
            r0['chorus'] = r1;
            r1 = r0.flanger;
            r1 = r3.bind(r2)(r1);
            r0['flanger'] = r1;
            r1 = r0.rotate;
            r1 = r3.bind(r2)(r1);
            r0['rotate'] = r1;
            r1 = r0.alc;
            r1 = r3.bind(r2)(r1);
            r0['alc'] = r1;
            r1 = r0.spspatial;
            r1 = r3.bind(r2)(r1);
            r0['spspatial'] = r1;
            return r0;
        };
        var _closure1_slot3 = r1;
        r0 = global;
        r6 = r0.Object;
        r5 = r6.defineProperty;
        r4 = {};
        r0 = true;
        r4['value'] = r0;
        r0 = '__esModule';
        r0 = r5.bind(r6)(r3, r0, r4);
        r0 = undefined;
        r3['SSHAPER_OFF_THRESHOLD'] = r0;
        r4 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9331: for(var _fun9331_ip = 0; ; ) switch(_fun9331_ip) {
case 0:
                r1 = a0;
                r4 = a1;
                r2 = 'number';
                r0 = typeof r4;
                if(!(r2 === r0)) { _fun9331_ip = 131; continue _fun9331 }
case 17:
                r0 = global;
                r2 = r0.Number;
                r0 = r2.isFinite;
                r0 = r0.bind(r2)(r4);
                if(!r0) { _fun9331_ip = 131; continue _fun9331 }
case 39:
                r2 = _closure1_slot0;
                r3 = r2.floatGt;
                r2 = _closure1_slot1;
                r0 = undefined;
                r0 = r3.bind(r0)(r4, r2);
                if(!r0) { _fun9331_ip = 87; continue _fun9331 }
case 67:
                r0 = r1.se;
                r2 = null;
                if(!(r2 != r0)) { _fun9331_ip = 87; continue _fun9331 }
case 79:
                r0 = r0.on;
                if(r0) { _fun9331_ip = 102; continue _fun9331 }
case 87:
                r0 = {};
                r0['params'] = r1;
                r2 = false;
                r0['sshaperDisabledByLoudness'] = r2;
                return r0;
case 102:
                r2 = r1.se;
                r0 = false;
                r2['sshaper'] = r0;
                r0 = {};
                r0['params'] = r1;
                r2 = true;
                r0['sshaperDisabledByLoudness'] = r2;
                return r0;
case 131:
                r0 = {};
                r0['params'] = r1;
                r1 = false;
                r0['sshaperDisabledByLoudness'] = r1;
                return r0;
            }
        };
        r3['applySshaperRule'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r2 = _closure1_slot3;
            r0 = _closure1_slot0;
            r3 = r0.deepClone;
            r1 = undefined;
            r0 = a0;
            r0 = r3.bind(r1)(r0);
            r1 = r2.bind(r1)(r0);
            return r0;
        };
        r3['prepareEffectTemplate'] = r4;
        r3['pruneDisabledFields'] = r1;
        r4 = a6;
        r1 = 0;
        r4 = r4[r1];
        r1 = a1;
        r1 = r1.bind(r0)(r4);
        var _closure1_slot0 = r1;
        r1 = -6;
        r3['SSHAPER_OFF_THRESHOLD'] = r1;
        var _closure1_slot1 = r1;
        return r0;
    };
factories[941]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r4 = a1;
        r3 = a5;
        r5 = a6;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: u, environment: r2
            _fun9334: for(var _fun9334_ip = 0; ; ) switch(_fun9334_ip) {
case 0:
                r0 = a0;
                r2 = null;
                r1 = undefined;
                if(!(r2 != r0)) { _fun9334_ip = 16; continue _fun9334 }
case 11:
                r1 = r0.on;
case 16:
                r2 = 'boolean';
                r1 = typeof r1;
                if(!(r2 === r1)) { _fun9334_ip = 34; continue _fun9334 }
case 27:
                r0 = r0.on;
                return r0;
case 34:
                r0 = global;
                r2 = r0.Error;
                r1 = a1;
                r0 = ' 缺失或不是 boolean';
                r3 = r1 + r0;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r4 = r1;
                r0 = originalConstruct(r2, [r3, r2]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        var _closure1_slot5 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: b, environment: r2
            _fun9335: for(var _fun9335_ip = 0; ; ) switch(_fun9335_ip) {
case 0:
                r3 = undefined;
                r0 = a0;
                r1 = r0.eq;
                r0 = null;
                r2 = undefined;
                if(!(r0 != r1)) { _fun9335_ip = 25; continue _fun9335 }
case 19:
                r2 = r1.eqs;
case 25:
                if(!(r0 != r2)) { _fun9335_ip = 193; continue _fun9335 }
case 32:
                r0 = global;
                r4 = r0.Array;
                r1 = r4.isArray;
                r1 = r1.bind(r4)(r2);
                if(!r1) { _fun9335_ip = 144; continue _fun9335 }
case 53:
                r5 = r2.length;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_EQ_GAIN_DB_SIZE;
                if(!(r5 === r4)) { _fun9335_ip = 144; continue _fun9335 }
case 75:
                r5 = r2.some;
                r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r4
                    _fun9336: for(var _fun9336_ip = 0; ; ) switch(_fun9336_ip) {
case 0:
                        r3 = a0;
                        r1 = 'number';
                        r0 = typeof r3;
                        r0 = r1 !== r0;
                        if(r0) { _fun9336_ip = 39; continue _fun9336 }
case 17:
                        r1 = global;
                        r2 = r1.Number;
                        r1 = r2.isFinite;
                        r1 = r1.bind(r2)(r3);
                        r0 = !r1;
case 39:
                        return r0;
                    }
                };
                r4 = r5.bind(r2)(r4);
                if(r4) { _fun9335_ip = 112; continue _fun9335 }
case 96:
                r1 = _closure1_slot0;
                r1 = r1.default;
                r1 = r1.bind(r3)(r2);
                return r1;
case 112:
                r3 = r0.Error;
                r1 = r3.prototype;
                r2 = Object.create(r1, {constructor: {value: r3}});
                r6 = 'template.eq.eqs 中存在非法数值';
                r7 = r2;
                r1 = originalConstruct(r3, [r6, r5]);
                r1 = r1 instanceof Object ? r1 : r2;
                throw r1;
case 144:
                r2 = r0.Error;
                r0 = _closure1_slot1;
                r1 = r0.AEV2_EQ_GAIN_DB_SIZE;
                r0 = 'template.eq.eqs 长度必须为 ';
                r6 = r0 + r1;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r7 = r1;
                r0 = originalConstruct(r2, [r6, r5]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
case 193:
                r0 = global;
                r2 = r0.Array;
                r0 = _closure1_slot1;
                r6 = r0.AEV2_EQ_GAIN_DB_SIZE;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r7 = r1;
                r0 = originalConstruct(r2, [r6, r5]);
                r2 = r0 instanceof Object ? r0 : r1;
                r1 = r2.fill;
                r0 = 0;
                r0 = r1.bind(r2)(r0);
                return r0;
            }
        };
        var _closure1_slot6 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: p, environment: r2
            _fun9337: for(var _fun9337_ip = 0; ; ) switch(_fun9337_ip) {
case 0:
                r1 = a1;
                r0 = _closure1_slot2;
                r4 = r0.assertFiniteNumber;
                r2 = r1.readTemplate;
                r0 = a0;
                r3 = r2.bind(r1)(r0);
                r2 = r1.templateField;
                r0 = undefined;
                r3 = r4.bind(r0)(r3, r2);
                r2 = r1.toAev2;
                r0 = r3;
                if(!r2) { _fun9337_ip = 67; continue _fun9337 }
case 56:
                r2 = r1.toAev2;
                r0 = r2.bind(r1)(r3);
case 67:
                r2 = global;
                r4 = r2.Number;
                r3 = r4.isFinite;
                r3 = r3.bind(r4)(r0);
                if(r3) { _fun9337_ip = 131; continue _fun9337 }
case 89:
                r3 = r2.Error;
                r2 = r1.templateField;
                r1 = ' 映射到 AEV2 后不是有限数值';
                r6 = r2 + r1;
                r2 = r3.prototype;
                r2 = Object.create(r2, {constructor: {value: r3}});
                r7 = r2;
                r1 = originalConstruct(r3, [r6, r5]);
                r1 = r1 instanceof Object ? r1 : r2;
                throw r1;
case 131:
                return r0;
            }
        };
        var _closure1_slot7 = r0;
        r0 = function(a0, a1, a2, a3) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: _, environment: r2
            _fun9338: for(var _fun9338_ip = 0; ; ) switch(_fun9338_ip) {
case 0:
                r4 = a1;
                r2 = a2;
                r0 = _closure1_slot2;
                r3 = r0.assertFiniteNumber;
                r0 = undefined;
                r1 = a3;
                r5 = r3.bind(r0)(r1, r2);
                r1 = r4.fromAev2;
                r3 = r5;
                if(!r1) { _fun9338_ip = 53; continue _fun9338 }
case 42:
                r1 = r4.fromAev2;
                r3 = r1.bind(r4)(r5);
case 53:
                r1 = global;
                r6 = r1.Number;
                r5 = r6.isFinite;
                r5 = r5.bind(r6)(r3);
                if(r5) { _fun9338_ip = 111; continue _fun9338 }
case 75:
                r5 = r1.Error;
                r1 = ' 映射回模板后不是有限数值';
                r8 = r2 + r1;
                r2 = r5.prototype;
                r2 = Object.create(r2, {constructor: {value: r5}});
                r9 = r2;
                r1 = originalConstruct(r5, [r8, r7]);
                r1 = r1 instanceof Object ? r1 : r2;
                throw r1;
case 111:
                r2 = r4.writeTemplate;
                r1 = a0;
                r1 = r2.bind(r4)(r1, r3);
                return r0;
            }
        };
        var _closure1_slot8 = r0;
        r0 = 0;
        r1 = r5[r0];
        r0 = undefined;
        r6 = r4.bind(r0)(r1);
        r1 = global;
        r9 = r1.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r1 = true;
        r7['value'] = r1;
        r1 = '__esModule';
        r1 = r8.bind(r9)(r3, r1, r7);
        r3['AEV2_TEMPLATE_SCALAR_MAPPINGS'] = r0;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9339: for(var _fun9339_ip = 0; ; ) switch(_fun9339_ip) {
case 0:
                r8 = a0;
                r7 = undefined;
                r3 = _closure1_slot5;
                r2 = r8.rvb;
                r1 = 'template.rvb.on';
                r3 = r3.bind(r7)(r2, r1);
                r2 = r8.dly;
                r4 = null;
                r1 = undefined;
                if(!(r4 != r2)) { _fun9339_ip = 47; continue _fun9339 }
case 42:
                r1 = r2.on;
case 47:
                r2 = 'boolean';
                r1 = typeof r1;
                r2 = r2 === r1;
                if(!r2) { _fun9339_ip = 72; continue _fun9339 }
case 61:
                r1 = r8.dly;
                r2 = r1.on;
case 72:
                r5 = _closure1_slot5;
                r4 = r8.se;
                r1 = 'template.se.on';
                r1 = r5.bind(r7)(r4, r1);
                r4 = _closure1_slot6;
                r4 = r4.bind(r7)(r8);
                if(r3) { _fun9339_ip = 114; continue _fun9339 }
case 104:
                r3 = {};
                r5 = false;
                r3['on'] = r5;
                _fun9339_ip = 234; continue _fun9339;
case 114:
                r6 = _closure1_slot7;
                r5 = _closure1_slot4;
                r5 = r5.reverb_time;
                r12 = r6.bind(r7)(r8, r5);
                r5 = _closure1_slot4;
                r5 = r5.dry_level;
                r11 = r6.bind(r7)(r8, r5);
                r5 = _closure1_slot4;
                r5 = r5.wet_level;
                r10 = r6.bind(r7)(r8, r5);
                r5 = _closure1_slot4;
                r5 = r5.room_size;
                r9 = r6.bind(r7)(r8, r5);
                r5 = _closure1_slot4;
                r5 = r5.diffusion;
                r6 = r6.bind(r7)(r8, r5);
                r5 = {};
                r13 = true;
                r5['on'] = r13;
                r5['reverb_time'] = r12;
                r5['dry_level'] = r11;
                r5['wet_level'] = r10;
                r5['room_size'] = r9;
                r5['diffusion'] = r6;
                r3 = r5;
case 234:
                if(r2) { _fun9339_ip = 247; continue _fun9339 }
case 237:
                r2 = {};
                r5 = false;
                r2['on'] = r5;
                _fun9339_ip = 304; continue _fun9339;
case 247:
                r6 = _closure1_slot7;
                r5 = _closure1_slot4;
                r5 = r5.delay_time;
                r9 = r6.bind(r7)(r8, r5);
                r5 = _closure1_slot4;
                r5 = r5.delay_feedback;
                r6 = r6.bind(r7)(r8, r5);
                r5 = {};
                r10 = true;
                r5['on'] = r10;
                r5['delay_time'] = r9;
                r5['feedback'] = r6;
                r2 = r5;
case 304:
                if(r1) { _fun9339_ip = 317; continue _fun9339 }
case 307:
                r1 = {};
                r5 = false;
                r1['on'] = r5;
                _fun9339_ip = 374; continue _fun9339;
case 317:
                r5 = _closure1_slot7;
                r6 = _closure1_slot4;
                r6 = r6.stereo_width;
                r6 = r5.bind(r7)(r8, r6);
                r0 = _closure1_slot4;
                r0 = r0.stereo_depth;
                r5 = r5.bind(r7)(r8, r0);
                r0 = {};
                r7 = true;
                r0['on'] = r7;
                r0['stereo_width'] = r6;
                r0['stereo_depth'] = r5;
                r1 = r0;
case 374:
                r0 = {};
                r0['eq_gain_db'] = r4;
                r0['reverb'] = r3;
                r0['delay'] = r2;
                r0['stereo_enhance'] = r1;
                return r0;
            }
        };
        r3['buildBaseExtraAev2FromTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9340: for(var _fun9340_ip = 0; ; ) switch(_fun9340_ip) {
case 0:
                r3 = a1;
                r1 = _closure1_slot2;
                r4 = r1.deepClone;
                r2 = undefined;
                r1 = a0;
                r1 = r4.bind(r2)(r1);
                r4 = r1.eq;
                r5 = null;
                if(!(r5 == r4)) { _fun9340_ip = 40; continue _fun9340 }
case 38:
                r4 = {};
case 40:
                r1['eq'] = r4;
                r5 = r1.eq;
                r4 = true;
                r5['on'] = r4;
                r5 = r1.eq;
                r4 = _closure1_slot0;
                r6 = r4.default;
                r4 = r3.eq_gain_db;
                r4 = r6.bind(r2)(r4);
                r5['eqs'] = r4;
                r4 = _closure1_slot2;
                r6 = r4.requireField;
                r5 = r1.rvb;
                r4 = 'template.rvb';
                r5 = r6.bind(r2)(r5, r4);
                r4 = r3.reverb;
                r4 = r4.on;
                r5['on'] = r4;
                r4 = r3.reverb;
                r4 = r4.on;
                if(!r4) { _fun9340_ip = 323; continue _fun9340 }
case 152:
                r7 = _closure1_slot8;
                r4 = _closure1_slot4;
                r10 = r4.reverb_time;
                r4 = r3.reverb;
                r8 = r4.reverb_time;
                r9 = 'aev2.reverb.reverb_time';
                r12 = undefined;
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
                r4 = _closure1_slot4;
                r10 = r4.dry_level;
                r4 = r3.reverb;
                r8 = r4.dry_level;
                r9 = 'aev2.reverb.dry_level';
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
                r4 = _closure1_slot4;
                r10 = r4.wet_level;
                r4 = r3.reverb;
                r8 = r4.wet_level;
                r9 = 'aev2.reverb.wet_level';
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
                r4 = _closure1_slot4;
                r10 = r4.room_size;
                r4 = r3.reverb;
                r8 = r4.room_size;
                r9 = 'aev2.reverb.room_size';
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
                r4 = _closure1_slot4;
                r10 = r4.diffusion;
                r4 = r3.reverb;
                r8 = r4.diffusion;
                r9 = 'aev2.reverb.diffusion';
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
case 323:
                r4 = r1.dly;
                if(r4) { _fun9340_ip = 346; continue _fun9340 }
case 332:
                r4 = {};
                r5 = false;
                r4['on'] = r5;
                r1['dly'] = r4;
case 346:
                r5 = r1.dly;
                r4 = r3.delay;
                r4 = r4.on;
                r5['on'] = r4;
                r4 = r3.delay;
                r4 = r4.on;
                if(!r4) { _fun9340_ip = 455; continue _fun9340 }
case 383:
                r7 = _closure1_slot8;
                r4 = _closure1_slot4;
                r10 = r4.delay_time;
                r4 = r3.delay;
                r8 = r4.delay_time;
                r9 = 'aev2.delay.delay_time';
                r12 = undefined;
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
                r4 = _closure1_slot4;
                r10 = r4.delay_feedback;
                r4 = r3.delay;
                r8 = r4.feedback;
                r9 = 'aev2.delay.feedback';
                r11 = r1;
                r4 = r7.call(r12, r11, r10, r9, r8, r7);
case 455:
                r4 = _closure1_slot2;
                r6 = r4.requireField;
                r5 = r1.se;
                r4 = 'template.se';
                r5 = r6.bind(r2)(r5, r4);
                r4 = r3.stereo_enhance;
                r4 = r4.on;
                r5['on'] = r4;
                r4 = r3.stereo_enhance;
                r4 = r4.on;
                if(!r4) { _fun9340_ip = 584; continue _fun9340 }
case 512:
                r6 = _closure1_slot8;
                r4 = _closure1_slot4;
                r10 = r4.stereo_width;
                r4 = r3.stereo_enhance;
                r8 = r4.stereo_width;
                r9 = 'aev2.stereo_enhance.stereo_width';
                r12 = undefined;
                r11 = r1;
                r4 = r6.call(r12, r11, r10, r9, r8, r7);
                r4 = _closure1_slot4;
                r10 = r4.stereo_depth;
                r3 = r3.stereo_enhance;
                r8 = r3.stereo_depth;
                r9 = 'aev2.stereo_enhance.stereo_depth';
                r11 = r1;
                r3 = r6.call(r12, r11, r10, r9, r8, r7);
case 584:
                r0 = _closure1_slot3;
                r0 = r0.pruneDisabledFields;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['mergeDecodedAev2IntoTemplate'] = r1;
        r1 = 1;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        r1 = r6.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 3;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        var _closure1_slot2 = r1;
        r1 = 4;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        var _closure1_slot3 = r1;
        r12 = {};
        r1 = 'template.rvb.rvb.dtime';
        r12['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9341: for(var _fun9341_ip = 0; ; ) switch(_fun9341_ip) {
case 0:
                r0 = a0;
                r2 = r0.rvb;
                r3 = null;
                r0 = undefined;
                if(!(r3 != r2)) { _fun9341_ip = 38; continue _fun9341 }
case 17:
                r2 = r2.rvb;
                r1 = undefined;
                if(!(r3 != r2)) { _fun9341_ip = 35; continue _fun9341 }
case 29:
                r1 = r2.dtime;
case 35:
                r0 = r1;
case 38:
                return r0;
            }
        };
        r12['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r1 = _closure1_slot2;
            r3 = r1.requireField;
            r0 = _closure1_slot2;
            r4 = r0.requireField;
            r0 = a0;
            r2 = r0.rvb;
            r0 = undefined;
            r1 = 'template.rvb';
            r1 = r4.bind(r0)(r2, r1);
            r2 = r1.rvb;
            r1 = 'template.rvb.rvb';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['dtime'] = r1;
            return r0;
        };
        r12['writeTemplate'] = r1;
        r11 = {};
        r1 = 'template.rvb.ol.dry';
        r11['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9343: for(var _fun9343_ip = 0; ; ) switch(_fun9343_ip) {
case 0:
                r0 = a0;
                r2 = r0.rvb;
                r3 = null;
                r0 = undefined;
                if(!(r3 != r2)) { _fun9343_ip = 38; continue _fun9343 }
case 17:
                r2 = r2.ol;
                r1 = undefined;
                if(!(r3 != r2)) { _fun9343_ip = 35; continue _fun9343 }
case 29:
                r1 = r2.dry;
case 35:
                r0 = r1;
case 38:
                return r0;
            }
        };
        r11['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r1 = _closure1_slot2;
            r3 = r1.requireField;
            r0 = _closure1_slot2;
            r4 = r0.requireField;
            r0 = a0;
            r2 = r0.rvb;
            r0 = undefined;
            r1 = 'template.rvb';
            r1 = r4.bind(r0)(r2, r1);
            r2 = r1.ol;
            r1 = 'template.rvb.ol';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['dry'] = r1;
            return r0;
        };
        r11['writeTemplate'] = r1;
        r10 = {};
        r1 = 'template.rvb.ol.rvb';
        r10['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9345: for(var _fun9345_ip = 0; ; ) switch(_fun9345_ip) {
case 0:
                r0 = a0;
                r2 = r0.rvb;
                r3 = null;
                r0 = undefined;
                if(!(r3 != r2)) { _fun9345_ip = 38; continue _fun9345 }
case 17:
                r2 = r2.ol;
                r1 = undefined;
                if(!(r3 != r2)) { _fun9345_ip = 35; continue _fun9345 }
case 29:
                r1 = r2.rvb;
case 35:
                r0 = r1;
case 38:
                return r0;
            }
        };
        r10['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r1 = _closure1_slot2;
            r3 = r1.requireField;
            r0 = _closure1_slot2;
            r4 = r0.requireField;
            r0 = a0;
            r2 = r0.rvb;
            r0 = undefined;
            r1 = 'template.rvb';
            r1 = r4.bind(r0)(r2, r1);
            r2 = r1.ol;
            r1 = 'template.rvb.ol';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['rvb'] = r1;
            return r0;
        };
        r10['writeTemplate'] = r1;
        r9 = {};
        r1 = 'template.rvb.rvb.rshape';
        r9['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9347: for(var _fun9347_ip = 0; ; ) switch(_fun9347_ip) {
case 0:
                r0 = a0;
                r2 = r0.rvb;
                r3 = null;
                r0 = undefined;
                if(!(r3 != r2)) { _fun9347_ip = 38; continue _fun9347 }
case 17:
                r2 = r2.rvb;
                r1 = undefined;
                if(!(r3 != r2)) { _fun9347_ip = 35; continue _fun9347 }
case 29:
                r1 = r2.rshape;
case 35:
                r0 = r1;
case 38:
                return r0;
            }
        };
        r9['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r1 = _closure1_slot2;
            r3 = r1.requireField;
            r0 = _closure1_slot2;
            r4 = r0.requireField;
            r0 = a0;
            r2 = r0.rvb;
            r0 = undefined;
            r1 = 'template.rvb';
            r1 = r4.bind(r0)(r2, r1);
            r2 = r1.rvb;
            r1 = 'template.rvb.rvb';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['rshape'] = r1;
            return r0;
        };
        r9['writeTemplate'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: toAev2, environment: r2
            r1 = a0;
            r0 = 100;
            r0 = r1 / r0;
            return r0;
        };
        r9['toAev2'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: fromAev2, environment: r2
            r1 = 100;
            r0 = a0;
            r0 = r1 * r0;
            return r0;
        };
        r9['fromAev2'] = r1;
        r8 = {};
        r1 = 'template.rvb.rvb.diffusion';
        r8['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9351: for(var _fun9351_ip = 0; ; ) switch(_fun9351_ip) {
case 0:
                r0 = a0;
                r2 = r0.rvb;
                r3 = null;
                r0 = undefined;
                if(!(r3 != r2)) { _fun9351_ip = 38; continue _fun9351 }
case 17:
                r2 = r2.rvb;
                r1 = undefined;
                if(!(r3 != r2)) { _fun9351_ip = 35; continue _fun9351 }
case 29:
                r1 = r2.diffusion;
case 35:
                r0 = r1;
case 38:
                return r0;
            }
        };
        r8['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r1 = _closure1_slot2;
            r3 = r1.requireField;
            r0 = _closure1_slot2;
            r4 = r0.requireField;
            r0 = a0;
            r2 = r0.rvb;
            r0 = undefined;
            r1 = 'template.rvb';
            r1 = r4.bind(r0)(r2, r1);
            r2 = r1.rvb;
            r1 = 'template.rvb.rvb';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['diffusion'] = r1;
            return r0;
        };
        r8['writeTemplate'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: toAev2, environment: r2
            r1 = a0;
            r0 = 100;
            r0 = r1 / r0;
            return r0;
        };
        r8['toAev2'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: fromAev2, environment: r2
            r1 = 100;
            r0 = a0;
            r0 = r1 * r0;
            return r0;
        };
        r8['fromAev2'] = r1;
        r7 = {};
        r1 = 'template.dly.delay';
        r7['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9355: for(var _fun9355_ip = 0; ; ) switch(_fun9355_ip) {
case 0:
                r1 = a0;
                r1 = r1.dly;
                r2 = null;
                r0 = undefined;
                if(!(r2 != r1)) { _fun9355_ip = 23; continue _fun9355 }
case 17:
                r0 = r1.delay;
case 23:
                return r0;
            }
        };
        r7['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r0 = _closure1_slot2;
            r3 = r0.requireField;
            r0 = a0;
            r2 = r0.dly;
            r0 = undefined;
            r1 = 'template.dly';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['delay'] = r1;
            return r0;
        };
        r7['writeTemplate'] = r1;
        r6 = {};
        r1 = 'template.dly.feedback';
        r6['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9357: for(var _fun9357_ip = 0; ; ) switch(_fun9357_ip) {
case 0:
                r1 = a0;
                r1 = r1.dly;
                r2 = null;
                r0 = undefined;
                if(!(r2 != r1)) { _fun9357_ip = 23; continue _fun9357 }
case 17:
                r0 = r1.feedback;
case 23:
                return r0;
            }
        };
        r6['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r0 = _closure1_slot2;
            r3 = r0.requireField;
            r0 = a0;
            r2 = r0.dly;
            r0 = undefined;
            r1 = 'template.dly';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['feedback'] = r1;
            return r0;
        };
        r6['writeTemplate'] = r1;
        r5 = {};
        r1 = 'template.se.stereoizer';
        r5['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9359: for(var _fun9359_ip = 0; ; ) switch(_fun9359_ip) {
case 0:
                r1 = a0;
                r1 = r1.se;
                r2 = null;
                r0 = undefined;
                if(!(r2 != r1)) { _fun9359_ip = 23; continue _fun9359 }
case 17:
                r0 = r1.stereoizer;
case 23:
                return r0;
            }
        };
        r5['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r0 = _closure1_slot2;
            r3 = r0.requireField;
            r0 = a0;
            r2 = r0.se;
            r0 = undefined;
            r1 = 'template.se';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['stereoizer'] = r1;
            return r0;
        };
        r5['writeTemplate'] = r1;
        r4 = {};
        r1 = 'template.se.presence';
        r4['templateField'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: readTemplate, environment: r2
            _fun9361: for(var _fun9361_ip = 0; ; ) switch(_fun9361_ip) {
case 0:
                r1 = a0;
                r1 = r1.se;
                r2 = null;
                r0 = undefined;
                if(!(r2 != r1)) { _fun9361_ip = 23; continue _fun9361 }
case 17:
                r0 = r1.presence;
case 23:
                return r0;
            }
        };
        r4['readTemplate'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: writeTemplate, environment: r2
            r0 = _closure1_slot2;
            r3 = r0.requireField;
            r0 = a0;
            r2 = r0.se;
            r0 = undefined;
            r1 = 'template.se';
            r2 = r3.bind(r0)(r2, r1);
            r1 = a1;
            r2['presence'] = r1;
            return r0;
        };
        r4['writeTemplate'] = r1;
        r1 = {};
        r1['reverb_time'] = r12;
        r1['dry_level'] = r11;
        r1['wet_level'] = r10;
        r1['room_size'] = r9;
        r1['diffusion'] = r8;
        r1['delay_time'] = r7;
        r1['delay_feedback'] = r6;
        r1['stereo_width'] = r5;
        r1['stereo_depth'] = r4;
        r3['AEV2_TEMPLATE_SCALAR_MAPPINGS'] = r1;
        var _closure1_slot4 = r1;
        return r0;
    };
factories[942]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r2 = a5;
        r0 = global;
        r4 = r0.Object;
        r3 = r4.defineProperty;
        r1 = {};
        r0 = true;
        r1['value'] = r0;
        r0 = '__esModule';
        r0 = r3.bind(r4)(r2, r0, r1);
        r0 = undefined;
        r2['AEV2_EQ_GAIN_DB_SIZE'] = r0;
        r2['AEV2_MIN_VALUES'] = r0;
        r1 = 10;
        r2['AEV2_EQ_GAIN_DB_SIZE'] = r1;
        r1 = {};
        r3 = {'reverb_time': 0, 'dry_level': -96.3, 'wet_level': -96.3, 'room_size': 0, 'diffusion': 0};
        r1['reverb'] = r3;
        r3 = {'delay_time': 0, 'feedback': 0};
        r1['delay'] = r3;
        r3 = {'stereo_width': 0, 'stereo_depth': 0};
        r1['stereo_enhance'] = r3;
        r2['AEV2_MIN_VALUES'] = r1;
        return r0;
    };
factories[943]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r1 = a5;
        r0 = global;
        r5 = r0.Object;
        r4 = r5.defineProperty;
        r3 = {};
        r0 = true;
        r3['value'] = r0;
        r0 = '__esModule';
        r0 = r4.bind(r5)(r1, r0, r3);
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9365: for(var _fun9365_ip = 0; ; ) switch(_fun9365_ip) {
case 0:
                r6 = undefined;
                r8 = false;
                r5 = 0;
                r0 = undefined;
                r7 = undefined;
                r2 = _closure1_slot0;
                r4 = r2.deepClone;
                r2 = a0;
                r0 = r4.bind(r6)(r2);
                r9 = _closure1_slot9;
                r2 = r9;
                var __iterator_values_2=Array.from(r2), __iterator_index_2=0; r4={return:function(){}}; r2=function(){if(__iterator_index_2>=__iterator_values_2.length){r4=undefined;return {value:undefined}}return {value:__iterator_values_2[__iterator_index_2++]}};
case 43:
                r10 = r2().value;
                r9 = r4;
                if(!(r9 !== r6)) { _fun9365_ip = 85; continue _fun9365 }
case 54: // try_start_0
                r9 = r0;
                r9 = r9[r10];
                r7 = r9;
                if(!r9) { _fun9365_ip = 76; continue _fun9365 }
case 67:
                r9 = r7;
                r9['on'] = r8;
case 76: // try_end0
                _fun9365_ip = 43; continue _fun9365;
case 78: // catch_target0
                CatchBlockStart(arg_register=2);
                r4.return();
                throw r2;
case 85:
                r2 = r0;
                r2 = r2.eq;
                if(r2) { _fun9365_ip = 114; continue _fun9365 }
case 97:
                r4 = r0;
                r2 = {};
                r7 = true;
                r2['on'] = r7;
                r4['eq'] = r2;
case 114:
                r7 = r0;
                r2 = r7.eq;
                r4 = true;
                r2['on'] = r4;
                r2 = global;
                r9 = r2.Array;
                r8 = r9.isArray;
                r7 = r7.eq;
                r7 = r7.eqs;
                r7 = r8.bind(r9)(r7);
                if(!r7) { _fun9365_ip = 198; continue _fun9365 }
case 164:
                r7 = r0;
                r7 = r7.eq;
                r7 = r7.eqs;
                r8 = r7.length;
                r7 = _closure1_slot6;
                if(!(!(r8 < r7))) { _fun9365_ip = 198; continue _fun9365 }
case 192:
                var _closure2_slot0 = r6;
                _fun9365_ip = 291; continue _fun9365;
case 198:
                r8 = r2.Array;
                r7 = r8.isArray;
                r2 = r0;
                r2 = r2.eq;
                r2 = r2.eqs;
                r2 = r7.bind(r8)(r2);
                if(r2) { _fun9365_ip = 238; continue _fun9365 }
case 232:
                r2 = new Array(0);
                _fun9365_ip = 253; continue _fun9365;
case 238:
                r7 = r0;
                r7 = r7.eq;
                r2 = r7.eqs;
case 253:
                _closure2_slot0 = r2;
                r2 = r0;
                r2 = r2.eq;
                r8 = _closure1_slot10;
                r7 = r8.map;
                r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r0 = _closure1_slot0;
                    r3 = r0.finiteOr;
                    r1 = _closure2_slot0;
                    r0 = a1;
                    r2 = r1[r0];
                    r1 = undefined;
                    r0 = a0;
                    r0 = r3.bind(r1)(r2, r0);
                    return r0;
                };
                r1 = r7.bind(r8)(r1);
                r2['eqs'] = r1;
case 291:
                r2 = _closure1_slot2;
                r1 = a1;
                r7 = r2[r1];
                if(!r7) { _fun9365_ip = 418; continue _fun9365 }
case 305:
                r1 = _closure1_slot6;
                r2 = 0;
                if(!(r2 < r1)) { _fun9365_ip = 418; continue _fun9365 }
case 315:
                r1 = r0;
                r8 = r1.eq;
                r8 = r8.eqs;
                r9 = _closure1_slot0;
                r11 = r9.clamp;
                r9 = _closure1_slot0;
                r9 = r9.finiteOr;
                r1 = r1.eq;
                r1 = r1.eqs;
                r1 = r1[r2];
                r9 = r9.bind(r6)(r1, r5);
                r10 = r7[r2];
                r1 = _closure1_slot1;
                r1 = r10 * r1;
                r10 = r9 + r1;
                r9 = _closure1_slot7;
                r1 = _closure1_slot8;
                r1 = r11.bind(r6)(r10, r9, r1);
                r8[r2] = r1;
                r2 = r2 + 1;
                r1 = _closure1_slot6;
                if(r2 < r1) { _fun9365_ip = 315; continue _fun9365 }
case 418:
                r1 = r0;
                r7 = r1.rvb;
                r1 = null;
                r2 = undefined;
                if(!(r1 != r7)) { _fun9365_ip = 440; continue _fun9365 }
case 435:
                r2 = r7.on;
case 440:
                if(!(r4 === r2)) { _fun9365_ip = 843; continue _fun9365 }
case 447:
                r2 = r0;
                r2 = r2.rvb;
                r2 = r2.rvb;
                if(!r2) { _fun9365_ip = 700; continue _fun9365 }
case 468:
                r2 = r0;
                r2 = r2.rvb;
                r4 = r2.rvb;
                r2 = r4.dtime;
                r7 = 'number';
                r2 = typeof r2;
                if(!(r7 === r2)) { _fun9365_ip = 562; continue _fun9365 }
case 500:
                r2 = _closure1_slot0;
                r9 = r2.clamp;
                r2 = _closure1_slot0;
                r8 = r2.finiteOr;
                r2 = r4.dtime;
                r8 = r8.bind(r6)(r2, r5);
                r2 = 1.15;
                r8 = r2 * r8;
                r2 = 10;
                r2 = r9.bind(r6)(r8, r5, r2);
                r4['dtime'] = r2;
case 562:
                r2 = r4.rshape;
                r2 = typeof r2;
                if(!(r7 === r2)) { _fun9365_ip = 631; continue _fun9365 }
case 575:
                r2 = _closure1_slot0;
                r9 = r2.clamp;
                r2 = _closure1_slot0;
                r8 = r2.finiteOr;
                r2 = r4.rshape;
                r8 = r8.bind(r6)(r2, r5);
                r2 = _closure1_slot4;
                r8 = r8 + r2;
                r2 = 100;
                r2 = r9.bind(r6)(r8, r5, r2);
                r4['rshape'] = r2;
case 631:
                r2 = r4.diffusion;
                r2 = typeof r2;
                if(!(r7 === r2)) { _fun9365_ip = 700; continue _fun9365 }
case 644:
                r2 = _closure1_slot0;
                r8 = r2.clamp;
                r2 = _closure1_slot0;
                r7 = r2.finiteOr;
                r2 = r4.diffusion;
                r7 = r7.bind(r6)(r2, r5);
                r2 = _closure1_slot5;
                r7 = r7 + r2;
                r2 = 100;
                r2 = r8.bind(r6)(r7, r5, r2);
                r4['diffusion'] = r2;
case 700:
                r2 = r0;
                r2 = r2.rvb;
                r2 = r2.ol;
                if(!r2) { _fun9365_ip = 750; continue _fun9365 }
case 718:
                r4 = r0;
                r4 = r4.rvb;
                r4 = r4.ol;
                r4 = r4.rvb;
                r7 = 'number';
                r4 = typeof r4;
                r2 = r7 === r4;
case 750:
                if(!r2) { _fun9365_ip = 843; continue _fun9365 }
case 753:
                r2 = r0;
                r4 = r2.rvb;
                r4 = r4.ol;
                r7 = _closure1_slot0;
                r8 = r7.clamp;
                r7 = _closure1_slot0;
                r7 = r7.finiteOr;
                r2 = r2.rvb;
                r2 = r2.ol;
                r2 = r2.rvb;
                r7 = r7.bind(r6)(r2, r5);
                r2 = _closure1_slot3;
                r7 = r7 + r2;
                r2 = -96.3;
                r2 = r8.bind(r6)(r7, r2, r5);
                r4['rvb'] = r2;
case 843:
                r2 = r0;
                r2 = r2.rvb;
                r1 = r1 != r2;
                if(!r1) { _fun9365_ip = 865; continue _fun9365 }
case 859:
                r1 = r2.ol;
case 865:
                if(!r1) { _fun9365_ip = 900; continue _fun9365 }
case 868:
                r2 = r0;
                r2 = r2.rvb;
                r2 = r2.ol;
                r2 = r2.dry;
                r4 = 'number';
                r2 = typeof r2;
                r1 = r4 === r2;
case 900:
                if(!r1) { _fun9365_ip = 985; continue _fun9365 }
case 903:
                r1 = r0;
                r2 = r1.rvb;
                r2 = r2.ol;
                r4 = _closure1_slot0;
                r4 = r4.clamp;
                r3 = _closure1_slot0;
                r3 = r3.finiteOr;
                r1 = r1.rvb;
                r1 = r1.ol;
                r1 = r1.dry;
                r3 = r3.bind(r6)(r1, r5);
                r1 = -96.3;
                r1 = r4.bind(r6)(r3, r1, r5);
                r2['dry'] = r1;
case 985:
                return r0;
            }
        };
        r1['applyMultichannelPostProcess'] = r0;
        r0 = a6;
        r3 = 0;
        r4 = r0[r3];
        r1 = a1;
        r0 = undefined;
        r1 = r1.bind(r0)(r4);
        var _closure1_slot0 = r1;
        r1 = 0.3;
        var _closure1_slot1 = r1;
        r15 = [0, 0, 1.6, 0];
        r17 = -1.5;
        r15[4] = r17;
        r15[5] = r3;
        r15[6] = r3;
        r8 = 0.5;
        r15[7] = r8;
        r18 = 0.2;
        r15[8] = r18;
        r15[9] = r3;
        r14 = [0, 0, 0.5];
        r14[3] = r17;
        r14[4] = r17;
        r7 = 1;
        r14[5] = r7;
        r1 = 1.5;
        r14[6] = r1;
        r14[7] = r1;
        r4 = 3;
        r14[8] = r4;
        r14[9] = r4;
        r13 = [0, 0, 0];
        r4 = -0.4;
        r13[3] = r4;
        r13[4] = r3;
        r13[5] = r3;
        r13[6] = r3;
        r5 = 3.4;
        r13[7] = r5;
        r13[8] = r3;
        r13[9] = r3;
        r12 = new Array(10);
        r5 = -2;
        r12[0] = r5;
        r9 = 0.8;
        r12[1] = r9;
        r12[2] = r7;
        r6 = 2;
        r12[3] = r6;
        r6 = 1.6;
        r12[4] = r6;
        r12[5] = r7;
        r16 = -1;
        r12[6] = r16;
        r6 = -1.6;
        r12[7] = r6;
        r6 = 1.4;
        r12[8] = r6;
        r12[9] = r18;
        r11 = [0, 1, 0.6, 0, 0, 0];
        r6 = -0.3;
        r11[6] = r6;
        r6 = -0.6;
        r11[7] = r6;
        r11[8] = r6;
        r6 = -0.2;
        r11[9] = r6;
        r10 = new Array(10);
        r10[0] = r16;
        r6 = -0.5;
        r10[1] = r6;
        r10[2] = r3;
        r10[3] = r8;
        r10[4] = r18;
        r10[5] = r9;
        r10[6] = r8;
        r10[7] = r16;
        r10[8] = r16;
        r10[9] = r16;
        r9 = [0, 0, 1.6, 0];
        r9[4] = r17;
        r9[5] = r3;
        r9[6] = r4;
        r9[7] = r7;
        r7 = 0.6;
        r9[8] = r7;
        r9[9] = r3;
        r8 = [0, 0, 0.5, 1.5, 1, 1];
        r8[6] = r6;
        r8[7] = r6;
        r8[8] = r1;
        r8[9] = r1;
        r7 = [1.5, 2, 1, 0.5, 0, 1];
        r7[6] = r6;
        r7[7] = r17;
        r7[8] = r5;
        r7[9] = r3;
        r6 = [0, 0, 1, 0.4, 0, 0, 1.3];
        r5 = -2.6;
        r6[7] = r5;
        r6[8] = r4;
        r6[9] = r3;
        r5 = new Array(10);
        r4 = -0.65;
        r5[0] = r4;
        r5[1] = r4;
        r4 = -0.15;
        r5[2] = r4;
        r4 = 0.35;
        r5[3] = r4;
        r4 = 1.35;
        r5[4] = r4;
        r4 = -0.05;
        r5[5] = r4;
        r4 = -0.85;
        r5[6] = r4;
        r4 = -3.25;
        r5[7] = r4;
        r5[8] = r4;
        r4 = -2.25;
        r5[9] = r4;
        r4 = [0, 0, 1, 0];
        r4[4] = r17;
        r4[5] = r3;
        r4[6] = r16;
        r16 = -2.2;
        r4[7] = r16;
        r16 = 0.4;
        r4[8] = r16;
        r4[9] = r3;
        r3 = {};
        r3[1] = r15;
        r15 = [0.2, 2, 2.6, 3, 2.6, 2, 1.6, 2, 2, 0.2];
        r3[2] = r15;
        r15 = [0.5, 1, 0.5, 0.5, 0.5, 3.5, 2.5, 2.5, 2, 3];
        r3[3] = r15;
        r3[4] = r14;
        r14 = [0.4, 0.4, 0.4, 2.2, 0.4, 1, 0.6, 4.3, 1.8, 0.4];
        r3[5] = r14;
        r3[6] = r13;
        r3[7] = r12;
        r3[8] = r11;
        r3[9] = r10;
        r3[10] = r9;
        r3[11] = r8;
        r3[12] = r7;
        r3[13] = r6;
        r3[14] = r5;
        r3[15] = r4;
        var _closure1_slot2 = r3;
        var _closure1_slot3 = r1;
        r1 = 10;
        var _closure1_slot4 = r1;
        var _closure1_slot5 = r1;
        var _closure1_slot6 = r1;
        r1 = -12;
        var _closure1_slot7 = r1;
        r1 = 12;
        var _closure1_slot8 = r1;
        r1 = ['se', 'dly', 'alc', 'cmp', 'bt', 'chorus', 'flanger', 'spspatial', 'rotate'];
        var _closure1_slot9 = r1;
        r1 = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        var _closure1_slot10 = r1;
        return r0;
    };
factories[944]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r1 = a5;
        r0 = global;
        r5 = r0.Object;
        r4 = r5.defineProperty;
        r3 = {};
        r0 = true;
        r3['value'] = r0;
        r0 = '__esModule';
        r0 = r4.bind(r5)(r1, r0, r3);
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9368: for(var _fun9368_ip = 0; ; ) switch(_fun9368_ip) {
case 0:
                r7 = undefined;
                r5 = false;
                r4 = 'object';
                r3 = 'number';
                r0 = undefined;
                r9 = undefined;
                r6 = undefined;
                r2 = _closure1_slot0;
                r8 = r2.deepClone;
                r2 = a0;
                r2 = r8.bind(r7)(r2);
                r0 = r2;
                r2 = r2.se;
                if(!r2) { _fun9368_ip = 67; continue _fun9368 }
case 51:
                r8 = r0;
                r8 = r8.se;
                r8 = typeof r8;
                r2 = r4 === r8;
case 67:
                if(!r2) { _fun9368_ip = 85; continue _fun9368 }
case 70:
                r2 = r0;
                r2 = r2.se;
                r2['sshaper'] = r5;
case 85:
                r10 = _closure1_slot1;
                r2 = r10;
                var __iterator_values_3=Array.from(r2), __iterator_index_3=0; r8={return:function(){}}; r2=function(){if(__iterator_index_3>=__iterator_values_3.length){r8=undefined;return {value:undefined}}return {value:__iterator_values_3[__iterator_index_3++]}};
case 95:
                r11 = r2().value;
                r10 = r8;
                if(!(r10 !== r7)) { _fun9368_ip = 153; continue _fun9368 }
case 106: // try_start_0
                r10 = r0;
                r11 = r10[r11];
                r9 = r11;
                r10 = r11;
                if(!r11) { _fun9368_ip = 132; continue _fun9368 }
case 122:
                r11 = r9;
                r11 = typeof r11;
                r10 = r4 === r11;
case 132:
                if(!r10) { _fun9368_ip = 144; continue _fun9368 }
case 135:
                r10 = r9;
                r10['on'] = r5;
case 144: // try_end0
                _fun9368_ip = 95; continue _fun9368;
case 146: // catch_target0
                CatchBlockStart(arg_register=2);
                r8.return();
                throw r2;
case 153:
                r2 = r0;
                r2 = r2.rvb;
                if(!r2) { _fun9368_ip = 346; continue _fun9368 }
case 168:
                r2 = r0;
                r2 = r2.rvb;
                r2 = typeof r2;
                if(!(r4 === r2)) { _fun9368_ip = 346; continue _fun9368 }
case 187:
                r2 = r0;
                r2 = r2.rvb;
                r4 = r2.ol;
                if(!r4) { _fun9368_ip = 271; continue _fun9368 }
case 205:
                r2 = r4.rvb;
                r2 = typeof r2;
                if(!(r3 === r2)) { _fun9368_ip = 238; continue _fun9368 }
case 218:
                r8 = r4.rvb;
                r2 = _closure1_slot2;
                r2 = r8 - r2;
                r4['rvb'] = r2;
case 238:
                r2 = r4.er;
                r2 = typeof r2;
                if(!(r3 === r2)) { _fun9368_ip = 271; continue _fun9368 }
case 251:
                r8 = r4.er;
                r2 = _closure1_slot2;
                r2 = r8 - r2;
                r4['er'] = r2;
case 271:
                r2 = r0;
                r2 = r2.rvb;
                r2 = r2.on;
                if(!(r5 !== r2)) { _fun9368_ip = 346; continue _fun9368 }
case 289:
                r2 = r0;
                r2 = r2.rvb;
                r4 = r2.rvb;
                r2 = r4;
                if(!r2) { _fun9368_ip = 323; continue _fun9368 }
case 310:
                r8 = r4.dtime;
                r8 = typeof r8;
                r2 = r3 === r8;
case 323:
                if(!r2) { _fun9368_ip = 346; continue _fun9368 }
case 326:
                r8 = r4.dtime;
                r2 = _closure1_slot5;
                r2 = r8 * r2;
                r4['dtime'] = r2;
case 346:
                r2 = r0;
                r2 = r2.eq;
                if(!r2) { _fun9368_ip = 376; continue _fun9368 }
case 358:
                r4 = r0;
                r4 = r4.eq;
                r4 = r4.on;
                r2 = r5 !== r4;
case 376:
                if(!r2) { _fun9368_ip = 412; continue _fun9368 }
case 379:
                r4 = global;
                r9 = r4.Array;
                r8 = r9.isArray;
                r4 = r0;
                r4 = r4.eq;
                r4 = r4.eqs;
                r2 = r8.bind(r9)(r4);
case 412:
                if(!r2) { _fun9368_ip = 459; continue _fun9368 }
case 415:
                r2 = r0;
                r4 = r2.eq;
                r2 = r2.eq;
                r9 = r2.eqs;
                r8 = r9.map;
                r2 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
                    r1 = _closure1_slot3;
                    r0 = a0;
                    r0 = r0 * r1;
                    return r0;
                };
                r2 = r8.bind(r9)(r2);
                r4['eqs'] = r2;
case 459:
                r2 = r0;
                r2 = r2.peq;
                if(!r2) { _fun9368_ip = 641; continue _fun9368 }
case 474:
                r2 = r0;
                r2 = r2.peq;
                r2 = r2.on;
                if(!(r5 !== r2)) { _fun9368_ip = 641; continue _fun9368 }
case 495:
                r2 = global;
                r8 = r2.Array;
                r4 = r8.isArray;
                r2 = r0;
                r2 = r2.peq;
                r2 = r2.f;
                r2 = r4.bind(r8)(r2);
                if(!r2) { _fun9368_ip = 641; continue _fun9368 }
case 531:
                r2 = r0;
                r2 = r2.peq;
                r8 = r2.f;
                r2 = r8;
                var __iterator_values_4=Array.from(r2), __iterator_index_4=0; r4={return:function(){}}; r2=function(){if(__iterator_index_4>=__iterator_values_4.length){r4=undefined;return {value:undefined}}return {value:__iterator_values_4[__iterator_index_4++]}};
case 552:
                r10 = r2().value;
                r8 = r4;
                if(!(r8 !== r7)) { _fun9368_ip = 641; continue _fun9368 }
case 563: // try_start_1
                r6 = r10;
                r9 = r10;
                if(!r10) { _fun9368_ip = 584; continue _fun9368 }
case 572:
                r10 = r6;
                r10 = r10.on;
                r9 = r5 !== r10;
case 584:
                r8 = r9;
                if(!r9) { _fun9368_ip = 606; continue _fun9368 }
case 590:
                r9 = r6;
                r9 = r9.gain;
                r9 = typeof r9;
                r8 = r3 === r9;
case 606:
                if(!r8) { _fun9368_ip = 632; continue _fun9368 }
case 609:
                r9 = r6;
                r10 = r9.gain;
                r8 = _closure1_slot3;
                r8 = r10 * r8;
                r9['gain'] = r8;
case 632: // try_end1
                _fun9368_ip = 552; continue _fun9368;
case 634: // catch_target1
                CatchBlockStart(arg_register=2);
                r4.return();
                throw r2;
case 641:
                r2 = r0;
                r2 = r2.se;
                if(!r2) { _fun9368_ip = 671; continue _fun9368 }
case 653:
                r4 = r0;
                r4 = r4.se;
                r4 = r4.on;
                r2 = r5 !== r4;
case 671:
                if(!r2) { _fun9368_ip = 776; continue _fun9368 }
case 674:
                r2 = r0;
                r2 = r2.se;
                r2 = r2.stereoizer;
                r2 = typeof r2;
                if(!(r3 === r2)) { _fun9368_ip = 725; continue _fun9368 }
case 696:
                r2 = r0;
                r4 = r2.se;
                r5 = r4.stereoizer;
                r2 = _closure1_slot4;
                r2 = r5 * r2;
                r4['stereoizer'] = r2;
case 725:
                r2 = r0;
                r2 = r2.se;
                r2 = r2.presence;
                r2 = typeof r2;
                if(!(r3 === r2)) { _fun9368_ip = 776; continue _fun9368 }
case 747:
                r2 = r0;
                r2 = r2.se;
                r3 = r2.presence;
                r1 = _closure1_slot4;
                r1 = r3 * r1;
                r2['presence'] = r1;
case 776:
                return r0;
            }
        };
        r1['applyDegradation'] = r0;
        r1 = a6;
        r0 = 0;
        r3 = r1[r0];
        r1 = a1;
        r0 = undefined;
        r1 = r1.bind(r0)(r3);
        var _closure1_slot0 = r1;
        r1 = ['dly', 'chorus', 'flanger'];
        var _closure1_slot1 = r1;
        r1 = 3;
        var _closure1_slot2 = r1;
        r1 = 0.8;
        var _closure1_slot3 = r1;
        r1 = 0.6;
        var _closure1_slot4 = r1;
        r1 = 0.7;
        var _closure1_slot5 = r1;
        return r0;
    };
factories[945]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r2 = a5;
        r1 = global;
        r6 = r1.Object;
        r5 = r6.defineProperty;
        r4 = {};
        r0 = true;
        r4['value'] = r0;
        r0 = '__esModule';
        r0 = r5.bind(r6)(r2, r0, r4);
        r0 = undefined;
        r2['EMOTION_INDEX'] = r0;
        r2['EMOTION_LABELS'] = r0;
        r2['GENRE_INDEX'] = r0;
        r2['VALID_EMOTIONS'] = r0;
        r2['VALID_FAMILIES'] = r0;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
            r0 = global;
            r0 = r0.Object;
            r0 = r0.prototype;
            r3 = r0.hasOwnProperty;
            r2 = r3.call;
            r1 = _closure1_slot0;
            r0 = a0;
            r0 = r2.bind(r3)(r1, r0);
            return r0;
        };
        r2['isEmotionLabel'] = r4;
        r5 = {'default': 0, 'ecstatic': 1, 'passionate': 2, 'energetic': 3, 'joyful': 4, 'sweet': 5, 'confident': 6, 'romantic': 7, 'relaxed': 8, 'healing': 9, 'calm': 10, 'nostalgic': 11, 'lonely': 12, 'sad': 13, 'bitter': 14, 'depressed': 15};
        r2['EMOTION_INDEX'] = r5;
        var _closure1_slot0 = r5;
        r4 = r1.Object;
        r3 = r4.keys;
        r5 = r3.bind(r4)(r5);
        r2['EMOTION_LABELS'] = r5;
        r3 = r1.Set;
        r4 = r3.prototype;
        r4 = Object.create(r4, {constructor: {value: r3}});
        r10 = r4;
        r9 = r5;
        r3 = originalConstruct(r3, [r9, r8]);
        r3 = r3 instanceof Object ? r3 : r4;
        r2['VALID_EMOTIONS'] = r3;
        r5 = {'pop-family': 0, 'folk-family': 1, 'rhythm-family': 2, 'instrumental-family': 3};
        r2['GENRE_INDEX'] = r5;
        r4 = r1.Set;
        r3 = r1.Object;
        r1 = r3.keys;
        r9 = r1.bind(r3)(r5);
        r3 = r4.prototype;
        r3 = Object.create(r3, {constructor: {value: r4}});
        r10 = r3;
        r1 = originalConstruct(r4, [r9, r8]);
        r1 = r1 instanceof Object ? r1 : r3;
        r2['VALID_FAMILIES'] = r1;
        return r0;
    };
factories[949]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r5 = a1;
        r3 = a5;
        r6 = a6;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: E, environment: r2
            _fun9413: for(var _fun9413_ip = 0; ; ) switch(_fun9413_ip) {
case 0:
                r3 = a0;
                r0 = global;
                r1 = r0.Array;
                r0 = r1.isArray;
                r0 = r0.bind(r1)(r3);
                if(!r0) { _fun9413_ip = 35; continue _fun9413 }
case 24:
                r2 = r3.length;
                r1 = 0;
                r0 = r2 > r1;
case 35:
                if(!r0) { _fun9413_ip = 62; continue _fun9413 }
case 38:
                r2 = r3.every;
                r1 = _closure1_slot3;
                r1 = r1.isPositiveInteger;
                r0 = r2.bind(r3)(r1);
case 62:
                return r0;
            }
        };
        var _closure1_slot8 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: x, environment: r2
            _fun9414: for(var _fun9414_ip = 0; ; ) switch(_fun9414_ip) {
case 0:
                r5 = a0;
                r2 = a1;
                r0 = null;
                if(!(r0 != r5)) { _fun9414_ip = 36; continue _fun9414 }
case 14:
                var _closure2_slot0 = r2;
                r4 = r5.find;
                r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
                    r0 = a0;
                    r1 = r0.name;
                    r0 = _closure2_slot0;
                    r0 = r1 === r0;
                    return r0;
                };
                r0 = r4.bind(r5)(r0);
                _fun9414_ip = 42; continue _fun9414;
case 36:
                _closure2_slot0 = r2;
                r0 = undefined;
case 42:
                return r0;
            }
        };
        var _closure1_slot9 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: F, environment: r2
            _fun9416: for(var _fun9416_ip = 0; ; ) switch(_fun9416_ip) {
case 0:
                r0 = a0;
                if(!r0) { _fun9416_ip = 29; continue _fun9416 }
case 6:
                r3 = _closure1_slot8;
                r2 = r0.shape;
                r4 = undefined;
                r2 = r3.bind(r4)(r2);
                if(r2) { _fun9416_ip = 33; continue _fun9416 }
case 29:
                r2 = undefined;
                return r2;
case 33:
                r3 = r0.name;
                r2 = r0.dtype;
                r1 = _closure1_slot1;
                r1 = r1.default;
                r0 = r0.shape;
                r1 = r1.bind(r4)(r0);
                r0 = {};
                r0['name'] = r3;
                r0['dtype'] = r2;
                r0['shape'] = r1;
                return r0;
            }
        };
        var _closure1_slot10 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: v, environment: r2
            _fun9417: for(var _fun9417_ip = 0; ; ) switch(_fun9417_ip) {
case 0:
                r2 = undefined;
                r1 = undefined;
                r4 = a0;
                var __iterator_values_5=Array.from(r4), __iterator_index_5=0; r3={return:function(){}}; r4=function(){if(__iterator_index_5>=__iterator_values_5.length){r3=undefined;return {value:undefined}}return {value:__iterator_values_5[__iterator_index_5++]}};
case 13:
                r6 = r4().value;
                r5 = r3;
                if(!(r5 !== r2)) { _fun9417_ip = 67; continue _fun9417 }
case 24: // try_start_0
                r1 = r6;
                r5 = _closure1_slot8;
                r5 = r5.bind(r2)(r6);
                if(r5) { _fun9417_ip = 41; continue _fun9417 }
case 39: // try_end0
                _fun9417_ip = 13; continue _fun9417;
case 41: // try_start_1
                r4 = _closure1_slot1;
                r4 = r4.default;
                r1 = r4.bind(r2)(r1);
case 55: // try_end1
                r3.return();
                return r1;
case 60: // catch_target0 // catch_target1
                CatchBlockStart(arg_register=1);
                r3.return();
                throw r1;
case 67:
                r0 = _closure1_slot1;
                r1 = r0.default;
                r0 = a1;
                r0 = r1.bind(r2)(r0);
                return r0;
            }
        };
        var _closure1_slot11 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: A, environment: r2
            _fun9418: for(var _fun9418_ip = 0; ; ) switch(_fun9418_ip) {
case 0:
                r4 = a0;
                r1 = undefined;
                r3 = _closure1_slot10;
                r2 = r4.runtimeTensor;
                r7 = r3.bind(r1)(r2);
                r6 = _closure1_slot11;
                r3 = r4.runtime;
                r5 = null;
                r2 = undefined;
                if(!(r5 != r3)) { _fun9418_ip = 47; continue _fun9418 }
case 41:
                r2 = r3.shape;
case 47:
                r3 = new Array(3);
                r3[0] = r2;
                r2 = undefined;
                if(!(r5 != r7)) { _fun9418_ip = 67; continue _fun9418 }
case 61:
                r2 = r7.shape;
case 67:
                r3[1] = r2;
                r8 = r4.requested;
                r2 = undefined;
                if(!(r5 != r8)) { _fun9418_ip = 89; continue _fun9418 }
case 83:
                r2 = r8.shape;
case 89:
                r3[2] = r2;
                r2 = r4.fallback;
                r2 = r2.shape;
                r2 = r6.bind(r1)(r3, r2);
                r6 = r4.runtime;
                r3 = undefined;
                if(!(r5 != r6)) { _fun9418_ip = 129; continue _fun9418 }
case 123:
                r3 = r6.dtype;
case 129:
                if(!(r5 == r3)) { _fun9418_ip = 148; continue _fun9418 }
case 133:
                r6 = undefined;
                if(!(r5 != r7)) { _fun9418_ip = 145; continue _fun9418 }
case 139:
                r6 = r7.dtype;
case 145:
                r3 = r6;
case 148:
                if(!(r5 == r3)) { _fun9418_ip = 173; continue _fun9418 }
case 152:
                r7 = r4.requested;
                r6 = undefined;
                if(!(r5 != r7)) { _fun9418_ip = 170; continue _fun9418 }
case 164:
                r6 = r7.dtype;
case 170:
                r3 = r6;
case 173:
                if(!(r5 == r3)) { _fun9418_ip = 189; continue _fun9418 }
case 177:
                r5 = r4.fallback;
                r3 = r5.dtype;
case 189:
                r4 = r4.name;
                r0 = _closure1_slot3;
                r0 = r0.getTensorSize;
                r1 = r0.bind(r1)(r2);
                r0 = {};
                r0['name'] = r4;
                r0['dtype'] = r3;
                r0['shape'] = r2;
                r0['size'] = r1;
                return r0;
            }
        };
        var _closure1_slot12 = r0;
        r0 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: h, environment: r2
            _fun9419: for(var _fun9419_ip = 0; ; ) switch(_fun9419_ip) {
case 0:
                r3 = a0;
                r5 = a1;
                r4 = a2;
                if(!r3) { _fun9419_ip = 25; continue _fun9419 }
case 14:
                r2 = r3.length;
                r0 = 0;
                if(!(!(r2 > r0))) { _fun9419_ip = 84; continue _fun9419 }
case 25:
                if(!r5) { _fun9419_ip = 39; continue _fun9419 }
case 28:
                r2 = r5.length;
                r0 = 0;
                if(!(!(r2 > r0))) { _fun9419_ip = 67; continue _fun9419 }
case 39:
                r0 = global;
                r2 = r0.Object;
                r0 = r2.keys;
                r6 = null;
                if(!(r6 == r4)) { _fun9419_ip = 60; continue _fun9419 }
case 58:
                r4 = {};
case 60:
                r0 = r0.bind(r2)(r4);
                _fun9419_ip = 82; continue _fun9419;
case 67:
                r4 = r5.map;
                r2 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r0 = a0;
                    r0 = r0.name;
                    return r0;
                };
                r0 = r4.bind(r5)(r2);
case 82:
                _fun9419_ip = 99; continue _fun9419;
case 84:
                r2 = r3.map;
                r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r0 = a0;
                    r0 = r0.name;
                    return r0;
                };
                r0 = r2.bind(r3)(r1);
case 99:
                return r0;
            }
        };
        var _closure1_slot13 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: y, environment: r2
            r1 = _closure1_slot6;
            r2 = [1];
            r2[1] = r1;
            r1 = _closure1_slot6;
            r0 = {};
            r3 = a0;
            r0['name'] = r3;
            r3 = 'float32';
            r0['dtype'] = r3;
            r0['shape'] = r2;
            r0['size'] = r1;
            return r0;
        };
        var _closure1_slot14 = r0;
        r0 = 0;
        r1 = r6[r0];
        r0 = undefined;
        r4 = r5.bind(r0)(r1);
        r1 = global;
        r9 = r1.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r1 = true;
        r7['value'] = r1;
        r1 = '__esModule';
        r1 = r8.bind(r9)(r3, r1, r7);
        r3['EMOFX_AEV2_CORE_SIZE'] = r0;
        r3['EMOFX_AEV2_DEFAULT_SIZE'] = r0;
        r3['EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE'] = r0;
        r3['EMOFX_FEATURE_CORE_SIZE'] = r0;
        r3['EMOFX_FEATURE_DEFAULT_SIZE'] = r0;
        r3['EMOFX_TENSOR_NAMES'] = r0;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r0 = a0;
            r3 = r0.modelId;
            r1 = r0.inputs;
            r1 = r1.emotion_idx;
            r6 = r1.name;
            r1 = r0.inputs;
            r1 = r1.emotion_idx;
            r5 = r1.dtype;
            r2 = _closure1_slot1;
            r4 = r2.default;
            r2 = r0.inputs;
            r2 = r2.emotion_idx;
            r2 = r2.shape;
            r7 = undefined;
            r2 = r4.bind(r7)(r2);
            r4 = {};
            r4['name'] = r6;
            r4['dtype'] = r5;
            r4['shape'] = r2;
            r2 = new Array(4);
            r2[0] = r4;
            r4 = r0.inputs;
            r4 = r4.genre_idx;
            r8 = r4.name;
            r4 = r0.inputs;
            r4 = r4.genre_idx;
            r6 = r4.dtype;
            r4 = _closure1_slot1;
            r5 = r4.default;
            r4 = r0.inputs;
            r4 = r4.genre_idx;
            r4 = r4.shape;
            r5 = r5.bind(r7)(r4);
            r4 = {};
            r4['name'] = r8;
            r4['dtype'] = r6;
            r4['shape'] = r5;
            r2[1] = r4;
            r4 = r0.inputs;
            r4 = r4.features;
            r8 = r4.name;
            r4 = r0.inputs;
            r4 = r4.features;
            r6 = r4.dtype;
            r4 = _closure1_slot1;
            r5 = r4.default;
            r4 = r0.inputs;
            r4 = r4.features;
            r4 = r4.shape;
            r5 = r5.bind(r7)(r4);
            r4 = {};
            r4['name'] = r8;
            r4['dtype'] = r6;
            r4['shape'] = r5;
            r2[2] = r4;
            r4 = r0.inputs;
            r4 = r4.base_extra_flat;
            r6 = r4.name;
            r4 = r0.inputs;
            r4 = r4.base_extra_flat;
            r5 = r4.dtype;
            r1 = _closure1_slot1;
            r4 = r1.default;
            r1 = r0.inputs;
            r1 = r1.base_extra_flat;
            r1 = r1.shape;
            r4 = r4.bind(r7)(r1);
            r1 = {};
            r1['name'] = r6;
            r1['dtype'] = r5;
            r1['shape'] = r4;
            r2[3] = r1;
            r1 = global;
            r4 = r1.Object;
            r1 = r4.values;
            r0 = r0.outputs;
            r4 = r1.bind(r4)(r0);
            r1 = r4.map;
            r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                r0 = a0;
                r3 = r0.name;
                r2 = r0.dtype;
                r1 = _closure1_slot1;
                r4 = r1.default;
                r1 = r0.shape;
                r0 = undefined;
                r1 = r4.bind(r0)(r1);
                r0 = {};
                r0['name'] = r3;
                r0['dtype'] = r2;
                r0['shape'] = r1;
                return r0;
            };
            r1 = r1.bind(r4)(r0);
            r0 = {};
            r0['modelId'] = r3;
            r0['inputs'] = r2;
            r0['outputs'] = r1;
            return r0;
        };
        r3['buildModelSignatureFromResolved'] = r1;
        r1 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9425: for(var _fun9425_ip = 0; ; ) switch(_fun9425_ip) {
case 0:
                r7 = undefined;
                r3 = undefined;
                r5 = undefined;
                r1 = arguments.length;
                r2 = 0;
                if(!(r1 > r2)) { _fun9425_ip = 25; continue _fun9425 }
case 17:
                r1 = arguments[r2];
                if(!(r7 === r1)) { _fun9425_ip = 29; continue _fun9425 }
case 25:
                r1 = {};
                _fun9425_ip = 33; continue _fun9425;
case 29:
                r1 = arguments[r2];
case 33:
                r3 = r1.fallback;
                r2 = null;
                if(!(r2 == r3)) { _fun9425_ip = 373; continue _fun9425 }
case 48:
                r6 = _closure1_slot2;
                r9 = r6.EMOFX_DEFAULT_MODEL_ID;
                r6 = _closure1_slot7;
                r6 = r6.emotion_idx;
                r12 = {};
                r12['name'] = r6;
                r8 = 'int32';
                r12['dtype'] = r8;
                r6 = [1];
                r12['shape'] = r6;
                r6 = 1;
                r12['size'] = r6;
                r10 = _closure1_slot7;
                r10 = r10.genre_idx;
                r11 = {};
                r11['name'] = r10;
                r11['dtype'] = r8;
                r8 = [1];
                r11['shape'] = r8;
                r11['size'] = r6;
                r6 = _closure1_slot7;
                r13 = r6.features;
                r6 = _closure1_slot4;
                r8 = [1];
                r8[1] = r6;
                r6 = _closure1_slot4;
                r10 = {};
                r10['name'] = r13;
                r13 = 'float32';
                r10['dtype'] = r13;
                r10['shape'] = r8;
                r10['size'] = r6;
                r6 = _closure1_slot7;
                r15 = r6.base_extra_flat;
                r6 = _closure1_slot5;
                r14 = [1];
                r14[1] = r6;
                r8 = _closure1_slot5;
                r6 = {};
                r6['name'] = r15;
                r6['dtype'] = r13;
                r6['shape'] = r14;
                r6['size'] = r8;
                r8 = {};
                r8['emotion_idx'] = r12;
                r8['genre_idx'] = r11;
                r8['features'] = r10;
                r8['base_extra_flat'] = r6;
                r6 = _closure1_slot0;
                r11 = r6.default;
                r6 = _closure1_slot7;
                r10 = r6.aev2;
                r6 = _closure1_slot7;
                r14 = r6.aev2;
                r6 = _closure1_slot6;
                r12 = [1];
                r12[1] = r6;
                r4 = _closure1_slot6;
                r6 = {};
                r6['name'] = r14;
                r6['dtype'] = r13;
                r6['shape'] = r12;
                r6['size'] = r4;
                r4 = {};
                r6 = r11.bind(r7)(r4, r10, r6);
                r4 = {};
                r4['modelId'] = r9;
                r4['inputs'] = r8;
                r4['outputs'] = r6;
                r3 = r4;
case 373:
                r4 = r1.requested;
                r13 = undefined;
                if(!(r2 != r4)) { _fun9425_ip = 391; continue _fun9425 }
case 385:
                r13 = r4.inputs;
case 391:
                r4 = r1.runtime;
                r12 = undefined;
                if(!(r2 != r4)) { _fun9425_ip = 409; continue _fun9425 }
case 403:
                r12 = r4.inputs;
case 409:
                r4 = r1.requested;
                r8 = undefined;
                if(!(r2 != r4)) { _fun9425_ip = 427; continue _fun9425 }
case 421:
                r8 = r4.outputs;
case 427:
                r4 = r1.runtime;
                r6 = undefined;
                if(!(r2 != r4)) { _fun9425_ip = 445; continue _fun9425 }
case 439:
                r6 = r4.outputs;
case 445:
                var _closure2_slot0 = r1;
                var _closure2_slot1 = r3;
                var _closure2_slot2 = r8;
                var _closure2_slot4 = r7;
                var _closure2_slot3 = r6;
                r4 = _closure1_slot13;
                r3 = r3.outputs;
                r6 = r4.bind(r7)(r6, r8, r3);
                r3 = {};
                _closure2_slot4 = r3;
                r5 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: Z, environment: r0
                    _fun9426: for(var _fun9426_ip = 0; ; ) switch(_fun9426_ip) {
case 0:
                        r3 = a0;
                        r0 = undefined;
                        r2 = _closure2_slot1;
                        r2 = r2.outputs;
                        r8 = r2[r3];
                        r5 = null;
                        if(!(r5 == r8)) { _fun9426_ip = 42; continue _fun9426 }
case 30:
                        r2 = _closure1_slot14;
                        r8 = r2.bind(r0)(r3);
case 42:
                        r2 = _closure2_slot4;
                        r4 = _closure1_slot12;
                        r10 = _closure1_slot9;
                        r6 = _closure2_slot2;
                        r7 = r10.bind(r0)(r6, r3);
                        r6 = _closure2_slot3;
                        r6 = r10.bind(r0)(r6, r3);
                        r9 = _closure2_slot0;
                        r10 = r9.outputs;
                        if(!(r5 != r10)) { _fun9426_ip = 113; continue _fun9426 }
case 91:
                        var _closure3_slot0 = r3;
                        r9 = r10.find;
                        r5 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                            r0 = a0;
                            r1 = r0.name;
                            r0 = _closure3_slot0;
                            r0 = r1 === r0;
                            return r0;
                        };
                        r5 = r9.bind(r10)(r5);
                        _fun9426_ip = 119; continue _fun9426;
case 113:
                        _closure3_slot0 = r3;
                        r5 = undefined;
case 119:
                        r1 = {};
                        r1['name'] = r3;
                        r1['fallback'] = r8;
                        r1['requested'] = r7;
                        r1['runtime'] = r6;
                        r1['runtimeTensor'] = r5;
                        r1 = r4.bind(r0)(r1);
                        r2[r3] = r1;
                        return r0;
                    }
                };
                r3 = r6;
                var __iterator_values_6=Array.from(r3), __iterator_index_6=0; r4={return:function(){}}; r3=function(){if(__iterator_index_6>=__iterator_values_6.length){r4=undefined;return {value:undefined}}return {value:__iterator_values_6[__iterator_index_6++]}};
case 502:
                r8 = r3().value;
                r6 = r4;
                if(!(r6 !== r7)) { _fun9425_ip = 530; continue _fun9425 }
case 513: // try_start_0
                r6 = r5;
                r6 = r6.bind(r7)(r8);
case 521: // try_end0
                _fun9425_ip = 502; continue _fun9425;
case 523: // catch_target0
                CatchBlockStart(arg_register=3);
                r4.return();
                throw r3;
case 530:
                r3 = _closure2_slot0;
                r4 = r3.runtime;
                r3 = undefined;
                if(!(r2 != r4)) { _fun9425_ip = 552; continue _fun9425 }
case 546:
                r3 = r4.modelId;
case 552:
                if(!(r2 == r3)) { _fun9425_ip = 581; continue _fun9425 }
case 556:
                r4 = _closure2_slot0;
                r5 = r4.requested;
                r4 = undefined;
                if(!(r2 != r5)) { _fun9425_ip = 578; continue _fun9425 }
case 572:
                r4 = r5.modelId;
case 578:
                r3 = r4;
case 581:
                if(!(r2 == r3)) { _fun9425_ip = 595; continue _fun9425 }
case 585:
                r2 = _closure2_slot1;
                r3 = r2.modelId;
case 595:
                r2 = _closure1_slot12;
                r4 = _closure1_slot7;
                r10 = r4.emotion_idx;
                r4 = _closure2_slot1;
                r4 = r4.inputs;
                r9 = r4.emotion_idx;
                r8 = _closure1_slot9;
                r4 = _closure1_slot7;
                r4 = r4.emotion_idx;
                r6 = r8.bind(r7)(r13, r4);
                r4 = _closure1_slot7;
                r4 = r4.emotion_idx;
                r5 = r8.bind(r7)(r12, r4);
                r4 = {};
                r4['name'] = r10;
                r4['fallback'] = r9;
                r4['requested'] = r6;
                r4['runtime'] = r5;
                r6 = r2.bind(r7)(r4);
                r4 = _closure1_slot7;
                r11 = r4.genre_idx;
                r4 = _closure2_slot1;
                r4 = r4.inputs;
                r10 = r4.genre_idx;
                r4 = _closure1_slot7;
                r4 = r4.genre_idx;
                r9 = r8.bind(r7)(r13, r4);
                r4 = _closure1_slot7;
                r4 = r4.genre_idx;
                r5 = r8.bind(r7)(r12, r4);
                r4 = {};
                r4['name'] = r11;
                r4['fallback'] = r10;
                r4['requested'] = r9;
                r4['runtime'] = r5;
                r5 = r2.bind(r7)(r4);
                r4 = _closure1_slot7;
                r14 = r4.features;
                r4 = _closure2_slot1;
                r4 = r4.inputs;
                r11 = r4.features;
                r4 = _closure1_slot7;
                r4 = r4.features;
                r10 = r8.bind(r7)(r13, r4);
                r4 = _closure1_slot7;
                r4 = r4.features;
                r9 = r8.bind(r7)(r12, r4);
                r4 = {};
                r4['name'] = r14;
                r4['fallback'] = r11;
                r4['requested'] = r10;
                r4['runtime'] = r9;
                r4 = r2.bind(r7)(r4);
                r9 = _closure1_slot7;
                r11 = r9.base_extra_flat;
                r9 = _closure2_slot1;
                r9 = r9.inputs;
                r10 = r9.base_extra_flat;
                r9 = _closure1_slot7;
                r9 = r9.base_extra_flat;
                r9 = r8.bind(r7)(r13, r9);
                r1 = _closure1_slot7;
                r1 = r1.base_extra_flat;
                r8 = r8.bind(r7)(r12, r1);
                r1 = {};
                r1['name'] = r11;
                r1['fallback'] = r10;
                r1['requested'] = r9;
                r1['runtime'] = r8;
                r1 = r2.bind(r7)(r1);
                r2 = {};
                r2['emotion_idx'] = r6;
                r2['genre_idx'] = r5;
                r2['features'] = r4;
                r2['base_extra_flat'] = r1;
                r1 = _closure2_slot4;
                r0 = {};
                r0['modelId'] = r3;
                r0['inputs'] = r2;
                r0['outputs'] = r1;
                return r0;
            }
        };
        r3['resolveEmoFXModelSignature'] = r1;
        r1 = 1;
        r1 = r6[r1];
        r1 = r5.bind(r0)(r1);
        r1 = r4.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r6[r1];
        r1 = r5.bind(r0)(r1);
        r1 = r4.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 3;
        r1 = r6[r1];
        r1 = r5.bind(r0)(r1);
        var _closure1_slot2 = r1;
        r4 = 4;
        r4 = r6[r4];
        r4 = r5.bind(r0)(r4);
        var _closure1_slot3 = r4;
        r4 = 50;
        r3['EMOFX_FEATURE_CORE_SIZE'] = r4;
        r4 = r1.EMOFX_FEATURE_DEFAULT_SIZE;
        r3['EMOFX_FEATURE_DEFAULT_SIZE'] = r4;
        var _closure1_slot4 = r4;
        r4 = 19;
        r3['EMOFX_AEV2_CORE_SIZE'] = r4;
        r4 = r1.EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE;
        r3['EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE'] = r4;
        var _closure1_slot5 = r4;
        r1 = r1.EMOFX_AEV2_DEFAULT_SIZE;
        r3['EMOFX_AEV2_DEFAULT_SIZE'] = r1;
        var _closure1_slot6 = r1;
        r1 = {'emotion_idx': 'emotion_idx', 'genre_idx': 'genre_idx', 'features': 'features', 'base_extra_flat': 'base_extra_flat', 'aev2': 'aev2'};
        r3['EMOFX_TENSOR_NAMES'] = r1;
        var _closure1_slot7 = r1;
        return r0;
    };
factories[950]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r2 = a5;
        r0 = global;
        r4 = r0.Object;
        r3 = r4.defineProperty;
        r1 = {};
        r0 = true;
        r1['value'] = r0;
        r0 = '__esModule';
        r0 = r3.bind(r4)(r2, r0, r1);
        r0 = undefined;
        r2['EMOFX_AEV2_DEFAULT_SIZE'] = r0;
        r2['EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE'] = r0;
        r2['EMOFX_DEFAULT_MODEL_ID'] = r0;
        r2['EMOFX_FEATURE_DEFAULT_SIZE'] = r0;
        r1 = 'motional_sound_effects_model';
        r2['EMOFX_DEFAULT_MODEL_ID'] = r1;
        r1 = 64;
        r2['EMOFX_FEATURE_DEFAULT_SIZE'] = r1;
        r1 = 32;
        r2['EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE'] = r1;
        r2['EMOFX_AEV2_DEFAULT_SIZE'] = r1;
        return r0;
    };
factories[952]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a1;
        r1 = a5;
        r4 = a6;
        r0 = global;
        r7 = r0.Object;
        r6 = r7.defineProperty;
        r5 = {};
        r0 = true;
        r5['value'] = r0;
        r0 = '__esModule';
        r0 = r6.bind(r7)(r1, r0, r5);
        r0 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9437: for(var _fun9437_ip = 0; ; ) switch(_fun9437_ip) {
case 0:
                r7 = a0;
                r5 = a2;
                r4 = 0;
                r2 = undefined;
                r1 = global;
                r8 = r1.Object;
                r6 = r8.keys;
                r3 = a1;
                r3 = r3.outputs;
                r3 = r6.bind(r8)(r3);
                r3 = r3[r4];
                if(r3) { _fun9437_ip = 78; continue _fun9437 }
case 46:
                r9 = r1.Error;
                r6 = r9.prototype;
                r8 = Object.create(r6, {constructor: {value: r9}});
                r15 = 'decode: no output tensor defined in signature';
                r16 = r8;
                r6 = originalConstruct(r9, [r15, r14]);
                r6 = r6 instanceof Object ? r6 : r8;
                throw r6;
case 78:
                var _closure2_slot0 = r3;
                r6 = r7.find;
                r3 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    r0 = a0;
                    r1 = r0.name;
                    r0 = _closure2_slot0;
                    r0 = r1 === r0;
                    return r0;
                };
                r6 = r6.bind(r7)(r3);
                if(r6) { _fun9437_ip = 149; continue _fun9437 }
case 101:
                r7 = r1.Error;
                r3 = _closure2_slot0;
                r0 = 'decode: missing "';
                r8 = r0 + r3;
                r0 = r7.prototype;
                r3 = Object.create(r0, {constructor: {value: r7}});
                r0 = '" tensor in outputs';
                r15 = r8 + r0;
                r16 = r3;
                r0 = originalConstruct(r7, [r15, r14]);
                r0 = r0 instanceof Object ? r0 : r3;
                throw r0;
case 149:
                r0 = r6.data;
                r8 = r1.Array;
                r7 = r8.isArray;
                r3 = r6.shape;
                r3 = r7.bind(r8)(r3);
                if(!r3) { _fun9437_ip = 194; continue _fun9437 }
case 179:
                r3 = r6.shape;
                r3 = r3.length;
                if(!(!(r3 > r4))) { _fun9437_ip = 218; continue _fun9437 }
case 194:
                r8 = null;
                r7 = undefined;
                if(!(r8 != r0)) { _fun9437_ip = 207; continue _fun9437 }
case 202:
                r7 = r0.length;
case 207:
                r3 = 0;
                if(!(r8 != r7)) { _fun9437_ip = 216; continue _fun9437 }
case 213:
                r3 = r7;
case 216:
                _fun9437_ip = 242; continue _fun9437;
case 218:
                r7 = _closure1_slot2;
                r8 = r7.getTensorSize;
                r7 = r6.shape;
                r3 = r8.bind(r2)(r7);
case 242:
                if(!r0) { _fun9437_ip = 297; continue _fun9437 }
case 245:
                r9 = r1.Array;
                r8 = r9.isArray;
                r7 = r6.shape;
                r7 = r8.bind(r9)(r7);
                if(!r7) { _fun9437_ip = 297; continue _fun9437 }
case 270:
                r6 = r6.shape;
                r6 = r6.length;
                if(!(r6 > r4)) { _fun9437_ip = 297; continue _fun9437 }
case 285:
                r6 = r0.length;
                if(!(r6 === r3)) { _fun9437_ip = 600; continue _fun9437 }
case 297:
                if(!r0) { _fun9437_ip = 517; continue _fun9437 }
case 303:
                r8 = r0.length;
                r7 = _closure1_slot1;
                r7 = r7.EMOFX_AEV2_CORE_SIZE;
                if(!(!(r8 < r7))) { _fun9437_ip = 517; continue _fun9437 }
case 328:
                r7 = r0.slice;
                r6 = _closure1_slot0;
                r6 = r6.AEV2_EQ_GAIN_DB_SIZE;
                r9 = r7.bind(r0)(r4, r6);
                r13 = r5.reverb;
                r6 = 10;
                r12 = r0[r6];
                r6 = 11;
                r11 = r0[r6];
                r6 = 12;
                r10 = r0[r6];
                r6 = 13;
                r7 = r0[r6];
                r6 = 14;
                r6 = r0[r6];
                r8 = {};
                r8['on'] = r13;
                r8['reverb_time'] = r12;
                r8['dry_level'] = r11;
                r8['wet_level'] = r10;
                r8['room_size'] = r7;
                r8['diffusion'] = r6;
                r11 = r5.delay;
                r6 = 15;
                r10 = r0[r6];
                r6 = 16;
                r6 = r0[r6];
                r7 = {};
                r7['on'] = r11;
                r7['delay_time'] = r10;
                r7['feedback'] = r6;
                r11 = r5.stereo_enhance;
                r5 = 17;
                r10 = r0[r5];
                r5 = 18;
                r5 = r0[r5];
                r6 = {};
                r6['on'] = r11;
                r6['stereo_width'] = r10;
                r6['stereo_depth'] = r5;
                r5 = {};
                r5['eq_gain_db'] = r9;
                r5['reverb'] = r8;
                r5['delay'] = r7;
                r5['stereo_enhance'] = r6;
                return r5;
case 517:
                r5 = r1.Error;
                r6 = null;
                r2 = undefined;
                if(!(r6 != r0)) { _fun9437_ip = 536; continue _fun9437 }
case 531:
                r2 = r0.length;
case 536:
                r4 = 0;
                if(!(r6 != r2)) { _fun9437_ip = 545; continue _fun9437 }
case 542:
                r4 = r2;
case 545:
                r2 = 'decode: aev2 tensor data length ';
                r6 = r2 + r4;
                r2 = _closure1_slot1;
                r4 = r2.EMOFX_AEV2_CORE_SIZE;
                r2 = ' < ';
                r2 = r6 + r2;
                r15 = r2 + r4;
                r4 = r5.prototype;
                r4 = Object.create(r4, {constructor: {value: r5}});
                r16 = r4;
                r2 = originalConstruct(r5, [r15, r14]);
                r2 = r2 instanceof Object ? r2 : r4;
                throw r2;
case 600:
                r2 = r1.Error;
                r1 = 'decode: aev2 tensor shape/data mismatch (';
                r3 = r1 + r3;
                r1 = r0.length;
                r0 = ' vs ';
                r0 = r3 + r0;
                r3 = r0 + r1;
                r0 = r2.prototype;
                r1 = Object.create(r0, {constructor: {value: r2}});
                r0 = ')';
                r15 = r3 + r0;
                r16 = r1;
                r0 = originalConstruct(r2, [r15, r14]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        r1['decodeEmoFX'] = r0;
        r0 = 0;
        r1 = r4[r0];
        r0 = undefined;
        r1 = r3.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 1;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 2;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot2 = r1;
        return r0;
    };
factories[953]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a1;
        r6 = a5;
        r4 = a6;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: o, environment: r2
            _fun9440: for(var _fun9440_ip = 0; ; ) switch(_fun9440_ip) {
case 0:
                r3 = a0;
                r1 = a1;
                r8 = undefined;
                r2 = _closure1_slot3;
                r2 = r2.EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE;
                if(!(r1 === r2)) { _fun9440_ip = 742; continue _fun9440 }
case 28:
                if(r3) { _fun9440_ip = 77; continue _fun9440 }
case 31:
                r2 = global;
                r2 = r2.Array;
                r4 = r2.prototype;
                r4 = Object.create(r4, {constructor: {value: r2}});
                r17 = r4;
                r16 = r1;
                r2 = originalConstruct(r2, [r16, r15]);
                r5 = r2 instanceof Object ? r2 : r4;
                r4 = r5.fill;
                r2 = 0;
                r2 = r4.bind(r5)(r2);
                return r2;
case 77:
                r11 = r3.eq_gain_db;
                r2 = global;
                r5 = r2.Array;
                r4 = r5.isArray;
                r4 = r4.bind(r5)(r11);
                if(!r4) { _fun9440_ip = 696; continue _fun9440 }
case 107:
                r5 = r11.length;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_EQ_GAIN_DB_SIZE;
                if(!(r5 === r4)) { _fun9440_ip = 696; continue _fun9440 }
case 129:
                r5 = r3.reverb;
                r10 = null;
                r4 = undefined;
                if(!(r10 != r5)) { _fun9440_ip = 148; continue _fun9440 }
case 143:
                r4 = r5.on;
case 148:
                r7 = false;
                if(!(r7 !== r4)) { _fun9440_ip = 198; continue _fun9440 }
case 154:
                r12 = r2.Object;
                r9 = r12.assign;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_MIN_VALUES;
                r6 = r4.reverb;
                r5 = r3.reverb;
                r4 = {};
                r6 = r9.bind(r12)(r4, r6, r5);
                _fun9440_ip = 233; continue _fun9440;
case 198:
                r12 = r2.Object;
                r9 = r12.assign;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_MIN_VALUES;
                r5 = r4.reverb;
                r4 = {};
                r6 = r9.bind(r12)(r4, r5);
case 233:
                r5 = r3.delay;
                r4 = undefined;
                if(!(r10 != r5)) { _fun9440_ip = 250; continue _fun9440 }
case 245:
                r4 = r5.on;
case 250:
                if(!(r7 !== r4)) { _fun9440_ip = 298; continue _fun9440 }
case 254:
                r13 = r2.Object;
                r12 = r13.assign;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_MIN_VALUES;
                r9 = r4.delay;
                r5 = r3.delay;
                r4 = {};
                r5 = r12.bind(r13)(r4, r9, r5);
                _fun9440_ip = 333; continue _fun9440;
case 298:
                r13 = r2.Object;
                r12 = r13.assign;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_MIN_VALUES;
                r9 = r4.delay;
                r4 = {};
                r5 = r12.bind(r13)(r4, r9);
case 333:
                r9 = r3.stereo_enhance;
                r4 = undefined;
                if(!(r10 != r9)) { _fun9440_ip = 350; continue _fun9440 }
case 345:
                r4 = r9.on;
case 350:
                if(!(r7 !== r4)) { _fun9440_ip = 398; continue _fun9440 }
case 354:
                r10 = r2.Object;
                r9 = r10.assign;
                r4 = _closure1_slot1;
                r4 = r4.AEV2_MIN_VALUES;
                r7 = r4.stereo_enhance;
                r4 = r3.stereo_enhance;
                r3 = {};
                r4 = r9.bind(r10)(r3, r7, r4);
                _fun9440_ip = 433; continue _fun9440;
case 398:
                r10 = r2.Object;
                r9 = r10.assign;
                r3 = _closure1_slot1;
                r3 = r3.AEV2_MIN_VALUES;
                r7 = r3.stereo_enhance;
                r3 = {};
                r4 = r9.bind(r10)(r3, r7);
case 433:
                r3 = new Array(0);
                r10 = r3.push;
                r9 = r10.apply;
                r7 = _closure1_slot0;
                r7 = r7.default;
                r7 = r7.bind(r8)(r11);
                r7 = r9.bind(r10)(r3, r7);
                r9 = r3.push;
                r7 = r6.reverb_time;
                r7 = r9.bind(r3)(r7);
                r9 = r3.push;
                r7 = r6.dry_level;
                r7 = r9.bind(r3)(r7);
                r9 = r3.push;
                r7 = r6.wet_level;
                r7 = r9.bind(r3)(r7);
                r9 = r3.push;
                r7 = r6.room_size;
                r7 = r9.bind(r3)(r7);
                r7 = r3.push;
                r6 = r6.diffusion;
                r6 = r7.bind(r3)(r6);
                r7 = r3.push;
                r6 = r5.delay_time;
                r6 = r7.bind(r3)(r6);
                r6 = r3.push;
                r5 = r5.feedback;
                r5 = r6.bind(r3)(r5);
                r6 = r3.push;
                r5 = r4.stereo_width;
                r5 = r6.bind(r3)(r5);
                r5 = r3.push;
                r4 = r4.stereo_depth;
                r4 = r5.bind(r3)(r4);
                r6 = r3.push;
                r5 = r6.apply;
                r4 = _closure1_slot0;
                r7 = r4.default;
                r10 = r2.Array;
                r4 = _closure1_slot3;
                r4 = r4.EMOFX_AEV2_CORE_SIZE;
                r16 = r1 - r4;
                r9 = r10.prototype;
                r9 = Object.create(r9, {constructor: {value: r10}});
                r17 = r9;
                r4 = originalConstruct(r10, [r16, r15]);
                r10 = r4 instanceof Object ? r4 : r9;
                r9 = r10.fill;
                r4 = 0;
                r4 = r9.bind(r10)(r4);
                r4 = r7.bind(r8)(r4);
                r4 = r5.bind(r6)(r3, r4);
                return r3;
case 696:
                r4 = r2.Error;
                r2 = _closure1_slot1;
                r3 = r2.AEV2_EQ_GAIN_DB_SIZE;
                r2 = 'base_extra_aev2.eq_gain_db 长度必须为 ';
                r16 = r2 + r3;
                r3 = r4.prototype;
                r3 = Object.create(r3, {constructor: {value: r4}});
                r17 = r3;
                r2 = originalConstruct(r4, [r16, r15]);
                r2 = r2 instanceof Object ? r2 : r3;
                throw r2;
case 742:
                r2 = global;
                r2 = r2.Error;
                r0 = _closure1_slot3;
                r3 = r0.EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE;
                r0 = 'base_extra_flat 维度必须为 ';
                r3 = r0 + r3;
                r0 = '，当前为 ';
                r0 = r3 + r0;
                r16 = r0 + r1;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r17 = r1;
                r0 = originalConstruct(r2, [r16, r15]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        var _closure1_slot4 = r0;
        r0 = 0;
        r1 = r4[r0];
        r0 = undefined;
        r5 = r3.bind(r0)(r1);
        r1 = global;
        r9 = r1.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r1 = true;
        r7['value'] = r1;
        r1 = '__esModule';
        r1 = r8.bind(r9)(r6, r1, r7);
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9441: for(var _fun9441_ip = 0; ; ) switch(_fun9441_ip) {
case 0:
                r6 = a0;
                r0 = a1;
                r3 = 'invalid';
                r11 = 1;
                r14 = 0;
                r1 = r0.inputs;
                r1 = r1.emotion_idx;
                r1 = r1.size;
                if(!(r11 === r1)) { _fun9441_ip = 1843; continue _fun9441 }
case 40:
                r1 = r0.inputs;
                r1 = r1.genre_idx;
                r1 = r1.size;
                if(!(r11 === r1)) { _fun9441_ip = 1787; continue _fun9441 }
case 65:
                r1 = r0.inputs;
                r1 = r1.features;
                r4 = r1.size;
                r2 = _closure1_slot3;
                r2 = r2.EMOFX_FEATURE_DEFAULT_SIZE;
                if(!(r4 === r2)) { _fun9441_ip = 1709; continue _fun9441 }
case 103:
                r2 = r0.inputs;
                r2 = r2.base_extra_flat;
                r4 = r2.size;
                r2 = _closure1_slot3;
                r2 = r2.EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE;
                if(!(r4 === r2)) { _fun9441_ip = 1631; continue _fun9441 }
case 138:
                r2 = _closure1_slot3;
                r2 = r2.EMOFX_TENSOR_NAMES;
                r7 = r2.emotion_idx;
                r2 = r0.inputs;
                r2 = r2.emotion_idx;
                r5 = r2.dtype;
                r2 = _closure1_slot0;
                r4 = r2.default;
                r2 = r0.inputs;
                r2 = r2.emotion_idx;
                r2 = r2.shape;
                r15 = undefined;
                r4 = r4.bind(r15)(r2);
                r8 = r6.emotion_idx;
                r2 = new Array(1);
                r2[0] = r8;
                r10 = {};
                r10['name'] = r7;
                r10['dtype'] = r5;
                r10['shape'] = r4;
                r10['data'] = r2;
                r2 = _closure1_slot3;
                r2 = r2.EMOFX_TENSOR_NAMES;
                r7 = r2.genre_idx;
                r2 = r0.inputs;
                r2 = r2.genre_idx;
                r5 = r2.dtype;
                r2 = _closure1_slot0;
                r4 = r2.default;
                r2 = r0.inputs;
                r2 = r2.genre_idx;
                r2 = r2.shape;
                r4 = r4.bind(r15)(r2);
                r8 = r6.genre_idx;
                r2 = new Array(1);
                r2[0] = r8;
                r9 = {};
                r9['name'] = r7;
                r9['dtype'] = r5;
                r9['shape'] = r4;
                r9['data'] = r2;
                r2 = r6.mel_spectrogram_32;
                r5 = global;
                r7 = r5.Array;
                r4 = r7.isArray;
                r4 = r4.bind(r7)(r2);
                if(!r4) { _fun9441_ip = 1571; continue _fun9441 }
case 370:
                r7 = r2.length;
                r4 = 32;
                if(!(r4 === r7)) { _fun9441_ip = 1571; continue _fun9441 }
case 385:
                r7 = r2.some;
                r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r4
                    _fun9442: for(var _fun9442_ip = 0; ; ) switch(_fun9442_ip) {
case 0:
                        r3 = a0;
                        r1 = 'number';
                        r0 = typeof r3;
                        r0 = r1 !== r0;
                        if(r0) { _fun9442_ip = 39; continue _fun9442 }
case 17:
                        r1 = global;
                        r2 = r1.Number;
                        r1 = r2.isFinite;
                        r1 = r1.bind(r2)(r3);
                        r0 = !r1;
case 39:
                        return r0;
                    }
                };
                r4 = r7.bind(r2)(r4);
                if(r4) { _fun9441_ip = 1539; continue _fun9441 }
case 409:
                r4 = _closure1_slot0;
                r4 = r4.default;
                r4 = r4.bind(r15)(r2);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r13 = r7.assertFiniteNumber;
                r12 = r6.dynamic_range;
                r7 = 'dynamic_range';
                r7 = r13.bind(r15)(r12, r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r13 = r7.assertFiniteNumber;
                r12 = r6.loudness_lufs;
                r7 = 'loudness_lufs';
                r7 = r13.bind(r15)(r12, r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r13 = r7.assertFiniteNumber;
                r12 = r6.stereo_width_ratio;
                r7 = 'stereo_width_ratio';
                r7 = r13.bind(r15)(r12, r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r13 = r7.assertFiniteNumber;
                r12 = r6.repbpm;
                r7 = 'repbpm';
                r7 = r13.bind(r15)(r12, r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r13 = r7.assertFiniteNumber;
                r12 = r6.chorus_loudness;
                r7 = 'chorus_loudness';
                r7 = r13.bind(r15)(r12, r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.vocal_main_freq;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.vocal_time_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.harmonic_intensity_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.vocal_dynamic_range;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.drum_time_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.rhythm_complexity;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.vocal_energy_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.drum_energy_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.bass_energy_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = _closure1_slot2;
                r12 = r7.optionalFiniteNumber;
                r7 = r6.other_instrument_energy_ratio;
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = r6.module_switches;
                r12 = r7.reverb;
                r7 = 0;
                if(!r12) { _fun9441_ip = 938; continue _fun9441 }
case 935:
                r7 = r11;
case 938:
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = r6.module_switches;
                r12 = r7.delay;
                r7 = 0;
                if(!r12) { _fun9441_ip = 968; continue _fun9441 }
case 965:
                r7 = r11;
case 968:
                r7 = r8.bind(r4)(r7);
                r8 = r4.push;
                r7 = r6.module_switches;
                r12 = r7.stereo_enhance;
                r7 = 0;
                if(!r12) { _fun9441_ip = 998; continue _fun9441 }
case 995:
                r7 = r11;
case 998:
                r7 = r8.bind(r4)(r7);
                r11 = r4.push;
                r8 = r11.apply;
                r7 = _closure1_slot0;
                r12 = r7.default;
                r16 = r5.Array;
                r7 = r0.inputs;
                r7 = r7.features;
                r13 = r7.size;
                r7 = _closure1_slot3;
                r7 = r7.EMOFX_FEATURE_CORE_SIZE;
                r18 = r13 - r7;
                r13 = r16.prototype;
                r13 = Object.create(r13, {constructor: {value: r16}});
                r19 = r13;
                r7 = originalConstruct(r16, [r18, r17]);
                r13 = r7 instanceof Object ? r7 : r13;
                r7 = r13.fill;
                r7 = r7.bind(r13)(r14);
                r7 = r12.bind(r15)(r7);
                r7 = r8.bind(r11)(r4, r7);
                r8 = r4.length;
                r7 = r0.inputs;
                r7 = r7.features;
                r7 = r7.size;
                if(!(r8 === r7)) { _fun9441_ip = 1468; continue _fun9441 }
case 1132:
                r7 = _closure1_slot3;
                r7 = r7.EMOFX_TENSOR_NAMES;
                r12 = r7.features;
                r7 = r0.inputs;
                r7 = r7.features;
                r11 = r7.dtype;
                r7 = _closure1_slot0;
                r8 = r7.default;
                r7 = r0.inputs;
                r7 = r7.features;
                r7 = r7.shape;
                r7 = r8.bind(r15)(r7);
                r8 = {};
                r8['name'] = r12;
                r8['dtype'] = r11;
                r8['shape'] = r7;
                r8['data'] = r4;
                r7 = _closure1_slot3;
                r7 = r7.EMOFX_TENSOR_NAMES;
                r13 = r7.base_extra_flat;
                r7 = r0.inputs;
                r7 = r7.base_extra_flat;
                r12 = r7.dtype;
                r7 = _closure1_slot0;
                r11 = r7.default;
                r7 = r0.inputs;
                r7 = r7.base_extra_flat;
                r7 = r7.shape;
                r11 = r11.bind(r15)(r7);
                r14 = _closure1_slot4;
                r7 = r6.base_extra_aev2;
                r6 = r0.inputs;
                r6 = r6.base_extra_flat;
                r6 = r6.size;
                r7 = r14.bind(r15)(r7, r6);
                r6 = {};
                r6['name'] = r13;
                r6['dtype'] = r12;
                r6['shape'] = r11;
                r6['data'] = r7;
                r7 = r6.data;
                r11 = r7.length;
                r7 = r0.inputs;
                r7 = r7.base_extra_flat;
                r7 = r7.size;
                if(!(r11 === r7)) { _fun9441_ip = 1392; continue _fun9441 }
case 1370:
                r7 = new Array(4);
                r7[0] = r10;
                r7[1] = r9;
                r7[2] = r8;
                r7[3] = r6;
                return r7;
case 1392:
                r8 = r5.Error;
                r7 = r0.inputs;
                r7 = r7.base_extra_flat;
                r9 = r7.size;
                r7 = 'base_extra_flat 编码长度异常，期望 ';
                r9 = r7 + r9;
                r6 = r6.data;
                r7 = r6.length;
                r6 = '，实际 ';
                r6 = r9 + r6;
                r18 = r6 + r7;
                r7 = r8.prototype;
                r7 = Object.create(r7, {constructor: {value: r8}});
                r19 = r7;
                r6 = originalConstruct(r8, [r18, r17]);
                r6 = r6 instanceof Object ? r6 : r7;
                throw r6;
case 1468:
                r7 = r5.Error;
                r6 = r0.inputs;
                r6 = r6.features;
                r8 = r6.size;
                r6 = 'features 编码长度异常，期望 ';
                r8 = r6 + r8;
                r6 = r4.length;
                r4 = '，实际 ';
                r4 = r8 + r4;
                r18 = r4 + r6;
                r6 = r7.prototype;
                r6 = Object.create(r6, {constructor: {value: r7}});
                r19 = r6;
                r4 = originalConstruct(r7, [r18, r17]);
                r4 = r4 instanceof Object ? r4 : r6;
                throw r4;
case 1539:
                r7 = r5.Error;
                r4 = r7.prototype;
                r6 = Object.create(r4, {constructor: {value: r7}});
                r18 = 'mel_spectrogram_32 数组中的元素必须全部为有限数值';
                r19 = r6;
                r4 = originalConstruct(r7, [r18, r17]);
                r4 = r4 instanceof Object ? r4 : r6;
                throw r4;
case 1571:
                r4 = r5.Error;
                r6 = r5.Array;
                r5 = r6.isArray;
                r5 = r5.bind(r6)(r2);
                if(!r5) { _fun9441_ip = 1601; continue _fun9441 }
case 1596:
                r3 = r2.length;
case 1601:
                r2 = 'mel_spectrogram_32 数组长度必须为 32，当前为 ';
                r18 = r2 + r3;
                r3 = r4.prototype;
                r3 = Object.create(r3, {constructor: {value: r4}});
                r19 = r3;
                r2 = originalConstruct(r4, [r18, r17]);
                r2 = r2 instanceof Object ? r2 : r3;
                throw r2;
case 1631:
                r2 = global;
                r4 = r2.Error;
                r2 = _closure1_slot3;
                r3 = r2.EMOFX_BASE_EXTRA_FLAT_DEFAULT_SIZE;
                r2 = 'base_extra_flat 维度必须为 ';
                r5 = r2 + r3;
                r2 = r0.inputs;
                r2 = r2.base_extra_flat;
                r3 = r2.size;
                r2 = '，当前为 ';
                r2 = r5 + r2;
                r18 = r2 + r3;
                r3 = r4.prototype;
                r3 = Object.create(r3, {constructor: {value: r4}});
                r19 = r3;
                r2 = originalConstruct(r4, [r18, r17]);
                r2 = r2 instanceof Object ? r2 : r3;
                throw r2;
case 1709:
                r2 = global;
                r3 = r2.Error;
                r1 = _closure1_slot3;
                r2 = r1.EMOFX_FEATURE_DEFAULT_SIZE;
                r1 = 'features 维度必须为 ';
                r4 = r1 + r2;
                r1 = r0.inputs;
                r1 = r1.features;
                r2 = r1.size;
                r1 = '，当前为 ';
                r1 = r4 + r1;
                r18 = r1 + r2;
                r2 = r3.prototype;
                r2 = Object.create(r2, {constructor: {value: r3}});
                r19 = r2;
                r1 = originalConstruct(r3, [r18, r17]);
                r1 = r1 instanceof Object ? r1 : r2;
                throw r1;
case 1787:
                r1 = global;
                r3 = r1.Error;
                r1 = r0.inputs;
                r1 = r1.genre_idx;
                r2 = r1.size;
                r1 = 'genre_idx 张量大小必须为 1，当前为 ';
                r18 = r1 + r2;
                r2 = r3.prototype;
                r2 = Object.create(r2, {constructor: {value: r3}});
                r19 = r2;
                r1 = originalConstruct(r3, [r18, r17]);
                r1 = r1 instanceof Object ? r1 : r2;
                throw r1;
case 1843:
                r1 = global;
                r2 = r1.Error;
                r0 = r0.inputs;
                r0 = r0.emotion_idx;
                r1 = r0.size;
                r0 = 'emotion_idx 张量大小必须为 1，当前为 ';
                r18 = r0 + r1;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r19 = r1;
                r0 = originalConstruct(r2, [r18, r17]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        r6['encodeEmoFX'] = r1;
        r1 = 1;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        r1 = r5.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 3;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot2 = r1;
        r1 = 4;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot3 = r1;
        return r0;
    };
factories[957]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a1;
        r6 = a5;
        r4 = a6;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: c, environment: r2
            _fun9450: for(var _fun9450_ip = 0; ; ) switch(_fun9450_ip) {
case 0:
                r1 = a0;
                r0 = {};
                r2 = r1.genre_id;
                r3 = null;
                if(!(r3 != r2)) { _fun9450_ip = 29; continue _fun9450 }
case 17:
                r2 = r1.genre_id;
                r0['genre_id'] = r2;
case 29:
                r2 = r1.repbpm;
                if(!(r3 != r2)) { _fun9450_ip = 51; continue _fun9450 }
case 39:
                r2 = r1.repbpm;
                r0['repbpm'] = r2;
case 51:
                r2 = r1.key_mode;
                if(!(r3 != r2)) { _fun9450_ip = 73; continue _fun9450 }
case 61:
                r2 = r1.key_mode;
                r0['key_mode'] = r2;
case 73:
                r2 = r1.loudness_lufs;
                if(!(r3 != r2)) { _fun9450_ip = 95; continue _fun9450 }
case 83:
                r2 = r1.loudness_lufs;
                r0['loudness_lufs'] = r2;
case 95:
                r2 = r1.dynamic_range;
                if(!(r3 != r2)) { _fun9450_ip = 117; continue _fun9450 }
case 105:
                r2 = r1.dynamic_range;
                r0['dynamic_range'] = r2;
case 117:
                r2 = r1.stereo_width_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 139; continue _fun9450 }
case 127:
                r2 = r1.stereo_width_ratio;
                r0['stereo_width_ratio'] = r2;
case 139:
                r2 = r1.mel_spectrogram_32;
                if(!(r3 != r2)) { _fun9450_ip = 180; continue _fun9450 }
case 149:
                r2 = _closure1_slot0;
                r5 = r2.default;
                r4 = r1.mel_spectrogram_32;
                r2 = undefined;
                r2 = r5.bind(r2)(r4);
                r0['mel_spectrogram_32'] = r2;
case 180:
                r2 = r1.chorus_loudness;
                if(!(r3 != r2)) { _fun9450_ip = 202; continue _fun9450 }
case 190:
                r2 = r1.chorus_loudness;
                r0['chorus_loudness'] = r2;
case 202:
                r2 = r1.vocal_main_freq;
                if(!(r3 != r2)) { _fun9450_ip = 224; continue _fun9450 }
case 212:
                r2 = r1.vocal_main_freq;
                r0['vocal_main_freq'] = r2;
case 224:
                r2 = r1.vocal_time_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 246; continue _fun9450 }
case 234:
                r2 = r1.vocal_time_ratio;
                r0['vocal_time_ratio'] = r2;
case 246:
                r2 = r1.harmonic_intensity_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 268; continue _fun9450 }
case 256:
                r2 = r1.harmonic_intensity_ratio;
                r0['harmonic_intensity_ratio'] = r2;
case 268:
                r2 = r1.vocal_dynamic_range;
                if(!(r3 != r2)) { _fun9450_ip = 290; continue _fun9450 }
case 278:
                r2 = r1.vocal_dynamic_range;
                r0['vocal_dynamic_range'] = r2;
case 290:
                r2 = r1.drum_time_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 312; continue _fun9450 }
case 300:
                r2 = r1.drum_time_ratio;
                r0['drum_time_ratio'] = r2;
case 312:
                r2 = r1.rhythm_complexity;
                if(!(r3 != r2)) { _fun9450_ip = 334; continue _fun9450 }
case 322:
                r2 = r1.rhythm_complexity;
                r0['rhythm_complexity'] = r2;
case 334:
                r2 = r1.vocal_energy_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 356; continue _fun9450 }
case 344:
                r2 = r1.vocal_energy_ratio;
                r0['vocal_energy_ratio'] = r2;
case 356:
                r2 = r1.drum_energy_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 378; continue _fun9450 }
case 366:
                r2 = r1.drum_energy_ratio;
                r0['drum_energy_ratio'] = r2;
case 378:
                r2 = r1.bass_energy_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 400; continue _fun9450 }
case 388:
                r2 = r1.bass_energy_ratio;
                r0['bass_energy_ratio'] = r2;
case 400:
                r2 = r1.other_instrument_energy_ratio;
                if(!(r3 != r2)) { _fun9450_ip = 422; continue _fun9450 }
case 410:
                r1 = r1.other_instrument_energy_ratio;
                r0['other_instrument_energy_ratio'] = r1;
case 422:
                return r0;
            }
        };
        var _closure1_slot6 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: y, environment: r2
            _fun9451: for(var _fun9451_ip = 0; ; ) switch(_fun9451_ip) {
case 0:
                r3 = a0;
                r11 = a1;
                r0 = 'input_tag_mapping';
                r1 = 'input_direct';
                r4 = 'default';
                r5 = 'emotion_mapping';
                r2 = r3.emotion_label;
                r8 = null;
                if(!(r8 != r2)) { _fun9451_ip = 60; continue _fun9451 }
case 34:
                r6 = _closure1_slot3;
                r7 = r6.isEmotionLabel;
                r6 = undefined;
                r6 = r7.bind(r6)(r2);
                if(r6) { _fun9451_ip = 261; continue _fun9451 }
case 60:
                r6 = _closure1_slot6;
                r10 = undefined;
                r9 = r6.bind(r10)(r3);
                r6 = _closure1_slot1;
                r6 = r6.validateEmotionMappingFeatures;
                r6 = r6.bind(r10)(r9);
                r12 = r6.valid;
                if(r12) { _fun9451_ip = 188; continue _fun9451 }
case 98:
                r14 = r6.issues;
                r13 = r14.join;
                r12 = '; ';
                r13 = r13.bind(r14)(r12);
                r12 = 'emotion_mapping input invalid: ';
                r12 = r12 + r13;
                if(!(r8 != r11)) { _fun9451_ip = 159; continue _fun9451 }
case 130:
                r14 = r11.warn;
                r6 = r6.issues;
                r13 = {};
                r13['issues'] = r6;
                r6 = 'emotion:mapping:invalid';
                r6 = r14.bind(r11)(r6, r13);
case 159:
                r6 = _closure1_slot5;
                r13 = r6.DEFAULT_EMOTION;
                r6 = {};
                r6['emotionLabel'] = r13;
                r6['emotionSource'] = r4;
                r6['fallbackReason'] = r12;
                return r6;
case 188:
                r6 = _closure1_slot1;
                r6 = r6.predictEmotionLabel;
                r6 = r6.bind(r10)(r9);
                if(!(r8 != r11)) { _fun9451_ip = 230; continue _fun9451 }
case 207:
                r10 = r11.info;
                r9 = {};
                r9['emotionLabel'] = r6;
                r8 = 'emotion:mapping:resolved';
                r8 = r10.bind(r11)(r8, r9);
case 230:
                r7 = _closure1_slot5;
                r7 = r7.DEFAULT_EMOTION;
                if(!(r6 === r7)) { _fun9451_ip = 247; continue _fun9451 }
case 244:
                r5 = r4;
case 247:
                r4 = {};
                r4['emotionLabel'] = r6;
                r4['emotionSource'] = r5;
                return r4;
case 261:
                r3 = r3.emotionFromTagMapping;
                if(!r3) { _fun9451_ip = 273; continue _fun9451 }
case 270:
                r1 = r0;
case 273:
                r0 = {};
                r0['emotionLabel'] = r2;
                r0['emotionSource'] = r1;
                return r0;
            }
        };
        var _closure1_slot7 = r0;
        r0 = 0;
        r1 = r4[r0];
        r0 = undefined;
        r5 = r3.bind(r0)(r1);
        r1 = global;
        r9 = r1.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r1 = true;
        r7['value'] = r1;
        r1 = '__esModule';
        r1 = r8.bind(r9)(r6, r1, r7);
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9452: for(var _fun9452_ip = 0; ; ) switch(_fun9452_ip) {
case 0:
                r2 = a0;
                r9 = a1;
                r0 = undefined;
                r30 = '未知曲风';
                r8 = null;
                if(!(r8 != r9)) { _fun9452_ip = 70; continue _fun9452 }
case 18:
                r4 = r9.info;
                r5 = undefined;
                if(!(r8 != r2)) { _fun9452_ip = 36; continue _fun9452 }
case 30:
                r5 = r2.emotion_label;
case 36:
                r1 = undefined;
                if(!(r8 != r2)) { _fun9452_ip = 48; continue _fun9452 }
case 42:
                r1 = r2.genre_id;
case 48:
                r3 = {};
                r3['emotion'] = r5;
                r3['genreId'] = r1;
                r1 = 'start';
                r1 = r4.bind(r9)(r1, r3);
case 70:
                r1 = _closure1_slot2;
                r1 = r1.sanitizeInput;
                r1 = r1.bind(r0)(r2, r9);
                r6 = r1.sanitized;
                r7 = r1.issues;
                r5 = r1.availability;
                r31 = r6.genre_id;
                r4 = r8 != r31;
                if(r4) { _fun9452_ip = 132; continue _fun9452 }
case 120:
                r1 = _closure1_slot5;
                r2 = r1.DEFAULT_FAMILY;
                _fun9452_ip = 147; continue _fun9452;
case 132:
                r1 = _closure1_slot4;
                r1 = r1.getFamily;
                r2 = r1.bind(r0)(r31);
case 147:
                if(!r4) { _fun9452_ip = 165; continue _fun9452 }
case 150:
                r1 = _closure1_slot4;
                r1 = r1.getGenreName;
                r30 = r1.bind(r0)(r31);
case 165:
                r1 = _closure1_slot7;
                r3 = r1.bind(r0)(r6, r9);
                r1 = r3.emotionLabel;
                r12 = r3.emotionSource;
                r3 = r3.fallbackReason;
                r11 = _closure1_slot3;
                r11 = r11.EMOTION_INDEX;
                r11 = r11[r1];
                r14 = 'number';
                r13 = typeof r11;
                if(!(r14 === r13)) { _fun9452_ip = 604; continue _fun9452 }
case 221:
                r10 = _closure1_slot3;
                r10 = r10.GENRE_INDEX;
                r10 = r10[r2];
                r13 = typeof r10;
                if(!(r14 === r13)) { _fun9452_ip = 566; continue _fun9452 }
case 245:
                r29 = r6.mel_spectrogram_32;
                r28 = r6.dynamic_range;
                r27 = r6.loudness_lufs;
                r26 = r6.stereo_width_ratio;
                r25 = r6.repbpm;
                r24 = r6.key_mode;
                r23 = r6.chorus_loudness;
                r22 = r6.vocal_main_freq;
                r21 = r6.vocal_time_ratio;
                r20 = r6.harmonic_intensity_ratio;
                r19 = r6.vocal_dynamic_range;
                r18 = r6.drum_time_ratio;
                r17 = r6.rhythm_complexity;
                r16 = r6.vocal_energy_ratio;
                r15 = r6.drum_energy_ratio;
                r14 = r6.bass_energy_ratio;
                r13 = r6.other_instrument_energy_ratio;
                r6 = {};
                r6['emotion_label'] = r1;
                r6['emotion_source'] = r12;
                r6['genre_id'] = r31;
                r6['genre_name'] = r30;
                r6['family'] = r2;
                r6['emotion_idx'] = r11;
                r6['genre_idx'] = r10;
                r6['mel_spectrogram_32'] = r29;
                r6['dynamic_range'] = r28;
                r6['loudness_lufs'] = r27;
                r6['stereo_width_ratio'] = r26;
                r6['repbpm'] = r25;
                r6['key_mode'] = r24;
                r6['chorus_loudness'] = r23;
                r6['vocal_main_freq'] = r22;
                r6['vocal_time_ratio'] = r21;
                r6['harmonic_intensity_ratio'] = r20;
                r6['vocal_dynamic_range'] = r19;
                r6['drum_time_ratio'] = r18;
                r6['rhythm_complexity'] = r17;
                r6['vocal_energy_ratio'] = r16;
                r6['drum_energy_ratio'] = r15;
                r6['bass_energy_ratio'] = r14;
                r6['other_instrument_energy_ratio'] = r13;
                if(!(r8 != r9)) { _fun9452_ip = 542; continue _fun9452 }
case 473:
                r8 = r9.info;
                r14 = r7.length;
                r13 = 0;
                r0 = undefined;
                if(!(r14 > r13)) { _fun9452_ip = 495; continue _fun9452 }
case 492:
                r0 = r7;
case 495:
                r7 = {};
                r7['emotion'] = r1;
                r7['emotionSource'] = r12;
                r7['family'] = r2;
                r7['emotionIdx'] = r11;
                r7['genreIdx'] = r10;
                r7['issues'] = r0;
                r7['availability'] = r5;
                r0 = 'done';
                r0 = r8.bind(r9)(r0, r7);
case 542:
                r0 = {};
                r0['input'] = r6;
                r0['availability'] = r5;
                r0['familyKnown'] = r4;
                r0['fallbackReason'] = r3;
                return r0;
case 566:
                r0 = global;
                r3 = r0.Error;
                r0 = 'genre index missing for ';
                r34 = r0 + r2;
                r2 = r3.prototype;
                r2 = Object.create(r2, {constructor: {value: r3}});
                r35 = r2;
                r0 = originalConstruct(r3, [r34, r33]);
                r0 = r0 instanceof Object ? r0 : r2;
                throw r0;
case 604:
                r0 = global;
                r2 = r0.Error;
                r0 = 'emotion index missing for ';
                r34 = r0 + r1;
                r1 = r2.prototype;
                r1 = Object.create(r1, {constructor: {value: r2}});
                r35 = r1;
                r0 = originalConstruct(r2, [r34, r33]);
                r0 = r0 instanceof Object ? r0 : r1;
                throw r0;
            }
        };
        r6['preprocess'] = r1;
        r1 = 1;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        r1 = r5.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 3;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot2 = r1;
        r1 = 4;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot3 = r1;
        r1 = 5;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot4 = r1;
        r1 = 6;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot5 = r1;
        return r0;
    };
factories[958]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r5 = a1;
        r3 = a5;
        r6 = a6;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: q, environment: r2
            _fun9454: for(var _fun9454_ip = 0; ; ) switch(_fun9454_ip) {
case 0:
                r3 = a0;
                r1 = 'number';
                r0 = typeof r3;
                r0 = r1 === r0;
                if(!r0) { _fun9454_ip = 36; continue _fun9454 }
case 17:
                r1 = global;
                r2 = r1.Number;
                r1 = r2.isFinite;
                r0 = r1.bind(r2)(r3);
case 36:
                return r0;
            }
        };
        var _closure1_slot26 = r0;
        r0 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: G, environment: r2
            r0 = global;
            r2 = r0.Math;
            r1 = r2.exp;
            r5 = r0.Math;
            r4 = r5.pow;
            r3 = a0;
            r0 = a1;
            r3 = r3 - r0;
            r0 = a2;
            r3 = r3 / r0;
            r0 = 2;
            r3 = r4.bind(r5)(r3, r0);
            r0 = -0.5;
            r0 = r0 * r3;
            r0 = r1.bind(r2)(r0);
            return r0;
        };
        var _closure1_slot27 = r0;
        r0 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: B, environment: r2
            _fun9456: for(var _fun9456_ip = 0; ; ) switch(_fun9456_ip) {
case 0:
                r4 = a0;
                r0 = -70;
                r3 = r4.slice;
                r2 = a1;
                r1 = a2;
                r1 = r3.bind(r4)(r2, r1);
                r2 = r1.length;
                r4 = 0;
                if(!(r2 > r4)) { _fun9456_ip = 65; continue _fun9456 }
case 37:
                r3 = r1.reduce;
                r2 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
                    r1 = a0;
                    r0 = a1;
                    r0 = r1 + r0;
                    return r0;
                };
                r2 = r3.bind(r1)(r2, r4);
                r1 = r1.length;
                r0 = r2 / r1;
case 65:
                return r0;
            }
        };
        var _closure1_slot28 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: F, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot6;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot29 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: R, environment: r2
            _fun9459: for(var _fun9459_ip = 0; ; ) switch(_fun9459_ip) {
case 0:
                r1 = a1;
                r0 = r1.trim;
                r2 = r0.bind(r1)();
                r1 = r2.endsWith;
                r0 = 'Major';
                r1 = r1.bind(r2)(r0);
                r2 = _closure1_slot7;
                r0 = a0;
                r2 = r2[r0];
                r0 = r2;
                if(r1) { _fun9459_ip = 55; continue _fun9459 }
case 48:
                r1 = 1;
                r0 = r1 - r2;
case 55:
                return r0;
            }
        };
        var _closure1_slot30 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: T, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot8;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot31 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: W, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot9;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot32 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: Z, environment: r2
            _fun9462: for(var _fun9462_ip = 0; ; ) switch(_fun9462_ip) {
case 0:
                r5 = a0;
                r6 = a1;
                r0 = 1;
                r2 = 0.1;
                r4 = 0.4;
                r3 = _closure1_slot10;
                r8 = r3[r5];
                r3 = null;
                if(!(r3 != r8)) { _fun9462_ip = 59; continue _fun9462 }
case 46:
                r7 = r8.has;
                r7 = r7.bind(r8)(r6);
                if(r7) { _fun9462_ip = 96; continue _fun9462 }
case 59:
                r1 = _closure1_slot11;
                r5 = r1[r5];
                r1 = r4;
                if(!(r3 != r5)) { _fun9462_ip = 93; continue _fun9462 }
case 74:
                r3 = r5.has;
                r3 = r3.bind(r5)(r6);
                r1 = r4;
                if(!r3) { _fun9462_ip = 93; continue _fun9462 }
case 90:
                r1 = r2;
case 93:
                r0 = r1;
case 96:
                return r0;
            }
        };
        var _closure1_slot33 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: z, environment: r2
            r11 = a0;
            r4 = a1;
            r2 = _closure1_slot28;
            r5 = undefined;
            r3 = 24;
            r1 = 32;
            r7 = r2.bind(r5)(r4, r3, r1);
            r3 = 0;
            r1 = 8;
            r4 = r2.bind(r5)(r4, r3, r1);
            r1 = _closure1_slot0;
            r2 = r1.default;
            r1 = _closure1_slot12;
            r1 = r1[r11];
            r10 = 2;
            r1 = r2.bind(r5)(r1, r10);
            r6 = r1[r3];
            r8 = 1;
            r1 = r1[r8];
            r2 = _closure1_slot0;
            r9 = r2.default;
            r2 = _closure1_slot13;
            r2 = r2[r11];
            r2 = r9.bind(r5)(r2, r10);
            r3 = r2[r3];
            r2 = r2[r8];
            r0 = _closure1_slot27;
            r6 = r0.bind(r5)(r7, r6, r1);
            r1 = 0.75;
            r1 = r1 * r6;
            r2 = r0.bind(r5)(r4, r3, r2);
            r0 = 0.25;
            r0 = r0 * r2;
            r0 = r1 + r0;
            return r0;
        };
        var _closure1_slot34 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: C, environment: r2
            r4 = _closure1_slot27;
            r1 = _closure1_slot14;
            r0 = a0;
            r3 = r1[r0];
            r2 = undefined;
            r1 = a1;
            r0 = 0.08;
            r0 = r4.bind(r2)(r1, r3, r0);
            return r0;
        };
        var _closure1_slot35 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: D, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot15;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot36 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: H, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot16;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot37 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: J, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot17;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot38 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: K, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot18;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot39 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: Q, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot19;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot40 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: U, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot20;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot41 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: V, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot21;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot42 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: X, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot22;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot43 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: Y, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot23;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot44 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: $, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot24;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot45 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: ee, environment: r2
            r1 = _closure1_slot0;
            r3 = r1.default;
            r2 = _closure1_slot25;
            r1 = a0;
            r2 = r2[r1];
            r4 = undefined;
            r1 = 2;
            r1 = r3.bind(r4)(r2, r1);
            r3 = _closure1_slot27;
            r0 = 0;
            r2 = r1[r0];
            r0 = 1;
            r1 = r1[r0];
            r0 = a1;
            r0 = r3.bind(r4)(r0, r2, r1);
            return r0;
        };
        var _closure1_slot46 = r0;
        r0 = function(a0, a1, a2, a3, a4) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: te, environment: r2
            _fun9476: for(var _fun9476_ip = 0; ; ) switch(_fun9476_ip) {
case 0:
                r6 = a0;
                r4 = a1;
                r2 = a2;
                r3 = a3;
                r5 = a4;
                r0 = null;
                if(!(r0 != r6)) { _fun9476_ip = 157; continue _fun9476 }
case 24:
                r8 = _closure1_slot26;
                r7 = undefined;
                r7 = r8.bind(r7)(r6);
                if(r7) { _fun9476_ip = 61; continue _fun9476 }
case 41:
                r8 = r2.push;
                r7 = ' 必须为有限数值';
                r7 = r4 + r7;
                r7 = r8.bind(r2)(r7);
                _fun9476_ip = 157; continue _fun9476;
case 61:
                if(!(r0 != r3)) { _fun9476_ip = 83; continue _fun9476 }
case 65:
                r7 = _closure1_slot1;
                r7 = r7.RANGE_EPSILON;
                r7 = r3 - r7;
                if(!(!(r6 < r7))) { _fun9476_ip = 135; continue _fun9476 }
case 83:
                r0 = r0 != r5;
                if(!r0) { _fun9476_ip = 108; continue _fun9476 }
case 90:
                r1 = _closure1_slot1;
                r1 = r1.RANGE_EPSILON;
                r1 = r5 + r1;
                r0 = r6 > r1;
case 108:
                if(!r0) { _fun9476_ip = 157; continue _fun9476 }
case 111:
                r1 = r2.push;
                r0 = ' 必须 <= ';
                r0 = r4 + r0;
                r0 = r0 + r5;
                r0 = r1.bind(r2)(r0);
                _fun9476_ip = 157; continue _fun9476;
case 135:
                r1 = r2.push;
                r0 = ' 必须 >= ';
                r0 = r4 + r0;
                r0 = r0 + r3;
                r0 = r1.bind(r2)(r0);
case 157:
                r0 = undefined;
                return r0;
            }
        };
        var _closure1_slot47 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: ne, environment: r2
            _fun9477: for(var _fun9477_ip = 0; ; ) switch(_fun9477_ip) {
case 0:
                r5 = a0;
                r4 = undefined;
                r1 = null;
                r6 = _closure1_slot4;
                r3 = r6;
                var __iterator_values_7=Array.from(r3), __iterator_index_7=0; r2={return:function(){}}; r3=function(){if(__iterator_index_7>=__iterator_values_7.length){r2=undefined;return {value:undefined}}return {value:__iterator_values_7[__iterator_index_7++]}};
case 20:
                r6 = r3().value;
                r7 = r2;
                if(!(r7 !== r4)) { _fun9477_ip = 53; continue _fun9477 }
case 31: // try_start_0
                r6 = r5[r6];
                if(!(r1 == r6)) { _fun9477_ip = 41; continue _fun9477 }
case 39: // try_end0
                _fun9477_ip = 20; continue _fun9477;
case 41:
                r2.return();
                return r1;
case 46: // catch_target0
                CatchBlockStart(arg_register=1);
                r2.return();
                throw r1;
case 53:
                r0 = _closure1_slot5;
                return r0;
            }
        };
        var _closure1_slot48 = r0;
        r0 = 0;
        r1 = r6[r0];
        r0 = undefined;
        r7 = r5.bind(r0)(r1);
        r4 = global;
        r10 = r4.Object;
        r9 = r10.defineProperty;
        r8 = {};
        r1 = true;
        r8['value'] = r1;
        r1 = '__esModule';
        r1 = r9.bind(r10)(r3, r1, r8);
        r3['EMOTION_MAPPING_LABELS'] = r0;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9478: for(var _fun9478_ip = 0; ; ) switch(_fun9478_ip) {
case 0:
                r1 = a0;
                r13 = null;
                r12 = undefined;
                r7 = undefined;
                r4 = undefined;
                r8 = undefined;
                r9 = undefined;
                r10 = undefined;
                r0 = _closure1_slot48;
                r0 = r0.bind(r12)(r1);
                if(!(r13 == r0)) { _fun9478_ip = 1319; continue _fun9478 }
case 38:
                r5 = r1.mel_spectrogram_32;
                if(!(r13 != r5)) { _fun9478_ip = 69; continue _fun9478 }
case 48:
                r5 = r1.mel_spectrogram_32;
                r6 = r5.length;
                r5 = 32;
                if(!(r5 === r6)) { _fun9478_ip = 1270; continue _fun9478 }
case 69:
                r6 = r1.rhythm_complexity;
                r5 = null;
                if(!(r5 != r6)) { _fun9478_ip = 84; continue _fun9478 }
case 81:
                r5 = r6;
case 84:
                r7 = r5;
                r5 = r13 != r5;
                if(!r5) { _fun9478_ip = 104; continue _fun9478 }
case 94:
                r14 = r7;
                r6 = 1;
                r5 = r14 > r6;
case 104:
                if(!r5) { _fun9478_ip = 117; continue _fun9478 }
case 107:
                r6 = r7;
                r5 = 10;
                r7 = r6 / r5;
case 117:
                r4 = {};
                r14 = _closure1_slot2;
                r5 = r14;
                var __iterator_values_8=Array.from(r5), __iterator_index_8=0; r6={return:function(){}}; r5=function(){if(__iterator_index_8>=__iterator_values_8.length){r6=undefined;return {value:undefined}}return {value:__iterator_values_8[__iterator_index_8++]}};
case 129:
                r14 = r5().value;
                r15 = r6;
                if(!(r15 !== r12)) { _fun9478_ip = 1244; continue _fun9478 }
case 143: // try_start_0
                r8 = r14;
                r9 = 0;
                r10 = 0;
                r14 = r1.mel_spectrogram_32;
                if(!(r13 != r14)) { _fun9478_ip = 207; continue _fun9478 }
case 160:
                r14 = _closure1_slot3;
                r15 = r14.mel_spectrogram_32;
                r16 = r9;
                r18 = _closure1_slot34;
                r17 = r8;
                r14 = r1.mel_spectrogram_32;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 207:
                r14 = r1.repbpm;
                if(!(r13 != r14)) { _fun9478_ip = 264; continue _fun9478 }
case 217:
                r14 = _closure1_slot3;
                r15 = r14.repbpm;
                r16 = r9;
                r18 = _closure1_slot29;
                r17 = r8;
                r14 = r1.repbpm;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 264:
                r14 = r1.key_mode;
                if(!(r13 != r14)) { _fun9478_ip = 340; continue _fun9478 }
case 274:
                r15 = r1.key_mode;
                r14 = r15.trim;
                r14 = r14.bind(r15)();
                if(!r14) { _fun9478_ip = 340; continue _fun9478 }
case 293:
                r14 = _closure1_slot3;
                r15 = r14.key_mode;
                r16 = r9;
                r18 = _closure1_slot30;
                r17 = r8;
                r14 = r1.key_mode;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 340:
                r14 = r1.loudness_lufs;
                if(!(r13 != r14)) { _fun9478_ip = 397; continue _fun9478 }
case 350:
                r14 = _closure1_slot3;
                r15 = r14.loudness_lufs;
                r16 = r9;
                r18 = _closure1_slot31;
                r17 = r8;
                r14 = r1.loudness_lufs;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 397:
                r14 = r1.chorus_loudness;
                if(!(r13 != r14)) { _fun9478_ip = 454; continue _fun9478 }
case 407:
                r14 = _closure1_slot3;
                r15 = r14.chorus_loudness;
                r16 = r9;
                r18 = _closure1_slot36;
                r17 = r8;
                r14 = r1.chorus_loudness;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 454:
                r14 = r1.dynamic_range;
                if(!(r13 != r14)) { _fun9478_ip = 511; continue _fun9478 }
case 464:
                r14 = _closure1_slot3;
                r15 = r14.dynamic_range;
                r16 = r9;
                r18 = _closure1_slot32;
                r17 = r8;
                r14 = r1.dynamic_range;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 511:
                r14 = r1.genre_id;
                if(!(r13 != r14)) { _fun9478_ip = 568; continue _fun9478 }
case 521:
                r14 = _closure1_slot3;
                r15 = r14.genre_id;
                r16 = r9;
                r18 = _closure1_slot33;
                r17 = r8;
                r14 = r1.genre_id;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 568:
                r14 = r1.stereo_width_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 625; continue _fun9478 }
case 578:
                r14 = _closure1_slot3;
                r15 = r14.stereo_width_ratio;
                r16 = r9;
                r18 = _closure1_slot35;
                r17 = r8;
                r14 = r1.stereo_width_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 625:
                r14 = r7;
                if(!(r13 != r14)) { _fun9478_ip = 676; continue _fun9478 }
case 632:
                r14 = _closure1_slot3;
                r15 = r14.rhythm_complexity;
                r16 = r9;
                r18 = _closure1_slot37;
                r17 = r8;
                r14 = r7;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 676:
                r14 = r1.drum_time_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 733; continue _fun9478 }
case 686:
                r14 = _closure1_slot3;
                r15 = r14.drum_time_ratio;
                r16 = r9;
                r18 = _closure1_slot38;
                r17 = r8;
                r14 = r1.drum_time_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 733:
                r14 = r1.vocal_time_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 790; continue _fun9478 }
case 743:
                r14 = _closure1_slot3;
                r15 = r14.vocal_time_ratio;
                r16 = r9;
                r18 = _closure1_slot39;
                r17 = r8;
                r14 = r1.vocal_time_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 790:
                r14 = r1.harmonic_intensity_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 847; continue _fun9478 }
case 800:
                r14 = _closure1_slot3;
                r15 = r14.harmonic_intensity_ratio;
                r16 = r9;
                r18 = _closure1_slot40;
                r17 = r8;
                r14 = r1.harmonic_intensity_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 847:
                r14 = r1.drum_energy_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 904; continue _fun9478 }
case 857:
                r14 = _closure1_slot3;
                r15 = r14.drum_energy_ratio;
                r16 = r9;
                r18 = _closure1_slot41;
                r17 = r8;
                r14 = r1.drum_energy_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 904:
                r14 = r1.vocal_dynamic_range;
                if(!(r13 != r14)) { _fun9478_ip = 961; continue _fun9478 }
case 914:
                r14 = _closure1_slot3;
                r15 = r14.vocal_dynamic_range;
                r16 = r9;
                r18 = _closure1_slot42;
                r17 = r8;
                r14 = r1.vocal_dynamic_range;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 961:
                r14 = r1.vocal_energy_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 1018; continue _fun9478 }
case 971:
                r14 = _closure1_slot3;
                r15 = r14.vocal_energy_ratio;
                r16 = r9;
                r18 = _closure1_slot43;
                r17 = r8;
                r14 = r1.vocal_energy_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 1018:
                r14 = r1.vocal_main_freq;
                if(!(r13 != r14)) { _fun9478_ip = 1075; continue _fun9478 }
case 1028:
                r14 = _closure1_slot3;
                r15 = r14.vocal_main_freq;
                r16 = r9;
                r18 = _closure1_slot44;
                r17 = r8;
                r14 = r1.vocal_main_freq;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 1075:
                r14 = r1.bass_energy_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 1132; continue _fun9478 }
case 1085:
                r14 = _closure1_slot3;
                r15 = r14.bass_energy_ratio;
                r16 = r9;
                r18 = _closure1_slot45;
                r17 = r8;
                r14 = r1.bass_energy_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 1132:
                r14 = r1.other_instrument_energy_ratio;
                if(!(r13 != r14)) { _fun9478_ip = 1189; continue _fun9478 }
case 1142:
                r14 = _closure1_slot3;
                r15 = r14.other_instrument_energy_ratio;
                r16 = r9;
                r18 = _closure1_slot46;
                r17 = r8;
                r14 = r1.other_instrument_energy_ratio;
                r14 = r18.bind(r12)(r17, r14);
                r14 = r15 * r14;
                r9 = r16 + r14;
                r14 = r10;
                r10 = r14 + r15;
case 1189:
                r16 = r4;
                r15 = r8;
                r14 = _closure1_slot1;
                r17 = r14.floatIsZero;
                r14 = r10;
                r17 = r17.bind(r12)(r14);
                r14 = 0;
                if(r17) { _fun9478_ip = 1228; continue _fun9478 }
case 1218:
                r18 = r9;
                r17 = r10;
                r14 = r18 / r17;
case 1228:
                r16[r15] = r14;
case 1232: // try_end0
                _fun9478_ip = 129; continue _fun9478;
case 1237: // catch_target0
                CatchBlockStart(arg_register=5);
                r6.return();
                throw r5;
case 1244:
                var _closure2_slot0 = r4;
                r4 = _closure1_slot2;
                r3 = r4.reduce;
                r2 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
                    _fun9479: for(var _fun9479_ip = 0; ; ) switch(_fun9479_ip) {
case 0:
                        r0 = a0;
                        r1 = a1;
                        r2 = _closure2_slot0;
                        r3 = r2[r1];
                        r2 = r2[r0];
                        if(!(r3 > r2)) { _fun9479_ip = 28; continue _fun9479 }
case 25:
                        r0 = r1;
case 28:
                        return r0;
                    }
                };
                r2 = r3.bind(r4)(r2);
                return r2;
case 1270:
                r2 = global;
                r3 = r2.Error;
                r1 = r1.mel_spectrogram_32;
                r2 = r1.length;
                r1 = 'mel_spectrogram_32 数组长度必须为 32，当前为 ';
                r20 = r1 + r2;
                r2 = r3.prototype;
                r2 = Object.create(r2, {constructor: {value: r3}});
                r21 = r2;
                r1 = originalConstruct(r3, [r20, r19]);
                r1 = r1 instanceof Object ? r1 : r2;
                throw r1;
case 1319:
                return r0;
            }
        };
        r3['predictEmotionLabel'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9480: for(var _fun9480_ip = 0; ; ) switch(_fun9480_ip) {
case 0:
                r0 = a0;
                r1 = new Array(0);
                r7 = _closure1_slot47;
                r3 = r0.genre_id;
                r6 = undefined;
                r2 = 'genre_id';
                r2 = r7.bind(r6)(r3, r2, r1);
                r3 = r0.repbpm;
                r2 = 'repbpm';
                r2 = r7.bind(r6)(r3, r2, r1);
                r3 = r0.loudness_lufs;
                r2 = 'loudness_lufs';
                r2 = r7.bind(r6)(r3, r2, r1);
                r3 = r0.dynamic_range;
                r2 = 'dynamic_range';
                r2 = r7.bind(r6)(r3, r2, r1);
                r13 = r0.stereo_width_ratio;
                r12 = 'stereo_width_ratio';
                r3 = 0;
                r5 = 1;
                r14 = undefined;
                r11 = r1;
                r10 = 0;
                r9 = r5;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r4 = r0.chorus_loudness;
                r2 = 'chorus_loudness';
                r2 = r7.bind(r6)(r4, r2, r1);
                r13 = r0.rhythm_complexity;
                r12 = 'rhythm_complexity';
                r9 = 10;
                r14 = undefined;
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.drum_time_ratio;
                r12 = 'drum_time_ratio';
                r11 = r1;
                r9 = r5;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.vocal_time_ratio;
                r12 = 'vocal_time_ratio';
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.harmonic_intensity_ratio;
                r12 = 'harmonic_intensity_ratio';
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.drum_energy_ratio;
                r12 = 'drum_energy_ratio';
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.vocal_dynamic_range;
                r12 = 'vocal_dynamic_range';
                r9 = 30;
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.vocal_energy_ratio;
                r12 = 'vocal_energy_ratio';
                r11 = r1;
                r9 = r5;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.vocal_main_freq;
                r12 = 'vocal_main_freq';
                r9 = 1200;
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.bass_energy_ratio;
                r12 = 'bass_energy_ratio';
                r11 = r1;
                r9 = r5;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r13 = r0.other_instrument_energy_ratio;
                r12 = 'other_instrument_energy_ratio';
                r11 = r1;
                r2 = r7.call(r14, r13, r12, r11, r10, r9, r8);
                r2 = r0.key_mode;
                r4 = null;
                r2 = r4 == r2;
                if(r2) { _fun9480_ip = 386; continue _fun9480 }
case 338:
                r5 = r0.key_mode;
                r6 = 'string';
                r5 = typeof r5;
                r5 = r6 === r5;
                if(!r5) { _fun9480_ip = 383; continue _fun9480 }
case 358:
                r7 = r0.key_mode;
                r6 = r7.trim;
                r6 = r6.bind(r7)();
                r6 = r6.length;
                r5 = r3 !== r6;
case 383:
                r2 = r5;
case 386:
                if(r2) { _fun9480_ip = 403; continue _fun9480 }
case 389:
                r5 = r1.push;
                r2 = 'key_mode 必须为非空字符串';
                r2 = r5.bind(r1)(r2);
case 403:
                r2 = r0.mel_spectrogram_32;
                if(!(r4 != r2)) { _fun9480_ip = 549; continue _fun9480 }
case 416:
                r2 = global;
                r5 = r2.Array;
                r4 = r5.isArray;
                r2 = r0.mel_spectrogram_32;
                r2 = r4.bind(r5)(r2);
                if(r2) { _fun9480_ip = 459; continue _fun9480 }
case 443:
                r4 = r1.push;
                r2 = 'mel_spectrogram_32 必须为数组';
                r2 = r4.bind(r1)(r2);
                _fun9480_ip = 549; continue _fun9480;
case 459:
                r2 = r0.mel_spectrogram_32;
                r4 = r2.length;
                r2 = 32;
                if(!(r2 === r4)) { _fun9480_ip = 520; continue _fun9480 }
case 477:
                r5 = r0.mel_spectrogram_32;
                r4 = r5.some;
                r2 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
                    r2 = _closure1_slot26;
                    r1 = undefined;
                    r0 = a0;
                    r0 = r2.bind(r1)(r0);
                    r0 = !r0;
                    return r0;
                };
                r2 = r4.bind(r5)(r2);
                if(!r2) { _fun9480_ip = 549; continue _fun9480 }
case 504:
                r4 = r1.push;
                r2 = 'mel_spectrogram_32 数组中的元素必须全部为有限数值';
                r2 = r4.bind(r1)(r2);
                _fun9480_ip = 549; continue _fun9480;
case 520:
                r2 = r1.push;
                r0 = r0.mel_spectrogram_32;
                r4 = r0.length;
                r0 = 'mel_spectrogram_32 数组长度必须为 32，当前为 ';
                r0 = r0 + r4;
                r0 = r2.bind(r1)(r0);
case 549:
                r2 = r1.length;
                r0 = {};
                r2 = r3 === r2;
                r0['valid'] = r2;
                r0['issues'] = r1;
                return r0;
            }
        };
        r3['validateEmotionMappingFeatures'] = r1;
        r1 = 1;
        r1 = r6[r1];
        r1 = r5.bind(r0)(r1);
        r1 = r7.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r6[r1];
        r1 = r5.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = ['ecstatic', 'passionate', 'energetic', 'joyful', 'sweet', 'confident', 'romantic', 'relaxed', 'healing', 'calm', 'nostalgic', 'lonely', 'sad', 'bitter', 'depressed'];
        r3['EMOTION_MAPPING_LABELS'] = r1;
        var _closure1_slot2 = r1;
        r1 = {'mel_spectrogram_32': 2.7, 'repbpm': 2.5, 'key_mode': 2, 'loudness_lufs': 1.5, 'chorus_loudness': 1.5, 'dynamic_range': 1, 'genre_id': 0.5, 'stereo_width_ratio': 0.2, 'rhythm_complexity': 2, 'drum_time_ratio': 1.5, 'vocal_time_ratio': 1.5, 'drum_energy_ratio': 2, 'harmonic_intensity_ratio': 1, 'vocal_dynamic_range': 1, 'vocal_energy_ratio': 1, 'vocal_main_freq': 0.5, 'bass_energy_ratio': 0.5, 'other_instrument_energy_ratio': 0.5};
        var _closure1_slot3 = r1;
        r1 = ['mel_spectrogram_32', 'repbpm'];
        var _closure1_slot4 = r1;
        r1 = 'default';
        var _closure1_slot5 = r1;
        r1 = {};
        r3 = [120, 28.17];
        r1['ecstatic'] = r3;
        r3 = [105, 32.62];
        r1['passionate'] = r3;
        r3 = [105, 31.13];
        r1['energetic'] = r3;
        r3 = [105, 29.65];
        r1['joyful'] = r3;
        r3 = [106, 31.13];
        r1['sweet'] = r3;
        r3 = [94, 31.13];
        r1['confident'] = r3;
        r3 = [86, 26.69];
        r1['romantic'] = r3;
        r3 = [89, 28.17];
        r1['relaxed'] = r3;
        r3 = [84, 25.2];
        r1['healing'] = r3;
        r3 = [84, 28.17];
        r1['calm'] = r3;
        r3 = [82, 25.2];
        r1['nostalgic'] = r3;
        r3 = [80, 23.72];
        r1['lonely'] = r3;
        r3 = [75, 17.79];
        r1['sad'] = r3;
        r3 = [76, 19.27];
        r1['bitter'] = r3;
        r3 = [84, 25.2];
        r1['depressed'] = r3;
        var _closure1_slot6 = r1;
        r1 = {'sweet': 0.88, 'romantic': 0.92, 'healing': 0.98, 'calm': 0.92, 'nostalgic': 0.96, 'sad': 0.97, 'bitter': 0.96, 'depressed': 0.93, 'lonely': 0.9, 'relaxed': 0.85, 'joyful': 0.78, 'confident': 0.72, 'passionate': 0.64, 'energetic': 0.67, 'ecstatic': 0.57};
        var _closure1_slot7 = r1;
        r22 = new Array(2);
        r1 = -8.21;
        r22[0] = r1;
        r1 = 1.9;
        r22[1] = r1;
        r21 = new Array(2);
        r1 = -8.64;
        r21[0] = r1;
        r1 = 2.55;
        r21[1] = r1;
        r20 = new Array(2);
        r1 = -8.87;
        r20[0] = r1;
        r1 = 2.34;
        r20[1] = r1;
        r19 = new Array(2);
        r1 = -9.45;
        r19[0] = r1;
        r1 = 2.7;
        r19[1] = r1;
        r18 = new Array(2);
        r1 = -9.75;
        r18[0] = r1;
        r5 = 3.05;
        r18[1] = r5;
        r17 = new Array(2);
        r1 = -9.25;
        r17[0] = r1;
        r1 = 2.05;
        r17[1] = r1;
        r16 = new Array(2);
        r3 = -10.51;
        r16[0] = r3;
        r7 = 2.65;
        r16[1] = r7;
        r15 = new Array(2);
        r1 = -11.02;
        r15[0] = r1;
        r6 = 3.4;
        r15[1] = r6;
        r14 = new Array(2);
        r1 = -10.68;
        r14[0] = r1;
        r1 = 2.94;
        r14[1] = r1;
        r13 = new Array(2);
        r8 = -13.23;
        r13[0] = r8;
        r8 = 4.31;
        r13[1] = r8;
        r12 = new Array(2);
        r8 = -10.4;
        r12[0] = r8;
        r8 = 2.49;
        r12[1] = r8;
        r11 = new Array(2);
        r8 = -11.53;
        r11[0] = r8;
        r8 = 3.2;
        r11[1] = r8;
        r10 = new Array(2);
        r8 = -10.66;
        r10[0] = r8;
        r8 = 2.36;
        r10[1] = r8;
        r9 = new Array(2);
        r8 = -10.86;
        r9[0] = r8;
        r8 = 2.56;
        r9[1] = r8;
        r8 = new Array(2);
        r23 = -10.65;
        r8[0] = r23;
        r8[1] = r7;
        r7 = {};
        r7['ecstatic'] = r22;
        r7['passionate'] = r21;
        r7['energetic'] = r20;
        r7['joyful'] = r19;
        r7['sweet'] = r18;
        r7['confident'] = r17;
        r7['romantic'] = r16;
        r7['relaxed'] = r15;
        r7['healing'] = r14;
        r7['calm'] = r13;
        r7['nostalgic'] = r12;
        r7['lonely'] = r11;
        r7['sad'] = r10;
        r7['bitter'] = r9;
        r7['depressed'] = r8;
        var _closure1_slot8 = r7;
        r7 = {};
        r8 = [4.5, 2.37];
        r7['ecstatic'] = r8;
        r8 = [5.4, 3.26];
        r7['passionate'] = r8;
        r8 = [5.1, 2.97];
        r7['energetic'] = r8;
        r8 = [5.1, 2.82];
        r7['joyful'] = r8;
        r8 = [5.2, 2.82];
        r7['sweet'] = r8;
        r8 = [6.2, 3.56];
        r7['confident'] = r8;
        r8 = [7.4, 4];
        r7['romantic'] = r8;
        r8 = [6.7, 3.56];
        r7['relaxed'] = r8;
        r8 = [8, 4.15];
        r7['healing'] = r8;
        r8 = [8, 4];
        r7['calm'] = r8;
        r8 = [8.1, 4.15];
        r7['nostalgic'] = r8;
        r8 = [8.7, 4.3];
        r7['lonely'] = r8;
        r8 = [9.1, 4.15];
        r7['sad'] = r8;
        r8 = [9.8, 4.15];
        r7['bitter'] = r8;
        r8 = [8.2, 4.15];
        r7['depressed'] = r8;
        var _closure1_slot9 = r7;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1015, 1017, 1008];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r22 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1008, 1017, 1015];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r21 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1017, 1015, 1008];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r20 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1017, 1015];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r19 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1017];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r18 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1017, 1015];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r17 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1028];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r16 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1028, 1003];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r15 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1003, 1006, 1007];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r14 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1003, 1006, 1007];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r13 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1010, 1003];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r12 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1003, 1010];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r11 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1003, 1010];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r10 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1010, 1003];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r9 = r7 instanceof Object ? r7 : r8;
        r23 = r4.Set;
        r7 = r23.prototype;
        r8 = Object.create(r7, {constructor: {value: r23}});
        r26 = [1010, 1003];
        r27 = r8;
        r7 = originalConstruct(r23, [r26, r25]);
        r8 = r7 instanceof Object ? r7 : r8;
        r7 = {};
        r7['ecstatic'] = r22;
        r7['passionate'] = r21;
        r7['energetic'] = r20;
        r7['joyful'] = r19;
        r7['sweet'] = r18;
        r7['confident'] = r17;
        r7['romantic'] = r16;
        r7['relaxed'] = r15;
        r7['healing'] = r14;
        r7['calm'] = r13;
        r7['nostalgic'] = r12;
        r7['lonely'] = r11;
        r7['sad'] = r10;
        r7['bitter'] = r9;
        r7['depressed'] = r8;
        var _closure1_slot10 = r7;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1006, 1007, 1003];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r11 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1006, 1007];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r10 = r7 instanceof Object ? r7 : r8;
        r9 = r4.Set;
        r7 = r9.prototype;
        r8 = Object.create(r7, {constructor: {value: r9}});
        r26 = [1017, 1015, 1008];
        r27 = r8;
        r7 = originalConstruct(r9, [r26, r25]);
        r9 = r7 instanceof Object ? r7 : r8;
        r12 = r4.Set;
        r7 = r12.prototype;
        r8 = Object.create(r7, {constructor: {value: r12}});
        r26 = [1015, 1008, 1017];
        r27 = r8;
        r7 = originalConstruct(r12, [r26, r25]);
        r8 = r7 instanceof Object ? r7 : r8;
        r12 = r4.Set;
        r4 = r12.prototype;
        r7 = Object.create(r4, {constructor: {value: r12}});
        r26 = [1017, 1015, 1008];
        r27 = r7;
        r4 = originalConstruct(r12, [r26, r25]);
        r7 = r4 instanceof Object ? r4 : r7;
        r4 = {};
        r4['ecstatic'] = r11;
        r4['energetic'] = r10;
        r4['healing'] = r9;
        r4['depressed'] = r8;
        r4['calm'] = r7;
        var _closure1_slot11 = r4;
        r21 = new Array(2);
        r4 = -55.72;
        r21[0] = r4;
        r4 = 5.7;
        r21[1] = r4;
        r20 = new Array(2);
        r4 = -56.96;
        r20[0] = r4;
        r4 = 6.63;
        r20[1] = r4;
        r19 = new Array(2);
        r4 = -57.72;
        r19[0] = r4;
        r4 = 6.71;
        r19[1] = r4;
        r18 = new Array(2);
        r4 = -58.01;
        r18[0] = r4;
        r4 = 6.83;
        r18[1] = r4;
        r17 = new Array(2);
        r4 = -58.02;
        r17[0] = r4;
        r4 = 7.7;
        r17[1] = r4;
        r16 = new Array(2);
        r4 = -59.73;
        r16[0] = r4;
        r4 = 5.4;
        r16[1] = r4;
        r15 = new Array(2);
        r4 = -62.44;
        r15[0] = r4;
        r4 = 5.68;
        r15[1] = r4;
        r14 = new Array(2);
        r4 = -64.13;
        r14[0] = r4;
        r4 = 8.01;
        r14[1] = r4;
        r13 = new Array(2);
        r4 = -63.22;
        r13[0] = r4;
        r4 = 6.65;
        r13[1] = r4;
        r12 = new Array(2);
        r4 = -68.88;
        r12[0] = r4;
        r4 = 9.41;
        r12[1] = r4;
        r11 = new Array(2);
        r4 = -62.02;
        r11[0] = r4;
        r4 = 5.28;
        r11[1] = r4;
        r10 = new Array(2);
        r4 = -64.83;
        r10[0] = r4;
        r4 = 6.26;
        r10[1] = r4;
        r9 = new Array(2);
        r4 = -62.88;
        r9[0] = r4;
        r4 = 4.7;
        r9[1] = r4;
        r8 = new Array(2);
        r4 = -62.59;
        r8[0] = r4;
        r4 = 4.45;
        r8[1] = r4;
        r7 = new Array(2);
        r4 = -63.85;
        r7[0] = r4;
        r4 = 6.01;
        r7[1] = r4;
        r4 = {};
        r4['ecstatic'] = r21;
        r4['passionate'] = r20;
        r4['energetic'] = r19;
        r4['joyful'] = r18;
        r4['sweet'] = r17;
        r4['confident'] = r16;
        r4['romantic'] = r15;
        r4['relaxed'] = r14;
        r4['healing'] = r13;
        r4['calm'] = r12;
        r4['nostalgic'] = r11;
        r4['lonely'] = r10;
        r4['sad'] = r9;
        r4['bitter'] = r8;
        r4['depressed'] = r7;
        var _closure1_slot12 = r4;
        r19 = new Array(2);
        r4 = -24.69;
        r19[0] = r4;
        r4 = 3.09;
        r19[1] = r4;
        r18 = new Array(2);
        r4 = -24.02;
        r18[0] = r4;
        r4 = 3.19;
        r18[1] = r4;
        r17 = new Array(2);
        r4 = -25.11;
        r17[0] = r4;
        r4 = 3.5;
        r17[1] = r4;
        r16 = new Array(2);
        r4 = -24.87;
        r16[0] = r4;
        r4 = 3.44;
        r16[1] = r4;
        r15 = new Array(2);
        r4 = -24.98;
        r15[0] = r4;
        r4 = 3.74;
        r15[1] = r4;
        r14 = new Array(2);
        r7 = -25.71;
        r14[0] = r7;
        r7 = 3.21;
        r14[1] = r7;
        r13 = new Array(2);
        r7 = -26.07;
        r13[0] = r7;
        r13[1] = r6;
        r12 = new Array(2);
        r6 = -26.6;
        r12[0] = r6;
        r6 = 3.99;
        r12[1] = r6;
        r11 = new Array(2);
        r6 = -25.44;
        r11[0] = r6;
        r6 = 3.58;
        r11[1] = r6;
        r10 = new Array(2);
        r6 = -27.12;
        r10[0] = r6;
        r6 = 4.05;
        r10[1] = r6;
        r9 = new Array(2);
        r6 = -25.69;
        r9[0] = r6;
        r9[1] = r5;
        r8 = new Array(2);
        r5 = -26.47;
        r8[0] = r5;
        r5 = 3.48;
        r8[1] = r5;
        r7 = new Array(2);
        r5 = -25.97;
        r7[0] = r5;
        r5 = 3;
        r7[1] = r5;
        r6 = new Array(2);
        r20 = -26.08;
        r6[0] = r20;
        r6[1] = r5;
        r5 = new Array(2);
        r20 = -26.44;
        r5[0] = r20;
        r5[1] = r4;
        r4 = {};
        r4['ecstatic'] = r19;
        r4['passionate'] = r18;
        r4['energetic'] = r17;
        r4['joyful'] = r16;
        r4['sweet'] = r15;
        r4['confident'] = r14;
        r4['romantic'] = r13;
        r4['relaxed'] = r12;
        r4['healing'] = r11;
        r4['calm'] = r10;
        r4['nostalgic'] = r9;
        r4['lonely'] = r8;
        r4['sad'] = r7;
        r4['bitter'] = r6;
        r4['depressed'] = r5;
        var _closure1_slot13 = r4;
        r4 = {'ecstatic': 0.255, 'passionate': 0.284, 'energetic': 0.258, 'joyful': 0.258, 'sweet': 0.257, 'confident': 0.248, 'romantic': 0.262, 'relaxed': 0.267, 'healing': 0.288, 'calm': 0.3, 'nostalgic': 0.269, 'lonely': 0.266, 'sad': 0.267, 'bitter': 0.267, 'depressed': 0.258};
        var _closure1_slot14 = r4;
        r17 = new Array(2);
        r4 = -7.71;
        r17[0] = r4;
        r4 = 2.01;
        r17[1] = r4;
        r16 = new Array(2);
        r4 = -8.08;
        r16[0] = r4;
        r4 = 2.59;
        r16[1] = r4;
        r15 = new Array(2);
        r4 = -8.37;
        r15[0] = r4;
        r4 = 2.43;
        r15[1] = r4;
        r14 = new Array(2);
        r4 = -8.9;
        r14[0] = r4;
        r4 = 2.79;
        r14[1] = r4;
        r13 = new Array(2);
        r4 = -9.14;
        r13[0] = r4;
        r4 = 3.07;
        r13[1] = r4;
        r12 = new Array(2);
        r4 = -8.69;
        r12[0] = r4;
        r4 = 2.18;
        r12[1] = r4;
        r11 = new Array(2);
        r4 = -9.96;
        r11[0] = r4;
        r4 = 2.84;
        r11[1] = r4;
        r10 = new Array(2);
        r4 = -10.37;
        r10[0] = r4;
        r4 = 3.39;
        r10[1] = r4;
        r9 = new Array(2);
        r4 = -10.03;
        r9[0] = r4;
        r4 = 3.11;
        r9[1] = r4;
        r8 = new Array(2);
        r4 = -12.4;
        r8[0] = r4;
        r4 = 4.14;
        r8[1] = r4;
        r7 = new Array(2);
        r4 = -9.83;
        r7[0] = r4;
        r4 = 2.77;
        r7[1] = r4;
        r6 = new Array(2);
        r4 = -11.03;
        r6[0] = r4;
        r4 = 3.36;
        r6[1] = r4;
        r5 = new Array(2);
        r4 = -10.18;
        r5[0] = r4;
        r4 = 2.75;
        r5[1] = r4;
        r4 = new Array(2);
        r4[0] = r3;
        r3 = 2.98;
        r4[1] = r3;
        r3 = new Array(2);
        r18 = -10.11;
        r3[0] = r18;
        r3[1] = r1;
        r1 = {};
        r1['ecstatic'] = r17;
        r1['passionate'] = r16;
        r1['energetic'] = r15;
        r1['joyful'] = r14;
        r1['sweet'] = r13;
        r1['confident'] = r12;
        r1['romantic'] = r11;
        r1['relaxed'] = r10;
        r1['healing'] = r9;
        r1['calm'] = r8;
        r1['nostalgic'] = r7;
        r1['lonely'] = r6;
        r1['sad'] = r5;
        r1['bitter'] = r4;
        r1['depressed'] = r3;
        var _closure1_slot15 = r1;
        r1 = {};
        r3 = [0.63, 0.18];
        r1['ecstatic'] = r3;
        r3 = [0.64, 0.22];
        r1['passionate'] = r3;
        r3 = [0.63, 0.21];
        r1['energetic'] = r3;
        r3 = [0.62, 0.21];
        r1['joyful'] = r3;
        r3 = [0.64, 0.22];
        r1['sweet'] = r3;
        r3 = [0.59, 0.21];
        r1['confident'] = r3;
        r3 = [0.52, 0.19];
        r1['romantic'] = r3;
        r3 = [0.56, 0.21];
        r1['relaxed'] = r3;
        r3 = [0.52, 0.22];
        r1['healing'] = r3;
        r3 = [0.48, 0.22];
        r1['calm'] = r3;
        r3 = [0.48, 0.18];
        r1['nostalgic'] = r3;
        r3 = [0.46, 0.18];
        r1['lonely'] = r3;
        r3 = [0.45, 0.16];
        r1['sad'] = r3;
        r3 = [0.45, 0.16];
        r1['bitter'] = r3;
        r3 = [0.5, 0.19];
        r1['depressed'] = r3;
        var _closure1_slot16 = r1;
        r1 = {};
        r3 = [0.72, 0.3];
        r1['ecstatic'] = r3;
        r3 = [0.79, 0.24];
        r1['passionate'] = r3;
        r3 = [0.65, 0.36];
        r1['energetic'] = r3;
        r3 = [0.58, 0.43];
        r1['joyful'] = r3;
        r3 = [0.55, 0.49];
        r1['sweet'] = r3;
        r3 = [0.46, 0.4];
        r1['confident'] = r3;
        r3 = [0.32, 0.37];
        r1['romantic'] = r3;
        r3 = [0.25, 0.37];
        r1['relaxed'] = r3;
        r3 = [0.36, 0.43];
        r1['healing'] = r3;
        r3 = [0.02, 0.05];
        r1['calm'] = r3;
        r3 = [0.31, 0.33];
        r1['nostalgic'] = r3;
        r3 = [0.2, 0.3];
        r1['lonely'] = r3;
        r3 = [0.28, 0.3];
        r1['sad'] = r3;
        r3 = [0.3, 0.27];
        r1['bitter'] = r3;
        r3 = [0.28, 0.36];
        r1['depressed'] = r3;
        var _closure1_slot17 = r1;
        r1 = {};
        r3 = [0.83, 0.16];
        r1['ecstatic'] = r3;
        r3 = [0.75, 0.21];
        r1['passionate'] = r3;
        r3 = [0.8, 0.19];
        r1['energetic'] = r3;
        r3 = [0.78, 0.18];
        r1['joyful'] = r3;
        r3 = [0.77, 0.18];
        r1['sweet'] = r3;
        r3 = [0.86, 0.12];
        r1['confident'] = r3;
        r3 = [0.78, 0.16];
        r1['romantic'] = r3;
        r3 = [0.72, 0.28];
        r1['relaxed'] = r3;
        r3 = [0.72, 0.21];
        r1['healing'] = r3;
        r3 = [0.62, 0.4];
        r1['calm'] = r3;
        r3 = [0.79, 0.13];
        r1['nostalgic'] = r3;
        r3 = [0.74, 0.16];
        r1['lonely'] = r3;
        r3 = [0.78, 0.12];
        r1['sad'] = r3;
        r3 = [0.78, 0.1];
        r1['bitter'] = r3;
        r3 = [0.77, 0.16];
        r1['depressed'] = r3;
        var _closure1_slot18 = r1;
        r1 = {};
        r3 = [0.32, 0.07];
        r1['ecstatic'] = r3;
        r3 = [0.31, 0.07];
        r1['passionate'] = r3;
        r3 = [0.31, 0.07];
        r1['energetic'] = r3;
        r3 = [0.3, 0.07];
        r1['joyful'] = r3;
        r3 = [0.31, 0.09];
        r1['sweet'] = r3;
        r3 = [0.31, 0.07];
        r1['confident'] = r3;
        r3 = [0.26, 0.07];
        r1['romantic'] = r3;
        r3 = [0.26, 0.07];
        r1['relaxed'] = r3;
        r3 = [0.26, 0.07];
        r1['healing'] = r3;
        r3 = [0.23, 0.07];
        r1['calm'] = r3;
        r3 = [0.27, 0.06];
        r1['nostalgic'] = r3;
        r3 = [0.25, 0.07];
        r1['lonely'] = r3;
        r3 = [0.26, 0.06];
        r1['sad'] = r3;
        r3 = [0.26, 0.06];
        r1['bitter'] = r3;
        r3 = [0.26, 0.07];
        r1['depressed'] = r3;
        var _closure1_slot19 = r1;
        r1 = {};
        r3 = [0.34, 0.16];
        r1['ecstatic'] = r3;
        r3 = [0.26, 0.16];
        r1['passionate'] = r3;
        r3 = [0.3, 0.18];
        r1['energetic'] = r3;
        r3 = [0.23, 0.16];
        r1['joyful'] = r3;
        r3 = [0.21, 0.18];
        r1['sweet'] = r3;
        r3 = [0.23, 0.18];
        r1['confident'] = r3;
        r3 = [0.14, 0.15];
        r1['romantic'] = r3;
        r3 = [0.15, 0.21];
        r1['relaxed'] = r3;
        r3 = [0.12, 0.15];
        r1['healing'] = r3;
        r3 = [0, 0.05];
        r1['calm'] = r3;
        r3 = [0.14, 0.13];
        r1['nostalgic'] = r3;
        r3 = [0.08, 0.12];
        r1['lonely'] = r3;
        r3 = [0.11, 0.1];
        r1['sad'] = r3;
        r3 = [0.11, 0.09];
        r1['bitter'] = r3;
        r3 = [0.16, 0.16];
        r1['depressed'] = r3;
        var _closure1_slot20 = r1;
        r1 = {};
        r3 = [16.76, 1.76];
        r1['ecstatic'] = r3;
        r3 = [16.57, 1.9];
        r1['passionate'] = r3;
        r3 = [16.83, 1.91];
        r1['energetic'] = r3;
        r3 = [16.32, 1.78];
        r1['joyful'] = r3;
        r3 = [16.21, 1.84];
        r1['sweet'] = r3;
        r3 = [16.88, 1.79];
        r1['confident'] = r3;
        r3 = [16.64, 1.91];
        r1['romantic'] = r3;
        r3 = [16.81, 1.94];
        r1['relaxed'] = r3;
        r3 = [16.3, 1.88];
        r1['healing'] = r3;
        r3 = [16.26, 1.96];
        r1['calm'] = r3;
        r3 = [16.32, 1.78];
        r1['nostalgic'] = r3;
        r3 = [16.47, 1.96];
        r1['lonely'] = r3;
        r3 = [16.23, 1.79];
        r1['sad'] = r3;
        r3 = [16.58, 1.82];
        r1['bitter'] = r3;
        r3 = [16.78, 2.09];
        r1['depressed'] = r3;
        var _closure1_slot21 = r1;
        r1 = {};
        r3 = [0.23, 0.13];
        r1['ecstatic'] = r3;
        r3 = [0.24, 0.19];
        r1['passionate'] = r3;
        r3 = [0.23, 0.16];
        r1['energetic'] = r3;
        r3 = [0.3, 0.18];
        r1['joyful'] = r3;
        r3 = [0.32, 0.21];
        r1['sweet'] = r3;
        r3 = [0.27, 0.18];
        r1['confident'] = r3;
        r3 = [0.36, 0.22];
        r1['romantic'] = r3;
        r3 = [0.23, 0.28];
        r1['relaxed'] = r3;
        r3 = [0.33, 0.29];
        r1['healing'] = r3;
        r3 = [0.27, 0.4];
        r1['calm'] = r3;
        r3 = [0.4, 0.19];
        r1['nostalgic'] = r3;
        r3 = [0.41, 0.22];
        r1['lonely'] = r3;
        r3 = [0.44, 0.16];
        r1['sad'] = r3;
        r3 = [0.44, 0.15];
        r1['bitter'] = r3;
        r3 = [0.33, 0.22];
        r1['depressed'] = r3;
        var _closure1_slot22 = r1;
        r1 = {};
        r3 = [502.77, 137.85];
        r1['ecstatic'] = r3;
        r3 = [511.44, 154.11];
        r1['passionate'] = r3;
        r3 = [479.26, 154.15];
        r1['energetic'] = r3;
        r3 = [457.65, 157.73];
        r1['joyful'] = r3;
        r3 = [448.09, 163.65];
        r1['sweet'] = r3;
        r3 = [449.32, 136.38];
        r1['confident'] = r3;
        r3 = [368.9, 133.48];
        r1['romantic'] = r3;
        r3 = [398.27, 144.6];
        r1['relaxed'] = r3;
        r3 = [381.97, 140.94];
        r1['healing'] = r3;
        r3 = [327.4, 105.95];
        r1['calm'] = r3;
        r3 = [334.69, 112.69];
        r1['nostalgic'] = r3;
        r3 = [328.6, 108.35];
        r1['lonely'] = r3;
        r3 = [326.5, 97.71];
        r1['sad'] = r3;
        r3 = [333.07, 97.15];
        r1['bitter'] = r3;
        r3 = [347.3, 135.11];
        r1['depressed'] = r3;
        var _closure1_slot23 = r1;
        r1 = {};
        r3 = [0.223, 0.11];
        r1['ecstatic'] = r3;
        r3 = [0.231, 0.109];
        r1['passionate'] = r3;
        r3 = [0.255, 0.108];
        r1['energetic'] = r3;
        r3 = [0.226, 0.1];
        r1['joyful'] = r3;
        r3 = [0.202, 0.099];
        r1['sweet'] = r3;
        r3 = [0.29, 0.135];
        r1['confident'] = r3;
        r3 = [0.221, 0.098];
        r1['romantic'] = r3;
        r3 = [0.227, 0.099];
        r1['relaxed'] = r3;
        r3 = [0.215, 0.067];
        r1['healing'] = r3;
        r3 = [0.236, 0.094];
        r1['calm'] = r3;
        r3 = [0.208, 0.09];
        r1['nostalgic'] = r3;
        r3 = [0.23, 0.098];
        r1['lonely'] = r3;
        r3 = [0.241, 0.094];
        r1['sad'] = r3;
        r3 = [0.224, 0.09];
        r1['bitter'] = r3;
        r3 = [0.213, 0.096];
        r1['depressed'] = r3;
        var _closure1_slot24 = r1;
        r1 = {};
        r3 = [0.13, 0.085];
        r1['ecstatic'] = r3;
        r3 = [0.148, 0.096];
        r1['passionate'] = r3;
        r3 = [0.115, 0.067];
        r1['energetic'] = r3;
        r3 = [0.139, 0.08];
        r1['joyful'] = r3;
        r3 = [0.152, 0.096];
        r1['sweet'] = r3;
        r3 = [0.127, 0.073];
        r1['confident'] = r3;
        r3 = [0.181, 0.084];
        r1['romantic'] = r3;
        r3 = [0.19, 0.109];
        r1['relaxed'] = r3;
        r3 = [0.23, 0.1];
        r1['healing'] = r3;
        r3 = [0.218, 0.098];
        r1['calm'] = r3;
        r3 = [0.209, 0.089];
        r1['nostalgic'] = r3;
        r3 = [0.2, 0.082];
        r1['lonely'] = r3;
        r3 = [0.201, 0.09];
        r1['sad'] = r3;
        r3 = [0.194, 0.079];
        r1['bitter'] = r3;
        r3 = [0.206, 0.081];
        r1['depressed'] = r3;
        var _closure1_slot25 = r1;
        return r0;
    };
factories[959]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a1;
        r6 = a5;
        r4 = a6;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: s, environment: r2
            _fun9483: for(var _fun9483_ip = 0; ; ) switch(_fun9483_ip) {
case 0:
                r4 = a0;
                r0 = null;
                r0 = r0 != r4;
                if(!r0) { _fun9483_ip = 44; continue _fun9483 }
case 12:
                r1 = global;
                r1 = r1.Object;
                r1 = r1.prototype;
                r3 = r1.hasOwnProperty;
                r2 = r3.call;
                r1 = a1;
                r0 = r2.bind(r3)(r4, r1);
case 44:
                return r0;
            }
        };
        var _closure1_slot6 = r0;
        r0 = function(a0, a1, a2, a3) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: u, environment: r2
            _fun9484: for(var _fun9484_ip = 0; ; ) switch(_fun9484_ip) {
case 0:
                r4 = a0;
                r6 = a1;
                r3 = a2;
                r1 = a3;
                r0 = undefined;
                r5 = _closure1_slot6;
                r5 = r5.bind(r0)(r4, r6);
                if(!r5) { _fun9484_ip = 323; continue _fun9484 }
case 33:
                r7 = null;
                r5 = undefined;
                if(!(r7 != r4)) { _fun9484_ip = 45; continue _fun9484 }
case 41:
                r5 = r4[r6];
case 45:
                r8 = 'number';
                r4 = typeof r5;
                if(!(r8 === r4)) { _fun9484_ip = 78; continue _fun9484 }
case 56:
                r4 = global;
                r9 = r4.Number;
                r8 = r9.isFinite;
                r8 = r8.bind(r9)(r5);
                if(r8) { _fun9484_ip = 105; continue _fun9484 }
case 78:
                r9 = r3.push;
                r8 = ' 必须为有限数值，实际值: ';
                r8 = r6 + r8;
                r8 = r8 + r5;
                r8 = r9.bind(r3)(r8);
                _fun9484_ip = 323; continue _fun9484;
case 105:
                if(!(r7 != r1)) { _fun9484_ip = 165; continue _fun9484 }
case 109:
                r8 = r1.integer;
                if(!r8) { _fun9484_ip = 165; continue _fun9484 }
case 118:
                r8 = r4.Number;
                r4 = r8.isInteger;
                r4 = r4.bind(r8)(r5);
                if(r4) { _fun9484_ip = 165; continue _fun9484 }
case 138:
                r8 = r3.push;
                r4 = ' 必须为整数，实际值: ';
                r4 = r6 + r4;
                r4 = r4 + r5;
                r4 = r8.bind(r3)(r4);
                _fun9484_ip = 323; continue _fun9484;
case 165:
                r4 = undefined;
                if(!(r7 != r1)) { _fun9484_ip = 176; continue _fun9484 }
case 171:
                r4 = r1.min;
case 176:
                if(!(r7 != r4)) { _fun9484_ip = 203; continue _fun9484 }
case 180:
                r8 = r1.min;
                r4 = _closure1_slot5;
                r4 = r4.RANGE_EPSILON;
                r4 = r8 - r4;
                if(!(!(r5 < r4))) { _fun9484_ip = 284; continue _fun9484 }
case 203:
                r4 = undefined;
                if(!(r7 != r1)) { _fun9484_ip = 214; continue _fun9484 }
case 209:
                r4 = r1.max;
case 214:
                if(!(r7 != r4)) { _fun9484_ip = 241; continue _fun9484 }
case 218:
                r4 = r1.max;
                r2 = _closure1_slot5;
                r2 = r2.RANGE_EPSILON;
                r2 = r4 + r2;
                if(!(!(r5 > r2))) { _fun9484_ip = 243; continue _fun9484 }
case 241:
                return r5;
case 243:
                r4 = r3.push;
                r2 = ' = ';
                r2 = r6 + r2;
                r8 = r2 + r5;
                r7 = r1.max;
                r2 = ' 超出上界 ';
                r2 = r8 + r2;
                r2 = r2 + r7;
                r2 = r4.bind(r3)(r2);
                _fun9484_ip = 323; continue _fun9484;
case 284:
                r2 = r3.push;
                r4 = ' = ';
                r4 = r6 + r4;
                r5 = r4 + r5;
                r4 = r1.min;
                r1 = ' 超出下界 ';
                r1 = r5 + r1;
                r1 = r1 + r4;
                r1 = r2.bind(r3)(r1);
case 323:
                return r0;
            }
        };
        var _closure1_slot7 = r0;
        r0 = function(a0, a1, a2, a3, a4) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: d, environment: r2
            _fun9485: for(var _fun9485_ip = 0; ; ) switch(_fun9485_ip) {
case 0:
                r10 = a0;
                r7 = a1;
                r6 = a2;
                r8 = a3;
                r5 = a4;
                r1 = 'number';
                r0 = typeof r10;
                if(!(r1 === r0)) { _fun9485_ip = 48; continue _fun9485 }
case 26:
                r0 = global;
                r1 = r0.Number;
                r0 = r1.isFinite;
                r0 = r0.bind(r1)(r10);
                if(r0) { _fun9485_ip = 82; continue _fun9485 }
case 48:
                r1 = r5.push;
                r0 = ' = ';
                r0 = r8 + r0;
                r3 = r0 + r10;
                r0 = ' 不是有效数字';
                r0 = r3 + r0;
                r0 = r1.bind(r5)(r0);
                r0 = false;
                _fun9485_ip = 203; continue _fun9485;
case 82:
                r3 = _closure1_slot5;
                r3 = r3.RANGE_EPSILON;
                r3 = r7 - r3;
                r3 = r10 < r3;
                if(r3) { _fun9485_ip = 124; continue _fun9485 }
case 106:
                r1 = _closure1_slot5;
                r1 = r1.RANGE_EPSILON;
                r1 = r6 + r1;
                r3 = r10 > r1;
case 124:
                r1 = !r3;
                if(!r3) { _fun9485_ip = 200; continue _fun9485 }
case 130:
                r4 = r5.push;
                r3 = ' = ';
                r8 = r8 + r3;
                r9 = r10.toFixed;
                r3 = 4;
                r3 = r9.bind(r10)(r3);
                r8 = r8 + r3;
                r3 = ' 超出范围 [';
                r3 = r8 + r3;
                r7 = r3 + r7;
                r3 = ', ';
                r3 = r7 + r3;
                r6 = r3 + r6;
                r3 = ']';
                r3 = r6 + r3;
                r3 = r4.bind(r5)(r3);
                r1 = false;
case 200:
                r0 = r1;
case 203:
                return r0;
            }
        };
        var _closure1_slot8 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: p, environment: r2
            _fun9486: for(var _fun9486_ip = 0; ; ) switch(_fun9486_ip) {
case 0:
                r0 = a0;
                r3 = 0;
                r12 = ']';
                r11 = -12;
                r10 = 12;
                r1 = new Array(0);
                if(r0) { _fun9486_ip = 61; continue _fun9486 }
case 25:
                r4 = new Array(1);
                r5 = 'output.aev2';
                r2 = ' 为 null/undefined';
                r2 = r5 + r2;
                r4[0] = r2;
                r2 = {};
                r5 = false;
                r2['valid'] = r5;
                r2['issues'] = r4;
                return r2;
case 61:
                r2 = global;
                r7 = r2.Array;
                r5 = r7.isArray;
                r4 = r0.eq_gain_db;
                r4 = r5.bind(r7)(r4);
                if(r4) { _fun9486_ip = 110; continue _fun9486 }
case 88:
                r5 = r1.push;
                r7 = 'output.aev2';
                r4 = '.eq_gain_db 必须为数组';
                r4 = r7 + r4;
                r4 = r5.bind(r1)(r4);
case 110:
                r5 = r2.Array;
                r4 = r5.isArray;
                r2 = r0.eq_gain_db;
                r2 = r4.bind(r5)(r2);
                if(r2) { _fun9486_ip = 141; continue _fun9486 }
case 135:
                r9 = new Array(0);
                _fun9486_ip = 147; continue _fun9486;
case 141:
                r9 = r0.eq_gain_db;
case 147:
                r5 = r9.length;
                r4 = _closure1_slot2;
                r4 = r4.AEV2_EQ_GAIN_DB_SIZE;
                if(!(r5 !== r4)) { _fun9486_ip = 222; continue _fun9486 }
case 169:
                r5 = r1.push;
                r4 = _closure1_slot2;
                r7 = r4.AEV2_EQ_GAIN_DB_SIZE;
                r8 = 'output.aev2';
                r4 = '.eq_gain_db 长度必须为 ';
                r4 = r8 + r4;
                r8 = r4 + r7;
                r7 = r9.length;
                r4 = '，当前为 ';
                r4 = r8 + r4;
                r4 = r4 + r7;
                r4 = r5.bind(r1)(r4);
case 222:
                r4 = r9.length;
                r7 = 'output.aev2';
                r5 = '.eq_gain_db[';
                r8 = r7 + r5;
                r5 = 0;
                if(!(r5 < r4)) { _fun9486_ip = 288; continue _fun9486 }
case 245:
                r14 = _closure1_slot8;
                r19 = r9[r5];
                r4 = r8 + r5;
                r16 = r4 + r12;
                r20 = undefined;
                r18 = r11;
                r17 = r10;
                r15 = r1;
                r4 = r14.call(r20, r19, r18, r17, r16, r15, r14);
                r5 = r5 + 1;
                r4 = r9.length;
                if(r5 < r4) { _fun9486_ip = 245; continue _fun9486 }
case 288:
                r4 = r0.reverb;
                if(!r4) { _fun9486_ip = 445; continue _fun9486 }
case 300:
                r4 = r0.reverb;
                r9 = _closure1_slot8;
                r19 = r4.reverb_time;
                r17 = 10;
                r5 = '.reverb.reverb_time';
                r16 = r7 + r5;
                r20 = undefined;
                r18 = 0;
                r15 = r1;
                r5 = r9.call(r20, r19, r18, r17, r16, r15, r14);
                r19 = r4.dry_level;
                r10 = -96.3;
                r5 = '.reverb.dry_level';
                r16 = r7 + r5;
                r18 = r10;
                r17 = 0;
                r15 = r1;
                r5 = r9.call(r20, r19, r18, r17, r16, r15, r14);
                r19 = r4.wet_level;
                r5 = '.reverb.wet_level';
                r16 = r7 + r5;
                r15 = r1;
                r5 = r9.call(r20, r19, r18, r17, r16, r15, r14);
                r19 = r4.room_size;
                r8 = 1;
                r5 = '.reverb.room_size';
                r16 = r7 + r5;
                r18 = 0;
                r17 = r8;
                r15 = r1;
                r5 = r9.call(r20, r19, r18, r17, r16, r15, r14);
                r19 = r4.diffusion;
                r4 = '.reverb.diffusion';
                r16 = r7 + r4;
                r15 = r1;
                r4 = r9.call(r20, r19, r18, r17, r16, r15, r14);
case 445:
                r4 = r0.delay;
                if(!r4) { _fun9486_ip = 519; continue _fun9486 }
case 454:
                r4 = r0.delay;
                r9 = _closure1_slot8;
                r19 = r4.delay_time;
                r17 = 5000;
                r5 = '.delay.delay_time';
                r16 = r7 + r5;
                r20 = undefined;
                r18 = 0;
                r15 = r1;
                r5 = r9.call(r20, r19, r18, r17, r16, r15, r14);
                r19 = r4.feedback;
                r17 = 1;
                r4 = '.delay.feedback';
                r16 = r7 + r4;
                r15 = r1;
                r4 = r9.call(r20, r19, r18, r17, r16, r15, r14);
case 519:
                r4 = r0.stereo_enhance;
                if(!r4) { _fun9486_ip = 590; continue _fun9486 }
case 528:
                r0 = r0.stereo_enhance;
                r5 = _closure1_slot8;
                r19 = r0.stereo_width;
                r4 = 10;
                r2 = '.stereo_enhance.stereo_width';
                r16 = r7 + r2;
                r20 = undefined;
                r18 = 0;
                r17 = r4;
                r15 = r1;
                r2 = r5.call(r20, r19, r18, r17, r16, r15, r14);
                r19 = r0.stereo_depth;
                r0 = '.stereo_enhance.stereo_depth';
                r16 = r7 + r0;
                r15 = r1;
                r0 = r5.call(r20, r19, r18, r17, r16, r15, r14);
case 590:
                r2 = r1.length;
                r0 = {};
                r2 = r3 === r2;
                r0['valid'] = r2;
                r0['issues'] = r1;
                return r0;
            }
        };
        var _closure1_slot9 = r0;
        r0 = 0;
        r1 = r4[r0];
        r0 = undefined;
        r5 = r3.bind(r0)(r1);
        r1 = global;
        r9 = r1.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r1 = true;
        r7['value'] = r1;
        r1 = '__esModule';
        r1 = r8.bind(r9)(r6, r1, r7);
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9487: for(var _fun9487_ip = 0; ; ) switch(_fun9487_ip) {
case 0:
                r2 = a0;
                r32 = a1;
                r3 = undefined;
                r8 = true;
                r0 = new Array(0);
                r9 = null;
                if(!(r9 != r2)) { _fun9487_ip = 1528; continue _fun9487 }
case 25:
                r5 = 'object';
                r4 = typeof r2;
                if(!(r5 === r4)) { _fun9487_ip = 1528; continue _fun9487 }
case 39:
                r5 = _closure1_slot6;
                r4 = 'emotion_label';
                r4 = r5.bind(r3)(r2, r4);
                r29 = undefined;
                r28 = false;
                if(!r4) { _fun9487_ip = 220; continue _fun9487 }
case 66:
                r4 = r2.emotion_label;
                r5 = 'string';
                r4 = typeof r4;
                if(!(r5 === r4)) { _fun9487_ip = 189; continue _fun9487 }
case 83:
                r5 = r2.emotion_label;
                r4 = r5.trim;
                r7 = r4.bind(r5)();
                r5 = r7.length;
                r4 = 0;
                r29 = undefined;
                r28 = false;
                if(!(r4 !== r5)) { _fun9487_ip = 220; continue _fun9487 }
case 114:
                r4 = _closure1_slot3;
                r4 = r4.isEmotionLabel;
                r4 = r4.bind(r3)(r7);
                r29 = r7;
                r28 = false;
                if(r4) { _fun9487_ip = 220; continue _fun9487 }
case 137:
                r4 = _closure1_slot4;
                r4 = r4.mapEmotionTags;
                r29 = r4.bind(r3)(r7);
                r28 = true;
                if(r29) { _fun9487_ip = 220; continue _fun9487 }
case 157:
                r5 = r0.push;
                r4 = 'emotion_label 中文标签映射未命中: ';
                r7 = r4 + r7;
                r4 = '，将尝试 emotion_mapping';
                r4 = r7 + r4;
                r4 = r5.bind(r0)(r4);
                r29 = undefined;
                r28 = false;
                _fun9487_ip = 220; continue _fun9487;
case 189:
                r5 = r0.push;
                r4 = r2.emotion_label;
                r7 = 'emotion_label 必须为字符串，实际类型: ';
                r4 = typeof r4;
                r4 = r7 + r4;
                r4 = r5.bind(r0)(r4);
                r29 = undefined;
                r28 = false;
case 220:
                r7 = _closure1_slot7;
                r5 = {};
                r5['integer'] = r8;
                r38 = 'genre_id';
                r40 = undefined;
                r39 = r2;
                r37 = r0;
                r36 = r5;
                r27 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'dynamic_range';
                r36 = {'min': 0, 'max': 30};
                r39 = r2;
                r37 = r0;
                r24 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'loudness_lufs';
                r36 = {'min': -40, 'max': 0};
                r39 = r2;
                r37 = r0;
                r23 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'stereo_width_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r22 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'repbpm';
                r36 = {'min': 30, 'max': 300};
                r39 = r2;
                r37 = r0;
                r21 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'chorus_loudness';
                r36 = {'min': -40, 'max': 0};
                r39 = r2;
                r37 = r0;
                r20 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'vocal_main_freq';
                r36 = {'min': 0, 'max': 1200};
                r39 = r2;
                r37 = r0;
                r19 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'vocal_time_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r18 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'harmonic_intensity_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r17 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'vocal_dynamic_range';
                r36 = {'min': 0, 'max': 30};
                r39 = r2;
                r37 = r0;
                r16 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'drum_time_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r15 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'rhythm_complexity';
                r36 = {'min': 0, 'max': 10};
                r39 = r2;
                r37 = r0;
                r14 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'vocal_energy_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r13 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'drum_energy_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r12 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'bass_energy_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r11 = r7.call(r40, r39, r38, r37, r36, r35);
                r38 = 'other_instrument_energy_ratio';
                r36 = {'min': 0, 'max': 1};
                r39 = r2;
                r37 = r0;
                r10 = r7.call(r40, r39, r38, r37, r36, r35);
                r5 = _closure1_slot6;
                r4 = 'key_mode';
                r4 = r5.bind(r3)(r2, r4);
                r26 = undefined;
                if(!r4) { _fun9487_ip = 710; continue _fun9487 }
case 629:
                r4 = r2.key_mode;
                r5 = 'string';
                r4 = typeof r4;
                if(!(r5 === r4)) { _fun9487_ip = 691; continue _fun9487 }
case 646:
                r5 = r2.key_mode;
                r4 = r5.trim;
                r4 = r4.bind(r5)();
                r5 = r4.length;
                r4 = 0;
                if(!(r4 !== r5)) { _fun9487_ip = 691; continue _fun9487 }
case 673:
                r5 = r2.key_mode;
                r4 = r5.trim;
                r4 = r4.bind(r5)();
                _fun9487_ip = 707; continue _fun9487;
case 691:
                r7 = r0.push;
                r5 = 'key_mode 必须为非空字符串';
                r5 = r7.bind(r0)(r5);
                r4 = undefined;
case 707:
                r26 = r4;
case 710:
                r5 = _closure1_slot6;
                r4 = 'mel_spectrogram_32';
                r4 = r5.bind(r3)(r2, r4);
                r25 = undefined;
                r7 = false;
                if(!r4) { _fun9487_ip = 916; continue _fun9487 }
case 734:
                r4 = global;
                r30 = r4.Array;
                r5 = r30.isArray;
                r4 = r2.mel_spectrogram_32;
                r4 = r5.bind(r30)(r4);
                if(r4) { _fun9487_ip = 784; continue _fun9487 }
case 761:
                r5 = r0.push;
                r4 = 'mel_spectrogram_32 不是数组';
                r4 = r5.bind(r0)(r4);
                r5 = undefined;
                r4 = false;
                _fun9487_ip = 910; continue _fun9487;
case 784:
                r30 = r2.mel_spectrogram_32;
                r33 = r30.length;
                r30 = 32;
                if(!(r30 === r33)) { _fun9487_ip = 871; continue _fun9487 }
case 802:
                r34 = r2.mel_spectrogram_32;
                r33 = r34.some;
                r30 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    _fun9488: for(var _fun9488_ip = 0; ; ) switch(_fun9488_ip) {
case 0:
                        r3 = a0;
                        r1 = 'number';
                        r0 = typeof r3;
                        r0 = r1 !== r0;
                        if(r0) { _fun9488_ip = 39; continue _fun9488 }
case 17:
                        r1 = global;
                        r2 = r1.Number;
                        r1 = r2.isFinite;
                        r1 = r1.bind(r2)(r3);
                        r0 = !r1;
case 39:
                        return r0;
                    }
                };
                r30 = r33.bind(r34)(r30);
                if(r30) { _fun9487_ip = 851; continue _fun9487 }
case 827:
                r6 = _closure1_slot1;
                r30 = r6.default;
                r6 = r2.mel_spectrogram_32;
                r30 = r30.bind(r3)(r6);
                r6 = true;
                _fun9487_ip = 869; continue _fun9487;
case 851:
                r34 = r0.push;
                r33 = 'mel_spectrogram_32 数组中的元素必须全部为有限数值';
                r33 = r34.bind(r0)(r33);
                r30 = undefined;
                r6 = false;
case 869:
                _fun9487_ip = 904; continue _fun9487;
case 871:
                r34 = r0.push;
                r33 = r2.mel_spectrogram_32;
                r35 = r33.length;
                r33 = 'mel_spectrogram_32 数组长度必须为 32，当前为 ';
                r33 = r33 + r35;
                r33 = r34.bind(r0)(r33);
                r30 = undefined;
                r6 = false;
case 904:
                r5 = r30;
                r4 = r6;
case 910:
                r25 = r5;
                r7 = r4;
case 916:
                var _closure2_slot0 = r0;
                r0 = ['mel_spectrogram_32'];
                r0[1] = r7;
                r5 = new Array(16);
                r5[0] = r0;
                r4 = r9 != r24;
                r0 = ['dynamic_range'];
                r0[1] = r4;
                r5[1] = r0;
                r4 = r9 != r23;
                r0 = ['loudness_lufs'];
                r0[1] = r4;
                r5[2] = r0;
                r4 = r9 != r22;
                r0 = ['stereo_width_ratio'];
                r0[1] = r4;
                r5[3] = r0;
                r4 = r9 != r21;
                r0 = ['repbpm'];
                r0[1] = r4;
                r5[4] = r0;
                r4 = r9 != r20;
                r0 = ['chorus_loudness'];
                r0[1] = r4;
                r5[5] = r0;
                r4 = r9 != r19;
                r0 = ['vocal_main_freq'];
                r0[1] = r4;
                r5[6] = r0;
                r4 = r9 != r18;
                r0 = ['vocal_time_ratio'];
                r0[1] = r4;
                r5[7] = r0;
                r4 = r9 != r17;
                r0 = ['harmonic_intensity_ratio'];
                r0[1] = r4;
                r5[8] = r0;
                r4 = r9 != r16;
                r0 = ['vocal_dynamic_range'];
                r0[1] = r4;
                r5[9] = r0;
                r4 = r9 != r15;
                r0 = ['drum_time_ratio'];
                r0[1] = r4;
                r5[10] = r0;
                r4 = r9 != r14;
                r0 = ['rhythm_complexity'];
                r0[1] = r4;
                r5[11] = r0;
                r4 = r9 != r13;
                r0 = ['vocal_energy_ratio'];
                r0[1] = r4;
                r5[12] = r0;
                r4 = r9 != r12;
                r0 = ['drum_energy_ratio'];
                r0[1] = r4;
                r5[13] = r0;
                r4 = r9 != r11;
                r0 = ['bass_energy_ratio'];
                r0[1] = r4;
                r5[14] = r0;
                r4 = r9 != r10;
                r0 = ['other_instrument_energy_ratio'];
                r0[1] = r4;
                r5[15] = r0;
                r4 = r5.filter;
                r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r0 = _closure1_slot0;
                    r3 = r0.default;
                    r2 = undefined;
                    r1 = a0;
                    r0 = 2;
                    r1 = r3.bind(r2)(r1, r0);
                    r0 = 1;
                    r0 = r1[r0];
                    r0 = !r0;
                    return r0;
                };
                r5 = r4.bind(r5)(r0);
                r4 = r5.map;
                r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r0 = _closure1_slot0;
                    r3 = r0.default;
                    r2 = undefined;
                    r1 = a0;
                    r0 = 1;
                    r1 = r3.bind(r2)(r1, r0);
                    r0 = 0;
                    r0 = r1[r0];
                    return r0;
                };
                r5 = r4.bind(r5)(r0);
                r6 = r5.length;
                r4 = r5.map;
                r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    _fun9491: for(var _fun9491_ip = 0; ; ) switch(_fun9491_ip) {
case 0:
                        r2 = a0;
                        var _closure3_slot0 = r2;
                        r3 = _closure2_slot0;
                        r1 = r3.find;
                        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                            r2 = a0;
                            r1 = r2.startsWith;
                            r3 = _closure3_slot0;
                            r0 = ' ';
                            r0 = r3 + r0;
                            r0 = r1.bind(r2)(r0);
                            return r0;
                        };
                        r0 = r1.bind(r3)(r0);
                        r1 = null;
                        if(!(r1 == r0)) { _fun9491_ip = 46; continue _fun9491 }
case 38:
                        r1 = ': missing or null';
                        r0 = r2 + r1;
case 46:
                        return r0;
                    }
                };
                r4 = r4.bind(r5)(r0);
                r0 = r7;
                if(r0) { _fun9487_ip = 1300; continue _fun9487 }
case 1296:
                r0 = r9 != r21;
case 1300:
                r5 = _closure2_slot0;
                r30 = r5.length;
                r5 = 0;
                if(!(r30 > r5)) { _fun9487_ip = 1346; continue _fun9487 }
case 1315:
                if(!(r9 != r32)) { _fun9487_ip = 1346; continue _fun9487 }
case 1319:
                r31 = r32.warn;
                r9 = _closure2_slot0;
                r30 = {};
                r30['issues'] = r9;
                r9 = 'input:sanitized';
                r9 = r31.bind(r32)(r9, r30);
case 1346:
                if(r28) { _fun9487_ip = 1351; continue _fun9487 }
case 1349:
                r28 = undefined;
case 1351:
                r9 = r2.model_signature;
                r2 = r2.supportStereo;
                r3 = {};
                r3['emotion_label'] = r29;
                r3['emotionFromTagMapping'] = r28;
                r3['genre_id'] = r27;
                r3['key_mode'] = r26;
                r3['mel_spectrogram_32'] = r25;
                r3['dynamic_range'] = r24;
                r3['loudness_lufs'] = r23;
                r3['stereo_width_ratio'] = r22;
                r3['repbpm'] = r21;
                r3['chorus_loudness'] = r20;
                r3['vocal_main_freq'] = r19;
                r3['vocal_time_ratio'] = r18;
                r3['harmonic_intensity_ratio'] = r17;
                r3['vocal_dynamic_range'] = r16;
                r3['drum_time_ratio'] = r15;
                r3['rhythm_complexity'] = r14;
                r3['vocal_energy_ratio'] = r13;
                r3['drum_energy_ratio'] = r12;
                r3['bass_energy_ratio'] = r11;
                r3['other_instrument_energy_ratio'] = r10;
                r3['model_signature'] = r9;
                r2 = r8 === r2;
                r3['supportStereo'] = r2;
                r2 = _closure2_slot0;
                r1 = {};
                r1['hasMelSpectrogram'] = r7;
                r5 = r5 === r6;
                r1['hasAllAudioFeatures'] = r5;
                r1['missingAudioFeatures'] = r4;
                r1['hasEmotionMappingFeatures'] = r0;
                r0 = {};
                r0['sanitized'] = r3;
                r0['issues'] = r2;
                r0['availability'] = r1;
                return r0;
case 1528:
                r1 = {'hasMelSpectrogram': false, 'hasAllAudioFeatures': false, 'missingAudioFeatures': null, 'hasEmotionMappingFeatures': false};
                r0 = ['all (input is null or not an object)'];
                r1['missingAudioFeatures'] = r0;
                r0 = {};
                r2 = {};
                r0['sanitized'] = r2;
                r2 = ['input 为空或非对象'];
                r0['issues'] = r2;
                r0['availability'] = r1;
                return r0;
            }
        };
        r6['sanitizeInput'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9493: for(var _fun9493_ip = 0; ; ) switch(_fun9493_ip) {
case 0:
                r4 = a1;
                r3 = _closure1_slot9;
                r2 = undefined;
                r1 = a0;
                r0 = 'output.aev2';
                r0 = r3.bind(r2)(r1, r0);
                r1 = r0.valid;
                if(r1) { _fun9493_ip = 40; continue _fun9493 }
case 34:
                r2 = null;
                r1 = r2 == r4;
case 40:
                if(r1) { _fun9493_ip = 72; continue _fun9493 }
case 43:
                r3 = r4.warn;
                r1 = r0.issues;
                r2 = {};
                r2['issues'] = r1;
                r1 = 'output:range:exceeded';
                r1 = r3.bind(r4)(r1, r2);
case 72:
                return r0;
            }
        };
        r6['validateOutputRanges'] = r1;
        r1 = 1;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        r1 = r5.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        r1 = r5.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 3;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot2 = r1;
        r1 = 4;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot3 = r1;
        r1 = 5;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot4 = r1;
        r1 = 6;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot5 = r1;
        return r0;
    };
factories[960]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a5;
        r0 = global;
        r6 = r0.Object;
        r5 = r6.defineProperty;
        r4 = {};
        r2 = true;
        r4['value'] = r2;
        r2 = '__esModule';
        r2 = r5.bind(r6)(r3, r2, r4);
        r2 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            _fun9495: for(var _fun9495_ip = 0; ; ) switch(_fun9495_ip) {
case 0:
                r6 = a0;
                r0 = undefined;
                r7 = undefined;
                r8 = undefined;
                r4 = undefined;
                r5 = undefined;
                if(r6) { _fun9495_ip = 20; continue _fun9495 }
case 18:
                return r0;
case 20:
                r3 = r6.split;
                r2 = ',';
                r6 = r3.bind(r6)(r2);
                r3 = r6.map;
                r2 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r1 = a0;
                    r0 = r1.trim;
                    r0 = r0.bind(r1)();
                    return r0;
                };
                r6 = r3.bind(r6)(r2);
                r3 = r6.filter;
                r2 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r0 = a0;
                    r1 = r0.length;
                    r0 = 0;
                    r0 = r1 > r0;
                    return r0;
                };
                r6 = r3.bind(r6)(r2);
                r3 = r6.length;
                r2 = 0;
                if(!(r2 !== r3)) { _fun9495_ip = 301; continue _fun9495 }
case 78:
                r2 = r6.every;
                r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r2 = _closure1_slot1;
                    r1 = r2.has;
                    r0 = a0;
                    r0 = r1.bind(r2)(r0);
                    return r0;
                };
                r1 = r2.bind(r6)(r1);
                if(r1) { _fun9495_ip = 299; continue _fun9495 }
case 100:
                r1 = r6;
                var __iterator_values_9=Array.from(r1), __iterator_index_9=0; r2={return:function(){}}; r1=function(){if(__iterator_index_9>=__iterator_values_9.length){r2=undefined;return {value:undefined}}return {value:__iterator_values_9[__iterator_index_9++]}};
case 109:
                r11 = r1().value;
                r9 = r2;
                if(!(r9 !== r0)) { _fun9495_ip = 200; continue _fun9495 }
case 120: // try_start_0
                r7 = r11;
                r10 = _closure1_slot0;
                r9 = r10.has;
                r9 = r9.bind(r10)(r11);
                if(r9) { _fun9495_ip = 191; continue _fun9495 }
case 140:
                r11 = _closure1_slot1;
                r10 = r11.has;
                r9 = r7;
                r9 = r10.bind(r11)(r9);
                if(r9) { _fun9495_ip = 191; continue _fun9495 }
case 160:
                r11 = _closure1_slot2;
                r10 = r11.get;
                r9 = r7;
                r9 = r10.bind(r11)(r9);
                r8 = r9;
                if(!r9) { _fun9495_ip = 191; continue _fun9495 }
case 183:
                r9 = r8;
case 186: // try_end0
                r2.return();
                return r9;
case 191:
                _fun9495_ip = 109; continue _fun9495;
case 193: // catch_target0
                CatchBlockStart(arg_register=1);
                r2.return();
                throw r1;
case 200:
                r1 = r6;
                var __iterator_values_10=Array.from(r1), __iterator_index_10=0; r2={return:function(){}}; r1=function(){if(__iterator_index_10>=__iterator_values_10.length){r2=undefined;return {value:undefined}}return {value:__iterator_values_10[__iterator_index_10++]}};
case 206:
                r8 = r1().value;
                r6 = r2;
                if(!(r6 !== r0)) { _fun9495_ip = 297; continue _fun9495 }
case 217: // try_start_1
                r4 = r8;
                r7 = _closure1_slot0;
                r6 = r7.has;
                r6 = r6.bind(r7)(r8);
                if(r6) { _fun9495_ip = 288; continue _fun9495 }
case 237:
                r8 = _closure1_slot1;
                r7 = r8.has;
                r6 = r4;
                r6 = r7.bind(r8)(r6);
                if(r6) { _fun9495_ip = 288; continue _fun9495 }
case 257:
                r8 = _closure1_slot3;
                r7 = r8.get;
                r6 = r4;
                r6 = r7.bind(r8)(r6);
                r5 = r6;
                if(!r6) { _fun9495_ip = 288; continue _fun9495 }
case 280:
                r6 = r5;
case 283: // try_end1
                r2.return();
                return r6;
case 288:
                _fun9495_ip = 206; continue _fun9495;
case 290: // catch_target1
                CatchBlockStart(arg_register=1);
                r2.return();
                throw r1;
case 297:
                return r0;
case 299:
                return r0;
case 301:
                return r0;
            }
        };
        r3['mapEmotionTags'] = r2;
        r4 = r0.Set;
        r2 = r4.prototype;
        r3 = Object.create(r2, {constructor: {value: r4}});
        r9 = ['正向情绪', '中性情绪', '负面情绪', '负向情绪', '伤感情绪'];
        r10 = r3;
        r2 = originalConstruct(r4, [r9, r8]);
        r2 = r2 instanceof Object ? r2 : r3;
        var _closure1_slot0 = r2;
        r4 = r0.Set;
        r2 = r4.prototype;
        r3 = Object.create(r2, {constructor: {value: r4}});
        r9 = ['无合适业务标签'];
        r10 = r3;
        r2 = originalConstruct(r4, [r9, r8]);
        r2 = r2 instanceof Object ? r2 : r3;
        var _closure1_slot1 = r2;
        r4 = r0.Map;
        r2 = new Array(15);
        r3 = ['狂欢', 'ecstatic'];
        r2[0] = r3;
        r3 = ['热血励志', 'passionate'];
        r2[1] = r3;
        r3 = ['活力', 'energetic'];
        r2[2] = r3;
        r3 = ['欢快', 'joyful'];
        r2[3] = r3;
        r3 = ['甜蜜', 'sweet'];
        r2[4] = r3;
        r3 = ['自信', 'confident'];
        r2[5] = r3;
        r3 = ['浪漫', 'romantic'];
        r2[6] = r3;
        r3 = ['放松', 'relaxed'];
        r2[7] = r3;
        r3 = ['治愈', 'healing'];
        r2[8] = r3;
        r3 = ['平静', 'calm'];
        r2[9] = r3;
        r3 = ['思念', 'nostalgic'];
        r2[10] = r3;
        r3 = ['孤独', 'lonely'];
        r2[11] = r3;
        r3 = ['悲伤', 'sad'];
        r2[12] = r3;
        r3 = ['苦情', 'bitter'];
        r2[13] = r3;
        r3 = ['抑郁', 'depressed'];
        r2[14] = r3;
        r3 = r4.prototype;
        r3 = Object.create(r3, {constructor: {value: r4}});
        r10 = r3;
        r9 = r2;
        r2 = originalConstruct(r4, [r9, r8]);
        r2 = r2 instanceof Object ? r2 : r3;
        var _closure1_slot2 = r2;
        r3 = r0.Map;
        r0 = new Array(39);
        r2 = ['动感', 'energetic'];
        r0[0] = r2;
        r2 = ['高燃', 'passionate'];
        r0[1] = r2;
        r2 = ['气势汹汹', 'passionate'];
        r0[2] = r2;
        r2 = ['侵略性的', 'passionate'];
        r0[3] = r2;
        r2 = ['紧张', 'passionate'];
        r0[4] = r2;
        r2 = ['愤怒', 'passionate'];
        r0[5] = r2;
        r2 = ['大气', 'passionate'];
        r0[6] = r2;
        r2 = ['快乐', 'joyful'];
        r0[7] = r2;
        r2 = ['幸福', 'joyful'];
        r0[8] = r2;
        r2 = ['轻快', 'joyful'];
        r0[9] = r2;
        r2 = ['清新', 'joyful'];
        r0[10] = r2;
        r2 = ['滑稽', 'joyful'];
        r0[11] = r2;
        r2 = ['阳光', 'joyful'];
        r0[12] = r2;
        r2 = ['赞美', 'joyful'];
        r0[13] = r2;
        r2 = ['抒情', 'romantic'];
        r0[14] = r2;
        r2 = ['情歌', 'romantic'];
        r0[15] = r2;
        r2 = ['调情', 'romantic'];
        r0[16] = r2;
        r2 = ['爱情', 'romantic'];
        r0[17] = r2;
        r2 = ['风流', 'romantic'];
        r0[18] = r2;
        r2 = ['可爱', 'sweet'];
        r0[19] = r2;
        r2 = ['友情', 'sweet'];
        r0[20] = r2;
        r2 = ['亲情', 'sweet'];
        r0[21] = r2;
        r2 = ['感恩', 'sweet'];
        r0[22] = r2;
        r2 = ['温柔', 'sweet'];
        r0[23] = r2;
        r2 = ['正气', 'confident'];
        r0[24] = r2;
        r2 = ['正直', 'confident'];
        r0[25] = r2;
        r2 = ['洒脱', 'confident'];
        r0[26] = r2;
        r2 = ['自由', 'confident'];
        r0[27] = r2;
        r2 = ['庄严', 'confident'];
        r0[28] = r2;
        r2 = ['自豪', 'confident'];
        r0[29] = r2;
        r2 = ['慵懒', 'relaxed'];
        r0[30] = r2;
        r2 = ['平和', 'calm'];
        r0[31] = r2;
        r2 = ['梦幻', 'healing'];
        r0[32] = r2;
        r2 = ['感人', 'healing'];
        r0[33] = r2;
        r2 = ['憧憬', 'nostalgic'];
        r0[34] = r2;
        r2 = ['遗憾', 'nostalgic'];
        r0[35] = r2;
        r2 = ['黑暗', 'depressed'];
        r0[36] = r2;
        r2 = ['惊悚', 'depressed'];
        r0[37] = r2;
        r2 = ['烦躁', 'bitter'];
        r0[38] = r2;
        r2 = r3.prototype;
        r2 = Object.create(r2, {constructor: {value: r3}});
        r10 = r2;
        r9 = r0;
        r0 = originalConstruct(r3, [r9, r8]);
        r0 = r0 instanceof Object ? r0 : r2;
        var _closure1_slot3 = r0;
        r0 = undefined;
        return r0;
    };
factories[961]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a5;
        r0 = global;
        r5 = r0.Object;
        r4 = r5.defineProperty;
        r1 = {};
        r0 = true;
        r1['value'] = r0;
        r0 = '__esModule';
        r0 = r4.bind(r5)(r3, r0, r1);
        r0 = undefined;
        r3['GENRE_TAG_ID_TO_FAMILY'] = r0;
        r3['GENRE_TAG_ID_TO_NAME'] = r0;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9500: for(var _fun9500_ip = 0; ; ) switch(_fun9500_ip) {
case 0:
                r2 = _closure1_slot1;
                r0 = a0;
                r0 = r2[r0];
                r2 = null;
                if(!(r2 == r0)) { _fun9500_ip = 30; continue _fun9500 }
case 20:
                r1 = _closure1_slot0;
                r0 = r1.DEFAULT_FAMILY;
case 30:
                return r0;
            }
        };
        r3['getFamily'] = r1;
        r1 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9501: for(var _fun9501_ip = 0; ; ) switch(_fun9501_ip) {
case 0:
                r0 = '未知曲风';
                r2 = _closure1_slot2;
                r1 = a0;
                r1 = r2[r1];
                r2 = null;
                if(!(r2 != r1)) { _fun9501_ip = 27; continue _fun9501 }
case 24:
                r0 = r1;
case 27:
                return r0;
            }
        };
        r3['getGenreName'] = r1;
        r4 = a6;
        r1 = 0;
        r4 = r4[r1];
        r1 = a1;
        r1 = r1.bind(r0)(r4);
        var _closure1_slot0 = r1;
        r1 = {1000: 'pop-family', 1028: 'pop-family', 1010: 'pop-family', 1252: 'pop-family', 1005: 'pop-family', 1029: 'pop-family', 1044: 'pop-family', 1068: 'pop-family', 1138: 'pop-family', 1262: 'pop-family', 1439: 'pop-family', 79088: 'pop-family', 1003: 'folk-family', 1026: 'folk-family', 10119: 'folk-family', 1001: 'folk-family', 1077: 'folk-family', 1037: 'folk-family', 1012: 'folk-family', 1263: 'folk-family', 1071: 'folk-family', 1043: 'folk-family', 1017: 'rhythm-family', 1015: 'rhythm-family', 1008: 'rhythm-family', 1014: 'rhythm-family', 1041: 'rhythm-family', 152124: 'rhythm-family', 1049: 'rhythm-family', 1022: 'instrumental-family', 1007: 'instrumental-family', 1011: 'instrumental-family', 1006: 'instrumental-family', 1060683: 'instrumental-family'};
        r3['GENRE_TAG_ID_TO_FAMILY'] = r1;
        var _closure1_slot1 = r1;
        r1 = {1000: '流行', 1001: '中国传统特色', 1003: '民谣', 1005: '其他', 1006: '轻音乐', 1007: '新世纪', 1008: '摇滚', 1010: '原声带', 1011: '爵士', 1012: '世界音乐', 1014: '另类/独立', 1015: '电子', 1017: '嘻哈说唱', 1022: '古典', 1026: '儿童', 1028: 'R&B', 1029: '放克', 1037: '蓝调', 1041: '金属', 1043: '雷鬼', 1044: '宗教', 1049: '实验', 1060683: '纯演奏音乐', 1068: '舞台', 1071: '拉丁音乐', 1077: '乡村', 1138: '有声音频', 1252: '二次元', 1262: '节日音乐', 1263: '世界乐器体裁', 1439: '唱作人', 10119: '国风', 79088: '慢摇DJ', 152124: '朋克'};
        r3['GENRE_TAG_ID_TO_NAME'] = r1;
        var _closure1_slot2 = r1;
        return r0;
    };
factories[962]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r2 = a5;
        r0 = global;
        r4 = r0.Object;
        r3 = r4.defineProperty;
        r1 = {};
        r0 = true;
        r1['value'] = r0;
        r0 = '__esModule';
        r0 = r3.bind(r4)(r2, r0, r1);
        r0 = undefined;
        r2['DEFAULT_EMOTION'] = r0;
        r2['DEFAULT_FAMILY'] = r0;
        r1 = 'default';
        r2['DEFAULT_EMOTION'] = r1;
        r1 = 'pop-family';
        r2['DEFAULT_FAMILY'] = r1;
        return r0;
    };
factories[963]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r3 = a1;
        r1 = a5;
        r4 = a6;
        r5 = global;
        r9 = r5.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r0 = true;
        r7['value'] = r0;
        r6 = '__esModule';
        r6 = r8.bind(r9)(r1, r6, r7);
        r9 = r5.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r7['enumerable'] = r0;
        r6 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: get, environment: r2
            r0 = _closure1_slot0;
            r0 = r0.V1FourFactorStrategy;
            return r0;
        };
        r7['get'] = r6;
        r6 = 'V1FourFactorStrategy';
        r6 = r8.bind(r9)(r1, r6, r7);
        r7 = r5.Object;
        r6 = r7.defineProperty;
        r5 = {};
        r5['enumerable'] = r0;
        r0 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: get, environment: r2
            r0 = _closure1_slot1;
            r0 = r0.VisualExtraStrategy;
            return r0;
        };
        r5['get'] = r0;
        r0 = 'VisualExtraStrategy';
        r0 = r6.bind(r7)(r1, r0, r5);
        r0 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            r0 = _closure1_slot0;
            r0 = r0.V1FourFactorStrategy;
            r1 = r0.prototype;
            r1 = Object.create(r1, {constructor: {value: r0}});
            r2 = r1;
            r0 = originalConstruct(r0, [r1]);
            r0 = r0 instanceof Object ? r0 : r1;
            return r0;
        };
        r1['getVisualExtraStrategy'] = r0;
        r0 = 0;
        r1 = r4[r0];
        r0 = undefined;
        r1 = r3.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 1;
        r1 = r4[r1];
        r1 = r3.bind(r0)(r1);
        var _closure1_slot1 = r1;
        return r0;
    };
factories[964]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r4 = a1;
        r2 = a5;
        r5 = a6;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: v, environment: r1
            r0 = global;
            r3 = r0.Math;
            r2 = r3.max;
            r5 = r0.Math;
            r4 = r5.min;
            r1 = 1;
            r0 = a0;
            r1 = r4.bind(r5)(r1, r0);
            r0 = 0;
            r0 = r2.bind(r3)(r0, r1);
            return r0;
        };
        var _closure1_slot10 = r0;
        r0 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: y, environment: r1
            _fun9509: for(var _fun9509_ip = 0; ; ) switch(_fun9509_ip) {
case 0:
                r5 = a1;
                r1 = a2;
                r3 = _closure1_slot6;
                r4 = r3.floatEqual;
                r3 = undefined;
                r4 = r4.bind(r3)(r1, r5);
                r0 = 0;
                if(r4) { _fun9509_ip = 56; continue _fun9509 }
case 32:
                r2 = _closure1_slot10;
                r4 = a0;
                r4 = r4 - r5;
                r1 = r1 - r5;
                r1 = r4 / r1;
                r0 = r2.bind(r3)(r1);
case 56:
                return r0;
            }
        };
        var _closure1_slot11 = r0;
        r0 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: d, environment: r1
            _fun9510: for(var _fun9510_ip = 0; ; ) switch(_fun9510_ip) {
case 0:
                r0 = a2;
                r7 = undefined;
                r2 = null;
                r6 = 'object';
                r5 = undefined;
                r1 = a0;
                r3 = a1;
                var __iterator_values_11=Array.from(r3), __iterator_index_11=0; r4={return:function(){}}; r3=function(){if(__iterator_index_11>=__iterator_values_11.length){r4=undefined;return {value:undefined}}return {value:__iterator_values_11[__iterator_index_11++]}};
case 22:
                r8 = r3().value;
                r9 = r4;
                if(!(r9 !== r7)) { _fun9510_ip = 77; continue _fun9510 }
case 33: // try_start_0
                r5 = r8;
                r8 = r1;
                if(!(r2 != r8)) { _fun9510_ip = 65; continue _fun9510 }
case 43:
                r8 = r1;
                r8 = typeof r8;
                if(!(r6 === r8)) { _fun9510_ip = 65; continue _fun9510 }
case 53:
                r9 = r1;
                r8 = r5;
                r1 = r9[r8];
case 63: // try_end0
                _fun9510_ip = 22; continue _fun9510;
case 65:
                r4.return();
                return r0;
case 70: // catch_target0
                CatchBlockStart(arg_register=3);
                r4.return();
                throw r3;
case 77:
                if(!(r2 != r1)) { _fun9510_ip = 84; continue _fun9510 }
case 81:
                r0 = r1;
case 84:
                return r0;
            }
        };
        var _closure1_slot12 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: S, environment: r1
            r4 = _closure1_slot12;
            r3 = new Array(2);
            r0 = a1;
            r3[0] = r0;
            r0 = 'on';
            r3[1] = r0;
            r2 = undefined;
            r1 = a0;
            r0 = false;
            r0 = r4.bind(r2)(r1, r3, r0);
            r0 = !r0;
            r0 = !r0;
            return r0;
        };
        var _closure1_slot13 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: I, environment: r1
            r1 = a0;
            var _closure2_slot0 = r1;
            r1 = _closure1_slot7;
            r3 = r1.VISUAL_DIMENSIONS;
            r2 = r3.reduce;
            r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                r0 = a0;
                r2 = a1;
                r4 = _closure1_slot9;
                r3 = r4[r2];
                r1 = _closure2_slot0;
                r1 = r3.bind(r4)(r1);
                r0[r2] = r1;
                return r0;
            };
            r0 = {};
            r0 = r2.bind(r3)(r1, r0);
            return r0;
        };
        var _closure1_slot14 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: A, environment: r1
            _fun9514: for(var _fun9514_ip = 0; ; ) switch(_fun9514_ip) {
case 0:
                r2 = a0;
                r33 = 120;
                r32 = -14;
                r31 = 15;
                r30 = 0.5;
                r20 = 0;
                r19 = undefined;
                r18 = 'high_freq';
                r17 = 2;
                r16 = 1;
                r15 = null;
                r14 = -70;
                r13 = 'loudness';
                r1 = undefined;
                r5 = undefined;
                r6 = undefined;
                r7 = undefined;
                r8 = undefined;
                r9 = undefined;
                r10 = undefined;
                r11 = undefined;
                r12 = undefined;
                if(r2) { _fun9514_ip = 110; continue _fun9514 }
case 74:
                r3 = global;
                r22 = r3.Object;
                r21 = r22.assign;
                r3 = _closure1_slot7;
                r4 = r3.FALLBACK_SONG_ACTIVATIONS;
                r3 = {};
                r3 = r21.bind(r22)(r3, r4);
                return r3;
case 110:
                r3 = r2.mel_spectrogram_32;
                r4 = r3.length;
                r3 = 32;
                if(!(r3 === r4)) { _fun9514_ip = 770; continue _fun9514 }
case 131:
                r3 = r2.repbpm;
                if(!(r15 != r3)) { _fun9514_ip = 144; continue _fun9514 }
case 141:
                r33 = r3;
case 144:
                r3 = r2.loudness_lufs;
                if(!(r15 != r3)) { _fun9514_ip = 157; continue _fun9514 }
case 154:
                r32 = r3;
case 157:
                r3 = r2.dynamic_range;
                if(!(r15 != r3)) { _fun9514_ip = 170; continue _fun9514 }
case 167:
                r31 = r3;
case 170:
                r3 = r2.stereo_width_ratio;
                if(!(r15 != r3)) { _fun9514_ip = 183; continue _fun9514 }
case 180:
                r30 = r3;
case 183:
                r3 = r2.vocal_main_freq;
                r29 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 198; continue _fun9514 }
case 195:
                r29 = r3;
case 198:
                r3 = r2.vocal_time_ratio;
                r28 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 213; continue _fun9514 }
case 210:
                r28 = r3;
case 213:
                r3 = r2.harmonic_intensity_ratio;
                r27 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 228; continue _fun9514 }
case 225:
                r27 = r3;
case 228:
                r3 = r2.vocal_dynamic_range;
                r26 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 243; continue _fun9514 }
case 240:
                r26 = r3;
case 243:
                r3 = r2.drum_time_ratio;
                r25 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 258; continue _fun9514 }
case 255:
                r25 = r3;
case 258:
                r3 = r2.rhythm_complexity;
                r24 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 273; continue _fun9514 }
case 270:
                r24 = r3;
case 273:
                r3 = r2.vocal_energy_ratio;
                r23 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 288; continue _fun9514 }
case 285:
                r23 = r3;
case 288:
                r3 = r2.drum_energy_ratio;
                r22 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 303; continue _fun9514 }
case 300:
                r22 = r3;
case 303:
                r3 = r2.bass_energy_ratio;
                r21 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 318; continue _fun9514 }
case 315:
                r21 = r3;
case 318:
                r3 = r2.other_instrument_energy_ratio;
                r4 = 0;
                if(!(r15 != r3)) { _fun9514_ip = 333; continue _fun9514 }
case 330:
                r4 = r3;
case 333:
                r3 = {};
                r3['repbpm'] = r33;
                r3['loudness_lufs'] = r32;
                r3['dynamic_range'] = r31;
                r3['stereo_width_ratio'] = r30;
                r3['vocal_main_freq'] = r29;
                r3['vocal_time_ratio'] = r28;
                r3['harmonic_intensity_ratio'] = r27;
                r3['vocal_dynamic_range'] = r26;
                r3['drum_time_ratio'] = r25;
                r3['rhythm_complexity'] = r24;
                r3['vocal_energy_ratio'] = r23;
                r3['drum_energy_ratio'] = r22;
                r3['bass_energy_ratio'] = r21;
                r3['other_instrument_energy_ratio'] = r4;
                var _closure2_slot0 = r3;
                r4 = r2.mel_spectrogram_32;
                r3 = r4.forEach;
                r2 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    r2 = _closure2_slot0;
                    r1 = 'mel_';
                    r0 = a1;
                    r1 = r1 + r0;
                    r0 = a0;
                    r2[r1] = r0;
                    r0 = undefined;
                    return r0;
                };
                r2 = r3.bind(r4)(r2);
                r2 = _closure1_slot7;
                r22 = r2.VISUAL_DIMENSIONS;
                r21 = r22.reduce;
                r3 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    r0 = a0;
                    r2 = 0;
                    r1 = a1;
                    r0[r1] = r2;
                    return r0;
                };
                r2 = {};
                r1 = r21.bind(r22)(r3, r2);
                r2 = _closure1_slot7;
                r21 = r2.VISUAL_DIMENSIONS;
                r2 = r21;
                var __iterator_values_12=Array.from(r2), __iterator_index_12=0; r3={return:function(){}}; r2=function(){if(__iterator_index_12>=__iterator_values_12.length){r3=undefined;return {value:undefined}}return {value:__iterator_values_12[__iterator_index_12++]}};
case 478:
                r22 = r2().value;
                r21 = r3;
                if(!(r21 !== r19)) { _fun9514_ip = 768; continue _fun9514 }
case 492: // try_start_1
                r5 = r22;
                r6 = 0;
                r21 = _closure1_slot7;
                r21 = r21.SONG_FEATURE_WEIGHTS;
                r23 = r21[r22];
                r21 = r23;
                var __iterator_values_13=Array.from(r21), __iterator_index_13=0; r22={return:function(){}}; r21=function(){if(__iterator_index_13>=__iterator_values_13.length){r22=undefined;return {value:undefined}}return {value:__iterator_values_13[__iterator_index_13++]}};
case 517:
                r24 = r21().value;
                r23 = r22;
                if(!(r23 !== r19)) { _fun9514_ip = 684; continue _fun9514 }
case 531: // try_start_0
                r23 = _closure1_slot3;
                r23 = r23.default;
                r23 = r23.bind(r19)(r24, r17);
                r24 = r23[r20];
                r9 = r24;
                r10 = r23[r16];
                r23 = _closure1_slot7;
                r23 = r23.SONG_FEATURE_RANGES;
                r23 = r23[r24];
                r7 = r23;
                if(!(r15 == r23)) { _fun9514_ip = 592; continue _fun9514 }
case 578:
                r24 = new Array(2);
                r24[0] = r14;
                r24[1] = r20;
                _fun9514_ip = 595; continue _fun9514;
case 592:
                r24 = r7;
case 595:
                r23 = _closure1_slot3;
                r23 = r23.default;
                r23 = r23.bind(r19)(r24, r17);
                r11 = r23[r20];
                r12 = r23[r16];
                r24 = r6;
                r25 = r10;
                r28 = _closure1_slot11;
                r26 = _closure2_slot0;
                r23 = r9;
                r23 = r26[r23];
                r8 = r23;
                r27 = 0;
                if(!(r15 != r23)) { _fun9514_ip = 651; continue _fun9514 }
case 648:
                r27 = r8;
case 651:
                r26 = r11;
                r23 = r12;
                r23 = r28.bind(r19)(r27, r26, r23);
                r23 = r25 * r23;
                r6 = r24 + r23;
case 672: // try_end0
                _fun9514_ip = 517; continue _fun9514;
case 677: // catch_target0
                CatchBlockStart(arg_register=21);
                r22.return();
                throw r21;
case 684:
                r21 = r5;
                if(!(r18 !== r21)) { _fun9514_ip = 717; continue _fun9514 }
case 691:
                r21 = r5;
                if(!(r13 === r21)) { _fun9514_ip = 734; continue _fun9514 }
case 698:
                r22 = r6;
                r21 = _closure1_slot7;
                r21 = r21.LOUDNESS_SONG_ACT_COMPENSATION;
                r6 = r22 * r21;
                _fun9514_ip = 734; continue _fun9514;
case 717:
                r22 = r6;
                r21 = _closure1_slot7;
                r21 = r21.HIGH_FREQ_SONG_ACT_COMPENSATION;
                r6 = r22 * r21;
case 734:
                r23 = r1;
                r22 = r5;
                r24 = _closure1_slot10;
                r21 = r6;
                r21 = r24.bind(r19)(r21);
                r23[r22] = r21;
case 756: // try_end1
                _fun9514_ip = 478; continue _fun9514;
case 761: // catch_target1
                CatchBlockStart(arg_register=2);
                r3.return();
                throw r2;
case 768:
                return r1;
case 770:
                r1 = _closure1_slot7;
                r3 = r1.VISUAL_DIMENSIONS;
                r2 = r3.reduce;
                r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    r0 = a0;
                    r2 = 0;
                    r1 = a1;
                    r0[r1] = r2;
                    return r0;
                };
                r0 = {};
                r0 = r2.bind(r3)(r1, r0);
                return r0;
            }
        };
        var _closure1_slot15 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: b, environment: r1
            _fun9518: for(var _fun9518_ip = 0; ; ) switch(_fun9518_ip) {
case 0:
                r1 = a0;
                r2 = global;
                r4 = r2.Array;
                r3 = r4.isArray;
                r2 = r1.mel_spectrogram_32;
                r2 = r3.bind(r4)(r2);
                r0 = null;
                if(!r2) { _fun9518_ip = 205; continue _fun9518 }
case 35:
                r16 = r1.mel_spectrogram_32;
                r15 = r1.repbpm;
                r14 = r1.loudness_lufs;
                r13 = r1.dynamic_range;
                r12 = r1.stereo_width_ratio;
                r11 = r1.vocal_main_freq;
                r10 = r1.vocal_time_ratio;
                r9 = r1.harmonic_intensity_ratio;
                r8 = r1.vocal_dynamic_range;
                r7 = r1.drum_time_ratio;
                r6 = r1.rhythm_complexity;
                r5 = r1.vocal_energy_ratio;
                r4 = r1.drum_energy_ratio;
                r3 = r1.bass_energy_ratio;
                r2 = r1.other_instrument_energy_ratio;
                r1 = {};
                r1['mel_spectrogram_32'] = r16;
                r1['repbpm'] = r15;
                r1['loudness_lufs'] = r14;
                r1['dynamic_range'] = r13;
                r1['stereo_width_ratio'] = r12;
                r1['vocal_main_freq'] = r11;
                r1['vocal_time_ratio'] = r10;
                r1['harmonic_intensity_ratio'] = r9;
                r1['vocal_dynamic_range'] = r8;
                r1['drum_time_ratio'] = r7;
                r1['rhythm_complexity'] = r6;
                r1['vocal_energy_ratio'] = r5;
                r1['drum_energy_ratio'] = r4;
                r1['bass_energy_ratio'] = r3;
                r1['other_instrument_energy_ratio'] = r2;
                r0 = r1;
case 205:
                return r0;
            }
        };
        var _closure1_slot16 = r0;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: E, environment: r1
            r1 = a0;
            var _closure2_slot0 = r1;
            r1 = _closure1_slot7;
            r2 = r1.VISUAL_DIMENSIONS;
            r1 = r2.every;
            r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                r2 = a0;
                r0 = _closure2_slot0;
                r1 = r0[r2];
                r0 = _closure1_slot7;
                r0 = r0.FALLBACK_SONG_ACTIVATIONS;
                r0 = r0[r2];
                r0 = r1 === r0;
                return r0;
            };
            r0 = r1.bind(r2)(r0);
            return r0;
        };
        var _closure1_slot17 = r0;
        r0 = function(a0, a1, a2, a3, a4, a5, a6, a7) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: N, environment: r1
            _fun9521: for(var _fun9521_ip = 0; ; ) switch(_fun9521_ip) {
case 0:
                r10 = a0;
                r16 = a3;
                r7 = undefined;
                r9 = null;
                r4 = 0;
                r3 = 1;
                r1 = undefined;
                r5 = undefined;
                r11 = undefined;
                r12 = undefined;
                r13 = undefined;
                r14 = undefined;
                r15 = undefined;
                r2 = a2;
                var _closure2_slot8 = r2;
                var _closure2_slot9 = r16;
                r2 = 0.25;
                var _closure2_slot10 = r2;
                r6 = 0.15;
                var _closure2_slot11 = r6;
                r6 = 0.35;
                var _closure2_slot12 = r6;
                var _closure2_slot13 = r2;
                r6 = _closure1_slot7;
                r6 = r6.EMOTION_BASELINES;
                r6 = r6[r10];
                var _closure2_slot0 = r6;
                r6 = _closure1_slot7;
                r8 = r6.GENRE_BASELINES;
                r6 = a1;
                r6 = r8[r6];
                var _closure2_slot1 = r6;
                r6 = _closure1_slot7;
                r18 = r6.VISUAL_DIMENSIONS;
                r17 = r18.reduce;
                r8 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    _fun9522: for(var _fun9522_ip = 0; ; ) switch(_fun9522_ip) {
case 0:
                        r0 = a0;
                        r2 = a1;
                        r1 = _closure2_slot8;
                        r4 = r1[r2];
                        r5 = _closure2_slot13;
                        r1 = _closure2_slot9;
                        r1 = r1[r2];
                        r5 = r5 * r1;
                        r1 = 1;
                        r1 = r1 + r5;
                        r5 = r4 * r1;
                        r4 = _closure2_slot10;
                        r8 = _closure2_slot0;
                        r7 = null;
                        r1 = undefined;
                        if(!(r7 != r8)) { _fun9522_ip = 68; continue _fun9522 }
case 60:
                        r8 = _closure2_slot0;
                        r1 = r8[r2];
case 68:
                        if(!(r7 == r1)) { _fun9522_ip = 79; continue _fun9522 }
case 72:
                        r1 = _closure1_slot8;
case 79:
                        r4 = r4 * r1;
                        r1 = _closure2_slot11;
                        r8 = _closure2_slot1;
                        r6 = undefined;
                        if(!(r7 != r8)) { _fun9522_ip = 105; continue _fun9522 }
case 97:
                        r8 = _closure2_slot1;
                        r6 = r8[r2];
case 105:
                        if(!(r7 == r6)) { _fun9522_ip = 116; continue _fun9522 }
case 109:
                        r6 = _closure1_slot8;
case 116:
                        r1 = r1 * r6;
                        r3 = _closure2_slot12;
                        r3 = r3 * r5;
                        r1 = r4 + r1;
                        r1 = r1 + r3;
                        r0[r2] = r1;
                        return r0;
                    }
                };
                r6 = {};
                r6 = r17.bind(r18)(r8, r6);
                var _closure2_slot2 = r6;
                r6 = _closure1_slot17;
                r6 = r6.bind(r7)(r16);
                var _closure2_slot3 = r6;
                r6 = _closure1_slot2;
                r8 = r6.default;
                r6 = _closure1_slot7;
                r6 = r6.VISUAL_DIMENSIONS;
                r17 = r8.bind(r7)(r6);
                r8 = r17.sort;
                r6 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    _fun9523: for(var _fun9523_ip = 0; ; ) switch(_fun9523_ip) {
case 0:
                        r4 = a0;
                        r3 = a1;
                        r0 = _closure2_slot3;
                        r6 = _closure2_slot2;
                        r8 = r6[r4];
                        if(r0) { _fun9523_ip = 96; continue _fun9523 }
case 24:
                        r7 = null;
                        r2 = 0;
                        if(!(r7 != r8)) { _fun9523_ip = 35; continue _fun9523 }
case 32:
                        r2 = r8;
case 35:
                        r0 = _closure2_slot9;
                        r9 = r0[r4];
                        r0 = 0;
                        if(!(r7 != r9)) { _fun9523_ip = 52; continue _fun9523 }
case 49:
                        r0 = r9;
case 52:
                        r2 = r2 - r0;
                        r0 = _closure2_slot2;
                        r9 = r0[r3];
                        r0 = 0;
                        if(!(r7 != r9)) { _fun9523_ip = 73; continue _fun9523 }
case 70:
                        r0 = r9;
case 73:
                        r5 = _closure2_slot9;
                        r5 = r5[r3];
                        r1 = 0;
                        if(!(r7 != r5)) { _fun9523_ip = 90; continue _fun9523 }
case 87:
                        r1 = r5;
case 90:
                        r1 = r0 - r1;
                        _fun9523_ip = 150; continue _fun9523;
case 96:
                        r5 = _closure1_slot7;
                        r5 = r5.FALLBACK_BEFORE_RATIOS;
                        r5 = r5[r4];
                        r7 = 1;
                        r5 = r7 - r5;
                        r2 = r8 * r5;
                        r6 = r6[r3];
                        r0 = _closure1_slot7;
                        r0 = r0.FALLBACK_BEFORE_RATIOS;
                        r0 = r0[r3];
                        r0 = r7 - r0;
                        r1 = r6 * r0;
case 150:
                        if(!(r2 === r1)) { _fun9523_ip = 167; continue _fun9523 }
case 154:
                        r0 = r4.localeCompare;
                        r0 = r0.bind(r4)(r3);
                        _fun9523_ip = 171; continue _fun9523;
case 167:
                        r0 = r1 - r2;
case 171:
                        return r0;
                    }
                };
                r17 = r8.bind(r17)(r6);
                r8 = r17.slice;
                r6 = 6;
                r17 = r8.bind(r17)(r4, r6);
                r1 = new Array(0);
                r5 = 0;
                r6 = r17;
                var __iterator_values_14=Array.from(r6), __iterator_index_14=0; r8={return:function(){}}; r6=function(){if(__iterator_index_14>=__iterator_values_14.length){r8=undefined;return {value:undefined}}return {value:__iterator_values_14[__iterator_index_14++]}};
case 242:
                r18 = r6().value;
                r17 = r8;
                if(!(r17 !== r7)) { _fun9521_ip = 407; continue _fun9521 }
case 256: // try_start_0
                r11 = r18;
                r17 = _closure2_slot2;
                r18 = r17[r18];
                r12 = r18;
                r17 = 0;
                if(!(r9 != r18)) { _fun9521_ip = 279; continue _fun9521 }
case 276:
                r17 = r12;
case 279:
                r14 = r17;
                r17 = _closure2_slot3;
                if(r17) { _fun9521_ip = 310; continue _fun9521 }
case 289:
                r17 = r11;
                r17 = r16[r17];
                r13 = r17;
                r18 = 0;
                if(!(r9 != r17)) { _fun9521_ip = 308; continue _fun9521 }
case 305:
                r18 = r13;
case 308:
                _fun9521_ip = 334; continue _fun9521;
case 310:
                r19 = r14;
                r17 = _closure1_slot7;
                r20 = r17.FALLBACK_BEFORE_RATIOS;
                r17 = r11;
                r17 = r20[r17];
                r18 = r19 * r17;
case 334:
                r15 = r18;
                r20 = r1;
                r19 = r20.push;
                r22 = r11;
                r21 = r14;
                r17 = {};
                r17['name'] = r22;
                r17['before'] = r18;
                r17['after'] = r21;
                r17 = r19.bind(r20)(r17);
                r17 = r5;
                if(!(r18 > r17)) { _fun9521_ip = 382; continue _fun9521 }
case 379:
                r5 = r15;
case 382:
                r18 = r14;
                r17 = r5;
                if(!(r18 > r17)) { _fun9521_ip = 395; continue _fun9521 }
case 392:
                r5 = r14;
case 395: // try_end0
                _fun9521_ip = 242; continue _fun9521;
case 400: // catch_target0
                CatchBlockStart(arg_register=6);
                r8.return();
                throw r6;
case 407:
                r11 = r5;
                r6 = 1e-06;
                r8 = r3;
                if(!(r11 > r6)) { _fun9521_ip = 444; continue _fun9521 }
case 427:
                r6 = r5;
                r5 = 0.9;
                r8 = r5 / r6;
case 444:
                r5 = _closure1_slot7;
                r11 = r5.VISUAL_DIMENSIONS;
                r6 = r11.every;
                r5 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    r1 = _closure2_slot9;
                    r0 = a0;
                    r1 = r1[r0];
                    r0 = 1e-06;
                    r0 = r1 < r0;
                    return r0;
                };
                r5 = r6.bind(r11)(r5);
                r6 = _closure1_slot7;
                r6 = r6.EMOTION_INTENSITY_THRESHOLDS;
                r6 = r6[r10];
                if(!(r9 == r6)) { _fun9521_ip = 498; continue _fun9521 }
case 488:
                r9 = _closure1_slot7;
                r6 = r9.INTENSITY_THRESHOLDS;
case 498:
                var _closure2_slot4 = r8;
                var _closure2_slot5 = r5;
                var _closure2_slot6 = r7;
                var _closure2_slot7 = r7;
                r2 = _closure1_slot3;
                r5 = r2.default;
                r2 = 2;
                r2 = r5.bind(r7)(r6, r2);
                r4 = r2[r4];
                _closure2_slot6 = r4;
                r2 = r2[r3];
                _closure2_slot7 = r2;
                r2 = r1;
                r1 = r2.map;
                r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r0
                    _fun9525: for(var _fun9525_ip = 0; ; ) switch(_fun9525_ip) {
case 0:
                        r0 = a0;
                        r7 = a1;
                        r2 = 'strong';
                        r4 = 'medium';
                        r1 = 'weak';
                        r5 = r0.name;
                        r9 = r0.before;
                        r8 = r0.after;
                        r0 = _closure2_slot4;
                        r11 = r9 * r0;
                        r12 = r8 * r0;
                        r9 = r12 - r11;
                        r0 = global;
                        r13 = r0.Math;
                        r8 = r13.round;
                        r10 = _closure2_slot8;
                        r10 = r10[r5];
                        r14 = null;
                        r6 = 0;
                        if(!(r14 != r10)) { _fun9525_ip = 86; continue _fun9525 }
case 83:
                        r6 = r10;
case 86:
                        r10 = 1000;
                        r6 = r10 * r6;
                        r6 = r8.bind(r13)(r6);
                        r8 = r6 / r10;
                        r6 = 3;
                        r6 = r7 < r6;
                        r14 = r0.Math;
                        r13 = r14.round;
                        r11 = r10 * r11;
                        r11 = r13.bind(r14)(r11);
                        r13 = r11 / r10;
                        r11 = new Array(2);
                        r11[0] = r13;
                        r14 = r0.Math;
                        r13 = r14.round;
                        r12 = r10 * r12;
                        r12 = r13.bind(r14)(r12);
                        r12 = r12 / r10;
                        r11[1] = r12;
                        r13 = r0.Math;
                        r12 = r13.round;
                        r0 = r10 * r9;
                        r0 = r12.bind(r13)(r0);
                        r10 = r0 / r10;
                        r0 = 1;
                        r7 = r7 + r0;
                        r0 = {};
                        r0['name'] = r5;
                        r0['value'] = r11;
                        r0['change'] = r10;
                        r0['fx_activation'] = r8;
                        r0['rank'] = r7;
                        r0['visual'] = r6;
                        if(!r6) { _fun9525_ip = 412; continue _fun9525 }
case 239:
                        r6 = _closure2_slot8;
                        r7 = r6[r5];
                        r6 = 1e-06;
                        if(!(!(r7 < r6))) { _fun9525_ip = 406; continue _fun9525 }
case 264:
                        r6 = _closure2_slot3;
                        if(r6) { _fun9525_ip = 353; continue _fun9525 }
case 271:
                        r6 = _closure2_slot5;
                        if(r6) { _fun9525_ip = 353; continue _fun9525 }
case 278:
                        r7 = _closure1_slot6;
                        r10 = r7.floatGte;
                        r7 = _closure2_slot7;
                        r8 = undefined;
                        r7 = r10.bind(r8)(r9, r7);
                        if(r7) { _fun9525_ip = 345; continue _fun9525 }
case 306:
                        r6 = _closure1_slot6;
                        r7 = r6.floatGte;
                        r6 = _closure2_slot6;
                        r6 = r7.bind(r8)(r9, r6);
                        if(r6) { _fun9525_ip = 337; continue _fun9525 }
case 329:
                        r0['intensity'] = r1;
                        _fun9525_ip = 412; continue _fun9525;
case 337:
                        r0['intensity'] = r4;
                        _fun9525_ip = 412; continue _fun9525;
case 345:
                        r0['intensity'] = r2;
                        _fun9525_ip = 412; continue _fun9525;
case 353:
                        r3 = _closure2_slot8;
                        r6 = r3[r5];
                        r3 = 0.5;
                        if(!(!(r6 >= r3))) { _fun9525_ip = 398; continue _fun9525 }
case 375:
                        r5 = 0.25;
                        r3 = r1;
                        if(!(r6 >= r5)) { _fun9525_ip = 395; continue _fun9525 }
case 392:
                        r3 = r4;
case 395:
                        r2 = r3;
case 398:
                        r0['intensity'] = r2;
                        _fun9525_ip = 412; continue _fun9525;
case 406:
                        r0['intensity'] = r1;
case 412:
                        return r0;
                    }
                };
                r0 = r1.bind(r2)(r0);
                return r0;
            }
        };
        var _closure1_slot18 = r0;
        r0 = 0;
        r3 = r5[r0];
        r0 = undefined;
        r6 = r4.bind(r0)(r3);
        r3 = global;
        r9 = r3.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r3 = true;
        r7['value'] = r3;
        r3 = '__esModule';
        r3 = r8.bind(r9)(r2, r3, r7);
        r2['V1FourFactorStrategy'] = r0;
        r3 = 1;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        r3 = r6.bind(r0)(r3);
        var _closure1_slot0 = r3;
        r3 = 2;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        r3 = r6.bind(r0)(r3);
        var _closure1_slot1 = r3;
        r3 = 3;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        r3 = r6.bind(r0)(r3);
        var _closure1_slot2 = r3;
        r3 = 4;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        r3 = r6.bind(r0)(r3);
        var _closure1_slot3 = r3;
        r3 = 5;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        var _closure1_slot4 = r3;
        r3 = 6;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        var _closure1_slot5 = r3;
        r3 = 7;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        var _closure1_slot6 = r3;
        r3 = 8;
        r3 = r5[r3];
        r3 = r4.bind(r0)(r3);
        var _closure1_slot7 = r3;
        r3 = 0.14285714285714285;
        var _closure1_slot8 = r3;
        r3 = {};
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: loudness, environment: r1
            _fun9526: for(var _fun9526_ip = 0; ; ) switch(_fun9526_ip) {
case 0:
                r9 = a0;
                r7 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'eq';
                r1 = r3.bind(r2)(r9, r1);
                r4 = 0;
                if(!r1) { _fun9526_ip = 168; continue _fun9526 }
case 32:
                r6 = _closure1_slot11;
                r5 = _closure1_slot12;
                r1 = global;
                r8 = r1.Array;
                r1 = r8.prototype;
                r3 = Object.create(r1, {constructor: {value: r8}});
                r12 = 10;
                r13 = r3;
                r1 = originalConstruct(r8, [r12, r11]);
                r3 = r1 instanceof Object ? r1 : r3;
                r1 = r3.fill;
                r3 = r1.bind(r3)(r7);
                r1 = ['eq', 'eqs'];
                r5 = r5.bind(r2)(r9, r1, r3);
                r1 = r5.slice;
                r3 = 5;
                r8 = r1.bind(r5)(r7, r3);
                r5 = r8.reduce;
                r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
                    r1 = a0;
                    r0 = a1;
                    r0 = r1 + r0;
                    return r0;
                };
                r1 = r5.bind(r8)(r1, r7);
                r5 = r1 / r3;
                r3 = -12;
                r1 = 12;
                r3 = r6.bind(r2)(r5, r3, r1);
                r1 = 0.55;
                r1 = r1 * r3;
                r4 = r7 + r1;
case 168:
                r3 = _closure1_slot13;
                r1 = 'rvb';
                r3 = r3.bind(r2)(r9, r1);
                r1 = r4;
                if(!r3) { _fun9526_ip = 246; continue _fun9526 }
case 188:
                r6 = _closure1_slot11;
                r8 = _closure1_slot12;
                r3 = ['rvb', 'ol', 'rvb'];
                r5 = -96.3;
                r3 = r8.bind(r2)(r9, r3, r5);
                r5 = r6.bind(r2)(r3, r5, r7);
                r3 = 0.45;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 246:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['loudness'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: high_freq, environment: r1
            _fun9528: for(var _fun9528_ip = 0; ; ) switch(_fun9528_ip) {
case 0:
                r8 = a0;
                r6 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'eq';
                r1 = r3.bind(r2)(r8, r1);
                r4 = 0;
                if(!r1) { _fun9528_ip = 216; continue _fun9528 }
case 32:
                r7 = _closure1_slot12;
                r1 = global;
                r1 = r1.Array;
                r3 = r1.prototype;
                r5 = Object.create(r3, {constructor: {value: r1}});
                r3 = 10;
                r15 = r5;
                r14 = r3;
                r1 = originalConstruct(r1, [r14, r13]);
                r5 = r1 instanceof Object ? r1 : r5;
                r1 = r5.fill;
                r5 = r1.bind(r5)(r6);
                r1 = ['eq', 'eqs'];
                r11 = r7.bind(r2)(r8, r1, r5);
                r1 = r11.length;
                r4 = 0;
                if(!(r1 >= r3)) { _fun9528_ip = 216; continue _fun9528 }
case 107:
                r10 = _closure1_slot11;
                r1 = 7;
                r1 = r11[r1];
                r9 = -12;
                r7 = 12;
                r3 = r10.bind(r2)(r1, r9, r7);
                r1 = 0.2;
                r1 = r1 * r3;
                r3 = 8;
                r3 = r11[r3];
                r5 = r10.bind(r2)(r3, r9, r7);
                r3 = 0.3;
                r5 = r3 * r5;
                r3 = 9;
                r3 = r11[r3];
                r7 = r10.bind(r2)(r3, r9, r7);
                r3 = 0.25;
                r3 = r3 * r7;
                r1 = r6 + r1;
                r1 = r1 + r5;
                r4 = r1 + r3;
case 216:
                r3 = _closure1_slot13;
                r1 = 'bt';
                r3 = r3.bind(r2)(r8, r1);
                r1 = r4;
                if(!r3) { _fun9528_ip = 293; continue _fun9528 }
case 236:
                r7 = _closure1_slot11;
                r5 = _closure1_slot12;
                r3 = ['bt', 'treble'];
                r6 = r5.bind(r2)(r8, r3, r6);
                r5 = -12;
                r3 = 12;
                r5 = r7.bind(r2)(r6, r5, r3);
                r3 = 0.25;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 293:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['high_freq'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: vocal, environment: r1
            _fun9529: for(var _fun9529_ip = 0; ; ) switch(_fun9529_ip) {
case 0:
                r8 = a0;
                r7 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'eq';
                r1 = r3.bind(r2)(r8, r1);
                r4 = 0;
                if(!r1) { _fun9529_ip = 251; continue _fun9529 }
case 32:
                r6 = _closure1_slot12;
                r1 = global;
                r1 = r1.Array;
                r3 = r1.prototype;
                r5 = Object.create(r3, {constructor: {value: r1}});
                r3 = 10;
                r16 = r5;
                r15 = r3;
                r1 = originalConstruct(r1, [r15, r14]);
                r5 = r1 instanceof Object ? r1 : r5;
                r1 = r5.fill;
                r5 = r1.bind(r5)(r7);
                r1 = ['eq', 'eqs'];
                r12 = r6.bind(r2)(r8, r1, r5);
                r1 = r12.length;
                r4 = 0;
                if(!(r1 >= r3)) { _fun9529_ip = 251; continue _fun9529 }
case 110:
                r11 = _closure1_slot11;
                r1 = 4;
                r1 = r12[r1];
                r10 = -12;
                r9 = 12;
                r3 = r11.bind(r2)(r1, r10, r9);
                r1 = 0.22;
                r1 = r1 * r3;
                r3 = 5;
                r3 = r12[r3];
                r5 = r11.bind(r2)(r3, r10, r9);
                r3 = 0.26;
                r6 = r3 * r5;
                r3 = 6;
                r3 = r12[r3];
                r5 = r11.bind(r2)(r3, r10, r9);
                r3 = 0.18;
                r5 = r3 * r5;
                r3 = 7;
                r3 = r12[r3];
                r9 = r11.bind(r2)(r3, r10, r9);
                r3 = 0.15;
                r3 = r3 * r9;
                r1 = r7 + r1;
                r1 = r1 + r6;
                r1 = r1 + r5;
                r4 = r1 + r3;
case 251:
                r3 = _closure1_slot13;
                r1 = 'cmp';
                r3 = r3.bind(r2)(r8, r1);
                r1 = r4;
                if(!r3) { _fun9529_ip = 322; continue _fun9529 }
case 271:
                r6 = _closure1_slot11;
                r5 = _closure1_slot12;
                r3 = ['cmp', 'attack'];
                r5 = r5.bind(r2)(r8, r3, r7);
                r3 = 2;
                r5 = r6.bind(r2)(r5, r7, r3);
                r3 = 0.19;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 322:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['vocal'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: rhythm, environment: r1
            _fun9530: for(var _fun9530_ip = 0; ; ) switch(_fun9530_ip) {
case 0:
                r8 = a0;
                r7 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'cmp';
                r1 = r3.bind(r2)(r8, r1);
                r3 = 0;
                if(!r1) { _fun9530_ip = 167; continue _fun9530 }
case 32:
                r10 = _closure1_slot11;
                r4 = _closure1_slot12;
                r1 = ['cmp', 'attack'];
                r1 = r4.bind(r2)(r8, r1, r7);
                r6 = 2;
                r1 = r10.bind(r2)(r1, r7, r6);
                r5 = 1;
                r9 = r5 - r1;
                r1 = ['cmp', 'release'];
                r1 = r4.bind(r2)(r8, r1, r7);
                r1 = r10.bind(r2)(r1, r7, r6);
                r6 = r5 - r1;
                r1 = ['cmp', 'ratio'];
                r4 = r4.bind(r2)(r8, r1, r5);
                r1 = 50;
                r4 = r10.bind(r2)(r4, r5, r1);
                r1 = 0.13;
                r4 = r1 * r4;
                r5 = 0.22;
                r5 = r5 * r9;
                r5 = r7 + r5;
                r1 = r1 * r6;
                r1 = r5 + r1;
                r3 = r1 + r4;
case 167:
                r4 = _closure1_slot13;
                r1 = 'limiter';
                r1 = r4.bind(r2)(r8, r1);
                r5 = r3;
                if(!r1) { _fun9530_ip = 262; continue _fun9530 }
case 187:
                r9 = _closure1_slot11;
                r4 = _closure1_slot12;
                r1 = ['limiter', 'attack'];
                r6 = 0.001;
                r4 = r4.bind(r2)(r8, r1, r6);
                r1 = 0.2;
                r4 = r9.bind(r2)(r4, r6, r1);
                r1 = 1;
                r4 = r1 - r4;
                r1 = 0.13;
                r1 = r1 * r4;
                r5 = r3 + r1;
case 262:
                r3 = _closure1_slot13;
                r1 = 'eq';
                r1 = r3.bind(r2)(r8, r1);
                r3 = r5;
                if(!r1) { _fun9530_ip = 476; continue _fun9530 }
case 285:
                r9 = _closure1_slot12;
                r1 = global;
                r1 = r1.Array;
                r4 = r1.prototype;
                r6 = Object.create(r4, {constructor: {value: r1}});
                r4 = 10;
                r17 = r6;
                r16 = r4;
                r1 = originalConstruct(r1, [r16, r15]);
                r6 = r1 instanceof Object ? r1 : r6;
                r1 = r6.fill;
                r6 = r1.bind(r6)(r7);
                r1 = ['eq', 'eqs'];
                r13 = r9.bind(r2)(r8, r1, r6);
                r1 = r13.length;
                r3 = r5;
                if(!(r1 >= r4)) { _fun9530_ip = 476; continue _fun9530 }
case 361:
                r10 = _closure1_slot11;
                r4 = r13[r7];
                r1 = 1;
                r1 = r13[r1];
                r4 = r4 + r1;
                r11 = 2;
                r1 = r13[r11];
                r1 = r4 + r1;
                r4 = 3;
                r1 = r1 / r4;
                r9 = -12;
                r6 = 12;
                r12 = r10.bind(r2)(r1, r9, r6);
                r1 = 0.17;
                r1 = r1 * r12;
                r12 = r13[r4];
                r4 = 4;
                r4 = r13[r4];
                r4 = r12 + r4;
                r4 = r4 / r11;
                r6 = r10.bind(r2)(r4, r9, r6);
                r4 = 0.1;
                r4 = r4 * r6;
                r1 = r5 + r1;
                r3 = r1 + r4;
case 476:
                r4 = _closure1_slot13;
                r1 = 'bt';
                r1 = r4.bind(r2)(r8, r1);
                r4 = r3;
                if(!r1) { _fun9530_ip = 553; continue _fun9530 }
case 496:
                r9 = _closure1_slot11;
                r5 = _closure1_slot12;
                r1 = ['bt', 'bass'];
                r6 = r5.bind(r2)(r8, r1, r7);
                r5 = -12;
                r1 = 12;
                r5 = r9.bind(r2)(r6, r5, r1);
                r1 = 0.08;
                r1 = r1 * r5;
                r4 = r3 + r1;
case 553:
                r3 = _closure1_slot13;
                r1 = 'dly';
                r3 = r3.bind(r2)(r8, r1);
                r1 = r4;
                if(!r3) { _fun9530_ip = 631; continue _fun9530 }
case 573:
                r6 = _closure1_slot11;
                r5 = _closure1_slot12;
                r3 = ['dly', 'feedback'];
                r5 = r5.bind(r2)(r8, r3, r7);
                r3 = 0.2;
                r5 = r6.bind(r2)(r5, r7, r3);
                r3 = 0.12;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 631:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['rhythm'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: stereo_width, environment: r1
            _fun9531: for(var _fun9531_ip = 0; ; ) switch(_fun9531_ip) {
case 0:
                r8 = a0;
                r7 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'se';
                r1 = r3.bind(r2)(r8, r1);
                r4 = 0;
                if(!r1) { _fun9531_ip = 126; continue _fun9531 }
case 29:
                r9 = _closure1_slot11;
                r5 = _closure1_slot12;
                r1 = ['se', 'stereoizer'];
                r1 = r5.bind(r2)(r8, r1, r7);
                r6 = 10;
                r3 = r9.bind(r2)(r1, r7, r6);
                r1 = 0.4;
                r1 = r1 * r3;
                r3 = ['se', 'presence'];
                r5 = r5.bind(r2)(r8, r3, r7);
                r3 = -10;
                r5 = r9.bind(r2)(r5, r3, r6);
                r3 = 0.25;
                r3 = r3 * r5;
                r1 = r7 + r1;
                r4 = r1 + r3;
case 126:
                r3 = _closure1_slot13;
                r1 = 'rvb';
                r3 = r3.bind(r2)(r8, r1);
                r1 = r4;
                if(!r3) { _fun9531_ip = 197; continue _fun9531 }
case 146:
                r6 = _closure1_slot11;
                r5 = _closure1_slot12;
                r3 = ['rvb', 'rvb', 'swidth'];
                r5 = r5.bind(r2)(r8, r3, r7);
                r3 = 180;
                r5 = r6.bind(r2)(r5, r7, r3);
                r3 = 0.35;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 197:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['stereo_width'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: space_size, environment: r1
            _fun9532: for(var _fun9532_ip = 0; ; ) switch(_fun9532_ip) {
case 0:
                r8 = a0;
                r7 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'rvb';
                r1 = r3.bind(r2)(r8, r1);
                r4 = 0;
                if(!r1) { _fun9532_ip = 182; continue _fun9532 }
case 32:
                r10 = _closure1_slot11;
                r11 = _closure1_slot12;
                r1 = ['rvb', 'rvb', 'dtime'];
                r3 = r11.bind(r2)(r8, r1, r7);
                r6 = 0.2;
                r1 = 10;
                r3 = r10.bind(r2)(r3, r6, r1);
                r1 = 0.35;
                r1 = r1 * r3;
                r3 = ['rvb', 'er', 'rsize'];
                r9 = r11.bind(r2)(r8, r3, r7);
                r5 = -100;
                r3 = 100;
                r5 = r10.bind(r2)(r9, r5, r3);
                r3 = 0.25;
                r5 = r3 * r5;
                r3 = ['rvb', 'ol', 'rvb'];
                r9 = -96.3;
                r3 = r11.bind(r2)(r8, r3, r9);
                r3 = r10.bind(r2)(r3, r9, r7);
                r3 = r6 * r3;
                r1 = r7 + r1;
                r1 = r1 + r5;
                r4 = r1 + r3;
case 182:
                r3 = _closure1_slot13;
                r1 = 'dly';
                r3 = r3.bind(r2)(r8, r1);
                r1 = r4;
                if(!r3) { _fun9532_ip = 256; continue _fun9532 }
case 202:
                r6 = _closure1_slot11;
                r5 = _closure1_slot12;
                r3 = ['dly', 'delay'];
                r5 = r5.bind(r2)(r8, r3, r7);
                r3 = 5000;
                r5 = r6.bind(r2)(r5, r7, r3);
                r3 = 0.2;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 256:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['space_size'] = r4;
        r4 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: richness, environment: r1
            _fun9533: for(var _fun9533_ip = 0; ; ) switch(_fun9533_ip) {
case 0:
                r7 = a0;
                r8 = 0;
                r3 = _closure1_slot13;
                r2 = undefined;
                r1 = 'chorus';
                r1 = r3.bind(r2)(r7, r1);
                r4 = 0;
                if(!r1) { _fun9533_ip = 209; continue _fun9533 }
case 32:
                r11 = _closure1_slot11;
                r9 = _closure1_slot12;
                r1 = ['chorus', 'mix'];
                r1 = r9.bind(r2)(r7, r1, r8);
                r5 = 1;
                r3 = r11.bind(r2)(r1, r8, r5);
                r1 = 0.22;
                r1 = r1 * r3;
                r3 = ['chorus', 'voices'];
                r6 = r9.bind(r2)(r7, r3, r5);
                r3 = 4;
                r6 = r11.bind(r2)(r6, r5, r3);
                r3 = 0.18;
                r6 = r3 * r6;
                r3 = ['chorus', 'depth'];
                r3 = r9.bind(r2)(r7, r3, r8);
                r3 = r11.bind(r2)(r3, r8, r5);
                r10 = 0.1;
                r5 = r10 * r3;
                r3 = ['chorus', 'rate'];
                r9 = r9.bind(r2)(r7, r3, r10);
                r3 = 5;
                r9 = r11.bind(r2)(r9, r10, r3);
                r3 = 0.08;
                r3 = r3 * r9;
                r1 = r8 + r1;
                r1 = r1 + r6;
                r1 = r1 + r5;
                r4 = r1 + r3;
case 209:
                r3 = _closure1_slot13;
                r1 = 'rvb';
                r1 = r3.bind(r2)(r7, r1);
                r5 = r4;
                if(!r1) { _fun9533_ip = 333; continue _fun9533 }
case 229:
                r10 = _closure1_slot11;
                r6 = _closure1_slot12;
                r1 = ['rvb', 'rvb', 'diffusion'];
                r3 = r6.bind(r2)(r7, r1, r8);
                r1 = 100;
                r3 = r10.bind(r2)(r3, r8, r1);
                r1 = 0.15;
                r1 = r1 * r3;
                r3 = ['rvb', 'rvb', 'dtime'];
                r9 = r6.bind(r2)(r7, r3, r8);
                r6 = 0.2;
                r3 = 10;
                r6 = r10.bind(r2)(r9, r6, r3);
                r3 = 0.12;
                r3 = r3 * r6;
                r1 = r4 + r1;
                r5 = r1 + r3;
case 333:
                r3 = _closure1_slot13;
                r1 = 'dly';
                r1 = r3.bind(r2)(r7, r1);
                r3 = r5;
                if(!r1) { _fun9533_ip = 457; continue _fun9533 }
case 353:
                r9 = _closure1_slot11;
                r6 = _closure1_slot12;
                r1 = ['dly', 'wet'];
                r4 = -40;
                r1 = r6.bind(r2)(r7, r1, r4);
                r4 = r9.bind(r2)(r1, r4, r8);
                r1 = 0.12;
                r1 = r1 * r4;
                r4 = ['dly', 'feedback'];
                r6 = r6.bind(r2)(r7, r4, r8);
                r4 = 0.2;
                r6 = r9.bind(r2)(r6, r8, r4);
                r4 = 0.08;
                r4 = r4 * r6;
                r1 = r5 + r1;
                r3 = r1 + r4;
case 457:
                r4 = _closure1_slot13;
                r1 = 'se';
                r1 = r4.bind(r2)(r7, r1);
                r4 = r3;
                if(!r1) { _fun9533_ip = 534; continue _fun9533 }
case 477:
                r9 = _closure1_slot11;
                r5 = _closure1_slot12;
                r1 = ['se', 'presence'];
                r6 = r5.bind(r2)(r7, r1, r8);
                r5 = -10;
                r1 = 10;
                r5 = r9.bind(r2)(r6, r5, r1);
                r1 = 0.1;
                r1 = r1 * r5;
                r4 = r3 + r1;
case 534:
                r3 = _closure1_slot13;
                r1 = 'eq';
                r3 = r3.bind(r2)(r7, r1);
                r1 = r4;
                if(!r3) { _fun9533_ip = 707; continue _fun9533 }
case 557:
                r6 = _closure1_slot12;
                r3 = global;
                r9 = r3.Array;
                r3 = r9.prototype;
                r5 = Object.create(r3, {constructor: {value: r9}});
                r14 = 10;
                r15 = r5;
                r3 = originalConstruct(r9, [r14, r13]);
                r5 = r3 instanceof Object ? r3 : r5;
                r3 = r5.fill;
                r5 = r3.bind(r5)(r8);
                r3 = ['eq', 'eqs'];
                r6 = r6.bind(r2)(r7, r3, r5);
                r5 = r6.length;
                r3 = 7;
                r1 = r4;
                if(!(r5 >= r3)) { _fun9533_ip = 707; continue _fun9533 }
case 633:
                r7 = _closure1_slot11;
                r3 = 4;
                r5 = r6[r3];
                r3 = 5;
                r3 = r6[r3];
                r5 = r5 + r3;
                r3 = 6;
                r3 = r6[r3];
                r5 = r5 + r3;
                r3 = 3;
                r6 = r5 / r3;
                r5 = -12;
                r3 = 12;
                r5 = r7.bind(r2)(r6, r5, r3);
                r3 = 0.13;
                r3 = r3 * r5;
                r1 = r4 + r3;
case 707:
                r0 = _closure1_slot10;
                r0 = r0.bind(r2)(r1);
                return r0;
            }
        };
        r3['richness'] = r4;
        var _closure1_slot9 = r3;
        r1 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            r0 = _closure1_slot1;
            r3 = r0.default;
            r2 = function() {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: r, environment: r1
                r2 = this;
                r0 = _closure1_slot0;
                r3 = r0.default;
                r1 = _closure2_slot0;
                r0 = undefined;
                r1 = r3.bind(r0)(r2, r1);
                r1 = 'emofx-visual';
                r2['name'] = r1;
                return r0;
            };
            var _closure2_slot0 = r2;
            r0 = {};
            r4 = 'compute';
            r0['key'] = r4;
            r1 = function(a0, a1, a2) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: value, environment: r1
                _fun9536: for(var _fun9536_ip = 0; ; ) switch(_fun9536_ip) {
case 0:
                    r3 = a0;
                    r4 = a1;
                    r7 = a2;
                    r1 = undefined;
                    if(r4) { _fun9536_ip = 29; continue _fun9536 }
case 17:
                    r2 = _closure1_slot5;
                    r2 = r2.EMPTY_VISUAL_EXTRA;
                    return r2;
case 29:
                    r2 = _closure1_slot14;
                    r17 = r2.bind(r1)(r4);
                    r4 = _closure1_slot15;
                    r2 = _closure1_slot16;
                    r2 = r2.bind(r1)(r3);
                    r16 = r4.bind(r1)(r2);
                    r9 = _closure1_slot18;
                    r19 = r3.emotion_label;
                    r18 = r3.family;
                    r5 = 0.25;
                    r14 = 0.15;
                    r13 = 0.35;
                    r20 = undefined;
                    r15 = r5;
                    r12 = r5;
                    r2 = r9.call(r20, r19, r18, r17, r16, r15, r14, r13, r12, r11);
                    r6 = r7.debug;
                    r9 = r3.emotion_label;
                    r8 = r3.family;
                    r4 = 0;
                    r5 = r2[r4];
                    r10 = null;
                    r4 = undefined;
                    if(!(r10 != r5)) { _fun9536_ip = 151; continue _fun9536 }
case 146:
                    r4 = r5.name;
case 151:
                    r5 = {};
                    r5['emotion'] = r9;
                    r5['family'] = r8;
                    r5['topDimension'] = r4;
                    r4 = 'visual:result';
                    r4 = r6.bind(r7)(r4, r5);
                    r0 = _closure1_slot4;
                    r0 = r0.resolveVisualCopywriting;
                    r1 = r0.bind(r1)(r3, r2);
                    r0 = {};
                    r0['dimensions'] = r2;
                    r0['copywriting'] = r1;
                    return r0;
                }
            };
            r0['value'] = r1;
            r1 = new Array(1);
            r1[0] = r0;
            r0 = undefined;
            r0 = r3.bind(r0)(r2, r1);
            return r0;
        };
        r1 = r1.bind(r0)();
        r2['V1FourFactorStrategy'] = r1;
        return r0;
    };
factories[965]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r4 = a1;
        r6 = a5;
        r5 = a6;
        r0 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: s, environment: r2
            _fun9538: for(var _fun9538_ip = 0; ; ) switch(_fun9538_ip) {
case 0:
                r3 = a0;
                r2 = r3.length;
                r1 = 2;
                r0 = null;
                if(!(!(r2 < r1))) { _fun9538_ip = 52; continue _fun9538 }
case 17:
                r1 = 0;
                r1 = r3[r1];
                r2 = r1.name;
                r1 = '+';
                r2 = r2 + r1;
                r1 = 1;
                r1 = r3[r1];
                r1 = r1.name;
                r0 = r2 + r1;
case 52:
                return r0;
            }
        };
        var _closure1_slot4 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: f, environment: r2
            _fun9539: for(var _fun9539_ip = 0; ; ) switch(_fun9539_ip) {
case 0:
                r3 = a0;
                r2 = a1;
                r1 = null;
                r0 = r3.dimPairs;
                r0 = r0[r2];
                r6 = 'string';
                r5 = typeof r0;
                if(!(r6 !== r5)) { _fun9539_ip = 173; continue _fun9539 }
case 34:
                var _closure2_slot0 = r2;
                r2 = global;
                r7 = r2.Object;
                r6 = r7.keys;
                r5 = r3.dimPairs;
                r7 = r6.bind(r7)(r5);
                r6 = r7.filter;
                r5 = function(a0) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r4
                    _fun9540: for(var _fun9540_ip = 0; ; ) switch(_fun9540_ip) {
case 0:
                        r3 = a0;
                        r0 = _closure2_slot0;
                        r0 = r3 === r0;
                        if(r0) { _fun9540_ip = 40; continue _fun9540 }
case 17:
                        r2 = r3.startsWith;
                        r4 = _closure2_slot0;
                        r1 = '_';
                        r1 = r4 + r1;
                        r0 = r2.bind(r3)(r1);
case 40:
                        return r0;
                    }
                };
                r6 = r6.bind(r7)(r5);
                r5 = r6.sort;
                r4 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r4
                    r2 = a0;
                    r1 = r2.localeCompare;
                    r0 = a1;
                    r0 = r1.bind(r2)(r0);
                    return r0;
                };
                r4 = r5.bind(r6)(r4);
                r6 = r4.length;
                r5 = 0;
                if(!(r5 !== r6)) { _fun9539_ip = 171; continue _fun9539 }
case 104:
                r6 = r2.Math;
                r5 = r6.floor;
                r7 = r2.Math;
                r2 = r7.random;
                r7 = r2.bind(r7)();
                r2 = r4.length;
                r2 = r7 * r2;
                r2 = r5.bind(r6)(r2);
                r3 = r3.dimPairs;
                r2 = r4[r2];
                r3 = r3[r2];
                r2 = null;
                if(!(r2 != r3)) { _fun9539_ip = 169; continue _fun9539 }
case 166:
                r2 = r3;
case 169:
                return r2;
case 171:
                return r1;
case 173:
                return r0;
            }
        };
        var _closure1_slot5 = r0;
        r0 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Original name: v, environment: r2
            _fun9542: for(var _fun9542_ip = 0; ; ) switch(_fun9542_ip) {
case 0:
                r5 = a0;
                r1 = a1;
                r2 = _closure1_slot5;
                r4 = undefined;
                r2 = r2.bind(r4)(r5, r1);
                if(r2) { _fun9542_ip = 111; continue _fun9542 }
case 24:
                r3 = r1.split;
                r6 = '+';
                r7 = r3.bind(r1)(r6);
                r8 = r7.length;
                r3 = 2;
                if(!(r3 === r8)) { _fun9542_ip = 84; continue _fun9542 }
case 50:
                r3 = 1;
                r3 = r7[r3];
                r6 = r3 + r6;
                r3 = 0;
                r3 = r7[r3];
                r3 = r6 + r3;
                r0 = _closure1_slot5;
                r4 = r0.bind(r4)(r5, r3);
                if(r4) { _fun9542_ip = 88; continue _fun9542 }
case 84:
                r0 = null;
                return r0;
case 88:
                r0 = {};
                r0['description'] = r4;
                r0['matchedKey'] = r3;
                r3 = 'matched_reversed';
                r0['matchLevel'] = r3;
                return r0;
case 111:
                r0 = {};
                r0['description'] = r2;
                r0['matchedKey'] = r1;
                r1 = 'matched_rank_order';
                r0['matchLevel'] = r1;
                return r0;
            }
        };
        var _closure1_slot6 = r0;
        r0 = 0;
        r1 = r5[r0];
        r0 = undefined;
        r3 = r4.bind(r0)(r1);
        r1 = global;
        r9 = r1.Object;
        r8 = r9.defineProperty;
        r7 = {};
        r1 = true;
        r7['value'] = r1;
        r1 = '__esModule';
        r1 = r8.bind(r9)(r6, r1, r7);
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r2
            _fun9543: for(var _fun9543_ip = 0; ; ) switch(_fun9543_ip) {
case 0:
                r0 = a0;
                r6 = r0.emotion_label;
                r2 = _closure1_slot4;
                r4 = undefined;
                r1 = a1;
                r2 = r2.bind(r4)(r1);
                r1 = _closure1_slot0;
                r1 = r1.isEmotionLabel;
                r1 = r1.bind(r4)(r6);
                if(!r1) { _fun9543_ip = 83; continue _fun9543 }
case 44:
                r1 = global;
                r1 = r1.Object;
                r1 = r1.prototype;
                r5 = r1.hasOwnProperty;
                r3 = r5.call;
                r1 = _closure1_slot2;
                r1 = r3.bind(r5)(r1, r6);
                if(!r1) { _fun9543_ip = 83; continue _fun9543 }
case 80:
                if(r2) { _fun9543_ip = 155; continue _fun9543 }
case 83:
                r1 = _closure1_slot3;
                r7 = r1.keyword;
                r1 = _closure1_slot3;
                r5 = r1.description;
                r1 = null;
                r3 = r2;
                if(!(r1 == r3)) { _fun9543_ip = 122; continue _fun9543 }
case 112:
                r1 = _closure1_slot1;
                r3 = r1.FALLBACK_DESCRIPTION_KEY;
case 122:
                r1 = {};
                r1['emotion'] = r6;
                r1['keyword'] = r7;
                r1['description'] = r5;
                r1['descriptionKey'] = r3;
                r3 = 'fallback';
                r1['matchLevel'] = r3;
                return r1;
case 155:
                r1 = _closure1_slot2;
                r3 = r1[r6];
                r1 = _closure1_slot6;
                r1 = r1.bind(r4)(r3, r2);
                r5 = r3.keyword;
                if(r1) { _fun9543_ip = 225; continue _fun9543 }
case 182:
                r0 = _closure1_slot3;
                r3 = r0.description;
                r0 = {};
                r0['emotion'] = r6;
                r0['keyword'] = r5;
                r0['description'] = r3;
                r0['descriptionKey'] = r2;
                r2 = 'emotion_only';
                r0['matchLevel'] = r2;
                _fun9543_ip = 273; continue _fun9543;
case 225:
                r4 = r1.description;
                r3 = r1.matchedKey;
                r2 = r1.matchLevel;
                r1 = {};
                r1['emotion'] = r6;
                r1['keyword'] = r5;
                r1['description'] = r4;
                r1['descriptionKey'] = r3;
                r1['matchLevel'] = r2;
                r0 = r1;
case 273:
                return r0;
            }
        };
        r6['resolveVisualCopywriting'] = r1;
        r1 = 1;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        var _closure1_slot0 = r1;
        r1 = 2;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        var _closure1_slot1 = r1;
        r1 = 3;
        r1 = r5[r1];
        r1 = r4.bind(r0)(r1);
        r1 = r3.bind(r0)(r1);
        r3 = r1.default;
        var _closure1_slot2 = r3;
        r1 = r1.default;
        r1 = r1.fallback;
        var _closure1_slot3 = r1;
        return r0;
    };
factories[966]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r2 = a5;
        r0 = global;
        r4 = r0.Object;
        r3 = r4.defineProperty;
        r1 = {};
        r0 = true;
        r1['value'] = r0;
        r0 = '__esModule';
        r0 = r3.bind(r4)(r2, r0, r1);
        r0 = undefined;
        r2['DEFAULT_COPYWRITING'] = r0;
        r2['EMPTY_VISUAL_EXTRA'] = r0;
        r2['FALLBACK_DESCRIPTION_KEY'] = r0;
        r3 = a6;
        r1 = 0;
        r3 = r3[r1];
        r1 = a1;
        r1 = r1.bind(r0)(r3);
        r3 = 'fallback';
        r2['FALLBACK_DESCRIPTION_KEY'] = r3;
        r1 = r1.DEFAULT_EMOTION;
        r3 = {'emotion': null, 'keyword': '每种心情都有回响', 'description': '音乐正在播放，声音的每个细节都在为你呈现', 'descriptionKey': 'fallback', 'matchLevel': 'fallback'};
        r3['emotion'] = r1;
        r2['DEFAULT_COPYWRITING'] = r3;
        r1 = {'name': 'stereo_width', 'value': null, 'change': 0.199, 'fx_activation': 0.357, 'rank': 1, 'visual': true, 'intensity': 'medium'};
        r4 = [0.455, 0.655];
        r1['value'] = r4;
        r4 = new Array(6);
        r4[0] = r1;
        r1 = {'name': 'loudness', 'value': null, 'change': 0.183, 'fx_activation': 0.647, 'rank': 2, 'visual': true, 'intensity': 'medium'};
        r5 = [0.445, 0.628];
        r1['value'] = r5;
        r4[1] = r1;
        r1 = {'name': 'rhythm', 'value': null, 'change': 0.183, 'fx_activation': 0.245, 'rank': 3, 'visual': true, 'intensity': 'medium'};
        r5 = [0.442, 0.625];
        r1['value'] = r5;
        r4[2] = r1;
        r1 = {'name': 'high_freq', 'value': null, 'change': 0.177, 'fx_activation': 0.408, 'rank': 4, 'visual': false};
        r5 = [0.402, 0.579];
        r1['value'] = r5;
        r4[3] = r1;
        r1 = {'name': 'space_size', 'value': null, 'change': 0.164, 'fx_activation': 0.32, 'rank': 5, 'visual': false};
        r5 = [0.418, 0.582];
        r1['value'] = r5;
        r4[4] = r1;
        r1 = {'name': 'vocal', 'value': null, 'change': 0.11, 'fx_activation': 0.425, 'rank': 6, 'visual': false};
        r5 = [0.743, 0.853];
        r1['value'] = r5;
        r4[5] = r1;
        r1 = {};
        r1['dimensions'] = r4;
        r1['copywriting'] = r3;
        r2['EMPTY_VISUAL_EXTRA'] = r1;
        return r0;
    };
factories[967]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r16 = {};
        r0 = '狂欢是迪斯科节奏';
        r16['keyword'] = r0;
        r0 = {'high_freq+richness': '饱满和声交织清透高频，清晰解析力让音色充满张力，尽享迪斯科狂欢', 'loudness+richness': '厚重和声配合充沛响度增益，强劲声压带来胸腔共鸣，身体随之摇摆', 'rhythm+richness': '饱满和声紧随迪斯科节奏律动，干脆的瞬态响应敲击心口，释放狂欢力量', 'richness+space_size': '多轨和声填满宽广空间混响，饱满的声学纵深让狂欢氛围在舞池中蔓延', 'richness+stereo_width': '宽广立体声场将厚重和声推向两侧，声像张力饱满，双耳尽享派对狂欢', 'richness+vocal_1': '质感人声与多轨和声频段交融，扎实结像力让迪斯科狂欢极具感染力', 'richness+vocal_2': '厚重和声稳稳托举核心人声，中频饱满共鸣带来极致张力，快乐自然蔓延', 'richness+vocal_3': '多层和声紧密包裹质感人声，出色的频段融合让狂欢情绪直达心底', 'richness+vocal_4': '扎实和声与人声产生声学共振，力量层层叠加，热烈派对氛围瞬间点燃', 'richness+vocal_5': '澎湃和声环绕质感人声，厚实声音底盘让满腔狂欢与快乐被稳稳托起', 'high_freq+loudness_1': '清亮高频配合饱满响度，扎实声压让音浪升腾而起，狂欢心绪被瞬间点燃', 'high_freq+loudness_2': '高频泛音清晰配合平稳响度动态，迪斯科狂欢在细腻声压中尽情释放', 'high_freq+rhythm_1': '通透高频紧扣干脆节奏律动，明快瞬态响应声声入耳，让身体自动狂舞', 'high_freq+rhythm_2': '明亮高频随强力鼓点节奏跃出，精准卡点推高声浪，狂欢情绪不断翻涌', 'high_freq+rhythm_3': '清脆高频点缀重拍节奏，清晰的动态响应精准传达热忱，感官随之沸腾', 'high_freq+space_size': '鲜明高频穿透开阔空间混响，自然泛音四处漫溢，打造全景迪斯科舞池', 'high_freq+stereo_width_1': '清亮高频在立体声场极速穿梭，适度声像拉扯让狂欢张力变得更为浓烈', 'high_freq+stereo_width_2': '干净高频在宽广立体声场游走，平稳相位控制让流光音符环绕双耳', 'high_freq+vocal_1': '高频解析力让人声更具穿透力，清亮结像力助情绪冲破听觉界限', 'high_freq+vocal_2': '高频增益提亮人声穿透感，明朗声线搭配干净频段，句句饱含狂欢张力', 'high_freq+vocal_3': '通透的高频人声，饱满声音能量让快乐随音浪迸发，欢愉在空气中弥漫', 'high_freq+vocal_4': '清晰高频伴随核心人声走向高潮，自然高音延展让狂欢在旋律中升温', 'high_freq+vocal_5': '细腻高频人声展现清晰质感，扎实声音力量自然流露，唤醒狂欢细胞', 'loudness+rhythm_1': '饱满响度驱动迪斯科节奏律动，扎实声压叩击感官，狂欢情绪稳步攀升', 'loudness+rhythm_2': '充沛响度拉满节奏张力，有力的低频下潜带来胸腔共振，身体随之摇摆', 'loudness+space_size': '扎实响度填满开阔空间混响，音波震荡自然扩散，饱满狂欢气场笼罩四周', 'loudness+stereo_width': '充沛响度横贯宽广立体声场，全景环绕音浪将澎湃狂欢情绪恰如其分放大', 'loudness+vocal_1': '核心人声随饱满响度呐喊，合理的声压级带来坚实力量感，掀动全场', 'loudness+vocal_2': '质感人声配合平稳的响度增益，厚重声线极具穿透力，将狂欢尽情传达', 'loudness+vocal_3': '充足响度赋予人声扎实爆发力，胸腔共鸣声效让旋律充满炽热派对信念', 'rhythm+space_size': '强劲节奏鼓点填满广阔空间混响，清晰声学纵深让狂欢情绪自然奔涌', 'rhythm+stereo_width': '紧凑节奏在立体声场左右交替，清晰律动全方位席卷，身体被节拍掌控', 'space_size+stereo_width': '开阔空间混响叠加全景立体声场，音浪全方位自然席卷，声学气场饱满', 'space_size+vocal_1': '质感人声在宽广空间混响中回荡，雄浑延音自然扩散，将狂欢氛围拉满', 'space_size+vocal_2': '穿透人声于空间混响中发力，声学共鸣平稳奔腾，炽烈派对情绪感染全场', 'stereo_width+vocal_1': '质感人声在宽广立体声场舒展，精准声像定位让狂欢更具物理听觉冲击', 'stereo_width+vocal_2': '核心人声在宽广立体声场铺展，饱满环绕共鸣将派对热情毫无保留地传递', 'stereo_width+vocal_3': '宽阔立体声场平稳托举饱满人声，声压多维扩散，让狂欢音浪扑面而来', 'rhythm+vocal': '密集鼓点与炽烈人声交锋，律动裹挟歌声推向顶点，身体为狂欢而生'};
        r16['dimPairs'] = r0;
        r15 = {};
        r0 = '热血是音浪翻滚';
        r15['keyword'] = r0;
        r0 = {'high_freq+richness': '饱满和声交织清透高频，清晰解析力让音色充满张力，热血感随旋律涌现', 'loudness+richness': '厚重和声配合扎实响度，平稳有力的声压带来胸腔共鸣，情绪随之攀升', 'rhythm+richness': '饱满和声紧随节奏律动起伏，干净的瞬态响应敲击心口，释放坚定力量', 'richness+space_size': '多轨和声填满宽广空间混响，饱满的声学纵深让热血情绪从心底稳步升腾', 'richness+stereo_width': '宽广立体声场将厚重和声推向两侧，声像分布均匀，双耳被热血张力充盈', 'richness+vocal_1': '质感人声与多轨和声频段交融，结像力扎实，让每句呐喊都充满向上力量', 'richness+vocal_2': '厚重和声稳稳托举核心人声，中频共鸣饱满，让热忱在坚定的旋律中燃烧', 'richness+vocal_3': '多层和声紧密包裹质感人声，出色的频段融合让副歌情绪张力直达心底', 'richness+vocal_4': '扎实和声与核心人声产生声学共振，力量层层叠加，热血情绪自然释放', 'richness+vocal_5': '澎湃和声环绕质感人声，厚实的声音底盘让满腔热血与斗志被稳稳点燃', 'high_freq+loudness_1': '清亮高频配合饱满响度，扎实声压让音浪升腾而起，心绪被这股力量点燃', 'high_freq+loudness_2': '高频泛音清晰配合平稳响度动态，热血在细腻声压中展现出坚定力量', 'high_freq+rhythm_1': '通透高频紧扣干脆节奏律动，明快的瞬态响应声声入耳，让情绪升温', 'high_freq+rhythm_2': '明亮高频随强力节奏律动跃出，精准卡点推高声浪，热血情绪不断翻涌', 'high_freq+rhythm_3': '清脆高频点缀重拍节奏，清晰的动态响应精准传达热忱，感官随之升温', 'high_freq+space_size': '鲜明高频穿透开阔空间混响，自然泛音四处漫溢，激昂的声学氛围拉满', 'high_freq+stereo_width_1': '清亮高频在立体声场穿梭，适度的声像拉扯让情绪张力变得更为浓烈', 'high_freq+stereo_width_2': '干净高频在宽广立体声场游走，平稳相位控制让激荡锋芒在双耳展现', 'high_freq+vocal_1': '高频解析力让人声更具穿透力，清亮的结像力助情绪冲破听觉界限', 'high_freq+vocal_2': '高频增益提亮人声穿透感，明朗声线搭配干净频段，句句饱含真实张力', 'high_freq+vocal_3': '通透的高频人声，饱满的声音能量让热忱随音浪迸发，激情在空气中弥漫', 'high_freq+vocal_4': '清晰高频伴随核心人声走向高潮，自然的高音延展让热血在旋律中升温', 'high_freq+vocal_5': '高频人声展现出清晰质感，扎实的声音力量自然流露，唤醒心底热爱', 'loudness+rhythm_1': '饱满响度驱动节奏律动，扎实声压铸成重拍叩击感官，情绪随之稳步攀升', 'loudness+rhythm_2': '充沛响度拉满节奏张力，有力的低频下潜带来胸腔共振，热血随之沸腾', 'loudness+space_size': '扎实响度填满开阔空间混响，音波震荡自然扩散，饱满气场笼罩四方', 'loudness+stereo_width': '充沛响度横贯宽广立体声场，环绕音浪将澎湃情绪恰如其分地传递放大', 'loudness+vocal_1': '核心人声随饱满响度呐喊，合理的声压级带来坚实力量感，字句铿锵', 'loudness+vocal_2': '质感人声配合平稳的响度增益，厚重声线极具穿透力，将热血尽情传达', 'loudness+vocal_3': '充足响度赋予人声扎实爆发力，胸腔共鸣声效让歌词充满炽热信念', 'rhythm+space_size': '强劲节奏鼓点填满广阔空间混响，清晰声学纵深让亢奋情绪自然奔涌', 'rhythm+stereo_width': '紧凑节奏在立体声场左右交替，清晰律动全方位席卷，听觉感官被唤醒', 'rhythm+vocal': '质感人声紧扣鼓点节奏呐喊，干脆瞬态让声线与律动相融，推高激昂情绪', 'space_size+stereo_width': '开阔空间混响叠加全景立体声场，音浪全方位自然席卷，声学气场饱满', 'space_size+vocal_1': '质感人声在宽广空间混响中回荡，雄浑延音自然扩散，将激昂氛围拉满', 'space_size+vocal_2': '穿透人声于空间混响中发力，声学共鸣平稳奔腾，炽烈情绪感染全场', 'stereo_width+vocal_1': '质感人声在宽广立体声场舒展，精准声像定位让呐喊更具物理听觉冲击', 'stereo_width+vocal_2': '核心人声在宽广立体声场铺展，饱满环绕共鸣将热情毫无保留地传递', 'stereo_width+vocal_3': '宽阔立体声场平稳托举饱满人声，声压多维扩散，让炽热音浪扑面而来'};
        r15['dimPairs'] = r0;
        r14 = {};
        r0 = '活力是向上的光';
        r14['keyword'] = r0;
        r0 = {'high_freq+richness': '明快和声交织清透高频，高解析力带来灵动听感，身体随轻盈音浪摇摆', 'loudness+richness': '饱满和声配合干脆响度，扎实的动态响应带来极佳弹跳感，注入活力', 'rhythm+richness': '跃动和声紧跟轻快节奏律动，明晰的瞬态响应让人忍不住随之摇摆', 'richness+space_size': '轻快和声融入通透空间混响，明亮的声学纵深让活力情绪随节拍轻跃', 'richness+stereo_width': '灵动和声在立体声场中舒展，均匀的声像分布让活力向双耳轻快蔓延', 'richness+vocal_1': '轻快人声与灵动和声自然交融，极具弹性的频段处理引人随旋律舞动', 'richness+vocal_2': '灵动和声平稳托举清透人声，清晰中频结像让活力伴随轻快节拍流露', 'richness+vocal_3': '轻快和声环绕饱满人声上扬，鲜活的声频能量让快乐情绪随节奏跳跃', 'richness+vocal_4': '跃动和声与清透人声细腻呼应，十足的声学弹性让周身细胞随之苏醒', 'richness+vocal_5': '饱满和声包裹人声向上攀升，扎实而灵动的声音底盘让旋律充满活力', 'high_freq+loudness_1': '清脆高频配合利落响度动态，明快的声压变化让细碎音浪在耳畔跃动', 'high_freq+loudness_2': '清透高频融入轻快响度起伏，平稳且富有弹性的声压让音符如精灵跳动', 'high_freq+rhythm_1': '灵动高频精准契合轻快节奏，干脆的瞬态响应让感官随律动自然振奋', 'high_freq+rhythm_2': '明亮高频随欢快节奏律动弹跳，极佳的动态跟随让身体瞬间被节拍带动', 'high_freq+rhythm_3': '清脆高频点缀明快节奏鼓点，清晰的声学瞬态让周身充满向上跳动的力量', 'high_freq+space_size': '剔透高频在通透空间混响中折射，明亮的声学泛音让欢快氛围瞬间点燃', 'high_freq+stereo_width_1': '灵动高频在立体声场中轻快跳跃，鲜活的声像张力让活力从四面轻盈涌来', 'high_freq+stereo_width_2': '清亮高频在宽广立体声场游走，明快的声音结像让阳光般的活力洒满全场', 'high_freq+vocal_1': '高频解析力让人声清亮上扬，轻快弹韧的音色质感让内心的活力即刻迸发', 'high_freq+vocal_2': '高频增益让人声更为清透欢快，干净利落的频段处理让句句歌词鲜活跃动', 'high_freq+vocal_3': '通透的高频人声伴随旋律上扬，明快的声学能量让鲜活生命力自然绽放', 'high_freq+vocal_4': '高频人声展现出清脆明亮的质感，自然灵动声音解析让快乐在音符间跳跃', 'high_freq+vocal_5': '细腻高频提亮轻快人声，扎实的声学底色摒弃毛刺感，让声声都充满活力', 'loudness+rhythm_1': '扎实响度驱动紧凑节奏律动，干脆的动态响应让重拍落处充满踏舞冲动', 'loudness+rhythm_2': '稳健响度拉满欢快节奏张力，利落的声压控制让身体随律动自然轻摇', 'loudness+space_size': '饱满响度填满明亮空间混响，自然扩散的声压张力让身心被温暖活力包裹', 'loudness+stereo_width': '充沛响度横贯宽广立体声场，饱满的环绕动态让活力向四面八方轻快迸发', 'loudness+vocal_1': '轻快人声配合扎实响度释出，恰到好处的声压级带来明朗且充满弹性听感', 'loudness+vocal_2': '饱满人声配合平稳响度增益，干脆利落的声线极具穿透力，让活力自然绽放', 'loudness+vocal_3': '充足响度赋予人声轻快爆发力，清晰的胸腔共鸣让句句充满鲜活跳动能量', 'rhythm+space_size': '轻快节奏鼓点填满通透空间混响，明亮的声学纵深让向上活力自然蔓延', 'rhythm+stereo_width': '灵动节奏在立体声场左右交替，清晰律动全方位席卷，唤醒轻快的脚步', 'rhythm+vocal': '质感人声紧扣轻快鼓点节奏，极佳瞬态让声线与律动相融，推高向上的活力', 'space_size+stereo_width': '通透空间混响叠加全景立体声场，欢快音浪自然席卷，营造饱满向上的气场', 'space_size+vocal_1': '清透人声在明亮空间混响中回荡，轻盈延音自然扩散，将活力氛围悄然拉满', 'space_size+vocal_2': '欢快人声于空间混响中轻盈发力，平稳的声学共振让向上的情绪感染全场', 'stereo_width+vocal_1': '灵动人声在宽广立体声场舒展，精准声像定位让轻快旋律更具物理听觉弹力', 'stereo_width+vocal_2': '清透人声在宽广立体声场铺展，饱满明亮的环绕共鸣将向上的活力温柔传递', 'stereo_width+vocal_3': '宽阔立体声场平稳托举轻快人声，声响多维扩散，让鲜活跳动能量扑面而来'};
        r14['dimPairs'] = r0;
        r13 = {};
        r0 = '愉悦是跳跃的鼓点';
        r13['keyword'] = r0;
        r0 = {'high_freq+richness': '明快和声交织清透高频，细腻解析力让音符充满弹性，快乐在旋律间闪烁', 'loudness+richness': '饱满和声配合轻快响度起伏，平稳的动态控制让愉悦心情随声学张力蔓延', 'rhythm+richness': '跃动和声紧随轻快节奏，干脆的瞬态律动带来欢快听感，满心欢喜自然流露', 'richness+space_size': '明亮和声融入宽广空间混响，通透的声学纵深让喜悦情绪洒满每个角落', 'richness+stereo_width': '细腻和声被宽广立体声场收拢，均匀的声像分布让轻盈愉悦在双耳萦绕', 'richness+vocal_1': '轻快人声与灵动和声无缝交融，顺滑的频段处理让句句歌词满含清甜欢喜', 'richness+vocal_2': '柔美和声平稳托举清透人声，极佳的声部融合度让快乐层层渐浓，明媚温柔', 'richness+vocal_3': '明丽和声环绕饱满人声上扬，清晰的中频结像让轻快的心情忍不住溢出来', 'richness+vocal_4': '跃动和声与清透人声细腻呼应，十足的声学弹性让鲜活愉悦在耳畔持续萦绕', 'richness+vocal_5': '饱满和声轻盈包裹人声，扎实而灵动的声音底盘让满心愉悦缓缓溢出', 'high_freq+loudness_1': '清透高频配合明快响度渐变，丝滑的声压控制让跳跃音符如星光般闪烁', 'high_freq+loudness_2': '晶莹高频融入轻快响度起伏，柔和的动态张力瞬间点亮好心情，鲜活通透', 'high_freq+rhythm_1': '灵动高频精准契合轻快节奏，明快的瞬态响应让人忍不住随律动轻轻摇曳', 'high_freq+rhythm_2': '明亮高频随欢快节奏跳跃，极佳的动态跟随让快乐情绪随韵律不断上扬', 'high_freq+rhythm_3': '清脆高频点缀明快节奏鼓点，清晰的声学瞬态让鲜活愉悦自然蔓延周身', 'high_freq+space_size': '剔透高频在通透空间混响中折射，明亮的声学泛音让喜悦氛围四散蔓延', 'high_freq+stereo_width_1': '灵动高频在宽广立体声场跳跃，鲜活的声像张力让欢喜情绪轻快扑面而来', 'high_freq+stereo_width_2': '清亮高频在立体声场游走，明快的声音结像让快乐音符精准环绕双耳', 'high_freq+vocal_1': '高频解析力让人声清亮上扬，轻快弹韧的音色质感让甜润情绪直达心底', 'high_freq+vocal_2': '高频增益提亮人声穿透感，干净利落的频段处理让快乐随明媚旋律绽放', 'high_freq+vocal_3': '细腻高频映衬清透人声，灵巧的声频能量让好心情在跳脱的音符间溢满', 'high_freq+vocal_4': '清脆高频伴随核心人声上扬，自然灵动的高音延展让愉悦藏于每个音节', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮的修音质感让轻快的好心情蹦跳出来', 'loudness+rhythm_1': '扎实响度驱动轻快节奏律动，平稳的动态响应让重拍落处尽是温柔欢喜', 'loudness+rhythm_2': '饱满响度拉满欢快节奏张力，利落的声压控制让活泼快乐的情绪绵延不止', 'loudness+space_size': '明丽响度填满通透空间混响，自然扩散的声压张力让轻快喜悦充盈全场', 'loudness+stereo_width': '明快响度横贯宽广立体声场，饱满的环绕动态让愉悦向四面八方温润包裹', 'loudness+vocal_1': '饱满人声配合平稳响度释出，恰到好处的声压级让好心情随歌声层层溢出', 'loudness+vocal_2': '质感人声配合明快响度增益，干脆利落的声线极具活力，让快乐尽情舒展', 'loudness+vocal_3': '充足响度赋予人声轻快爆发力，清晰的共鸣让欢快明亮的情绪自然绽放', 'rhythm+space_size': '轻快节奏鼓点填满明亮空间混响，通透的声学纵深让温柔喜悦自然漫散', 'rhythm+stereo_width': '灵动节奏在立体声场左右交替，清晰律动全方位席卷，身心随快乐轻摇', 'rhythm+vocal': '质感人声紧扣轻快节奏律动，极佳瞬态让声线与节拍相融，喜悦缓缓攀升', 'space_size+stereo_width': '通透空间混响叠加宽广立体声场，欢快音浪全方位自然席卷，氛围明媚温润', 'space_size+vocal_1': '清透人声在明亮空间混响中回荡，轻盈延音自然扩散，好心情在静静酝酿', 'space_size+vocal_2': '欢快人声于空间混响中轻盈发力，平稳的声学共振让治愈喜悦洒满四周', 'stereo_width+vocal_1': '灵动人声在宽广立体声场舒展，精准声像定位让听觉被轻快明丽温柔环抱', 'stereo_width+vocal_2': '宽阔立体声场平稳托举清透人声，饱满的声频环绕将鲜活愉悦送达心底', 'stereo_width+vocal_3': '质感人声在宽广立体声场铺展，明亮的声音结像让快乐情绪满溢于心间'};
        r13['dimPairs'] = r0;
        r12 = {};
        r0 = '甜蜜悬浮在粉色中';
        r12['keyword'] = r0;
        r0 = {'high_freq+richness': '柔美和声交织清透高频，高解析力带来丝滑听感，如坠入一片粉色云海', 'loudness+richness': '温润和声配合平滑的响度起伏，细腻的动态控制让甜意轻绕在耳畔', 'rhythm+richness': '细腻和声紧随舒缓节奏律动，柔和的瞬态响应让心绪在甜蜜中融化', 'richness+space_size': '柔美和声融入通透空间混响，细腻的声学纵深让人如坠粉色丝绒梦境', 'richness+stereo_width': '甜美和声在立体声场中舒展，精准声像定位如爱人在耳畔温柔低语', 'richness+vocal_1': '甜美人声与柔和和声无缝交融，顺滑频段处理让句句歌词甜润至心底', 'richness+vocal_2': '细腻和声平稳托举清透人声，极佳的声部融合让空气裹满粉色甜蜜', 'richness+vocal_3': '柔甜和声环绕饱满人声，清晰的中频结像带来梦幻氛围，让心跳变轻柔', 'richness+vocal_4': '柔美和声与清透人声细腻交织，十足的声学弹性让双耳被爱意温柔环抱', 'richness+vocal_5': '轻盈和声包裹甜美人声，扎实而柔和的声音底盘让满溢的浪漫温柔散开', 'high_freq+loudness_1': '清透高频配合细腻响度渐变，丝滑的声压控制让甜意在耳畔轻轻散开', 'high_freq+loudness_2': '柔和高频融入轻盈响度起伏，平稳的动态张力像含住一口融化的粉色糖果', 'high_freq+rhythm_1': '丝滑高频精准契合舒缓节奏，轻柔的瞬态响应让心绪随甜蜜律动摇曳', 'high_freq+rhythm_2': '明亮高频随轻柔节奏律动弹跳，极佳的动态跟随让甜蜜浪漫氛围蔓延', 'high_freq+rhythm_3': '清透高频点缀舒缓节奏鼓点，清晰的声学瞬态让软糯甜意悄然漫溢', 'high_freq+space_size': '剔透高频在通透空间混响中折射，柔和的声学泛音让粉色甜意无处不在', 'high_freq+stereo_width_1': '灵动高频在宽广立体声场游走，柔和的声像张力让甜蜜气息环绕双耳', 'high_freq+stereo_width_2': '清亮高频在立体声场中轻闪，明快的声音结像让粉色浪漫气息满溢', 'high_freq+vocal_1': '高频解析力让人声柔美上扬，软糯的音色质感像舌尖触及蜜糖般温柔', 'high_freq+vocal_2': '高频增益让人声更为清甜明亮，干净顺滑的频段处理如精灵在耳畔吟唱', 'high_freq+vocal_3': '细腻高频映衬清透人声，柔和的声频能量让心绪在柔软旋律中缓缓融化', 'high_freq+vocal_4': '清透高频伴随甜美人声上扬，自然灵动的高音延展让浪漫在耳畔盛开', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮的修音质感让清甜字句温柔动心', 'loudness+rhythm_1': '柔和响度配合舒缓节奏律动，平稳的动态响应让爱意随韵律轻轻流淌', 'loudness+rhythm_2': '温软响度拉满舒缓节奏张力，细腻的声压控制让粉色幸福感漫溢全身', 'loudness+space_size': '柔和响度填满通透空间混响，自然扩散的声压张力让身体浮在粉色云海', 'loudness+stereo_width': '轻柔响度横贯宽广立体声场，饱满的环绕动态让缱绻甜意温柔治愈人心', 'loudness+vocal_1': '甜美人声配合平稳响度释出，恰到好处的声压级让软糯甜意抵达心底', 'loudness+vocal_2': '质感人声配合柔和响度增益，丝滑的声线极具感染力，满含浪漫气韵', 'loudness+vocal_3': '充足响度赋予人声柔和爆发力，清晰的共鸣让清甜温婉的情绪治愈动人', 'rhythm+space_size': '舒缓节奏律动填满明亮空间混响，通透的声学纵深让粉色甜意轻轻弥散', 'rhythm+stereo_width': '柔和节奏在立体声场左右交替，清晰律动全方位席卷，心绪被悄然柔化', 'rhythm+vocal': '甜美人声紧扣舒缓节奏律动，极佳瞬态让声线与节拍相融，甜意静静流淌', 'space_size+stereo_width': '通透空间混响叠加宽广立体声场，柔和音浪自然席卷，浪漫气息绵长', 'space_size+vocal_1': '清透人声在柔和空间混响中回荡，轻盈延音自然扩散，心绪被安然治愈', 'space_size+vocal_2': '甜美人声于空间混响中轻盈发力，平稳的声学共振让人如悬停粉色云间', 'stereo_width+vocal_1': '甜美人声在宽广立体声场舒展，精准声像定位让双耳被浪漫轻轻环抱', 'stereo_width+vocal_2': '宽阔立体声场平稳托举清透人声，饱满的声频环绕将温甜柔婉送达心底', 'stereo_width+vocal_3': '质感人声在宽广立体声场铺展，明亮的声音结像让清甜柔润入骨绵长'};
        r12['dimPairs'] = r0;
        r11 = {};
        r0 = '自信放光芒';
        r11['keyword'] = r0;
        r0 = {'high_freq+richness': '稳健和声交织清透高频，高解析力让音色充满张力，沉静中自带从容微光', 'loudness+richness': '扎实和声配合平稳响度控制，宽广的动态范围让自信底气藏于每段旋律', 'rhythm+richness': '稳健和声紧随坚定节奏律动，极佳的瞬态响应让旋律自带从容气度', 'richness+space_size': '层次和声填满开阔空间混响，饱满的声学纵深带来笃定从容的存在感', 'richness+stereo_width': '饱满和声在立体声场中舒展，精准的声像分布让沉稳自信气质缓缓流露', 'richness+vocal_1': '质感人声与稳健和声频段交融，扎实结像力让句句歌词从容而有力量', 'richness+vocal_2': '醇厚和声稳稳托举质感人声，出色的频段融合让内敛底气自然显现', 'richness+vocal_3': '扎实和声环绕饱满人声，清晰的中频结像带来坚定有力的从容气场', 'richness+vocal_4': '稳健和声与质感人声细腻交织，极佳的频段控制力让自信光芒自然流露', 'richness+vocal_5': '饱满和声稳步托举质感人声，扎实的声音底盘让自信在每段吟唱中发光', 'high_freq+loudness_1': '清透高频配合平稳响度控制，细腻的声压张力让内敛锋芒自成气场', 'high_freq+loudness_2': '顺滑高频融入稳健响度起伏，出色的动态压限带来不卑不亢的从容底气', 'high_freq+rhythm_1': '清澈高频精准契合坚定节奏，干净的瞬态响应让律动间尽显笃定姿态', 'high_freq+rhythm_2': '明亮高频随稳健节奏律动上扬，极佳的动态跟随让开阔自信悄然绽放', 'high_freq+rhythm_3': '细腻高频点缀稳健节奏鼓点，清晰的声学瞬态带来沉静自持的从容姿态', 'high_freq+space_size': '剔透高频在开阔空间混响中折射，自然的声学泛音让从容气度无限舒展', 'high_freq+stereo_width_1': '清亮高频在立体声场中均衡舒展，高解析张力让沉稳自信的锋芒耀眼', 'high_freq+stereo_width_2': '顺滑高频在宽广立体声场游走，精准的声音结像让开阔格局自然流露', 'high_freq+vocal_1': '高频解析力让人声清亮通透，扎实的音色结像让句句吟唱都坚定有力', 'high_freq+vocal_2': '高频增益提亮人声穿透感，干净利落的频段处理自带从容笃定的气度', 'high_freq+vocal_3': '细腻高频映衬质感人声，稳定的声频能量让强大底气深藏于清透声线', 'high_freq+vocal_4': '清澈高频伴随笃定人声舒展，自然的高频延展展现不疾不徐的从容光芒', 'high_freq+vocal_5': '顺滑高频消解人声杂质，自然透亮的修音质感尽显沉稳从容的强大气场', 'loudness+rhythm_1': '扎实响度驱动稳健节奏律动，平稳的动态响应让重拍落处尽显笃定底气', 'loudness+rhythm_2': '充沛响度拉满坚定节奏张力，利落的声压控制让从容步伐极具力量感', 'loudness+space_size': '稳健响度填满通透空间混响，自然扩散的声压张力让沉稳气场不彰自显', 'loudness+stereo_width': '均衡响度横贯宽广立体声场，饱满的环绕动态让内敛自信沉稳且舒展', 'loudness+vocal_1': '质感人声配合稳健响度释出，恰到好处的声压级让笃定声线自有从容风骨', 'loudness+vocal_2': '饱满人声配合克制响度增益，干脆利落的声线充满底气，自信藏于吟唱', 'loudness+vocal_3': '充足响度赋予人声扎实爆发力，平稳的共鸣让不动声色的从容锋芒尽显', 'rhythm+space_size': '稳健节奏鼓点填满开阔空间混响，清晰的声学纵深让从容自信自然铺展', 'rhythm+stereo_width': '坚定节奏在立体声场左右交替，清晰律动全方位席卷，带来沉稳自持感', 'rhythm+vocal': '质感人声紧扣稳健节奏律动，极佳瞬态让笃定声线与节拍相融，从容铺展', 'space_size+stereo_width': '开阔空间混响叠加全景立体声场，稳健音浪自然席卷，营造笃定从容气场', 'space_size+vocal_1': '质感人声在开阔空间混响中回荡，扎实延音自然扩散，气场沉静且安定', 'space_size+vocal_2': '笃定人声于空间混响中平稳发力，稳健的声学共振自带不动声色的锋芒', 'stereo_width+vocal_1': '稳健人声在宽广立体声场舒展，精准声像定位让从容自信的气场自然流露', 'stereo_width+vocal_2': '宽阔立体声场平稳托举笃定人声，饱满的声频环绕将大气沉静的威仪送达', 'stereo_width+vocal_3': '质感人声在宽广立体声场铺展，扎实的声音结像尽显从容，站在这就够了'};
        r11['dimPairs'] = r0;
        r10 = {};
        r0 = '浪漫是绽放的星';
        r10['keyword'] = r0;
        r0 = {'high_freq+richness': '温润和声交织清透高频，细腻解析力让音符如星芒闪烁，浪漫氛围环绕周身', 'loudness+richness': '温润和声配合平滑的响度控制，细腻动态让爱意于旋律间缱绻流淌', 'rhythm+richness': '温婉和声紧随舒缓节奏律动，平稳的瞬态响应让浪漫氛围感极尽绵长', 'richness+space_size': '柔美和声融入深邃空间混响，细腻的声学纵深让浪漫如夜空般向四周绵延', 'richness+stereo_width': '温润和声在宽广立体声场中铺展，精准声像定位让人置身温柔浪漫星海', 'richness+vocal_1': '质感人声与柔婉和声无缝交融，顺滑频段处理让温软声线满含心动与浪漫', 'richness+vocal_2': '细腻和声平稳托举清透人声，极佳声部融合似星子垂落，浪漫氛围顷刻拉满', 'richness+vocal_3': '柔婉和声环绕质感人声上扬，清晰中频结像如坠浩瀚星海，悠远绵长', 'richness+vocal_4': '温润和声与清透人声细腻交织，十足的声学弹性让浪漫悠远，满心尽温柔', 'richness+vocal_5': '轻盈和声包裹甜美人声，扎实而柔和的声音底盘让星辰为你浪漫陨落', 'high_freq+loudness_1': '清透高频配合柔和响度渐变，丝滑的声压控制让星芒般的浪漫于耳畔轻绕', 'high_freq+loudness_2': '柔亮高频融入温润响度起伏，平稳的动态张力带来细腻缱绻的浪漫氛围', 'high_freq+rhythm_1': '灵动高频精准契合舒缓节奏，轻柔的瞬态响应如星光般在律动中缓缓流淌', 'high_freq+rhythm_2': '明亮高频随轻柔节奏律动闪烁，极佳动态跟随让浪漫缱绻的心动悄然蔓延', 'high_freq+rhythm_3': '清透高频点缀舒缓节奏鼓点，清晰的声学瞬态让星光跃动般满含浪漫气息', 'high_freq+space_size': '剔透高频在深邃空间混响中折射，柔和的声学泛音如星芒散落于辽阔夜空', 'high_freq+stereo_width_1': '灵动高频在宽广立体声场轻闪，柔和声像张力让立体缱绻的浪漫环拥双耳', 'high_freq+stereo_width_2': '清亮高频在立体声场中游走，明快声音结像似星芒闪烁，和煦又浪漫', 'high_freq+vocal_1': '高频解析力让人声柔亮细腻，软糯音色质感让缱绻浪漫直抵心底', 'high_freq+vocal_2': '高频增益让人声更显清透，干净顺滑频段处理似被星光轻拥，浪漫绵长', 'high_freq+vocal_3': '细腻高频映衬清透人声，柔和声频能量如星光透亮，浪漫治愈满含温柔', 'high_freq+vocal_4': '清透高频伴随甜美人声舒展，自然灵动高音延展让浪漫缱绻氛围缓缓弥散', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮修音质感让悠远浪漫的心动藏于声线', 'loudness+rhythm_1': '柔和响度配合舒缓节奏律动，平稳动态响应让浪漫爱意随韵律轻轻流淌', 'loudness+rhythm_2': '温润响度拉满舒缓节奏张力，细腻声压控制让缱绻旋律里的浪漫缓缓蔓延', 'loudness+space_size': '温润响度填满深邃空间混响，自然扩散的声压张力让夜空般辽阔浪漫拉满', 'loudness+stereo_width': '轻柔响度横贯宽广立体声场，饱满环绕动态让人置身温雅缱绻的浪漫怀抱', 'loudness+vocal_1': '甜美人声配合平稳响度释出，恰到好处的声压级让缱绻声线满含浪漫爱意', 'loudness+vocal_2': '质感人声配合柔和响度增益，丝滑低吟极具感染力，让浪漫氛围悄然绽放', 'loudness+vocal_3': '充足响度赋予人声柔和张力，清晰共鸣让温润悠远的浪漫令人深深沉溺', 'rhythm+space_size': '舒缓节奏律动填满深邃空间混响，辽阔的声学纵深让温雅浪漫被缓缓诉说', 'rhythm+stereo_width': '柔和节奏在立体声场左右交替，清晰律动全方位席卷，带来辽阔和煦浪漫', 'rhythm+vocal': '甜美人声紧扣舒缓节奏律动，极佳瞬态让缱绻声线与节拍相融，浪漫流淌', 'space_size+stereo_width': '辽阔空间混响叠加全景立体声场，柔和音浪自然席卷，如星光点点极尽浪漫', 'space_size+vocal_1': '清透人声在深邃空间混响中回荡，轻盈延音自然扩散，似星子垂落温雅浪漫', 'space_size+vocal_2': '甜美人声于空间混响中轻盈发力，平稳声学共振让悠远浪漫的氛围感拉满', 'stereo_width+vocal_1': '甜美人声在宽广立体声场舒展，精准声像定位让双耳被立体缱绻的浪漫轻拥', 'stereo_width+vocal_2': '宽阔立体声场平稳托举清透人声，饱满声频环绕似星辰陨落，浪漫拨动心弦', 'stereo_width+vocal_3': '质感人声在宽广立体声场铺展，明亮声音结像尽显温柔，有颗星为你坠落'};
        r10['dimPairs'] = r0;
        r9 = {};
        r0 = '放松让时间融化';
        r9['keyword'] = r0;
        r0 = {'high_freq+richness': '温润和声交织柔和高频，细腻解析力让音符抚平疲惫，时间在旋律中放慢', 'loudness+richness': '舒展和声配合平滑的响度控制，细腻动态让柔软声韵轻缓流淌，带来放松', 'rhythm+richness': '绵长和声紧随慵懒节奏律动，平稳瞬态响应让心绪随音符渐静，时光融化', 'richness+space_size': '柔美和声融入通透空间混响，深邃的声学纵深让人如在静海之上悠然漂浮', 'richness+stereo_width': '温润和声在宽广立体声场舒展，均匀的声像分布让松弛感温柔轻裹周身', 'richness+vocal_1': '质感人声与温润和声无缝交融，平滑的频段处理让绵长声线尽显舒缓从容', 'richness+vocal_2': '细腻和声平稳托举清透人声，极佳的声部融合让心绪渐归平静，享受安然', 'richness+vocal_3': '柔和和声环绕慵懒人声，清晰的中频结像带来自在氛围，如海浪轻轻摇晃', 'richness+vocal_4': '温润和声与质感人声细腻交织，舒缓的频段控制让周身疲惫悄然消融散去', 'richness+vocal_5': '轻盈和声包裹舒缓人声，扎实而柔和的声音底盘让心境渐入安宁，全放松', 'high_freq+loudness_1': '柔敛高频配合平滑响度渐变，丝滑的声压控制消解锐感，只留温润声学回响', 'high_freq+loudness_2': '柔和高频融入轻缓响度起伏，平稳的动态张力带来干净声线，让身心舒展', 'high_freq+rhythm_1': '柔和高频精准契合慵懒节奏，舒缓的瞬态响应似浪花轻吻岸沿，时间放慢', 'high_freq+rhythm_2': '清透高频随舒缓节奏律动轻浮，极佳的动态跟随如海风徐徐拂面，抚平焦虑', 'high_freq+rhythm_3': '细腻高频点缀慵缓节奏鼓点，柔和的声学瞬态让时光随音符慢慢融化', 'high_freq+space_size': '柔敛高频在通透空间混响中折射，轻长的声学泛音让温润声韵缓缓漫开', 'high_freq+stereo_width_1': '柔和高频在宽广立体声场游走，平稳的声像张力让松弛感舒展地盈满双耳', 'high_freq+stereo_width_2': '清透高频在立体声场中轻漾，明快的声学结像让双耳被温柔气息环抱', 'high_freq+vocal_1': '高频解析力让人声空灵干净，柔和通透的音色质感让慵懒声线轻漾空气中', 'high_freq+vocal_2': '高频增益让人声清透空灵，顺滑的频段处理带来松弛自在，如浮海面轻摇', 'high_freq+vocal_3': '细腻高频映衬澄澈人声，柔和的声频能量让句句都充满慵懒治愈的质感', 'high_freq+vocal_4': '清润高频伴随空灵人声舒展，自然柔和的高音延展让烦绪被声音温柔抚平', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然纯净的修音质感让身心完全沉浸于松弛之境', 'loudness+rhythm_1': '柔和响度配合舒缓节奏律动，平稳的动态响应让重拍轻落，身心随之舒展', 'loudness+rhythm_2': '收敛响度拉满慵缓节奏张力，细腻的声压控制温柔安抚每一丝紧绷的心绪', 'loudness+space_size': '温润响度填满舒缓空间混响，自然扩散的声压张力让静谧治愈感充盈四周', 'loudness+stereo_width': '轻柔响度横贯宽广立体声场，饱满的环绕动态让声学包裹带来极致的松弛', 'loudness+vocal_1': '慵懒人声配合平稳响度释出，克制的声压级让内敛情绪深深沉浸于温柔中', 'loudness+vocal_2': '质感人声配合柔敛响度控制，丝滑舒缓的声线极具感染力，让疲惫融化', 'loudness+vocal_3': '温润响度赋予人声柔和张力，轻缓吟唱的共鸣让身心彻底沉浸在松弛治愈', 'rhythm+space_size': '慵懒节奏律动填满深邃空间混响，悠长的声学纵深让紧绷的时光悄然放缓', 'rhythm+stereo_width': '舒缓节奏在立体声场左右交替，柔和的律动全方位席卷，让身心渐渐放空', 'rhythm+vocal_1': '质感人声紧扣慵懒节奏律动，极佳瞬态让舒展声线与慢拍相融，如水流淌', 'rhythm+vocal_2': '舒缓人声随着柔和节奏自由流淌，平稳的声学下沉感让人心甘情愿全放松', 'space_size+stereo_width': '开阔空间混响叠加全景立体声场，柔和音浪自然环绕，卸下全身心的紧绷', 'space_size+vocal_1': '舒缓人声在开阔空间混响中回荡，轻长的延音自然扩散，心境渐归于安宁', 'space_size+vocal_2': '慵懒人声于空间混响中轻盈发力，平稳的声学共振让内心重获安谧与松弛', 'stereo_width+vocal_1': '舒缓人声在宽广立体声场舒展，精准声像定位让双耳被自然松弛感轻绕', 'stereo_width+vocal_2': '宽阔立体声场平稳托举柔和人声，饱满的声频环绕将治愈暖意温柔送达', 'stereo_width+vocal_3': '质感人声在宽广立体声场蔓延，松弛的声音结像让烦绪尽被温柔声浪化去'};
        r9['dimPairs'] = r0;
        r8 = {};
        r0 = '治愈是一抹绿';
        r8['keyword'] = r0;
        r0 = {'high_freq+richness': '温润和声交织柔和高频，细腻解析力让治愈音符如春风，不安被轻轻抚平', 'loudness+richness': '舒缓和声配合平滑的响度起伏，细腻的动态控制让疲惫在饱满旋律里消散', 'rhythm+richness': '绵软和声紧随轻缓节奏律动，平稳的瞬态响应让紧绷心绪被温柔治愈', 'richness+space_size': '柔美和声融入治愈空间混响，温暖声学纵深向四周舒展，将疲惫稳稳接住', 'richness+stereo_width': '温润和声在宽广立体声场铺展，均匀的声像分布让治愈的安全感蔓延', 'richness+vocal_1': '质感人声与温柔和声无缝交融，平滑的频段处理让每一句吟唱都被稳稳接住', 'richness+vocal_2': '细腻和声平稳托举清透人声，极佳声部融合带来柔软治愈，安放所有情绪', 'richness+vocal_3': '温润和声紧密包裹质感人声，清晰的中频结像带来安心舒缓，烦躁被抚平', 'richness+vocal_4': '柔和和声与清透人声细腻交织，平稳的声频控制让治愈感绵长，内心平静', 'richness+vocal_5': '饱满和声轻柔环绕治愈人声，扎实而温润的声音底盘让疲惫瞬间被卸下', 'high_freq+loudness_1': '柔敛高频配合平滑响度渐变，丝滑的声压控制消解刺耳感，只留安心治愈', 'high_freq+loudness_2': '温润高频融入舒缓响度起伏，平稳的动态张力温柔包裹所有情绪，带来安稳', 'high_freq+rhythm_1': '柔和高频精准契合安稳节奏，舒缓的瞬态响应让心绪跟着律动慢慢平静', 'high_freq+rhythm_2': '清透高频随舒缓节奏律动荡漾，极佳的动态跟随带来柔软治愈，消散不安', 'high_freq+rhythm_3': '细腻高频点缀舒缓节奏鼓点，柔和的声学瞬态如绿意铺展，将疲惫稳稳接住', 'high_freq+space_size': '剔透高频在通透空间混响中折射，明媚声学泛音在四周飘散，治愈安心感拉满', 'high_freq+stereo_width_1': '灵动高频在宽广立体声场游走，柔和的声像张力让治愈气息环绕双耳', 'high_freq+stereo_width_2': '清亮高频在立体声场中轻闪，明快而温暖的声音结像让治愈安全感十足', 'high_freq+vocal_1': '高频解析力让人声温润柔和，干净的音色结像让疲惫感被声浪轻轻抚平', 'high_freq+vocal_2': '高频增益让人声更加舒缓清透，顺滑的频段处理让内心重归安稳与平静', 'high_freq+vocal_3': '细腻高频映衬清透人声，柔和的声频能量带来温暖治愈，所有情绪都被接纳', 'high_freq+vocal_4': '清澈高频伴随治愈人声舒展，自然柔和的高音延展带来安心感，像被温柔抱住', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮的修音质感让绵软声音助你卸下紧绷', 'loudness+rhythm_1': '温和响度配合安稳节奏律动，平稳的动态响应让重拍轻落，每一拍都在治愈', 'loudness+rhythm_2': '温润响度拉满舒缓节奏张力，细腻的声压控制让治愈的安全感在旋律中蔓延', 'loudness+space_size': '柔和响度填满治愈空间混响，自然扩散的声压张力让内心重获安稳平静', 'loudness+stereo_width': '温和响度横贯宽广立体声场，饱满的环绕动态让治愈的温暖包裹全身', 'loudness+vocal_1': '治愈人声配合温润响度释出，恰到好处的声压级让所有不安都被吟唱抚平', 'loudness+vocal_2': '质感人声配合饱满响度增益，丝滑舒缓的声线极具包容力，慢慢安抚疲惫感', 'loudness+vocal_3': '温润响度赋予人声轻柔张力，平稳的共鸣让治愈安心的声浪将你稳稳接住', 'rhythm+space_size': '轻缓节奏律动填满深邃空间混响，柔和的声学纵深让治愈氛围蔓延，内心平静', 'rhythm+stereo_width': '舒缓节奏在立体声场左右扩散，柔和的律动全方位席卷，被立体治愈包围', 'rhythm+vocal': '质感人声紧扣轻柔节奏律动，极佳瞬态让舒缓声线与节拍相融，治愈小情绪', 'space_size+stereo_width': '治愈空间混响叠加全景立体声场，柔和音浪自然环绕，带来宁静安心的包裹', 'space_size+vocal_1': '舒缓人声在通透空间混响中回荡，柔和延音自然扩散，治愈余韵让心绪平静', 'space_size+vocal_2': '治愈人声于空间混响中温柔回荡，平稳声学共振带来温暖安心，被稳稳接住', 'stereo_width+vocal_1': '治愈人声在宽广立体声场舒展，精准声像定位让轻柔环绕感拉满治愈安全感', 'stereo_width+vocal_2': '宽阔立体声场平稳托举柔和人声，饱满声频环绕形成温柔包裹，治愈疲惫', 'stereo_width+vocal_3': '质感人声在宽广立体声场蔓延，柔和声音结像带来温暖治愈，被声音稳稳抱住'};
        r8['dimPairs'] = r0;
        r7 = {};
        r0 = '平静是风止步';
        r7['keyword'] = r0;
        r0 = {'high_freq+richness': '温润和声交织柔和高频，细腻解析力收净毛刺感，让心绪如无风水面般平静', 'loudness+richness': '清淡和声配合克制平稳的响度起伏，细腻动态控制让一切都归于宁静', 'rhythm+richness': '舒展和声紧随平缓节奏律动，柔和的瞬态响应让浮躁的心慢慢静下来', 'richness+space_size': '柔和和声融入静谧空间混响，深邃的声学纵深如湖面般安宁，不起波澜', 'richness+stereo_width': '温润和声在宽广立体声场均衡铺展，平稳声像让人沉浸于安静与从容中', 'richness+vocal_1': '质感人声与平和和声无缝交融，顺滑频段处理让宁静声线如水般平稳', 'richness+vocal_2': '细腻和声平稳托举清透人声，极佳声部融合让内心的浮躁一点点沉淀', 'richness+vocal_3': '温润和声紧密包裹质感人声，清晰中频结像带来匀净舒缓，内心无涟漪', 'richness+vocal_4': '平和和声与清透人声细腻交织，极佳的声学控制力让万物归于静谧', 'richness+vocal_5': '柔和和声环绕澄澈人声，扎实而平静的声音底盘让心境如湖面映月', 'high_freq+loudness_1': '柔敛高频配合平稳响度渐变，丝滑的声压控制消解锐感，只剩平和安宁', 'high_freq+loudness_2': '净透高频融入轻柔响度起伏，平稳的动态张力让心绪静如止水映天', 'high_freq+rhythm_1': '柔和高频精准契合匀净节奏，舒缓的瞬态响应让心神慢慢归于平静', 'high_freq+rhythm_2': '清透高频随平缓节奏律动流淌，极佳的动态跟随带来静如湖面的安稳', 'high_freq+rhythm_3': '细腻高频点缀平缓节奏鼓点，柔和的声学瞬态让内心再无一丝波澜', 'high_freq+space_size': '剔透高频在静谧空间混响中折射，轻柔的声学泛音让平和氛围淡然铺开', 'high_freq+stereo_width_1': '柔和高频在立体声场中轻缓游走，平稳的声像张力带来极致的宁静平和', 'high_freq+stereo_width_2': '清亮高频在宽广立体声场游走，干净的声音结像打造无风湖面般的静谧', 'high_freq+vocal_1': '高频解析力让人声温润净透，干净的音色结像带来心静无扰的平和感', 'high_freq+vocal_2': '高频增益让人声更加清透，顺滑的频段处理让烦躁在宁静中慢慢消散', 'high_freq+vocal_3': '细腻高频映衬质感人声，克制的声频能量让淡然心湖保持一片平静', 'high_freq+vocal_4': '清澈高频伴随宁静人声舒展，自然的高音延展让万物都在声浪中静下来', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮的修音质感尽显恰到好处的淡然平和', 'loudness+rhythm_1': '清淡响度配合平稳节奏律动，极佳的动态响应让每一声都安静从容', 'loudness+rhythm_2': '温润响度拉满平和节奏张力，细腻的声压控制让内心静如无风湖面', 'loudness+space_size': '柔和响度填满静谧空间混响，自然扩散的声压张力让内心平静无波澜', 'loudness+stereo_width': '平稳响度横贯宽广立体声场，克制的环绕动态让治愈的安静感平和蔓延', 'loudness+vocal_1': '宁静人声配合克制响度释出，恰到好处的声压级让心绪平稳淡然', 'loudness+vocal_2': '质感人声配合温润响度增益，丝滑舒缓的声线极具包容力，让内心平静', 'loudness+vocal_3': '平稳响度赋予人声柔和张力，清淡的共鸣让静谧安稳如小舟停泊静港湾', 'rhythm+space_size': '平缓节奏律动填满静谧空间混响，悠长的声学纵深让安宁心湖保持平静', 'rhythm+stereo_width': '匀净节奏在立体声场左右扩散，柔和的律动全方位席卷，带来安静淡然', 'rhythm+vocal': '质感人声紧扣平缓节奏律动，极佳瞬态让宁静声线与节拍相融，心静如水', 'space_size+stereo_width': '静谧空间混响叠加全景立体声场，柔和音浪自然环绕，安宁如无风湖面', 'space_size+vocal_1': '宁静人声在通透空间混响中回荡，柔和延音自然扩散，心绪平和无波澜', 'space_size+vocal_2': '质感人声于空间混响中轻漾，平稳的声学共振带来淡然宁静，风止水静', 'stereo_width+vocal_1': '质感人声在宽广立体声场舒展，精准声像定位让平和的宁静感安稳环绕', 'stereo_width+vocal_2': '宽阔立体声场平稳托举清淡人声，舒展的声频环绕带来静得心安的平和', 'stereo_width+vocal_3': '质感人声在宽广立体声场蔓延，柔和的声音结像带来无风湖面般的平静'};
        r7['dimPairs'] = r0;
        r6 = {};
        r0 = '思念藏在歌里';
        r6['keyword'] = r0;
        r0 = {'high_freq+richness': '温暖和声交织清透高频，高解析力将回忆细腻还原，思念在绝美音色中浮现', 'loudness+richness': '醇厚和声配合细腻响度起伏，温润动态控制让思念如潮水般温柔涌上心头', 'rhythm+richness': '绵密和声契合舒缓节奏律动，平稳的听感让往日时光随音符慢慢浮现', 'richness+space_size': '温润和声融入宽广空间混响，悠长的声学余韵将思念拉长，满是旧时温柔', 'richness+stereo_width': '细腻和声被精准立体声场收拢，环绕包裹的质感让思念在心底温柔缠绕', 'richness+vocal_1': '质感人声与唯美和声无缝交融，深情声线将思念的高级质感完美呈现', 'richness+vocal_2': '柔美和声层层包裹温润人声，极佳的声部融合度让回忆在耳畔温柔打转', 'richness+vocal_3': '醇厚和声稳稳托起清透人声，专业声学层次让怀念的旧时光涌上心头', 'richness+vocal_4': '立体和声与深情人声细腻交织，完美的频段贴合让绵长思念娓娓道来', 'richness+vocal_5': '沉浸式和声温润环绕人声，极具质感的声学空间让想念藏进每段旋律', 'high_freq+loudness_1': '清透高频配合平滑响度渐变，丝滑的动态控制让往事在耳边轻柔晃动', 'high_freq+loudness_2': '晶莹高频融入细腻响度起伏，柔和声压带来极致怀旧感，思念渐渐蔓延', 'high_freq+rhythm_1': '剔透高频在舒缓节奏中流转，精准瞬态响应让回忆随节拍慢慢清晰', 'high_freq+rhythm_2': '细腻高频随慢拍节奏轻漾，顶级律动感让温润的想念止不住地蔓延', 'high_freq+rhythm_3': '纯净高频点缀着轻缓节奏，恰到好处的声学留白让旧时光在心底放映', 'high_freq+space_size': '丝滑高频在开阔空间混响中折射，打造极强纵深感，让思念如画卷般展开', 'high_freq+stereo_width_1': '灵动高频在宽广立体声场中游走，高解析环绕听感将怀旧情绪瞬间拉满', 'high_freq+stereo_width_2': '柔和高频融入私享立体声场，精准声像定位带来专属陪伴，思念如影随形', 'high_freq+vocal_1': '高频解析力十足，空灵人声入耳，像微风将珍贵的回忆轻轻吹送至心头', 'high_freq+vocal_2': '细腻高频为人声镀上温润光泽，悠长声线将无言思念化作最高级的倾诉', 'high_freq+vocal_3': '柔和高频如微风轻抚人声，干净的频段处理让绵长旧时光在耳畔重现', 'high_freq+vocal_4': '空灵高频交织深邃质感人声，营造极具电影感的听觉空间，想念藏进音符', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然高级的修音质感让心扉再次被往事温柔牵动', 'loudness+rhythm_1': '平滑响度渐变配合舒缓慢拍节奏，如电影慢镜头般的听感让思念蔓延', 'loudness+rhythm_2': '柔和响度在慵懒节奏中起伏，舒适动态范围让珍贵回忆慢慢清晰浮现', 'loudness+space_size': '精密响度控制填满深邃空间混响，打造极具包容感的声学画卷，盛满思念', 'loudness+stereo_width': '细腻响度与宽阔立体声场自然融合，沉浸式的包裹听感让想念萦绕耳畔', 'loudness+vocal_1': '质感人声轻吟配合入微的响度捕捉，保留真实呼吸感，让思念在心底蔓延', 'loudness+vocal_2': '平滑响度压限让深情人声尽显丝滑，温润的声学质感让人忍不住想起从前', 'loudness+vocal_3': '克制而平衡的响度托起空灵人声，沉甸甸的思念瞬间被这股声浪温柔化解', 'rhythm+space_size': '舒缓节奏律动在开阔空间混响蔓延，时间仿佛被拉长，回忆画卷徐徐展开', 'rhythm+stereo_width': '慵懒节奏在宽阔立体声场摇曳，超强包裹感带你进入时光穿梭的思念旅途', 'rhythm+vocal': '磁性人声精准契合舒缓慢拍节奏，自然松弛的律动感让想念温柔流淌', 'space_size+stereo_width': '开阔空间混响结合全景立体声场，打造极具纵深感的回忆结界，满是想念', 'space_size+vocal_1': '空灵人声在开阔混响空间中回荡，清透余韵勾勒丰富层次，又一次想起你', 'space_size+vocal_2': '穿透力极强的人声响彻无垠混响空间，恰到好处的声学延音让回忆翻涌', 'stereo_width+vocal_1': '环绕人声在私享立体声场游走，高解析贴耳感让你安心享受被思念包裹', 'stereo_width+vocal_2': '宽广立体声场稳稳包裹质感人声，深邃沉浸的声学体验让温暖旧时光浮现', 'stereo_width+vocal_3': '顶级立体声场为质感人声打造专属空间，绝佳声像定位让思念变得有迹可循'};
        r6['dimPairs'] = r0;
        r5 = {};
        r0 = '孤单是徘徊的身影';
        r5['keyword'] = r0;
        r0 = {'high_freq+richness': '清冷和声交织通透高频，细腻解析力带来质感，徘徊的身影在晚风中享受独处', 'loudness+richness': '内敛和声配合克制响度，平稳声压隔绝喧嚣，让徘徊的身影沉浸于安静独处', 'rhythm+richness': '舒缓和声跟随慵懒节奏律动，平稳的瞬态响应让沉默的脚步也带着电影感', 'richness+space_size': '清冷和声融入深邃空间混响，空旷的声学纵深让孤单的余韵在夜色中绵长', 'richness+stereo_width': '柔和和声被立体声场精准收拢，干净的声音结像让孤单身影被夜色温柔包裹', 'richness+vocal_1': '质感人声与清冷和声无缝交融，顺滑频段处理让独自行走的声线自带高级感', 'richness+vocal_2': '细腻和声平稳包裹清透人声，极佳声部融合让孤单的心绪在晚风中安静沉淀', 'richness+vocal_3': '内敛和声稳稳托起质感人声，清晰中频结像隔绝外界喧闹，留给孤单一片宁静', 'richness+vocal_4': '质感人声与轻缓和声细腻交织，极佳声学控制力让独自漫步的旅程充满诗意', 'richness+vocal_5': '清冷和声环绕质感人声，扎实声音底盘让孤单的脚步在夜色深处轻轻回响', 'high_freq+loudness_1': '清透高频配合克制响度，平稳声压将喧嚣隔绝，让徘徊的身影享受安宁独处', 'high_freq+loudness_2': '清冷高频融入柔和响度起伏，平稳动态张力让世界安静，只剩从容的呼吸声', 'high_freq+rhythm_1': '清透高频精准契合舒缓节奏，平缓的瞬态响应让孤单的脚步也显得笃定从容', 'high_freq+rhythm_2': '细腻高频随缓慢节奏律动摇曳，极佳动态跟随让夜色里的心绪变得安稳宁静', 'high_freq+rhythm_3': '清亮高频点缀舒缓节奏鼓点，清晰声学瞬态让置身人群的身影依然保持清醒', 'high_freq+space_size': '剔透高频在空旷空间混响中折射，清冷的声学泛音让独处的氛围愈发沉静', 'high_freq+stereo_width_1': '灵动高频在立体声场中轻缓游走，克制的声像张力让夜色里的孤单格外安静', 'high_freq+stereo_width_2': '清亮高频在立体声场中微微收窄，干净的声音结像打造出与世无争的清冷感', 'high_freq+vocal_1': '高频解析力让人声清冷虚渺，干净的音色结像让沉在心底的情绪安静释放', 'high_freq+vocal_2': '高频增益让人声更为清透单薄，顺滑的频段处理陪你独自穿过夜色与马路', 'high_freq+vocal_3': '细腻高频映衬质感人声，克制的声频能量让孤单的影子也散发着安静的气息', 'high_freq+vocal_4': '清澈高频伴随清冷人声舒展，自然的高音延展让心事藏在旋律中不被打扰', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮的修音质感让夜色微凉中的漫步更加从容', 'loudness+rhythm_1': '克制响度配合缓慢节奏律动，平稳动态响应让安静的脚步带上沉稳的质感', 'loudness+rhythm_2': '轻柔响度拉满舒缓节奏张力，细腻声压控制让沉静心绪在旋律中被温柔接纳', 'loudness+space_size': '轻弱响度填满空旷空间混响，自然扩散的声压张力让万物沉寂，只留独处', 'loudness+stereo_width': '内敛响度横贯立体声场，收窄的环绕动态带来安全感，让孤单身影不被打扰', 'loudness+vocal_1': '质感人声配合克制响度低吟，恰到好处的声压级让无人的诉说也变得迷人', 'loudness+vocal_2': '舒缓人声配合清淡响度增益，丝滑声线极具包容力，默默穿行于安静夜色中', 'loudness+vocal_3': '舒缓响度赋予人声柔和张力，轻柔的共鸣让微凉夜色里的心境保持平静淡然', 'rhythm+space_size': '缓慢节奏律动填满空荡空间混响，悠长声学纵深让无人并肩的漫步也从容', 'rhythm+stereo_width': '舒缓节奏在立体声场左右摇晃，冷清的律动全方位席卷，独自走向迷人夜色', 'rhythm+vocal': '质感人声紧扣缓慢节奏律动，极佳瞬态让微凉声线与慢拍相融，心事缓缓流淌', 'space_size+stereo_width': '清冷空间混响叠加全景立体声场，克制音浪自然包裹，让喧嚣世界瞬间安静', 'space_size+vocal_1': '质感人声在空旷空间混响中回荡，清冷的延音自然扩散，让孤单心绪沉静', 'space_size+vocal_2': '空灵人声于空间混响中轻漾，平稳声学共振伴随夜色渐深，一个人从容前行', 'stereo_width+vocal_1': '质感人声在窄立体声场中冷清环绕，精准声像定位让独处时光绝不被打扰', 'stereo_width+vocal_2': '宽阔立体声场平稳托举单薄人声，克制的声频环绕将孤单身影与喧嚣隔绝', 'stereo_width+vocal_3': '质感人声在立体声场清淡舒展，干净的声音结像伴你穿行夜色，安静且迷人'};
        r5['dimPairs'] = r0;
        r4 = {};
        r0 = '悲伤是像素的眼泪';
        r4['keyword'] = r0;
        r0 = {'high_freq+richness': '和声轻柔，高频如细碎像素微颤，低落的情绪在此刻得到安静的拥抱', 'loudness+richness': '和声低缓，响度轻虚如飘落的像素，疲惫的心终于可以卸下防备', 'rhythm+richness': '和声徐缓，节奏慢下脚步，就让心里的微酸随着旋律慢慢释怀吧', 'richness+space_size': '和声在空阔处回荡，混响漫长，给无处安放的忧伤留出呼吸的空间', 'richness+stereo_width': '和声收窄，声场化作单薄却温暖的毛毯，轻轻包裹住偶尔的脆弱', 'richness+vocal_1': '人声与和声轻柔交织，声线微颤，允许自己在此刻做一个易碎的梦', 'richness+vocal_2': '和声裹着人声缓缓下沉，不必时刻坚强，这首歌接住你所有的眼泪', 'richness+vocal_3': '和声层叠缠绕人声，带来心安的陪伴，委屈就化作像素随风散去吧', 'richness+vocal_4': '和声沉缓托着人声，化解心底的酸涩，累了就在这温柔里歇息片刻', 'richness+vocal_5': '微冷的和声包裹人声，声线轻颤，所有的不开心都在此刻被理解', 'high_freq+loudness_1': '高频细碎发颤，轻柔响度如像素剥落，借一首歌的时间释放掉软弱', 'high_freq+loudness_2': '高频微弱，响度徐缓，像一场无声的春雨，悄悄洗刷掉心头的沉闷', 'high_freq+rhythm_1': '高频细碎，节奏放慢了步调，让那些沉重的心事一点点被节拍化解', 'high_freq+rhythm_2': '高频随节拍如水滴滑落，偶尔的低落没关系，让音乐陪你安静发呆', 'high_freq+rhythm_3': '高频微点缀慢拍，声线轻颤，心底的酸涩在音符的抚慰中渐渐平息', 'high_freq+space_size': '高频如像素碎散，混响悠长，给满心的沉郁找到一个安全倾诉的出口', 'high_freq+stereo_width_1': '高频在耳畔微颤，声场微微收拢，在这个只属于你的小宇宙里疗伤', 'high_freq+stereo_width_2': '高频清冷，轻薄的声场将周身包裹，就让这首歌轻轻抽走你的烦恼', 'high_freq+vocal_1': '高频人声清冷，音色微涩，把千钧重的心事化作眼泪，痛快哭一场', 'high_freq+vocal_2': '高频凄柔萦绕，声线微颤，就算眼泪决堤，也有这温柔旋律托着你', 'high_freq+vocal_3': '高频人声轻飘，声线微颤，别怕偶尔的脆弱，让音乐带走你的酸涩', 'high_freq+vocal_4': '高频轻缠人声，透出微凉底色，在这里卸下防备，不用再假装坚强', 'high_freq+vocal_5': '高频人声细碎如眼泪剥落，心底发闷时，不如跟着旋律痛快哭出来', 'loudness+rhythm_1': '响度轻缓，节拍放慢，别急着赶路，停下来听听内心的悲伤与柔软', 'loudness+rhythm_2': '响度伴随慢节奏低吟，当无力感袭来，就让音乐成为你依靠的肩膀', 'loudness+space_size': '细弱响度在混响中漫开，周遭安静下来，留给悲伤一点释放的空间', 'loudness+stereo_width': '低沉声响在声场中环拥，像一个温暖的怀抱，轻轻抚平你心中的伤', 'loudness+vocal_1': '人声低闷轻吟，力度轻柔，脆弱的情绪顺着声线流淌，被慢慢治愈', 'loudness+vocal_2': '人声沉郁低唱，响度轻飘，允许自己偶尔不开心，积攒的委屈会散', 'loudness+vocal_3': '暗弱人声伴随低响轻落，不用时刻伪装坚强，眼泪也是释怀的开始', 'rhythm+space_size': '慢节奏在空旷声场蔓延，被悲伤填满的时间，有旋律陪伴就不算难熬', 'rhythm+stereo_width': '慢节奏在宽阔声场中摇摆，偶尔坠落也没关系，温柔的音符会接住你', 'rhythm+vocal': '人声踩着慢拍低唱，让压抑已久的情绪随着这首歌痛快且安全地释放', 'space_size+stereo_width': '空阔声场将你环绕，隔绝外界的喧嚣，委屈和难过在这不用再硬扛', 'space_size+vocal_1': '人声在远处回荡，余韵悠长，当胸口堵得慌，就让歌声替你诉说忧伤', 'space_size+vocal_2': '颤抖声线在巨大的空旷中回响，孤独时请记得，想哭就大方哭出来', 'stereo_width+vocal_1': '破碎的声线在窄场中环绕，像一个专属的拥抱，接纳你所有的不完美', 'stereo_width+vocal_2': '立体声场稳稳托住人声，化解心底酸涩，脆弱的情绪此刻在这里很安全', 'stereo_width+vocal_3': '轻缓人声在左右声场舒展，抚平微颤的气息，像素眼泪终会化作光芒'};
        r4['dimPairs'] = r0;
        r3 = {};
        r0 = '深情是一杯咖啡';
        r3['keyword'] = r0;
        r0 = {'high_freq+richness': '和声醇厚如微酿，高频轻盈微颤，藏在心底的故事在旋律中迎来回甘', 'loudness+richness': '和声低回温润，响度轻柔克制，万千思绪在温柔的陪伴中慢慢化解', 'rhythm+richness': '和声温和舒缓，节奏放慢步调，翻涌的思绪随着灵动音符缓缓消散', 'richness+space_size': '和声在空阔处回荡，混响悠长，回忆的微甘终会化作被治愈的温暖', 'richness+stereo_width': '和声微微收紧，声场化作安静角落，心底的轻声呢喃有旋律静静倾听', 'richness+vocal_1': '人声与醇厚和声相融，声线低回，那些未曾说出口的话让这首歌替你说', 'richness+vocal_2': '和声温柔包裹人声，内心的千言万语，都在这醇厚包容的声场里释怀', 'richness+vocal_3': '温润和声稳稳托住人声，纷繁心绪不妨借着优美的旋律尽情地倾诉', 'richness+vocal_4': '和声与人声轻柔交织，懂你心底的微澜，安静时有音乐给予深深安慰', 'richness+vocal_5': '温润和声轻柔环绕人声，让心底彻底放下防备，把所有思绪交给旋律', 'high_freq+loudness_1': '高频清亮轻颤，响度柔和克制，纷扰的思绪都被温热的音符悄然化解', 'high_freq+loudness_2': '高频柔和内敛，响度沉稳，那些难以言喻的心情在音符里找到了出口', 'high_freq+rhythm_1': '高频清润，节奏放慢脚步，别急着赶路，让旋律替你抚平紧皱的眉头', 'high_freq+rhythm_2': '高频随节拍轻轻跃动，品过回忆的余味，翻涌的心绪终会迎来平静释怀', 'high_freq+rhythm_3': '高频轻灵点缀慢拍，微颤的声线里，无从说起的心愿都有温暖旋律懂', 'high_freq+space_size': '高频在空阔处飘散，混响悠长，给回忆留出一个安全安放与释怀的空间', 'high_freq+stereo_width_1': '高频在耳畔轻游，收拢的声场为你留出一隅宁静，静静等心情彻底放晴', 'high_freq+stereo_width_2': '高频清淡柔和，声场轻轻包裹，过往的百般滋味有这首歌陪你一起品尝', 'high_freq+vocal_1': '高频人声轻颤，音色醇美，藏在心间的情绪不如随温暖旋律一吐为快', 'high_freq+vocal_2': '高频柔美萦绕人声，轻缓气息中，一言难尽的滋味都有温暖音乐作伴', 'high_freq+vocal_3': '高频人声轻灵，声线温和，藏在每句歌词里的思绪总会被旋律轻柔接住', 'high_freq+vocal_4': '高频轻缠人声，增添醇厚底色，不知从何说起时就静静听完这首歌', 'high_freq+vocal_5': '高频人声细碎轻颤，心底偶尔的波澜，就放心地交给旋律去温柔表达', 'loudness+rhythm_1': '响度轻柔，节拍舒缓，放慢匆忙的脚步，在音乐里享受片刻的安宁', 'loudness+rhythm_2': '响度伴随慢节奏轻吟，思绪再多也无妨，总有轻柔的节拍愿意为你分担', 'loudness+space_size': '轻柔响度在混响中漫开，带来清新空气，让回忆在静谧氛围中慢慢升华', 'loudness+stereo_width': '内敛低响在声场中环拥，像一阵暖风轻轻包裹，未曾说出口的话旋律懂', 'loudness+vocal_1': '人声低声轻吟，力度柔和，内敛的情感顺着旋律流淌，心境逐渐变轻盈', 'loudness+vocal_2': '人声深情低唱，响度轻柔克制，万千感慨借着这首动人的歌得以彻底释放', 'loudness+vocal_3': '温柔人声伴随低响轻落，增添声线里的醇厚，过去的故事已无需再强求', 'rhythm+space_size': '慢节奏在空阔处蔓延，时间变得温柔，正好用来品味时光沉淀的美好', 'rhythm+stereo_width': '慢节奏在宽阔声场摇摆，带来轻盈感，纷扰心事随着轻柔旋律慢慢释怀', 'rhythm+vocal': '人声踩慢拍轻柔低唱，醇厚旋律如暖流，所有心绪都在此刻被温柔接纳', 'space_size+stereo_width': '宽阔声场将你环绕，温热的气息带来安宁氛围，无处安放的思绪终会消散', 'space_size+vocal_1': '人声在远处温柔回荡，余韵绵长醇厚，心底的万千滋味始终有旋律倾听', 'space_size+vocal_2': '动人声线在空旷中回响，安静时请记得，无法言语的心声都可以交给歌', 'stereo_width+vocal_1': '低回声线在窄场中环绕，给你一个专属拥抱，懂你只与歌声分享的心事', 'stereo_width+vocal_2': '立体声场稳稳托住人声，带来安心陪伴，在这里可以卸下防备轻松做自己', 'stereo_width+vocal_3': '舒缓人声在左右声场轻展，托起安宁气息，就让旋律陪你品味时光的醇厚'};
        r3['dimPairs'] = r0;
        r2 = {};
        r0 = '忧郁是深蓝的房间';
        r2['keyword'] = r0;
        r0 = {'high_freq+richness': '醇厚和声交织清透高频，如深海微光，让疲惫在细腻的声学层次中消散', 'loudness+richness': '内敛和声配合平滑响度，营造失重悬浮感，连呼吸都变得轻盈且放松', 'rhythm+richness': '醇厚和声随舒缓律动起伏，平稳的节奏让紧绷神经在深蓝中沉降', 'richness+space_size': '宽广空间混响交织细腻和声，深蓝余韵在此回荡，用声学赋予内心宁静', 'richness+stereo_width': '宽阔立体声场将深邃和声精准收拢，打造专属深蓝结界，隔绝外界喧嚣', 'richness+vocal_1': '质感人声与沉浸式和声交融，声线深邃，精准抚慰每一寸疲惫', 'richness+vocal_2': '柔和和声如海浪包裹人声，呼吸随声浪放慢，畅享深海般的治愈', 'richness+vocal_3': '空灵和声立体环绕磁性人声，清晰解析让烦恼随深海光影瞬间蒸发', 'richness+vocal_4': '深邃和声稳稳托住质感人声，细腻温和的听感让人安心沉入深蓝梦境', 'richness+vocal_5': '饱满和声将人声细致包裹，平滑频段过渡带来沉浸式放空，尽享自由', 'high_freq+loudness_1': '清透高频搭配细腻响度控制，柔和声浪洗净铅华，只留纯粹空灵', 'high_freq+loudness_2': '晶莹高频融入丝滑的响度起伏，如深海微光闪烁，带来清透声学美感', 'high_freq+rhythm_1': '清澈高频在舒缓节奏中流转，精准瞬态让细碎音符也充满治愈生命力', 'high_freq+rhythm_2': '通透高频随慵懒律动荡漾，平稳下沉感带你坠入深蓝舒适区', 'high_freq+rhythm_3': '纯净高频点缀随性慢拍节奏，恰到好处的留白散发慵懒松弛的质感', 'high_freq+space_size': '剔透高频在开阔空间混响中折射，打造极强纵深感，让心神无限舒展', 'high_freq+stereo_width_1': '清亮高频在环绕声场中细腻游走，高解析贴耳感让人瞬间卸下防备', 'high_freq+stereo_width_2': '柔和高频融入私享立体声场，精准声像定位带来深海般的包裹感', 'high_freq+vocal_1': '高频清澈通透，人声空灵入耳，像一阵深海微风吹散所有沉重与疲惫', 'high_freq+vocal_2': '细腻高频为人声镀上深蓝微光，悠长声线将无言疲惫化作温柔倾诉', 'high_freq+vocal_3': '柔和高频如海浪轻抚人声，干净的频段处理，让心事被专业声学治愈', 'high_freq+vocal_4': '空灵高频交织深邃人声，营造恰到好处的失重感，沉醉在深蓝结界中', 'high_freq+vocal_5': '丝滑高频消解人声毛刺感，自然修音质感让低落情绪开出深海之花', 'loudness+rhythm_1': '平滑响度渐变配合深海慢拍节奏，放慢时间流速，带来细腻听觉享受', 'loudness+rhythm_2': '柔和响度在慵懒律动中起伏，乘着深蓝洋流，畅享零压力的漂浮感', 'loudness+space_size': '精密响度控制填满深邃空间混响，打造极具包容感的静谧水域', 'loudness+stereo_width': '细腻响度与环绕立体声场自然融合，如被深蓝包裹，享受绝对的安全', 'loudness+vocal_1': '人声轻吟配合入微的响度捕捉，保留真实呼吸感，治愈一切不开心', 'loudness+vocal_2': '平滑响度压限让深邃人声尽显丝滑，心底疲惫被温润质感妥帖安放', 'loudness+vocal_3': '克制而平衡的响度托起空灵人声，沉重心事瞬间被深海声浪融化', 'rhythm+space_size': '舒缓节奏在深蓝空间混响蔓延，时间仿佛被静止，带来深层次治愈', 'rhythm+stereo_width': '慵懒律动在宽阔立体声场摇曳，失重般的听感带你进入深蓝冥想', 'rhythm+vocal_1': '磁性人声精准契合深海慢拍律动，自然松弛感让紧绷神经瞬间放松', 'rhythm+vocal_2': '人声随着慵懒节奏自由流淌，平稳的下沉感让你心甘情愿沉醉于此', 'space_size+stereo_width': '开阔空间混响结合全景立体声场，打造深邃静谧的极致深蓝结界', 'space_size+vocal_1': '空灵人声在深蓝混响空间中回荡，清透余韵勾勒丰富层次，抚平波澜', 'space_size+vocal_2': '极具穿透力的人声响彻无垠空间，恰到好处的混响声学让情绪释放', 'stereo_width+vocal_1': '环绕人声在私享立体声场游走，高解析贴耳感让你安心享受独处', 'stereo_width+vocal_2': '宽广立体声场稳稳包裹质感人声，深邃沉浸体验洗刷一切疲倦感', 'stereo_width+vocal_3': '立体声场为质感人声打造专属安全舱，温润低语只为给你最专业陪伴'};
        r2['dimPairs'] = r0;
        r0 = {};
        r1 = '每种心情都有回响';
        r0['keyword'] = r1;
        r1 = {'high_freq+richness': '细腻和声交织透亮高频，高解析力让音色纯净无暇，静静沉浸在纯粹旋律中', 'loudness+richness': '温润和声配合均衡响度，平滑的动态控制带来自然听感，安心享受此刻音乐', 'rhythm+richness': '舒展和声紧扣自然节奏律动，极佳的瞬态响应让人安静聆听每一段纯粹旋律', 'richness+space_size': '细腻和声融入清澈空间混响，通透的声学纵深带来极致的沉浸式听歌体验', 'richness+stereo_width': '均衡和声在宽广立体声场铺展，精准声像定位打造专属听音区，不被打扰', 'richness+vocal_1': '质感人声与温润和声无缝交融，顺滑频段处理让你安静听歌，感受旋律流淌', 'richness+vocal_2': '细腻和声平稳托举清透人声，极佳声部融合带来高级听感，安静享受音乐', 'richness+vocal_3': '柔和和声紧密包裹质感人声，清晰中频结像让安静听歌的时光变得格外治愈', 'richness+vocal_4': '均衡和声与清透人声细腻交织，极佳频段控制力带来舒缓听感，安静沉浸', 'richness+vocal_5': '饱满和声环绕质感人声，扎实声音底盘带来层次分明的听感，安静听歌就好', 'high_freq+loudness_1': '通透高频配合平稳响度控制，细腻的声压张力带来纯净听感，此刻只有音乐', 'high_freq+loudness_2': '干净高频融入均衡响度起伏，平滑动态压限让你在舒适中静静感受音乐细节', 'high_freq+rhythm_1': '顺滑高频精准契合舒缓节奏，清晰瞬态响应带来沉浸氛围，安静享受听歌', 'high_freq+rhythm_2': '轻盈高频随自然节奏律动起伏，极佳动态跟随让你沉浸在纯粹的音乐世界里', 'high_freq+rhythm_3': '通透高频点缀平缓节奏鼓点，柔和声学瞬态带来自然听感，安心享受音乐', 'high_freq+space_size': '剔透高频在开阔空间混响中折射，自然的声学泛音让听歌氛围更加沉浸', 'high_freq+stereo_width_1': '灵动高频在立体声场中均衡舒展，高解析环绕听感让安静听歌变得超舒服', 'high_freq+stereo_width_2': '顺滑高频在宽广立体声场游走，精准声音结像带来极具沉浸感的音乐享受', 'high_freq+vocal_1': '高频解析力让人声细腻通透，扎实音色结像带来舒适听感，静享温柔旋律', 'high_freq+vocal_2': '高频增益让人声更为干净柔和，顺滑频段处理让你静静聆听细腻歌声流淌', 'high_freq+vocal_3': '细腻高频映衬透亮人声，稳定的声频能量带来温润听感，安静享受这一刻', 'high_freq+vocal_4': '清澈高频伴随质感人声舒展，自然的高音延展带来舒缓平和的沉浸式听歌', 'high_freq+vocal_5': '丝滑高频消解人声杂质，自然透亮的修音质感还原清晰细节，安静听歌极好', 'loudness+rhythm_1': '扎实响度驱动舒缓节奏律动，平稳的动态响应让时光在音符中静静流淌', 'loudness+rhythm_2': '均衡响度拉满平缓节奏张力，细腻声压控制让你沉浸享受音乐律动的纯粹', 'loudness+space_size': '饱满响度填满通透空间混响，自然扩散的声压张力让听歌层次更为清晰', 'loudness+stereo_width': '均衡响度横贯宽广立体声场，饱满的环绕动态让清晰音符在耳畔轻快跳跃', 'loudness+vocal_1': '质感人声配合均衡响度释出，恰到好处的声压级让你安静细细品味每句吟唱', 'loudness+vocal_2': '饱满人声配合温润响度增益，丝滑声线极具感染力，静静享受音乐的美好', 'loudness+vocal_3': '平稳响度赋予人声细腻张力，清透共鸣带来平和舒缓听感，安静聆听就好', 'rhythm+space_size': '舒缓节奏律动填满清透空间混响，悠长的声学纵深让安静听歌氛围感拉满', 'rhythm+stereo_width': '平缓节奏在立体声场左右交替，清晰律动全方位席卷，让每个音符都在诉说', 'rhythm+vocal': '质感人声紧扣舒缓节奏律动，极佳瞬态让声线与节拍完美相融，安静感受', 'space_size+stereo_width': '通透空间混响叠加全景立体声场，均衡音浪自然环绕，安静听歌极致治愈', 'space_size+vocal_1': '质感人声在通透空间混响中回荡，清晰延音自然扩散，安静享受音乐时刻', 'space_size+vocal_2': '清透人声于空间混响中平稳发力，稳健声学共振带来平和氛围，静心听歌', 'stereo_width+vocal_1': '质感人声在宽广立体声场舒展，精准声像定位带来温润包围，安静听歌沉浸', 'stereo_width+vocal_2': '宽阔立体声场平稳托举清透人声，饱满的声频环绕带来干净纯粹的音乐享受', 'stereo_width+vocal_3': '质感人声在宽广立体声场蔓延，精准的声音结像清晰环绕耳畔，安静听歌'};
        r0['dimPairs'] = r1;
        r1 = {};
        r1['ecstatic'] = r16;
        r1['passionate'] = r15;
        r1['energetic'] = r14;
        r1['joyful'] = r13;
        r1['sweet'] = r12;
        r1['confident'] = r11;
        r1['romantic'] = r10;
        r1['relaxed'] = r9;
        r1['healing'] = r8;
        r1['calm'] = r7;
        r1['nostalgic'] = r6;
        r1['lonely'] = r5;
        r1['sad'] = r4;
        r1['bitter'] = r3;
        r1['depressed'] = r2;
        r1['default'] = r0;
        r0 = {'keyword': '每种心情都有回响', 'description': '音乐正在播放，声音的每个细节都在为你呈现'};
        r1['fallback'] = r0;
        r0 = a4;
        r0['exports'] = r1;
        r0 = undefined;
        return r0;
    };
factories[968]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r2 = a5;
        r1 = global;
        r5 = r1.Object;
        r4 = r5.defineProperty;
        r3 = {};
        r0 = true;
        r3['value'] = r0;
        r0 = '__esModule';
        r0 = r4.bind(r5)(r2, r0, r3);
        r0 = undefined;
        r2['EMOTION_BASELINES'] = r0;
        r2['EMOTION_INTENSITY_THRESHOLDS'] = r0;
        r2['FALLBACK_BEFORE_RATIOS'] = r0;
        r2['FALLBACK_SONG_ACTIVATIONS'] = r0;
        r2['GENRE_BASELINES'] = r0;
        r2['HIGH_FREQ_SONG_ACT_COMPENSATION'] = r0;
        r2['INTENSITY_THRESHOLDS'] = r0;
        r2['LOUDNESS_SONG_ACT_COMPENSATION'] = r0;
        r2['SONG_FEATURE_RANGES'] = r0;
        r2['SONG_FEATURE_WEIGHTS'] = r0;
        r2['VISUAL_DIMENSIONS'] = r0;
        r3 = ['loudness', 'high_freq', 'vocal', 'rhythm', 'stereo_width', 'space_size', 'richness'];
        r2['VISUAL_DIMENSIONS'] = r3;
        r3 = [0.16, 0.29];
        r2['INTENSITY_THRESHOLDS'] = r3;
        r3 = {};
        r4 = [0.148, 0.239];
        r3['bitter'] = r4;
        r4 = [0.218, 0.383];
        r3['calm'] = r4;
        r4 = [0.178, 0.308];
        r3['confident'] = r4;
        r4 = [0.169, 0.334];
        r3['depressed'] = r4;
        r4 = [0.185, 0.308];
        r3['ecstatic'] = r4;
        r4 = [0.164, 0.293];
        r3['energetic'] = r4;
        r4 = [0.165, 0.269];
        r3['healing'] = r4;
        r4 = [0.128, 0.256];
        r3['joyful'] = r4;
        r4 = [0.222, 0.36];
        r3['lonely'] = r4;
        r4 = [0.137, 0.232];
        r3['nostalgic'] = r4;
        r4 = [0.123, 0.223];
        r3['passionate'] = r4;
        r4 = [0.161, 0.329];
        r3['relaxed'] = r4;
        r4 = [0.188, 0.31];
        r3['romantic'] = r4;
        r4 = [0.159, 0.25];
        r3['sad'] = r4;
        r4 = [0.121, 0.228];
        r3['sweet'] = r4;
        r2['EMOTION_INTENSITY_THRESHOLDS'] = r3;
        r3 = {};
        r4 = {'loudness': 0.228, 'high_freq': 0.385, 'vocal': 0.672, 'rhythm': 0.575, 'stereo_width': 0.857, 'space_size': 0.688, 'richness': 0.457};
        r3['default'] = r4;
        r4 = {'loudness': 0.361, 'high_freq': 0.539, 'vocal': 0.538, 'rhythm': 0.977, 'stereo_width': 0.92, 'space_size': 0.55, 'richness': 0.576};
        r3['ecstatic'] = r4;
        r4 = {'loudness': 0.342, 'high_freq': 0.578, 'vocal': 0.739, 'rhythm': 0.92, 'stereo_width': 0.685, 'space_size': 0.413, 'richness': 0.413};
        r3['passionate'] = r4;
        r4 = {'loudness': 0.323, 'high_freq': 0.578, 'vocal': 0.605, 'rhythm': 0.92, 'stereo_width': 0.594, 'space_size': 0.343, 'richness': 0.329};
        r3['energetic'] = r4;
        r4 = {'loudness': 0.323, 'high_freq': 0.616, 'vocal': 0.739, 'rhythm': 0.862, 'stereo_width': 0.513, 'space_size': 0.343, 'richness': 0.413};
        r3['joyful'] = r4;
        r4 = {'loudness': 0.228, 'high_freq': 0.424, 'vocal': 1, 'rhythm': 0.46, 'stereo_width': 0.594, 'space_size': 0.343, 'richness': 0.658};
        r3['sweet'] = r4;
        r4 = {'loudness': 0.323, 'high_freq': 0.539, 'vocal': 0.806, 'rhythm': 0.747, 'stereo_width': 0.685, 'space_size': 0.482, 'richness': 0.329};
        r3['confident'] = r4;
        r4 = {'loudness': 0.162, 'high_freq': 0.308, 'vocal': 0.941, 'rhythm': 0.287, 'stereo_width': 0.594, 'space_size': 0.757, 'richness': 0.742};
        r3['romantic'] = r4;
        r4 = {'loudness': 0.162, 'high_freq': 0.231, 'vocal': 0.739, 'rhythm': 0.345, 'stereo_width': 0.513, 'space_size': 0.618, 'richness': 0.413};
        r3['relaxed'] = r4;
        r4 = {'loudness': 0.162, 'high_freq': 0.27, 'vocal': 0.806, 'rhythm': 0.287, 'stereo_width': 0.513, 'space_size': 0.618, 'richness': 0.658};
        r3['healing'] = r4;
        r4 = {'loudness': 0.114, 'high_freq': 0.193, 'vocal': 0.605, 'rhythm': 0.23, 'stereo_width': 0.434, 'space_size': 0.757, 'richness': 0.413};
        r3['calm'] = r4;
        r4 = {'loudness': 0.18, 'high_freq': 0.27, 'vocal': 0.941, 'rhythm': 0.402, 'stereo_width': 0.594, 'space_size': 0.757, 'richness': 0.493};
        r3['nostalgic'] = r4;
        r4 = {'loudness': 0.162, 'high_freq': 0.154, 'vocal': 0.739, 'rhythm': 0.345, 'stereo_width': 0.513, 'space_size': 0.893, 'richness': 0.658};
        r3['lonely'] = r4;
        r4 = {'loudness': 0.162, 'high_freq': 0.154, 'vocal': 0.874, 'rhythm': 0.345, 'stereo_width': 0.513, 'space_size': 0.825, 'richness': 0.658};
        r3['sad'] = r4;
        r4 = {'loudness': 0.228, 'high_freq': 0.193, 'vocal': 0.739, 'rhythm': 0.517, 'stereo_width': 0.434, 'space_size': 0.893, 'richness': 0.742};
        r3['bitter'] = r4;
        r4 = {'loudness': 0.114, 'high_freq': 0.116, 'vocal': 0.672, 'rhythm': 0.23, 'stereo_width': 0.343, 'space_size': 0.963, 'richness': 0.493};
        r3['depressed'] = r4;
        r2['EMOTION_BASELINES'] = r3;
        r3 = {};
        r4 = {'loudness': 0.294, 'high_freq': 0.462, 'vocal': 1, 'rhythm': 0.575, 'stereo_width': 0.48, 'space_size': 0.343, 'richness': 0.484};
        r3['pop-family'] = r4;
        r4 = {'loudness': 0.162, 'high_freq': 0.27, 'vocal': 0.941, 'rhythm': 0.287, 'stereo_width': 0.399, 'space_size': 0.688, 'richness': 0.731};
        r3['folk-family'] = r4;
        r4 = {'loudness': 0.427, 'high_freq': 0.5, 'vocal': 0.538, 'rhythm': 0.977, 'stereo_width': 0.799, 'space_size': 0.413, 'richness': 0.275};
        r3['rhythm-family'] = r4;
        r4 = {'loudness': 0.19, 'high_freq': 0.347, 'vocal': 0.202, 'rhythm': 0.345, 'stereo_width': 0.92, 'space_size': 1, 'richness': 0.731};
        r3['instrumental-family'] = r4;
        r2['GENRE_BASELINES'] = r3;
        r6 = r1.Object;
        r5 = r6.assign;
        r3 = new Array(2);
        r4 = -40;
        r3[0] = r4;
        r4 = 0;
        r3[1] = r4;
        r4 = {};
        r7 = [60, 200];
        r4['repbpm'] = r7;
        r4['loudness_lufs'] = r3;
        r3 = [0, 30];
        r4['dynamic_range'] = r3;
        r3 = [0, 1];
        r4['stereo_width_ratio'] = r3;
        r7 = r1.Object;
        r3 = r7.fromEntries;
        r10 = r1.Array;
        r9 = r10.from;
        r8 = {};
        r1 = 32;
        r8['length'] = r1;
        r1 = function(a0, a1) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r1
            r1 = 'mel_';
            r0 = a1;
            r1 = r1 + r0;
            r0 = new Array(2);
            r0[0] = r1;
            r1 = new Array(2);
            r2 = -70;
            r1[0] = r2;
            r2 = 0;
            r1[1] = r2;
            r0[1] = r1;
            return r0;
        };
        r1 = r9.bind(r10)(r8, r1);
        r3 = r3.bind(r7)(r1);
        r1 = {};
        r7 = [0, 1];
        r1['vocal_time_ratio'] = r7;
        r7 = [0, 1];
        r1['vocal_energy_ratio'] = r7;
        r7 = [0, 1];
        r1['harmonic_intensity_ratio'] = r7;
        r7 = [0, 1];
        r1['drum_time_ratio'] = r7;
        r7 = [0, 10];
        r1['rhythm_complexity'] = r7;
        r7 = [0, 1];
        r1['drum_energy_ratio'] = r7;
        r7 = [0, 1200];
        r1['vocal_main_freq'] = r7;
        r7 = [0, 30];
        r1['vocal_dynamic_range'] = r7;
        r7 = [0, 1];
        r1['bass_energy_ratio'] = r7;
        r7 = [0, 1];
        r1['other_instrument_energy_ratio'] = r7;
        r1 = r5.bind(r6)(r4, r3, r1);
        r2['SONG_FEATURE_RANGES'] = r1;
        r9 = new Array(11);
        r1 = ['mel_0', 0.04];
        r9[0] = r1;
        r1 = ['mel_1', 0.04];
        r9[1] = r1;
        r1 = ['mel_2', 0.04];
        r9[2] = r1;
        r1 = ['mel_3', 0.04];
        r9[3] = r1;
        r1 = ['mel_4', 0.03];
        r9[4] = r1;
        r1 = ['mel_5', 0.03];
        r9[5] = r1;
        r1 = ['mel_6', 0.03];
        r9[6] = r1;
        r1 = ['mel_7', 0.03];
        r9[7] = r1;
        r1 = ['loudness_lufs', 0.4];
        r9[8] = r1;
        r1 = ['dynamic_range'];
        r3 = -0.3;
        r1[1] = r3;
        r9[9] = r1;
        r1 = ['bass_energy_ratio', 0.05];
        r9[10] = r1;
        r8 = new Array(8);
        r1 = ['mel_24', 0.13];
        r8[0] = r1;
        r1 = ['mel_25', 0.13];
        r8[1] = r1;
        r1 = ['mel_26', 0.12];
        r8[2] = r1;
        r1 = ['mel_27', 0.12];
        r8[3] = r1;
        r1 = ['mel_28', 0.13];
        r8[4] = r1;
        r1 = ['mel_29', 0.12];
        r8[5] = r1;
        r1 = ['mel_30', 0.13];
        r8[6] = r1;
        r1 = ['mel_31', 0.12];
        r8[7] = r1;
        r7 = new Array(17);
        r1 = ['mel_8', 0.055];
        r7[0] = r1;
        r1 = ['mel_9', 0.055];
        r7[1] = r1;
        r1 = ['mel_10', 0.055];
        r7[2] = r1;
        r1 = ['mel_11', 0.055];
        r7[3] = r1;
        r1 = ['mel_12', 0.055];
        r7[4] = r1;
        r1 = ['mel_13', 0.055];
        r7[5] = r1;
        r1 = ['mel_14', 0.055];
        r7[6] = r1;
        r1 = ['mel_15', 0.055];
        r7[7] = r1;
        r1 = ['mel_16', 0.055];
        r7[8] = r1;
        r1 = ['mel_17', 0.055];
        r7[9] = r1;
        r1 = ['mel_18', 0.055];
        r7[10] = r1;
        r1 = ['mel_19', 0.055];
        r7[11] = r1;
        r1 = ['vocal_time_ratio', 0.1];
        r7[12] = r1;
        r1 = ['vocal_energy_ratio', 0.08];
        r7[13] = r1;
        r1 = ['harmonic_intensity_ratio', 0.06];
        r7[14] = r1;
        r1 = ['vocal_main_freq', 0.05];
        r7[15] = r1;
        r1 = ['vocal_dynamic_range', 0.05];
        r7[16] = r1;
        r6 = new Array(6);
        r1 = ['repbpm', 0.28];
        r6[0] = r1;
        r1 = ['dynamic_range', 0.12];
        r6[1] = r1;
        r1 = ['drum_time_ratio', 0.22];
        r6[2] = r1;
        r1 = ['rhythm_complexity', 0.22];
        r6[3] = r1;
        r1 = ['drum_energy_ratio', 0.08];
        r6[4] = r1;
        r1 = ['bass_energy_ratio', 0.08];
        r6[5] = r1;
        r5 = new Array(1);
        r1 = ['stereo_width_ratio', 1];
        r5[0] = r1;
        r4 = new Array(2);
        r1 = ['dynamic_range', 0.6];
        r4[0] = r1;
        r1 = ['stereo_width_ratio', 0.4];
        r4[1] = r1;
        r3 = new Array(14);
        r1 = ['mel_16', 0.065];
        r3[0] = r1;
        r1 = ['mel_17', 0.065];
        r3[1] = r1;
        r1 = ['mel_18', 0.065];
        r3[2] = r1;
        r1 = ['mel_19', 0.065];
        r3[3] = r1;
        r1 = ['mel_20', 0.06];
        r3[4] = r1;
        r1 = ['mel_21', 0.06];
        r3[5] = r1;
        r1 = ['mel_22', 0.06];
        r3[6] = r1;
        r1 = ['mel_23', 0.06];
        r3[7] = r1;
        r1 = ['mel_24', 0.05];
        r3[8] = r1;
        r1 = ['mel_25', 0.05];
        r3[9] = r1;
        r1 = ['mel_26', 0.05];
        r3[10] = r1;
        r1 = ['mel_27', 0.05];
        r3[11] = r1;
        r1 = ['harmonic_intensity_ratio', 0.15];
        r3[12] = r1;
        r1 = ['other_instrument_energy_ratio', 0.15];
        r3[13] = r1;
        r1 = {};
        r1['loudness'] = r9;
        r1['high_freq'] = r8;
        r1['vocal'] = r7;
        r1['rhythm'] = r6;
        r1['stereo_width'] = r5;
        r1['space_size'] = r4;
        r1['richness'] = r3;
        r2['SONG_FEATURE_WEIGHTS'] = r1;
        r1 = 1.8;
        r2['HIGH_FREQ_SONG_ACT_COMPENSATION'] = r1;
        r1 = 0.57;
        r2['LOUDNESS_SONG_ACT_COMPENSATION'] = r1;
        r1 = {'loudness': 0.287, 'high_freq': 0.254, 'vocal': 0.421, 'rhythm': 0.252, 'stereo_width': 0.269, 'space_size': 0.25, 'richness': 0.277};
        r2['FALLBACK_SONG_ACTIVATIONS'] = r1;
        r1 = {'loudness': 0.85, 'high_freq': 0.83, 'vocal': 0.9, 'rhythm': 0.84, 'stereo_width': 0.85, 'space_size': 0.83, 'richness': 0.85};
        r2['FALLBACK_BEFORE_RATIOS'] = r1;
        return r0;
    };
factories[969]=function(a0, a1, a2, a3, a4, a5, a6) {
var r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13,r14,r15,r16,r17,r18,r19,r20,r21,r22,r23,r24,r25,r26,r27,r28,r29,r30,r31,r32,r33,r34,r35,r36,r37,r38,r39,r40,r41,r42,r43,r44,r45,r46,r47,r48,r49,r50,r51,r52,r53,r54,r55,r56,r57,r58,r59,r60,r61,r62,r63,r64,r65,r66,r67,r68,r69,r70,r71,r72,r73,r74,r75,r76,r77,r78,r79,r80,r81,r82,r83,r84,r85,r86,r87,r88,r89,r90,r91,r92,r93,r94,r95,r96,r97,r98,r99; // Environment: r3
        r0 = global;
        r4 = r0.Object;
        r3 = r4.defineProperty;
        r2 = {};
        r0 = true;
        r2['value'] = r0;
        r1 = a5;
        r0 = '__esModule';
        r0 = r3.bind(r4)(r1, r0, r2);
        r0 = undefined;
        return r0;
    };

var dependencies = {"872": [873, 874], "873": [2, 10], "874": [2, 875, 876, 877, 878, 879, 880, 881, 882, 883, 884, 885, 886, 887, 888, 889, 890, 891, 892, 893, 894, 895, 896, 897, 898, 899, 900, 901, 902, 903, 904, 905, 906, 907, 908, 909, 910, 911, 912, 913, 914, 915, 916, 917, 918, 919, 920, 921, 922, 923, 924, 925, 926, 927, 928, 929, 930, 931, 932, 933, 934, 935, 936, 937, 938], "875": [], "876": [], "877": [], "878": [], "879": [], "880": [], "881": [], "882": [], "883": [], "884": [], "885": [], "886": [], "887": [], "888": [], "889": [], "890": [], "891": [], "892": [], "893": [], "894": [], "895": [], "896": [], "897": [], "898": [], "899": [], "900": [], "901": [], "902": [], "903": [], "904": [], "905": [], "906": [], "907": [], "908": [], "909": [], "910": [], "911": [], "912": [], "913": [], "914": [], "915": [], "916": [], "917": [], "918": [], "919": [], "920": [], "921": [], "922": [], "923": [], "924": [], "925": [], "926": [], "927": [], "928": [], "929": [], "930": [], "931": [], "932": [], "933": [], "934": [], "935": [], "936": [], "937": [], "938": [], "939": [940, 941, 943, 944], "940": [873], "941": [2, 10, 942, 873, 940], "942": [], "943": [873], "944": [873], "945": [], "949": [2, 325, 10, 950, 873], "950": [], "952": [942, 949, 873], "953": [2, 10, 942, 873, 949], "957": [2, 10, 958, 959, 945, 961, 962], "958": [2, 4, 873], "959": [2, 4, 10, 942, 945, 960, 873], "960": [], "961": [962], "962": [], "963": [964, 969], "964": [2, 14, 15, 10, 4, 965, 966, 873, 968], "965": [2, 945, 966, 967], "966": [962], "967": [], "968": [], "969": []};
function load(id){
 if(id===2)return function(x){return x&&x.__esModule?x:{default:x}};
 if(id===4)return function(x,n){return Array.from(x).slice(0,n)};
 if(id===10)return function(x){return Array.from(x)};
 if(id===14)return function(instance,Constructor){if(!(instance instanceof Constructor))throw new TypeError('Cannot call a class as a function')};
 if(id===15)return function(Constructor,proto,statics){function define(target,props){if(props)props.forEach(function(p){p.enumerable=p.enumerable||false;p.configurable=true;if('value' in p)p.writable=true;Object.defineProperty(target,p.key,p)})}define(Constructor.prototype,proto);define(Constructor,statics);return Constructor};
 if(id===325)return function(o,k,v){o[k]=v;return o};
 if(cache[id])return cache[id].exports;
 if(!factories[id])throw Error('missing '+id);
 var module={exports:{}};cache[id]=module;
 factories[id](global,load,null,null,module,module.exports,dependencies[id]);return module.exports;
}

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
