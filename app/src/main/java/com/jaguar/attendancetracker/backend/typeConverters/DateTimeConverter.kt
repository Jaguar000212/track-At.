package com.jaguar.attendancetracker.backend.typeConverters

import androidx.room.TypeConverter
import java.time.LocalDate

class DateTimeConverter {

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? {
        return epochDay?.let { LocalDate.ofEpochDay(it) }
    }

//    @TypeConverter
//    fun localTimeToSeconds(time: LocalTime?): Int? {
//        return time?.toSecondOfDay()
//    }
//
//    @TypeConverter
//    fun secondsToLocalTime(seconds: Int?): LocalTime? {
//        return seconds?.let { LocalTime.ofSecondOfDay(it.toLong()) }
//    }
}
