package net.corda.core.transactions

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import java.security.PublicKey

/**
 * A special transaction for changing the notary of a state. It only needs specifying the state(s) as input(s),
 * old and new notaries. Output states can be computed by applying the notary modification to corresponding inputs
 * on the fly.
 */
data class NotaryChangeWireTransaction(
        override val inputs: List<StateRef>,
        override val notary: Party,
        val newNotary: Party) : AbstractWireTransaction() {
    init {
        require(notary != newNotary) { "The old and new notaries must be different – $newNotary" }
    }

    fun resolve(services: ServiceHub): NotaryChangeTransaction {
        val resolvedInputs = inputs.map { ref ->
            services.loadState(ref).let { StateAndRef(it, ref) }
        }
        return NotaryChangeTransaction(resolvedInputs, notary, newNotary, id)
    }

    override val availableComponents: List<Any>
        get() = inputs + listOf(notary, newNotary)
}

/** A notary change transaction with fully resolved inputs. */
data class NotaryChangeTransaction(
        val inputs: List<StateAndRef<*>>,
        val notary: Party,
        val newNotary: Party,
        val id: SecureHash) {

    val outputs: List<TransactionState<ContractState>>
        get() = inputs.mapIndexed { pos, (state) ->
            if (state.encumbrance != null) {
                // NotaryChangeFlow guarantees that encumbrances will follow encumbered states
                state.copy(notary = newNotary, encumbrance = pos + 1)
            } else state.copy(notary = newNotary)
        }

    val requiredSigningKeys: Set<PublicKey> get() = inputs.flatMap { it.state.data.participants }.map { it.owningKey }.toSet()

    fun verifySignatures(sigs: List<DigitalSignature.WithKey>, vararg allowedToBeMissing: PublicKey) {
        fun checkSignaturesAreValid() {
            for (sig in sigs) {
                sig.verify(id.bytes)
            }
        }

        fun getMissingSignatures(): Set<PublicKey> {
            val sigKeys = sigs.map { it.by }.toSet()
            // TODO Problem is that we can get single PublicKey wrapped as CompositeKey in allowedToBeMissing/mustSign
            //  equals on CompositeKey won't catch this case (do we want to single PublicKey be equal to the same key wrapped in CompositeKey with threshold 1?)
            val missing = requiredSigningKeys.filter { !it.isFulfilledBy(sigKeys) }.toSet()
            return missing
        }

        checkSignaturesAreValid()

        val missing = getMissingSignatures()
        if (missing.isNotEmpty()) {
            val allowed = allowedToBeMissing.toSet()
            val needed = missing - allowed
            if (needed.isNotEmpty())
                throw SignedTransaction.SignaturesMissingException(missing, missing.map { it.toBase58String() }, id)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int) = StateAndRef(outputs[index] as TransactionState<T>, StateRef(id, index))
}