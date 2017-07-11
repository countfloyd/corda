package net.corda.core.transactions

import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party

/**
 * A base class for transaction identifiable by the Merkle tree root.
 * Implementations need to provide a list of [availableComponent] that will form the leaves of the tree.
 */
abstract class AbstractWireTransaction : NamedByHash {
    /** The inputs of this transaction. Note that in BaseTransaction subclasses the type of this list may change! */
    abstract val inputs: List<StateRef>

    /**
     * If present, the notary for this transaction. If absent then the transaction is not notarised at all.
     * This is intended for issuance/genesis transactions that don't consume any other states and thus can't
     * double spend anything.
     */
    abstract val notary: Party?

    override val id: SecureHash get() = merkleTree.hash

    val merkleTree: MerkleTree by lazy { MerkleTree.getMerkleTree(availableComponentHashes) }

    /**
     * Calculate the hashes of the sub-components of the transaction, that are used to build its Merkle tree.
     * The root of the tree is the transaction identifier. The tree structure is helpful for privacy, please
     * see the user-guide section "Transaction tear-offs" to learn more about this topic.
     */
    private val availableComponentHashes: List<SecureHash> get() = availableComponents.map { serializedHash(it) }

    protected abstract val availableComponents: List<Any>
}