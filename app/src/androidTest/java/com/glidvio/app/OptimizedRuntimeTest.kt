// SPDX-License-Identifier: GPL-3.0-or-later
package com.glidvio.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.utils.JavaScript
import java.util.Base64

/** Deterministic checks for dependency APIs that shrinking can break at runtime. */
@RunWith(AndroidJUnit4::class)
class OptimizedRuntimeTest {
    @Test(timeout = 30_000)
    fun extractorJavaScriptKeepsReflectivelyLoadedStandardObjects() {
        // Use the same interpreted/safe-scope entry point as YouTube signature
        // extraction. Exercise each lazy standard constructor in Rhino 1.8.1,
        // rather than depending on the operations in today's remote player JS.
        val script = """
            function exercise(value) {
                var reversed = value.split("").reverse().join("");
                var json = JSON.stringify({text: reversed.replace(/[aeiou]/g, "_")});
                var decoded = JSON.parse(json);
                var buffer = new ArrayBuffer(4);
                var bytes = new Uint8Array(buffer);
                bytes[0] = 65;
                bytes[1] = 66;
                var view = new DataView(buffer);
                view.setUint16(2, 258);
                var typed = [
                    new Int8Array([257])[0],
                    new Uint8Array([257])[0],
                    new Uint8ClampedArray([300])[0],
                    new Int16Array([65537])[0],
                    new Uint16Array([-1])[0],
                    new Int32Array([4294967297])[0],
                    new Uint32Array([-1])[0],
                    new Float32Array([1.5])[0],
                    new Float64Array([-1.5])[0]
                ].join(",");
                return decoded.text + "|" + String.fromCharCode(bytes[0], bytes[1]) +
                    "|" + view.getUint16(2) + "|" + typed +
                    "|" + new RegExp("^[a-z]+$").test(value) + "|" + typeof Continuation;
            }
        """.trimIndent()

        JavaScript.compileOrThrow(script)
        assertEquals(
            "__vd_lg|AB|258|1,1,255,1,65535,1,4294967295,1.5,-1.5|true|function",
            JavaScript.run(script, "exercise", "glidvio"),
        )
    }

    @Test(timeout = 30_000)
    fun extractorProtobufParsesAndRoundTripsKnownAudioTags() {
        // Independent wire-format fixture: xtags=[{acont: original}, {lang: en}].
        // Protobuf Lite resolves generated field names reflectively; both the
        // repeated container and optional key/value fields must survive R8.
        val wire = Base64.getDecoder().decode("ChEKBWFjb250EghvcmlnaW5hbAoKCgRsYW5nEgJlbg==")
        val parsed = Xtags.XTags.parseFrom(wire)
        assertEquals(2, parsed.xtagsCount)
        assertEquals("acont", parsed.getXtags(0).key)
        assertEquals("original", parsed.getXtags(0).value)
        assertEquals("lang", parsed.getXtags(1).key)
        assertEquals("en", parsed.getXtags(1).value)
        assertArrayEquals(wire, parsed.toByteArray())

        assertEquals(
            AudioTrackType.ORIGINAL,
            YoutubeParsingHelper.extractAudioTrackType(
                Base64.getUrlEncoder().withoutPadding().encodeToString(parsed.toByteArray()),
            ),
        )
    }
}
