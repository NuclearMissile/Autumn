package org.example.autumn.server.component

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream

class ServletInputStreamImpl : ServletInputStream() {
    override fun read(): Int {
        TODO("Not yet implemented")
    }

    override fun isFinished(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isReady(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setReadListener(readListener: ReadListener?) {
        TODO("Not yet implemented")
    }
}