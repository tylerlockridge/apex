package com.healthplatform.sync.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMessageTest {

    @Test
    fun `socket timeout exception maps to unreachable message`() {
        val msg = SocketTimeoutException("timed out").toFriendlyMessage()
        assertEquals("Server unreachable — check your connection", msg)
    }

    @Test
    fun `connect exception maps to cannot connect message`() {
        val msg = ConnectException("connection refused").toFriendlyMessage()
        assertEquals("Cannot connect to server", msg)
    }

    @Test
    fun `unknown host exception maps to no network message`() {
        val msg = UnknownHostException("host not found").toFriendlyMessage()
        assertEquals("No network connection", msg)
    }

    @Test
    fun `generic exception falls through to default message`() {
        val msg = RuntimeException("something weird").toFriendlyMessage()
        assertEquals("Server unreachable — try again", msg)
    }
}
