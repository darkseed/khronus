package com.despegar.khronus.store

import org.scalatest.Matchers
import com.despegar.khronus.util.BaseIntegrationTest
import org.scalatest.FunSuite
import scala.util.Try
import com.despegar.khronus.model.{Timestamp, MetricType, Metric}

class CassandraMetaStoreTest extends FunSuite with BaseIntegrationTest with Matchers {
  override val tableNames: Seq[String] = Seq("meta")

  test("should store and retrieve metadata for metrics") {
    await { Meta.metaStore.insert(Metric("metric1","histogram")) }
    val metrics = await { Meta.metaStore.allMetrics() }
    metrics shouldEqual Seq(Metric("metric1","histogram"))

  }

  test("should getLastProcessedTimestamp ok") {
    val metric = Metric("test",MetricType.Counter)
    await { Meta.metaStore.insert(metric) }
    val initialTimestamp = await { Meta.metaStore.getLastProcessedTimestampFromCassandra(metric)}
    initialTimestamp.ms should be(1)

    val expectedTimestamp = 1000L
    await { Meta.metaStore.update(metric, Timestamp(expectedTimestamp))}
    val timestamp = await { Meta.metaStore.getLastProcessedTimestampFromCassandra(metric)}
    timestamp.ms should be(expectedTimestamp)
  }

}