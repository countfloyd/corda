package net.corda.core.internal.dataVending

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.flows.SendTransactionFlow

/**
 * The [SendTransactionFlow] is expected to receive [FetchDataFlow.Request] continuously until the [otherSide] has all the data
 * they need to resolve the transaction, an [EndDataRequest] will be sent from the [otherSide] to indicate end of data request.
 */
abstract class AbstractSendTransactionFlow(private val payload: Any? = null) : FlowLogic<Unit>() {
    @Suspendable
    protected abstract fun sendPayloadAndReceiveDataRequest(payload: Any?): UntrustworthyData<FetchDataFlow.Request>

    @Suspendable
    protected open fun verifyRequest(request: FetchDataFlow.Request) {
    }

    @Suspendable
    override fun call() {
        var payload = payload
        // This loop will receive [FetchDataFlow.Request] continuously until the `otherSide` has all the data they need
        // to resolve the transaction, a [EndDataRequest] will be sent from the `otherSide` to indicate end of data request.
        var receivedEndRequest = false
        while (!receivedEndRequest) {
            val fetchDataRequest = sendPayloadAndReceiveDataRequest(payload).unwrap {
                if (it !is EndDataRequest) {
                    if (it.hashes.isEmpty()) throw FlowException("Empty hash list")
                    verifyRequest(it)
                }
                it
            }
            payload = when (fetchDataRequest) {
                is FetchTransactionsRequest -> fetchDataRequest.hashes.map {
                    serviceHub.validatedTransactions.getTransaction(it) ?: throw FetchDataFlow.HashNotFound(it)
                }
                is FetchAttachmentsRequest -> fetchDataRequest.hashes.map {
                    serviceHub.attachments.openAttachment(it)?.open()?.readBytes() ?: throw FetchDataFlow.HashNotFound(it)
                }
                is EndDataRequest -> receivedEndRequest = true
                else -> throw FlowException("Unsupported Fetch Data Request : $fetchDataRequest")
            }
        }
    }
}

/**
 * The [SendTransactionWithRetry] flow is equivalent to [SendTransactionFlow] but using [sendAndReceiveWithRetry]
 * instead of [sendAndReceive], [SendTransactionWithRetry] is intended to be use by the notary client only.
 */
internal class SendTransactionWithRetry(private val otherSide: Party, initialPayload: Any) : AbstractSendTransactionFlow(initialPayload) {
    @Suspendable
    override fun sendPayloadAndReceiveDataRequest(payload: Any?) = sendAndReceiveWithRetry<FetchDataFlow.Request>(otherSide, payload!!)
}
