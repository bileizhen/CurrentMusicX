# Original mood interoperability adapter

The adapter targets NetEase Android 9.5.30 (9005030), APK SHA-256
`947a1f573470525f5f49a85c0ee268ae76cb883c17cf297f01567f83d4c93cdf`.
It contains the recovered pure audio preprocessing, template selection and output
conversion modules, plus our small JSON facade. It does not contain the original
React Native bridge, network client, login code or authorization implementation.

Reproduce the adapter from a user-provided matching APK:

1. Read `assets/rn_js-kernel/js-kernel.tar.xz`; decompress XZ/TAR to `js-kernel.hbc`.
   The extracted HBC SHA-256 is
   `bf2ed2d3a656b04602ea4b752f73dfc38f8371753172ad7af27c5504d2241db2`.
2. Use [P1sec/hermes-dec](https://github.com/P1sec/hermes-dec) 0.1.8
   `src/hermes_dec/decompilation/hbc_decompiler.py` on that Hermes v89 bytecode.
   The decompiler listing used here has SHA-256
   `9ef71bf806862995512ad8b463542cdbec0c21b15aa60c404ee8642c789b0766`.
3. Run `python scripts/Extract-NeteaseMoodCodec.py kernel-decompiled.js app/src/main/assets/netease-original/mood-codec.js`.
   This selects the dependency closure of modules 872, 939, 957, 953, 952 and 963
   (91 modules), including original DSP-derived visual dimensions and copywriting.
   It also repairs documented register-oriented decompiler syntax:
   signed constants, receiver/callee calls, iterator completion, local registers
   and constructor spill arguments. Six Babel helpers are implemented locally.
4. Expected generated UTF-8/LF asset SHA-256:
   `bb756e69dffae120e355b00e3242d656922a0f2c82c7555e7abeb3a975a8059a`.
   `OriginalMoodCodec` enforces this hash before evaluating code. A changed source
   requires review, regeneration of reference data and an explicit updated pin.

Execution uses Rhino 1.8.1, interpreted ES6, safe standard objects, a denying
ClassShutter, instruction observation and bounded execution deadlines. No Java
object is exposed as an input or global binding. The facade accepts JSON only.
`OriginalMoodCodecTest` compares all four encoded input tensors against an
independent Node execution of the recovered adapter and checks decoded output,
fallback handling and rejection of malformed resources or model output.
This establishes JavaScript runtime parity, not an independent execution of
the original Hermes VM.

Ordinary arm64 builds include the pinned original MNN and DSP libraries from
NetEase 9.5.30. Their hashes are recorded in
`app/src/main/assets/original-effects/engine-manifest.json`; builds verify the
vendor binaries before packaging. The model and song parameters still come from
the authenticated account.
The app imports them from the user's compatible original APK, with separate
hash verification. The model is obtained from the original DAI model manifest,
verified against its manifest size and MD5, decoded into private storage, and
fed to the original MNN JNI. Local test fixtures are ignored. The model observed
in development is `emofxnet.mnn`, SHA-256
`a5537bea1958bd27202cba2fdf3754858cb788e0bc18366c6ecc9894b05236d1`.

Original UI mapping: privilege type 116/resource 106 maps to `vibe`, which sets
audio effect type 11 and `curr_ae_vip=4`. Its native continuous-playback check
ultimately calls `UserPrivilege.isRedPlus()` (vipCode 300, future expiry).
The generic `vipauth/app/auth/query` is also available for benefit/trial data;
its body is two comma-joined decimal strings, `resourceIdList` and
`resourceTypeList`. A single negative response from that generic endpoint is
not evidence that a valid original-client SVIP lacks the mood effect.
