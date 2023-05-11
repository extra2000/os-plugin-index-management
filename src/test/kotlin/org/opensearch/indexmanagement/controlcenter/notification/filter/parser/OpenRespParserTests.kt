/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.indexmanagement.controlcenter.notification.filter.parser

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import org.junit.Assert
import org.junit.Before
import org.mockito.Mockito
import org.opensearch.action.IndicesRequest
import org.opensearch.action.admin.indices.open.OpenIndexRequest
import org.opensearch.action.admin.indices.open.OpenIndexResponse
import org.opensearch.action.support.ActiveShardCount
import org.opensearch.action.support.ActiveShardsObserver
import org.opensearch.cluster.metadata.IndexNameExpressionResolver
import org.opensearch.common.unit.TimeValue
import java.lang.Exception

class OpenRespParserTests : BaseRespParserTestCase() {

    private lateinit var activeShardsObserver: ActiveShardsObserver
    private lateinit var indexNameExpressionResolver: IndexNameExpressionResolver

    @Before
    fun setup() {
        activeShardsObserver = Mockito.mock()
        indexNameExpressionResolver = Mockito.mock()
    }

    fun `test all shards are started`() {
        val request = OpenIndexRequest("index-1", "index-2")
        val response = OpenIndexResponse(true, true)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        parser.parseAndSendNotification(response) { ret ->
            Assert.assertEquals(ret.message, "The open index job on test-cluster/index-1,index-2 has completed.")
        }

        Mockito.verify(activeShardsObserver, never())
            .waitForActiveShards(any(), any(), any(), any(), any())
    }

    fun `test not all shards are started sync`() {
        val request = OpenIndexRequest("index-1", "index-2")
        val response = OpenIndexResponse(true, false)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        doReturn(arrayOf("index-1", "index-2"))
            .`when`(indexNameExpressionResolver).concreteIndexNames(any(), any() as IndicesRequest)

        parser.parseAndSendNotification(response) { ret ->
            Assert.assertEquals(ret.message, "Open index [index-1,index-2] has completed.")
        }

        Mockito.verify(activeShardsObserver, times(1))
            .waitForActiveShards(any(), Mockito.eq(ActiveShardCount.DEFAULT), any(), any(), any())
    }

    fun `test not all shards are started async`() {
        val request: OpenIndexRequest = Mockito.mock()
        Mockito.`when`(request.indices()).thenReturn(arrayOf("index-1", "index-2"))
        Mockito.`when`(request.shouldStoreResult).thenReturn(true)
        Mockito.`when`(request.ackTimeout()).thenReturn(TimeValue.timeValueHours(1))

        val response = OpenIndexResponse(true, false)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        Mockito
            .`when`(indexNameExpressionResolver.concreteIndexNames(any(), any() as IndicesRequest))
            .thenReturn(arrayOf("index-1", "index-2"))

        parser.parseAndSendNotification(response) { ret ->
            Assert.assertEquals(
                ret.message,
                "The open index job on test-cluster/index-1,index-2 has completed, but timed out while waiting for enough shards to be started in 1h, try with `GET /<target>/_recovery` to get more details."
            )
        }

        Mockito.verify(activeShardsObserver, never())
            .waitForActiveShards(any(), Mockito.eq(ActiveShardCount.DEFAULT), any(), any(), any())
    }

    fun `test not all shards are started timeout`() {
        val request = OpenIndexRequest("index-1", "index-2")
        request.timeout(TimeValue.timeValueHours(2))
        val response = OpenIndexResponse(true, false)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        doReturn(arrayOf("index-1", "index-2"))
            .`when`(indexNameExpressionResolver).concreteIndexNames(any(), any() as IndicesRequest)

        parser.parseAndSendNotification(response) { ret ->
            Assert.assertEquals(
                ret.message,
                "The open index job on test-cluster/index-1,index-2 has completed, but timed out while waiting for enough shards to be started in 2h, try with `GET /<target>/_recovery` to get more details."
            )
        }

        Mockito.verify(activeShardsObserver, never())
            .waitForActiveShards(any(), Mockito.eq(ActiveShardCount.DEFAULT), any(), any(), any())
    }

    fun `test build message for completion`() {
        val request = OpenIndexRequest("index-1", "index-2")
        val response = OpenIndexResponse(true, true)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        val msg = parser.buildNotificationMessage(response)
        Assert.assertEquals(msg, "The open index job on test-cluster/index-1,index-2 has completed.")
    }

    fun `test build message for failure`() {
        val request = OpenIndexRequest("index-1", "index-2")
        val response = OpenIndexResponse(true, true)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        val msg = parser.buildNotificationMessage(response, Exception("index already exits error"))
        Assert.assertEquals(
            msg,
            "The open index job on test-cluster/index-1,index-2 has failed: index already exits error"
        )
    }

    fun `test build message for timeout`() {
        val request = OpenIndexRequest("index-1", "index-2")
        val response = OpenIndexResponse(true, true)
        val parser = OpenIndexRespParser(activeShardsObserver, request, indexNameExpressionResolver, clusterService)

        val msg = parser.buildNotificationMessage(response, isTimeout = true)
        Assert.assertEquals(
            msg,
            "The open index job on test-cluster/index-1,index-2 has completed, but timed out while waiting for enough shards to be started in 1h, try with `GET /<target>/_recovery` to get more details."
        )
    }
}
