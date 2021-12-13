/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.time;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.math.RoundingMode.FLOOR;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.DiscreteDomain;
import com.google.common.math.DoubleMath;
import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import com.google.j2objc.annotations.J2ObjCIncompatible;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Static utility methods pertaining to {@link Duration} instances.
 *
 * <p>Use the following methods to create a {@link Duration}:
 *
 * <ul>
 *   <li>{@link Duration#ofDays(long)} if you have a long of days
 *   <li>{@link Duration#ofHours(long)} if you have a long of hours
 *   <li>{@link Duration#ofMinutes(long)} if you have a long of minutes
 *   <li>{@link Duration#ofSeconds(long)} if you have a long of seconds
 *   <li>{@link Duration#ofSeconds(long, long)} if you have a long of seconds and a nanosecond
 *       adjustment
 *   <li>{@link Durations#ofSeconds(double)} if you have a double of seconds
 *   <li>{@link Duration#ofMillis(long)} if you have a long of milliseconds
 *   <li>{@link Durations#ofMicros(long)} if you have a long of microseconds
 *   <li>{@link Duration#ofNanos(long)} if you have a long of nanoseconds
 * </ul>
 *
 * <p>Use the following methods to decompose a {@link Duration}:
 *
 * <ul>
 *   <li>{@link Duration#toDays()} to convert the duration to a long of days
 *   <li>{@link Duration#toHours()} to convert the duration to a long of hours
 *   <li>{@link Duration#toMinutes()} to convert the duration to a long of minutes
 *   <li>{@link Duration#getSeconds()} to convert the duration to a long of seconds
 *   <li>{@link Durations#toSecondsAsDouble()} to convert the duration to a double of seconds
 *   <li>{@link Duration#toMillis()} to convert the duration to a long of milliseconds
 *   <li>{@link Durations#toMillisSaturated()} to convert the duration to a long of milliseconds
 *       (which saturates to {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} instead of throwing
 *       {@link ArithmeticException})
 *   <li>{@link Durations#toMicros()} to convert the duration to a long of microseconds
 *   <li>{@link Durations#toMicrosSaturated()} to convert the duration to a long of microseconds
 *       (which saturates to {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} instead of throwing
 *       {@link ArithmeticException})
 *   <li>{@link Duration#toNanos()} to convert the duration to a long of nanoseconds
 *   <li>{@link Durations#toNanosSaturated()} to convert the duration to a long of nanoseconds
 *       (which saturates to {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} instead of throwing
 *       {@link ArithmeticException})
 * </ul>
 */
@GwtIncompatible
@J2ObjCIncompatible
@SuppressWarnings("GoodTime")
public final class Durations {

  /** The minimum supported {@code Duration}, approximately -292 billion years. */
  // Note: before making this constant public, consider that "MIN" might not be a great name (not
  //       everyone knows that Durations can be negative!).
  static final Duration MIN = Duration.ofSeconds(Long.MIN_VALUE);

  /** The exact number of seconds in {@link MIN}. */
  @SuppressWarnings("DurationSecondsToDouble")
  private static final double MIN_SECONDS = (double) MIN.getSeconds();

  /** The maximum supported {@code Duration}, approximately 292 billion years. */
  public static final Duration MAX = Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);

  /** The smallest double number of seconds greater than MAX. */
  private static final double MAX_SECONDS = 0x1p63; // 2^63

  private static final int NANOS_PER_SECOND = 1_000_000_000;
  private static final int NANOS_PER_MICRO = 1_000;
  private static final int MICROS_PER_SECOND = 1_000_000;
  private static final int NANOS_POWER_OF_TEN = 9;

  private static final Duration SATURATED_MAX_MILLIS = Duration.ofMillis(Long.MAX_VALUE);
  private static final Duration SATURATED_MIN_MILLIS = Duration.ofMillis(Long.MIN_VALUE);
  private static final Duration SATURATED_MAX_MICROS = ofMicros(Long.MAX_VALUE);
  private static final Duration SATURATED_MIN_MICROS = ofMicros(Long.MIN_VALUE);
  private static final Duration SATURATED_MAX_NANOS = Duration.ofNanos(Long.MAX_VALUE);
  private static final Duration SATURATED_MIN_NANOS = Duration.ofNanos(Long.MIN_VALUE);

  private Durations() {}

  /**
   * Returns the result of scaling this {@code Duration} by a factor of {@code c}. The result is
   * rounded to the nearest nanosecond as if by {@link RoundingMode#HALF_EVEN}.
   *
   * @throws ArithmeticException if numeric overflow occurs, or if c is NaN
   */
  public static Duration multiplyChecked(Duration duration, double c) {
    if (Double.isNaN(c)) {
      throw new ArithmeticException("Cannot multiply a duration by NaN");
    } else if (Double.isInfinite(c)) {
      throw new ArithmeticException("result does not fit into the range of a Duration");
    }
    // Not the most efficient, but absolutely precise.
    return Durations.ofSecondsChecked(toSecondsAsBigDecimal(duration).multiply(new BigDecimal(c)));
  }

  /**
   * Returns the number of seconds of the given duration as a {@code double}. This method should be
   * used to accommodate APIs that <b>only</b> accept durations as {@code double} values.
   *
   * <p>This conversion may lose precision.
   *
   * <p>If you need the number of seconds in this duration as a {@code long} (not a {@code double}),
   * simply use {@code duration.getSeconds()}.
   */
  @SuppressWarnings("DurationSecondsToDouble") // that's the whole point of this method...
  public static double toSecondsAsDouble(Duration duration) {
    return duration.getSeconds() + duration.getNano() / 1e9;
  }

  /** Returns the precise number of seconds represented by this {@code Duration}. */
  private static BigDecimal toSecondsAsBigDecimal(Duration duration) {
    return BigDecimal.valueOf(duration.getSeconds())
        .add(BigDecimal.valueOf(duration.getNano(), NANOS_POWER_OF_TEN));
  }

  /**
   * Returns a {@link Duration} representing the given number of seconds, positive or negative.
   *
   * <p><b>Note:</b> If {@code seconds} is {@link Double#POSITIVE_INFINITY} or larger than the
   * maximum capacity of a duration, {@link Durations#MAX} is returned. If {@code seconds} is {@link
   * Double#NEGATIVE_INFINITY} or smaller than the minimum capacity of a duration, the smallest
   * representable duration is returned.
   *
   * @throws ArithmeticException if {@code seconds} is {@link Double#NaN}
   */
  public static Duration ofSeconds(double seconds) {
    // we return MAX if the argument is outside the range representable as a Duration. I.e., if it
    // is equal or greater than the smallest double number of seconds greater than MAX.
    if (seconds >= MAX_SECONDS) {
      return MAX;
    }
    if (seconds <= MIN_SECONDS) {
      return MIN;
    }
    long wholeSeconds = DoubleMath.roundToLong(seconds, FLOOR);
    long nanos = DoubleMath.roundToLong((seconds - wholeSeconds) * NANOS_PER_SECOND, FLOOR);
    return Duration.ofSeconds(wholeSeconds, nanos);
  }

  /**
   * Returns a {@link Duration} representing the given number of seconds, positive or negative, to
   * nanosecond precision.
   */
  private static Duration ofSecondsChecked(BigDecimal seconds) {
    if (seconds.compareTo(BigDecimal.valueOf(MAX_SECONDS)) >= 0
        || seconds.compareTo(BigDecimal.valueOf(MIN_SECONDS)) <= 0) {
      throw new ArithmeticException("result does not fit into the range of a Duration");
    }
    long wholeSeconds = seconds.longValue();
    long nanos =
        seconds
            .subtract(BigDecimal.valueOf(wholeSeconds))
            .setScale(NANOS_POWER_OF_TEN, RoundingMode.HALF_EVEN)
            .unscaledValue()
            .longValue();
    return Duration.ofSeconds(wholeSeconds, nanos);
  }

  /** @deprecated Use {@link Duration#ofSeconds(long)} instead. */
  @Deprecated
  @InlineMe(
      replacement = "Duration.ofSeconds(seconds)",
      imports = {"java.time.Duration"})
  public static Duration ofSeconds(long seconds) {
    return Duration.ofSeconds(seconds);
  }

  /**
   * Returns the number of milliseconds of the given duration without throwing or overflowing.
   *
   * <p>Instead of throwing {@link ArithmeticException}, this method silently saturates to either
   * {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE}. This behavior can be useful when decomposing
   * a duration in order to call a legacy API which requires a {@code long, TimeUnit} pair, or a
   * legacy API which accepts a long of milliseconds.
   */
  public static long toMillisSaturated(Duration duration) {
    if (duration.compareTo(SATURATED_MAX_MILLIS) >= 0) {
      return Long.MAX_VALUE;
    }
    if (duration.compareTo(SATURATED_MIN_MILLIS) <= 0) {
      return Long.MIN_VALUE;
    }
    // Still use a try/catch because different platforms have slightly different overflow edge cases
    try {
      return duration.toMillis();
    } catch (ArithmeticException tooBig) {
      return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }

  /**
   * Returns the number of microseconds of the given duration. If that number is too large to fit in
   * a long, then an exception is thrown.
   *
   * <p>If the given duration has greater than microsecond precision, then the conversion will drop
   * any excess precision information as though the amount in nanoseconds was subject to integer
   * division by one thousand.
   *
   * @throws ArithmeticException if numeric overflow occurs during conversion
   */
  public static long toMicros(Duration duration) {
    if (duration.getSeconds() < Long.MIN_VALUE / MICROS_PER_SECOND) {
      // To avoid overflow when very close to Long.MIN_VALUE, add 1 second, multiply, then subtract
      // again.
      return LongMath.checkedAdd(
          LongMath.checkedMultiply(duration.getSeconds() + 1, MICROS_PER_SECOND),
          (duration.getNano() / NANOS_PER_MICRO) - MICROS_PER_SECOND);
    }
    long micros = LongMath.checkedMultiply(duration.getSeconds(), MICROS_PER_SECOND);
    return LongMath.checkedAdd(micros, duration.getNano() / NANOS_PER_MICRO);
  }

  /**
   * Returns the number of microseconds of the given duration without throwing or overflowing.
   *
   * <p>Instead of throwing {@link ArithmeticException}, this method silently saturates to either
   * {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE}. This behavior can be useful when decomposing
   * a duration in order to call a legacy API which requires a {@code long, TimeUnit} pair, or a
   * legacy API which accepts a long of microseconds.
   */
  public static long toMicrosSaturated(Duration duration) {
    if (duration.compareTo(SATURATED_MAX_MICROS) >= 0) {
      return Long.MAX_VALUE;
    }
    if (duration.compareTo(SATURATED_MIN_MICROS) <= 0) {
      return Long.MIN_VALUE;
    }
    // Still use a try/catch because different platforms have slightly different overflow edge cases
    try {
      return toMicros(duration);
    } catch (ArithmeticException tooBig) {
      return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }

  /**
   * Returns a {@link Duration} representing the given number of microseconds, positive or negative.
   */
  public static Duration ofMicros(long micros) {
    return Duration.of(micros, ChronoUnit.MICROS);
  }

  /**
   * Returns the number of nanoseconds of the given duration without throwing or overflowing.
   *
   * <p>Instead of throwing {@link ArithmeticException}, this method silently saturates to either
   * {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE}. This behavior can be useful when decomposing
   * a duration in order to call a legacy API which requires a {@code long, TimeUnit} pair.
   */
  public static long toNanosSaturated(Duration duration) {
    if (duration.compareTo(SATURATED_MAX_NANOS) >= 0) {
      return Long.MAX_VALUE;
    }
    if (duration.compareTo(SATURATED_MIN_NANOS) <= 0) {
      return Long.MIN_VALUE;
    }
    // Still use a try/catch because different platforms have slightly different overflow edge cases
    try {
      return duration.toNanos();
    } catch (ArithmeticException tooBig) {
      return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }

  /**
   * Returns a {@link Duration} representing the given value/{@link TimeUnit}. This is often useful
   * for converting between legacy APIs which accept a {@code long}/{@code TimeUnit} pair and modern
   * APIs which accept a {@link Duration}.
   *
   * <p>This API is <b>only</b> useful if one or both of the parameters are variables. This API
   * should <b>not</b> be called with literals (e.g., {@code Durations.of(5, SECONDS)}). Instead,
   * please use the static factory methods listed in the class docs above.
   */
  public static Duration of(long value, TimeUnit timeUnit) {
    return Duration.of(value, convertTimeUnitToChronoUnit(timeUnit));
  }

  /** Converts from a {@link TimeUnit} to the corresponding {@link ChronoUnit}. */
  private static ChronoUnit convertTimeUnitToChronoUnit(TimeUnit timeUnit) {
    checkNotNull(timeUnit, "timeUnit");
    switch (timeUnit) {
      case NANOSECONDS:
        return ChronoUnit.NANOS;
      case MICROSECONDS:
        return ChronoUnit.MICROS;
      case MILLISECONDS:
        return ChronoUnit.MILLIS;
      case SECONDS:
        return ChronoUnit.SECONDS;
      case MINUTES:
        return ChronoUnit.MINUTES;
      case HOURS:
        return ChronoUnit.HOURS;
      case DAYS:
        return ChronoUnit.DAYS;
    }
    throw new AssertionError("Unknown TimeUnit enum constant");
  }

  /**
   * Ensures that the given {@link Duration} is not negative.
   *
   * @throws IllegalArgumentException if {@code duration} is negative
   */
  @CanIgnoreReturnValue
  public static Duration checkNotNegative(Duration duration) {
    checkArgument(!duration.isNegative(), "duration (%s) must not be negative", duration);
    return duration;
  }

  /**
   * Ensures that the given {@link Duration} is positive.
   *
   * @throws IllegalArgumentException if {@code duration} is negative or {@link Duration#ZERO}
   */
  @CanIgnoreReturnValue
  public static Duration checkPositive(Duration duration) {
    checkArgument(isPositive(duration), "duration (%s) must be positive", duration);
    return duration;
  }

  /** Returns whether the given {@link Duration} is positive. */
  public static boolean isPositive(Duration duration) {
    return !duration.isNegative() && !duration.isZero();
  }

  private static final class DurationDomain extends DiscreteDomain<Duration> {
    private static final DurationDomain INSTANCE = new DurationDomain();

    @Override
    public Duration minValue() {
      return MIN;
    }

    @Override
    public Duration maxValue() {
      return MAX;
    }

    @Override
    @Nullable
    public Duration next(Duration value) {
      return value.equals(maxValue()) ? null : value.plusNanos(1);
    }

    @Override
    @Nullable
    public Duration previous(Duration value) {
      return value.equals(minValue()) ? null : value.minusNanos(1);
    }

    @Override
    public long distance(Duration start, Duration end) {
      long seconds = LongMath.saturatedSubtract(end.getSeconds(), start.getSeconds());
      long nanos = LongMath.saturatedMultiply(seconds, NANOS_PER_SECOND);
      return LongMath.saturatedAdd(nanos, end.getNano() - start.getNano());
    }

    @Override
    public String toString() {
      return "Durations.domain()";
    }
  }

  /** A {@code DiscreteDomain} for {@code Duration}s. */
  public static DiscreteDomain<Duration> domain() {
    return DurationDomain.INSTANCE;
  }
}
