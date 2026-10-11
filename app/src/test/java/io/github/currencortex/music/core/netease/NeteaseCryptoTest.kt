package io.github.currencortex.music.core.netease

import org.junit.Assert.*
import org.junit.Test

class NeteaseCryptoTest {
    // Independently generated with Python PyCryptodome; includes UTF-8 and JSON punctuation.
    private val text = """{"s":"中文 & Die For You","type":1}"""
    @Test fun desktopEncodingMatchesIndependentVector() {
        assertEquals("2B5D64177AA6460FBAA3DCB1285E28954BBB4F7556E09B0FB25750F12398BB5025E4442C198DEBCF5F984BB1D16FFCF80C13A1B3412372A8C6A6F13AFE40CD09B17E09792508FF68011077E6E8888EAA9CC53C3E0C8521427FC8C67D9C466A54F4DB0027E7CBCDC567BBC4B3EA6828CAFBC1BE8006CA4BE3B2FF3F95414AE427", NeteaseCrypto.eapi("/api/cloudsearch/pc", text))
    }
    @Test fun webAesAndRsaMatchIndependentVectors() {
        val encoded = NeteaseCrypto.weapi(text, "0123456789abcdef")
        assertEquals("AsXt69Gc5It3v9HENU/jGyOYezSRSMUWQ8VaIREUnYZes5x4KXlVeGbED3O4+Phnyrnzv4cFi6DNx0Bc7NDTiCXxklIdbE/t5242wb4CLiw=", encoded["params"])
        assertEquals("35701388baf89fed412e11269b9c76625d095ecaf17f03fa018abe19ea2d38b949debf242ee39a71ca1f6cda71b1b86a45aa909ee27f7e78e267d34e732f0de948206c3340a788d0003372183e2f753c1f78b66ac23d134ac1fc9b993156520ea826b8aa89a962d4491b4b8d7e08738e1da9b07aa39bf4a7ef0b1c210728cd52", encoded["encSecKey"])
    }
    @Test fun eachWebRequestGetsAnIndependentSecret() {
        assertNotEquals(NeteaseCrypto.weapi(text), NeteaseCrypto.weapi(text))
    }
}
