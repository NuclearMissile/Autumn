package org.example.autumn.server.component

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream

class ServletInputStreamImpl(private val data: ByteArray) : ServletInputStream() {
    private var readListener: ReadListener? = null
    private var index = -1

    override fun read(): Int {
        if (index < data.size) {
            index++
            val n = data[index].toInt()
            if (isFinished && readListener != null) {
                try {
                    readListener!!.onAllDataRead()
                } catch (e: Exception) {
                    readListener!!.onError(e)
                    throw e
                }
            }
            return n
        }
        return -1
    }

    override fun isFinished(): Boolean {
        return index == data.size - 1
    }

    override fun isReady(): Boolean {
        return true
    }

    override fun setReadListener(readListener: ReadListener) {
        this.readListener = readListener
        try {
            if (!isFinished) {
                readListener.onDataAvailable()
            } else {
                readListener.onAllDataRead()
            }
        } catch (e: Exception) {
            readListener.onError(e)
        }
    }

    override fun available(): Int {
        return data.size - index - 1
    }

    override fun close() {
        index = data.size - 1
    }
}