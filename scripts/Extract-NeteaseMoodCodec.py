"""Recover the pure 9.5.30 JSK mood adapter from a local Hermes decompiler listing.

Input is hbc-decompiler (P1sec/hermes-dec 0.1.8) output for the APK's
assets/rn_js-kernel/js-kernel.tar.xz -> js-kernel.hbc (Hermes bytecode v89).
No network, account, React Native bridge, or feature authorization modules are included.
Usage: python scripts/Extract-NeteaseMoodCodec.py kernel-decompiled.js output.js
"""
import argparse
import hashlib
import json
import re
from pathlib import Path


def recover(listing: str) -> str:
    pattern = re.compile(r'^    r7 = (function\(a0, a1, a2, a3, a4, a5, a6\) \{.*?^    \});\n    r6 = (\d+);\n    r5 = ([^\n]+);\n    r5 = r8.bind\(r2\)\(r7, r6, r5\);', re.M | re.S)
    modules = {}
    for function, key, dependencies in pattern.findall(listing):
        modules[int(key)] = (function, [] if dependencies == 'new Array(0)' else json.loads(dependencies))
    selected = set()
    def visit(key):
        if key in (2, 4, 10, 14, 15, 325) or key in selected:
            return
        selected.add(key)
        for dependency in modules[key][1]:
            visit(dependency)
    for root in (872, 939, 957, 953, 952, 963):
        visit(root)
    assert len(selected) == 91, f'Unsupported JSK build: {len(selected)} modules'
    source = 'var global = (function(){return this})(); var factories = {}, cache = {};\n'
    for key in sorted(selected):
        source += f'factories[{key}]={modules[key][0]};\n'
    source = re.sub(r'(function\([^)]*\) \{)', lambda m: m[0] + '\nvar ' + ','.join(f'r{i}' for i in range(100)) + ';', source)
    # Hermes decompiler displays signed LoadConstInt operands as unsigned.
    source = re.sub(r'\b\d{10}\b', lambda m: str(int(m[0]) - 2**32) if 2**31 <= int(m[0]) < 2**32 else m[0], source)
    # Construct includes an uninitialized spill argument; ordinary Array would count it.
    source = re.sub(r'new (r\d+)\[(r\d+)\]\(([^\n]*)\)', r'originalConstruct(\2, [\3])', source)
    # Call opcode receiver and callee are separate registers, not a property lookup.
    source = re.sub(r'(r\d+)\[(r\d+)\]\(', r'\2.call(\1, ', source)
    iterator = 0
    def fix_iterator(match):
        nonlocal iterator
        iterator += 1
        flag, value, next_fn = match.groups()
        return f'var __iterator_values_{iterator}=Array.from({value}), __iterator_index_{iterator}=0; {flag}={{return:function(){{}}}}; {next_fn}=function(){{if(__iterator_index_{iterator}>=__iterator_values_{iterator}.length){{{flag}=undefined;return {{value:undefined}}}}return {{value:__iterator_values_{iterator}[__iterator_index_{iterator}++]}}}};'
    source = re.sub(r'(r\d+) = (r\d+)\[Symbol.iterator\];\s*(r\d+) = \1\(\).next;', fix_iterator, source)
    assert iterator == 14, f'Unsupported iterator layout: {iterator}'
    source = 'function originalConstruct(fn,args){while(args.length&&args[args.length-1]===undefined)args.pop();return Reflect.construct(fn,args);}\n' + source
    source += '\nvar dependencies = ' + json.dumps({key: modules[key][1] for key in sorted(selected)}) + ';\n'
    source += '''function load(id){
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
'''
    return source + '\n' + Path(__file__).with_name('netease-mood-facade.js').read_text(encoding='utf8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('listing', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    result = recover(args.listing.read_text(encoding='utf8'))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(result, encoding='utf8', newline='\n')
    print('sha256=' + hashlib.sha256(result.encode()).hexdigest())
