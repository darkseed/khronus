package com.despegar.khronus.store

import com.despegar.khronus.model.{CounterSummary, Metric, MetricType}
import com.despegar.khronus.util.BaseIntegrationTest
import com.despegar.khronus.model.{MetricType, CounterSummary, Metric, StatisticSummary}
import com.despegar.khronus.util.{Settings, BaseIntegrationTest}
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.duration._

class CassandraCounterSummaryStoreTest extends FunSuite with BaseIntegrationTest with Matchers{
  override val tableNames: Seq[String] = Settings.Window.WindowDurations.map(duration => Summaries.counterSummaryStore.tableName(duration))

  test("An CounterSummary should be capable of serialize and deserialize from Cassandra") {
    val summary = new CounterSummary(22L, 250L)
    val secondSummary = new CounterSummary(30L, 3000L)
    val summaries = Seq(summary, secondSummary)
    await {
      Summaries.counterSummaryStore.store(Metric("testMetric", MetricType.Counter), 30 seconds, summaries)
    }

    val bucketsFromCassandra = await {
      Summaries.counterSummaryStore.sliceUntilNow(Metric("testMetric", MetricType.Counter), 30 seconds)
    }

    bucketsFromCassandra(0) shouldEqual summary
    bucketsFromCassandra(1) shouldEqual secondSummary
  }

  test("Slice without results") {
    val bucketsFromCassandra = await {
      Summaries.counterSummaryStore.sliceUntilNow(Metric("inexistent metric", MetricType.Counter), 30 seconds)
    }

    bucketsFromCassandra.isEmpty shouldBe true
  }

}
