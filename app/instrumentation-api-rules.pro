# SPDX-License-Identifier: GPL-3.0-or-later

# The release instrumentation APK shares app dependencies and excludes duplicate
# classes. App R8 does not see those external callers when deciding to inline or
# remove a public method. Preserve exactly the callable boundary used by the
# three instrumentation classes, so tests execute the actual optimized release
# classes rather than duplicate unoptimized dependencies from their own APK.
# Method bodies and the rest of the app/dependencies remain optimizable.
# Update these entries when instrumentation starts calling an additional API.

# AndroidJUnitRunner/AndroidXTracer call these shared tracing entry points.
-keep,allowoptimization class androidx.tracing.Trace {
    public static void beginSection(java.lang.String);
    public static void endSection();
    public static void forceEnableAppTracing();
}

# XTags inherits the serialization method exercised by the known-wire test.
-keep,allowoptimization class com.google.protobuf.AbstractMessageLite {
    public byte[] toByteArray();
}

-keep,allowoptimization interface androidx.media3.common.Player {
    androidx.media3.common.PlaybackException getPlayerError();
    androidx.media3.common.VideoSize getVideoSize();
    boolean isPlaying();
    int getPlaybackState();
    long getCurrentPosition();
    void play();
    void seekTo(long);
}

-keep,allowoptimization class androidx.media3.common.VideoSize {
    int height;
    int width;
}

-keep,allowoptimization class com.glidvio.app.MainActivity {
    androidx.media3.common.Player getActivePlayer();
    boolean getPip();
}

-keep,allowoptimization class com.glidvio.app.data.ExtractorDownloader {
    com.glidvio.app.data.ExtractorDownloader$Companion Companion;
}

-keep,allowoptimization class com.glidvio.app.data.ExtractorDownloader$Companion {
    okhttp3.OkHttpClient getClient();
}

-keep,allowoptimization class com.glidvio.app.data.PlaybackInfo {
    java.lang.String getAudioUrl();
    java.lang.String getTitle();
    java.lang.String getVideoUrl();
    java.util.Map getHttpHeaders();
    long getDurationSeconds();
}

-keep,allowoptimization class com.glidvio.app.data.VideoItem {
    java.lang.String getId();
    java.lang.String getTitle();
    java.lang.String getUrl();
    long getDurationSeconds();
}

-keep,allowoptimization class com.glidvio.app.data.VideoPage {
    java.util.List getItems();
    org.schabi.newpipe.extractor.Page getNextPage();
}

-keep,allowoptimization class com.glidvio.app.data.YouTubeRepository {
    <init>();
    java.lang.Object loadPlayback(java.lang.String, kotlin.coroutines.Continuation);
    java.lang.Object search$default(com.glidvio.app.data.YouTubeRepository, java.lang.String, org.schabi.newpipe.extractor.Page, kotlin.coroutines.Continuation, int, java.lang.Object);
    java.lang.Object search(java.lang.String, org.schabi.newpipe.extractor.Page, kotlin.coroutines.Continuation);
}

-keep,allowoptimization class com.glidvio.app.sponsor.SkipPlanner {
    com.glidvio.app.sponsor.SkipPlanner INSTANCE;
    java.util.List normalize(java.util.List, java.lang.Double);
}

-keep,allowoptimization class com.glidvio.app.sponsor.SkipWindow {
    double getEndSeconds();
    double getStartSeconds();
    int hashCode();
}

-keep,allowoptimization class com.glidvio.app.sponsor.SponsorBlockClient {
    <init>(okhttp3.OkHttpClient, int, kotlin.jvm.internal.DefaultConstructorMarker);
    java.lang.Object load$default(com.glidvio.app.sponsor.SponsorBlockClient, java.lang.String, java.util.Set, kotlin.coroutines.Continuation, int, java.lang.Object);
}

-keep,allowoptimization class com.glidvio.app.sponsor.SponsorCategory {
    com.glidvio.app.sponsor.SponsorCategory$Companion Companion;
    java.lang.String getApiName();
}

-keep,allowoptimization class com.glidvio.app.sponsor.SponsorCategory$Companion {
    java.util.Set getDefaults();
}

-keep,allowoptimization class com.glidvio.app.sponsor.SponsorLoadResult$Available {
    java.util.List getSegments();
}

-keep,allowoptimization class kotlin.ResultKt {
    void throwOnFailure(java.lang.Object);
}

-keep,allowoptimization class kotlin.Unit {
    kotlin.Unit INSTANCE;
}

-keep,allowoptimization class kotlin.collections.CollectionsKt {

}

-keep,allowoptimization class kotlin.collections.IntIterator {
    int nextInt();
}

-keep,allowoptimization class kotlin.coroutines.intrinsics.IntrinsicsKt {

}

-keep,allowoptimization class kotlin.coroutines.jvm.internal.Boxing {
    java.lang.Double boxDouble(double);
}

-keep,allowoptimization class kotlin.coroutines.jvm.internal.SpillingKt {
    java.lang.Object nullOutSpilledVariable(java.lang.Object);
}

