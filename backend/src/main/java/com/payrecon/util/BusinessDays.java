package com.payrecon.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class BusinessDays {

    private BusinessDays() {
        // Only static methods, so nobody should create an object of this class.
    }

    /** Adds business days, skipping Saturdays and Sundays. */
    public static LocalDate add(LocalDate date, int businessDays) {
        LocalDate result = date;
        int added = 0;
        while (added < businessDays) {
            result = result.plusDays(1);
            DayOfWeek dow = result.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }
}