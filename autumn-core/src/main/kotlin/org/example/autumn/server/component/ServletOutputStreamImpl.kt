package org.example.autumn.server.component

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener

class ServletOutputStreamImpl : ServletOutputStream() {
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