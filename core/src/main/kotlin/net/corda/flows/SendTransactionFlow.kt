package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.internal.dataVending.AbstractSendTransactionFlow
import net.corda.core.internal.dataVending.FetchDataFlow

/**
 * The [SendTransactionFlow] corresponds to the [ResolveTransactionsFlow].
 *
 * The [SendTransactionFlow] provides an ad hoc data vending service, which anticipates incoming data request from the
 * [otherSide] during the transaction resolving process.
 *
 * The number of request from [ResolveTransactionsFlow] is depends on the depth of the transaction history and the data
 * [otherSide] already possess, which is unpredictable. The [SendTransactionFlow] is expected to receive [FetchDataFlow.Request]
 * continuously until the [otherSide] has all the data they need to resolve the transaction, an [EndDataRequest] will be
 * sent from the [otherSide] to indicate end of data request.
 *
 * @param otherSide the target party.
 * @param requestVerifier optional verifier to spot potential malicious data request, the [requestVerifier] can have additional
 * checks to make sure data request is relevant to the flow. Defaulted to
 */

class SendTransactionFlow(val otherSide: Party,
                          val requestVerifier: (FetchDataFlow.Request) -> Boolean) : AbstractSendTransactionFlow() {
    constructor(otherSide: Party) : this(otherSide, { true })

    @Suspendable
    override fun sendPayloadAndReceiveDataRequest(payload: Any?) = payload?.let {
        sendAndReceive<FetchDataFlow.Request>(otherSide, it)
    } ?: receive<FetchDataFlow.Request>(otherSide)

    @Suspendable
    override fun verifyRequest(request: FetchDataFlow.Request) = require(requestVerifier(request))
}
