package net.corda.bank

import net.corda.core.contracts.DOLLARS
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.concurrent.transpose
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.*
import org.junit.Test

class BankOfCordaRPCClientTest {
    @Test
    fun `issuer flow via RPC`() {
        driver(dsl = {
            val bocManager = User("bocManager", "password1", permissions = setOf(startFlowPermission<IssuanceRequester>()))
            val bigCorpCFO = User("bigCorpCFO", "password2", permissions = emptySet())
            val (nodeBankOfCorda, nodeBigCorporation) = listOf(
                    startNode(BOC.name, setOf(ServiceInfo(SimpleNotaryService.type)), listOf(bocManager)),
                    startNode(BIGCORP_LEGAL_NAME, rpcUsers = listOf(bigCorpCFO))
            ).transpose().getOrThrow()

            // Bank of Corda RPC Client
            val bocClient = nodeBankOfCorda.rpcClientToNode()
            val bocProxy = bocClient.start("bocManager", "password1").proxy

            // Big Corporation RPC Client
            val bigCorpClient = nodeBigCorporation.rpcClientToNode()
            val bigCorpProxy = bigCorpClient.start("bigCorpCFO", "password2").proxy

            // Register for Bank of Corda Vault updates
            val vaultUpdatesBoc = bocProxy.vaultAndUpdates().second

            // Register for Big Corporation Vault updates
            val vaultUpdatesBigCorp = bigCorpProxy.vaultAndUpdates().second

            // Kick-off actual Issuer Flow
            // TODO: Update checks below to reflect states consumed/produced under anonymisation
            val anonymous = false
            bocProxy.startFlow(
                    ::IssuanceRequester,
                    1000.DOLLARS,
                    nodeBigCorporation.nodeInfo.legalIdentity,
                    BIG_CORP_PARTY_REF,
                    nodeBankOfCorda.nodeInfo.legalIdentity,
                    anonymous).returnValue.getOrThrow()

            // Check Bank of Corda Vault Updates
            vaultUpdatesBoc.expectEvents {
                sequence(
                        // ISSUE
                        expect { update ->
                            require(update.consumed.isEmpty()) { "Expected 0 consumed states, actual: $update" }
                            require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                        },
                        // MOVE
                        expect { update ->
                            require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                            require(update.produced.isEmpty()) { "Expected 0 produced states, actual: $update" }
                        }
                )
            }

            // Check Big Corporation Vault Updates
            vaultUpdatesBigCorp.expectEvents {
                sequence(
                        // MOVE
                        expect { update ->
                            require(update.consumed.isEmpty()) { update.consumed.size }
                            require(update.produced.size == 1) { update.produced.size }
                        }
                )
            }
        }, isDebug = true)
    }
}