-keep,allowoptimization class kotlin.coroutines.jvm.internal.SuspendLambda {
    <init>(int, kotlin.coroutines.Continuation);
}

-keep,allowoptimization class kotlin.io.CloseableKt {
    void closeFinally(java.io.Closeable, java.lang.Throwable);
}

-keep,allowoptimization interface kotlin.jvm.functions.Function1 {
    java.lang.Object invoke(java.lang.Object);
}

-keep,allowoptimization class kotlin.jvm.internal.Intrinsics {
    boolean areEqual(java.lang.Object, java.lang.Object);
    void checkNotNull(java.lang.Object);
    void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    void checkNotNullParameter(java.lang.Object, java.lang.String);
    void throwUninitializedPropertyAccessException(java.lang.String);
}

-keep,allowoptimization class kotlin.jvm.internal.Ref$BooleanRef {
    <init>();
    boolean element;
}

-keep,allowoptimization class kotlin.jvm.internal.Ref$LongRef {
    <init>();
    long element;
}

-keep,allowoptimization class kotlin.jvm.internal.Ref$ObjectRef {
    <init>();
    java.lang.Object element;
}

-keep,allowoptimization class kotlin.ranges.RangesKt {

}

-keep,allowoptimization class kotlin.text.Regex {
    <init>(java.lang.String);
    boolean matches(java.lang.CharSequence);
}

-keep,allowoptimization class kotlin.text.StringsKt {

}

-keep,allowoptimization class kotlinx.coroutines.BuildersKt {
    java.lang.Object runBlocking$default(kotlin.coroutines.CoroutineContext, kotlin.jvm.functions.Function2, int, java.lang.Object);
}

-keep,allowoptimization interface okhttp3.Call {
    okhttp3.Response execute();
}

-keep,allowoptimization class okhttp3.OkHttpClient {
    okhttp3.Call newCall(okhttp3.Request);
}

-keep,allowoptimization class okhttp3.Request$Builder {
    <init>();
    okhttp3.Request build();
    okhttp3.Request$Builder header(java.lang.String, java.lang.String);
    okhttp3.Request$Builder url(java.lang.String);
}

-keep,allowoptimization class okhttp3.Response {
    boolean isSuccessful();
    int code();
    java.lang.String header$default(okhttp3.Response, java.lang.String, java.lang.String, int, java.lang.Object);
    okhttp3.ResponseBody body();
}

-keep,allowoptimization class okhttp3.ResponseBody {
    okio.BufferedSource source();
}

-keep,allowoptimization interface okio.BufferedSource {
    byte[] readByteArray(long);
}

-keep,allowoptimization class org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper {
    org.schabi.newpipe.extractor.stream.AudioTrackType extractAudioTrackType(java.lang.String);
}

-keep,allowoptimization class org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags$KeyValuePair {
    java.lang.String getKey();
    java.lang.String getValue();
}

-keep,allowoptimization class org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags$XTags {
    byte[] toByteArray();
    int getXtagsCount();
    org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags$KeyValuePair getXtags(int);
    org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags$XTags parseFrom(byte[]);
}

-keep,allowoptimization class org.schabi.newpipe.extractor.stream.AudioTrackType {
    org.schabi.newpipe.extractor.stream.AudioTrackType ORIGINAL;
}

-keep,allowoptimization class org.schabi.newpipe.extractor.utils.JavaScript {
    java.lang.String run(java.lang.String, java.lang.String, java.lang.String[]);
    void compileOrThrow(java.lang.String);
}

# Kotlin static facades inherit these methods; preserve their actual declaring
# classes explicitly so the published APK also supports calls through facades.
-keep,allowoptimization class kotlin.collections.CollectionsKt__CollectionsKt {
    java.util.List mutableListOf(java.lang.Object[]);
}

-keep,allowoptimization class kotlin.collections.CollectionsKt__IterablesKt {
    int collectionSizeOrDefault(java.lang.Iterable, int);
}

-keep,allowoptimization class kotlin.collections.CollectionsKt___CollectionsKt {
    java.lang.String joinToString$default(java.lang.Iterable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1, int, java.lang.Object);
    java.util.List take(java.lang.Iterable, int);
    java.util.Set toSet(java.lang.Iterable);
}

-keep,allowoptimization class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt {
    java.lang.Object getCOROUTINE_SUSPENDED();
}

-keep,allowoptimization class kotlin.ranges.RangesKt___RangesKt {
    kotlin.ranges.IntRange until(int, int);
    long coerceAtLeast(long, long);
}

-keep,allowoptimization class kotlin.text.StringsKt__StringsJVMKt {
    boolean startsWith$default(java.lang.String, java.lang.String, boolean, int, java.lang.Object);
}

-keep,allowoptimization class kotlin.text.StringsKt__StringsKt {
    boolean contains$default(java.lang.CharSequence, java.lang.CharSequence, boolean, int, java.lang.Object);
    boolean isBlank(java.lang.CharSequence);
}

-keep,allowoptimization class kotlin.text.StringsKt___StringsKt {
    java.lang.String take(java.lang.String, int);
}
