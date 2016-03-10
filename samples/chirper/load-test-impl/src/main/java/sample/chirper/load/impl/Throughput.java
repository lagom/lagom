package sample.chirper.load.impl;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.Longs;

@Immutable
public final class Throughput {

  public final long startTime;
  public final long endTime;
  public final long count;
  public final long totalCount;

  public Throughput(long startTime, long endTime, long count, long totalCount) {
    this.startTime = startTime;
      this.endTime = endTime;
      this.count = count;
      this.totalCount = totalCount;
  }
  public double throughput() {
    if (endTime == startTime)
      return 0.0;
    else
      return 1.0 * count * TimeUnit.SECONDS.toNanos(1) / (endTime - startTime);
  }

   @Override
    public boolean equals(@Nullable Object another) {
      if (this == another) return true;
      return another instanceof Throughput
          && equalTo((Throughput) another);
    }

    private boolean equalTo(Throughput another) {
      return startTime == another.startTime
          && endTime == another.endTime
          && count == another.count
          && totalCount == another.totalCount;
    }
    @Override
    public int hashCode() {
      int h = 31;
      h = h * 17 + Longs.hashCode(startTime);
      h = h * 17 + Longs.hashCode(endTime);
      h = h * 17 + Longs.hashCode(count);
      h = h * 17 + Longs.hashCode(totalCount);
      return h;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper("Throughput")
          .add("startTime", startTime)
          .add("endTime", endTime)
          .add("count", count)
          .add("totalCount", totalCount)
          .toString();
    }
}
