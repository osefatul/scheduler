package com.usbank.corp.dcr.api.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RotationUtils {
    
    private static final Logger log = LoggerFactory.getLogger(RotationUtils.class);
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final SimpleDateFormat SDF = new SimpleDateFormat(DATE_FORMAT);
    
    /**
     * Convert Date from yyyyMMdd to yyyy-MM-dd
     * @param dateString in format yyyyMMdd
     * @return formatted date string
     */
    public String convertDate(String dateString) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate date = LocalDate.parse(dateString, inputFormatter);
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        return date.format(outputFormatter);
    }
    
    /**
     * Get start date of the week for a given date
     * Using Monday as the first day of the week
     * 
     * @param date Date to find week start for
     * @return Start date of the week
     */
    public Date getWeekStartDate(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate weekStart = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return Date.from(weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
    
    /**
     * Get end date of the week for a given date
     * Using Sunday as the last day of the week
     * 
     * @param date Date to find week end for
     * @return End date of the week
     */
    public Date getWeekEndDate(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate weekEnd = localDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return Date.from(weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
    
    /**
     * Convert string date to Date object
     * 
     * @param dateString Date in yyyy-MM-dd format
     * @return Date object
     */
    public Date getinDate(String dateString) {
        try {
            return SDF.parse(dateString);
        } catch (ParseException e) {
            log.error("Error parsing date: {}", dateString, e);
            throw new RuntimeException("Error parsing date: " + dateString, e);
        }
    }
    
    /**
     * Get the difference in days between two dates
     * 
     * @param d1 First date
     * @param d2 Second date
     * @return Number of days between dates
     */
    public long getDifferenceDays(Date d1, Date d2) {
        long diff = d2.getTime() - d1.getTime();
        return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Get week key for a given date
     * Format: YYYY-WW where WW is the week number in the year
     * Uses Monday as first day of week to ensure consistent calendar weeks
     * 
     * @param date The date to get week key for
     * @return Week key in YYYY-WW format
     */
    public String getWeekKey(Date date) {
        Calendar cal = Calendar.getInstance();
        // Set Monday as first day of week to ensure consistent calendar weeks
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.setMinimalDaysInFirstWeek(4); // ISO 8601 standard
        cal.setTime(date);
        
        int year = cal.get(Calendar.YEAR);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        
        // Log for debugging
        log.debug("Date {} is in week {}-{}", 
               new SimpleDateFormat("yyyy-MM-dd").format(date), year, week);
        
        return year + "-" + String.format("%02d", week);
    }
    
    /**
     * Find the start date of the week that contains the specified date
     * This is an enhanced version that respects campaign start/end dates
     * 
     * @param requestedDate The date being requested
     * @param campaignStartDate Start date of the campaign
     * @param campaignEndDate End date of the campaign
     * @return Start date of the week containing the requested date
     */
    public Date getWeekNearestStartDateBasedonRequestedDate(String requestedDate, 
            String campaignStartDate,
            String campaignEndDate) {
        try {
            Date reqDate = SDF.parse(requestedDate);
            Date startDate = SDF.parse(campaignStartDate);
            Date endDate = SDF.parse(campaignEndDate);
            
            // Get start of the week for requested date
            Date weekStart = getWeekStartDate(reqDate);
            
            // Adjust if before campaign start
            if (weekStart.before(startDate)) {
                weekStart = getWeekStartDate(startDate);
            }
            
            // Adjust if after campaign end
            if (weekStart.after(endDate)) {
                throw new RuntimeException("Requested date is after campaign end date");
            }
            
            return weekStart;
        } catch (ParseException e) {
            log.error("Error parsing date", e);
            throw new RuntimeException("Error parsing date", e);
        }
    }
}