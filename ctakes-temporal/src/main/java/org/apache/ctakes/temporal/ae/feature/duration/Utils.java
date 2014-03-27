package org.apache.ctakes.temporal.ae.feature.duration;

import info.bethard.timenorm.Period;
import info.bethard.timenorm.PeriodSet;
import info.bethard.timenorm.Temporal;
import info.bethard.timenorm.TemporalExpressionParser;
import info.bethard.timenorm.TimeSpan;
import info.bethard.timenorm.TimeSpanSet;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.threeten.bp.temporal.TemporalField;
import org.threeten.bp.temporal.TemporalUnit;

import scala.collection.immutable.Set;
import scala.util.Try;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import com.google.common.io.LineProcessor;

/**
 * Various useful classes and methods for evaluating event duration data.
 */
public class Utils {

  // events and their duration distributions
  public static final String durationDistributionPath = "/Users/dima/Boston/Thyme/Duration/Data/Combined/Distribution/mimic.txt";
  
  // eight bins over which we define a duration distribution
  public static final String[] bins = {"second", "minute", "hour", "day", "week", "month", "year", "decade"};
  
  /**
   * Take the time unit from Bethard's noramlizer
   * and output a coarser time unit, i.e. one of the eight bins
   */
  public static String makeCoarse(String timeUnit) {
    
    HashSet<String> allowableTimeUnits = new HashSet<String>(Arrays.asList(bins));
    
    // map output of Behard's normalizer to coarser time units
    Map<String, String> mapping = ImmutableMap.<String, String>builder()
        .put("afternoon", "hour")
        .put("evening", "hour")
        .put("fall", "month")
        .put("winter", "month")
        .put("morning", "hour")
        .put("night", "hour")
        .put("quarteryear", "month")
        .put("spring", "month")
        .put("summer", "month")
        .build(); 

    // e.g. Years -> year
    String singularAndLowercased = timeUnit.substring(0, timeUnit.length() - 1).toLowerCase();

    // is this one of the bins?
    if(allowableTimeUnits.contains(singularAndLowercased)) {
      return singularAndLowercased;
    } 
    
    // it's not one of the bins; can we map to to a bin?
    if(mapping.get(singularAndLowercased) != null) {
      return mapping.get(singularAndLowercased);
    }

    // we couldn't map it to a bin
    return null;
  }
  
  /**
   * Compute expected duration in seconds. Normalize by number of seconds in a decade.
   */
  public static float expectedDuration(Map<String, Float> distribution) {
    
    // unit of time -> duration in seconds
    final Map<String, Integer> timeUnitInSeconds = ImmutableMap.<String, Integer>builder()
        .put("second", 1)
        .put("minute", 60)
        .put("hour", 60 * 60)
        .put("day", 60 * 60 * 24)
        .put("week", 60 * 60 * 24 * 7)
        .put("month", 60 * 60 * 24 * 30)
        .put("year", 60 * 60 * 24 * 365)
        .put("decade", 60 * 60 * 24 * 365 * 10)
        .build();

    float expectation = 0f;
    for(String unit : distribution.keySet()) {
      expectation = expectation + (timeUnitInSeconds.get(unit) * distribution.get(unit));
    }
  
    return expectation / timeUnitInSeconds.get("decade");
  }

  /*
   * Use Bethard normalizer to map a temporal expression to a time unit.
   */
  public static Set<TemporalUnit> normalize(String timex) {

    URL grammarURL = DurationEventTimeFeatureExtractor.class.getResource("/info/bethard/timenorm/en.grammar");
    TemporalExpressionParser parser = new TemporalExpressionParser(grammarURL);
    TimeSpan anchor = TimeSpan.of(2013, 12, 16);
    Try<Temporal> result = parser.parse(timex, anchor);

    Set<TemporalUnit> units = null;
    if (result.isSuccess()) {
      Temporal temporal = result.get();

      if (temporal instanceof Period) {
        units = ((Period) temporal).unitAmounts().keySet();
      } else if (temporal instanceof PeriodSet) {
        units = ((PeriodSet) temporal).period().unitAmounts().keySet();
      } else if (temporal instanceof TimeSpan) {
        units = ((TimeSpan) temporal).period().unitAmounts().keySet();
      } else if (temporal instanceof TimeSpanSet) {
        Set<TemporalField> fields = ((TimeSpanSet) temporal).fields().keySet();
        units = null; // fill units by calling .getBaseUnit() on each field
      }
    }
    
    return units;
  }
  
  /**
   * Take a time unit and return a probability distribution
   * in which p(this time unit) = 1 and all others are zero.
   */
  public static Map<String, Float> convertToDistribution(String timeUnit) {
    
    Map<String, Float> distribution = new HashMap<String, Float>();
    
    for(String bin: bins) {
      // convert things like "Hours" to "hour"
      String normalized = timeUnit.substring(0, timeUnit.length() - 1).toLowerCase(); 
      if(bin.equals(normalized)) {
        distribution.put(bin, 1.0f);
      } else {
        distribution.put(bin, 0.0f);
      }
    }
    
    return distribution;
  }
  
  /**
   * Convert duration distribution multiset to a format that's easy to parse automatically.
   * Format: <sign/symptom>, <time bin>:<count>, ...
   * Example: apnea, second:5, minute:1, hour:5, day:10, week:1, month:0, year:0
   */
  public static String formatDistribution(
      String mentionText, 
      Multiset<String> durationDistribution, 
      String separator,
      boolean normalize) {
    
    List<String> distribution = new LinkedList<String>();
    distribution.add(mentionText);

    double total = 0;
    if(normalize) {
      for(String bin : bins) {
        total += durationDistribution.count(bin);
      }
    }
    
    for(String bin : bins) {
      if(normalize) {
        distribution.add(String.format("%s:%.3f", bin, durationDistribution.count(bin) / total));  
      } else {
        distribution.add(String.format("%s:%d", bin, durationDistribution.count(bin)));
      }
      
    }
    
    Joiner joiner = Joiner.on(separator);
    return joiner.join(distribution);
  }

  
  /**
   * Read event duration distributions from file.
   */
  public static class Callback implements LineProcessor <Map<String, Map<String, Float>>> {

    // map event text to its duration distribution
    private Map<String, Map<String, Float>> textToDistribution;

    public Callback() {
      textToDistribution = new HashMap<String, Map<String, Float>>();
    }

    public boolean processLine(String line) throws IOException {

      String[] elements = line.split(", "); // e.g. pain, second:0.000, minute:0.005, hour:0.099, ...
      Map<String, Float> distribution = new HashMap<String, Float>();

      for(int durationBinNumber = 1; durationBinNumber < elements.length; durationBinNumber++) {
        String[] durationAndValue = elements[durationBinNumber].split(":"); // e.g. "day:0.475"
        distribution.put(durationAndValue[0], Float.parseFloat(durationAndValue[1]));
      }

      textToDistribution.put(elements[0], distribution);
      return true;
    }

    public Map<String, Map<String, Float>> getResult() {

      return textToDistribution;
    }
  }
}
