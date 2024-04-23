package org.example.autumn.server.component

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import java.io.OutputStream

class ServletOutputStreamImpl(private val outputStream: OutputStream) : ServletOutputStream() {
    override fun write(b: Int) {
        TODO("Not yet implemented")
    }

    override fun isReady(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setWriteListener(writeListener: WriteListener?) {
        TODO("Not yet implemented")
    }
}