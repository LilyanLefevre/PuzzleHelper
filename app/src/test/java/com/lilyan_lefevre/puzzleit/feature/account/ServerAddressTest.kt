package com.lilyan_lefevre.puzzleit.feature.account

import com.lilyan_lefevre.puzzleit.feature.account.data.isAcceptableServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A password is only sent over https, or over plain http on a home network. */
class ServerAddressTest {

    @Test
    fun `https is accepted anywhere`() {
        assertTrue(isAcceptableServer("https://puzzleit.example.com"))
        assertTrue(isAcceptableServer("https://8.8.8.8:8090"))
    }

    @Test
    fun `plain http is accepted on a home network only`() {
        listOf("http://192.168.1.20:8090", "http://10.0.2.2:8090", "http://172.16.0.5", "http://172.31.255.1", "http://100.64.0.9:8090",
            "http://raspberrypi.local:8090", "http://localhost:8090", "http://127.0.0.1:8090").forEach { assertTrue(it, isAcceptableServer(it)) }
        listOf("http://8.8.8.8:8090", "http://172.32.0.1", "http://192.169.1.1", "http://puzzleit.example.com", "http://100.128.0.1").forEach { assertFalse(it, isAcceptableServer(it)) }
    }

    @Test
    fun `anything else is refused`() {
        listOf("", "not-an-address", "ftp://192.168.1.20", "192.168.1.20:8090").forEach { assertFalse(it, isAcceptableServer(it)) }
    }
}
