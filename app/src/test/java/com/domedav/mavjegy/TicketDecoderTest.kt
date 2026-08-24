package com.domedav.mavjegy

import com.domedav.mavjegy.util.TicketDecoder
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class TicketDecoderTest {

    private fun encode(json: String): String {
        val def = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        def.setInput(json.toByteArray(Charsets.UTF_8))
        def.finish()
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (!def.finished()) out.write(buf, 0, def.deflate(buf))
        def.end()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    @Test
    fun `decodes barcode content and jegy sorszam`() {
        val b64 = encode("""{"Kod":"AZTEC-PAYLOAD","JegySorszam":"55940033"}""")
        val decoded = TicketDecoder.decodeSerialized(b64)
        assertNotNull(decoded)
        assertEquals("AZTEC-PAYLOAD", decoded!!.barcodeContent)
        assertEquals("55940033", decoded.jegySorszam)
    }

    @Test
    fun `nested kod is found`() {
        val b64 = encode("""{"a":{"b":[{"Kod":"NESTED"}]}}""")
        assertEquals("NESTED", TicketDecoder.decodeSerialized(b64)?.barcodeContent)
    }

    @Test
    fun `invalid base64 returns null`() {
        assertNull(TicketDecoder.decodeSerialized("!!!not-base64!!!"))
    }

    @Test
    fun `garbage payload returns null`() {
        val def = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        def.setInput(byteArrayOf(1, 2, 3))
        def.finish()
        val out = ByteArrayOutputStream()
        out.write(def.deflate(ByteArray(64)))
        assertNull(TicketDecoder.decodeSerialized(Base64.getEncoder().encodeToString(out.toByteArray())))
    }
}
