package org.example.autumn.server.component

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import java.io.IOException
import java.io.OutputStream

class ServletOutputStreamImpl(private val output: OutputStream) : ServletOutputStream() {
    private var writeListener: WriteListener? = null

    override fun write(b: Int) {
        try {
            output.write(b)
        } catch (e: IOException) {
            writeListener?.onError(e)
            throw e
        }
    }

    override fun close() {
        output.close()
    }

    override fun isReady(): Boolean {
        return true
    }

    override fun setWriteListener(writeListener: WriteListener) {
        this.writeListener = writeListener
        writeListener.onWritePossible()
    }
}