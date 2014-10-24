/*
 * =========================================================================================
 * Copyright © 2014 the metrik project <https://github.com/hotels-tech/metrik>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package com.despegar.metrik.model

import com.despegar.metrik.model.HistogramBucket._
import com.despegar.metrik.store._
import com.despegar.metrik.util.{ BucketUtils, Logging }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

abstract class TimeWindow(val duration: Duration, val previousWindowDuration: Duration, val shouldStoreTemporalHistograms: Boolean = true)
    extends BucketStoreSupport with SummaryStoreSupport with MetaSupport with Logging {

  def process(metric: Metric, executionTimestamp: Long): Future[Unit] = {
    log.debug(s"Process HistogramTimeWindow of $duration for metric $metric")
    //retrieve the temporal histogram buckets from previous window
    val previousWindowBuckets = retrievePreviousBuckets(metric, executionTimestamp)

    //group histograms in buckets of my window duration
    val groupedHistogramBuckets = groupInBucketsOfMyWindow(previousWindowBuckets, metric)

    //filter out buckets already processed. we don't want to override our precious buckets with late data
    val filteredGroupedHistogramBuckets = filterAlreadyProcessedBuckets(groupedHistogramBuckets, metric)

    val resultingBuckets = aggregateBuckets(filteredGroupedHistogramBuckets)

    //store temporal histogram buckets for next window if needed
    val storeTemporalFuture = storeTemporalBuckets(resultingBuckets, metric)

    //calculate the statistic summaries (percentiles, min, max, etc...)
    val statisticsSummaries = storeTemporalFuture flatMap { _ ⇒ resultingBuckets.map(buckets ⇒ buckets map (_.summary)) }

    //store the statistic summaries
    val storeFuture = statisticsSummaries flatMap (summaries ⇒ summaryStore.store(metric, duration, summaries))

    //remove previous histogram buckets
    storeFuture flatMap { _ ⇒ previousWindowBuckets flatMap (windows ⇒ bucketStore.remove(metric, previousWindowDuration, windows)) }
  }

  private def storeTemporalBuckets(resultingBuckets: Future[Seq[Bucket]], metric: Metric) = {
    if (shouldStoreTemporalHistograms) {
      resultingBuckets flatMap (buckets ⇒ bucketStore.store(metric, duration, buckets))
    } else {
      Future.successful[Unit](log.debug("Last window. No need to store buckets"))
    }
  }

  protected def aggregateBuckets(buckets: Future[Map[Long, scala.Seq[Bucket]]]): Future[Seq[Bucket]]

  private def retrievePreviousBuckets(metric: Metric, executionTimestamp: Long) = {
    bucketStore.sliceUntil(metric, BucketUtils.getCurrentBucketTimestamp(duration, executionTimestamp), previousWindowDuration)
  }

  private def groupInBucketsOfMyWindow(previousWindowBuckets: Future[Seq[Bucket]], metric: Metric) = {
    val future = previousWindowBuckets map (buckets ⇒ buckets.groupBy(_.timestamp / duration.toMillis))
    future.onSuccess { case buckets ⇒ log.debug(s"${buckets.size} grouped buckets: ${buckets} of $duration for metric $metric") }
    future
  }

  private def filterAlreadyProcessedBuckets(groupedHistogramBuckets: Future[Map[Long, Seq[Bucket]]], metric: Metric) = {
    val future = lastProcessedBucket(metric) flatMap { lastBucket ⇒ groupedHistogramBuckets map (_.filterNot(_._1 < (lastBucket - 1))) }
    future.onSuccess { case buckets ⇒ log.debug(s"${buckets.size} buckets after filtering: ${buckets} of $duration for metric $metric") }
    future
  }

  /**
   * Returns the last bucket number found in statistics summaries
   * @param metric
   * @return a Long representing the bucket number. If nothing if found -1 is returned
   */
  private def lastProcessedBucket(metric: Metric): Future[Long] = {
    val future = metaStore.getLastProcessedTimestamp(metric) map { timestamp ⇒ timestamp / duration.toMillis }
    future.onSuccess { case bucket ⇒ log.debug(s"Last processed bucket: $bucket of $duration for metric $metric") }
    future
  }

}

case class CounterTimeWindow(override val duration: Duration, override val previousWindowDuration: Duration, override val shouldStoreTemporalHistograms: Boolean = true)
    extends TimeWindow(duration, previousWindowDuration, shouldStoreTemporalHistograms) with CounterBucketStoreSupport with CounterSummaryStoreSupport {

  def aggregateBuckets(buckets: Future[Map[Long, scala.Seq[Bucket]]]): Future[Seq[CounterBucket]] = {
    null
  }
}

case class HistogramTimeWindow(override val duration: Duration, override val previousWindowDuration: Duration, override val shouldStoreTemporalHistograms: Boolean = true)
    extends TimeWindow(duration, previousWindowDuration, shouldStoreTemporalHistograms) with HistogramBucketSupport with StatisticSummarySupport {

  def aggregateBuckets(buckets: Future[Map[Long, Seq[Bucket]]]) = {
    buckets map (buckets ⇒ buckets.asInstanceOf[Map[Long, Seq[HistogramBucket]]].collect { case (bucketNumber, histogramBuckets) ⇒ new HistogramBucket(bucketNumber, duration, histogramBuckets) }.toSeq)
  }

}

