# SPDX-License-Identifier: GPL-3.0-or-later

# NewPipeExtractor v0.26.5 executes YouTube's JavaScript in Rhino 1.8.1's
# interpreter with initSafeStandardObjects(). Rhino loads these implementations
# through Kit.classOrNull()/newInstanceOrNull(), using their original names and
# no-argument constructors. Ordinary calls keep their remaining implementation.
-keep,allowoptimization class org.mozilla.javascript.jdk18.VMBridge_jdk18 {
    public <init>();
}
-keep,allowoptimization class org.mozilla.javascript.Interpreter {
    public <init>();
}
-keep,allowoptimization class org.mozilla.javascript.regexp.RegExpImpl {
    public <init>();
}

# ScriptRuntime resolves these types by name when matching reflective init()
# signatures; all four are already reachable through normal interpreter calls.
-keepnames class org.mozilla.javascript.Context
-keepnames class org.mozilla.javascript.ContextFactory
-keepnames interface org.mozilla.javascript.Function
-keepnames class org.mozilla.javascript.ScriptableObject

# ScriptableObject stores this accessor as a java.lang.reflect.Method.
-keepclassmembers,allowoptimization class org.mozilla.javascript.ScriptableObject {
    public java.lang.Object getExternalArrayLength();
}

# Safe standard objects lazily load these classes by name, then discover init()
# using FunctionObject.getMethodList(). Preserve just that reflective entry.
-keep,allowoptimization class org.mozilla.javascript.NativeContinuation {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}
-keep,allowoptimization class org.mozilla.javascript.regexp.NativeRegExp {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}
-keep,allowoptimization class org.mozilla.javascript.typedarrays.Native*Array {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}
-keep,allowoptimization class org.mozilla.javascript.typedarrays.NativeArrayBuffer {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}
-keep,allowoptimization class org.mozilla.javascript.typedarrays.NativeDataView {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}

# Extractor's generated YouTube Protobuf Lite schemas refer to their own fields
# by name in newMessageInfo(). The protobuf-javalite 4.35.1 JAR ships no consumer
# rules. Do not rename or remove those fields; unrelated classes remain eligible
# for shrinking and optimization.
-keepclassmembers class org.schabi.newpipe.extractor.services.youtube.protos.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Rhino 1.8.1's optional JavaToJSONConverters.BEAN adapter references desktop
# JavaBeans introspection APIs absent on Android. Context defaults to STRING;
# neither Extractor nor Glidvio selects BEAN or exposes Java beans to its safe
# JavaScript scope. Only that unused adapter's five missing types are suppressed.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Media3's AARs and OkHttp/jsoup's JARs already supply their consumer rules.
# Extractor's v0.26.5 timeago PatternMap uses direct references, not reflection.
