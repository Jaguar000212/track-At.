package com.jaguar.attendancetracker.backend.typeConverters

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.util.UUID

class UUIDConverter {
    @TypeConverter
    fun fromBytes(bytes: ByteArray?): UUID? {
        if (bytes == null || bytes.size != 16) return null
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.getLong(), buffer.getLong())
    }

    @TypeConverter
    fun toBytes(uuid: UUID?): ByteArray? {
        if (uuid == null) return null
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }
}
