package net.corda.networkcloner.impl

import net.corda.core.cloning.NodeDb
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.networkcloner.api.NodeDatabase
import net.corda.networkcloner.entity.CoreCordaData
import net.corda.networkcloner.util.JpaEntityManagerFactory
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.vault.VaultSchemaV1
import javax.persistence.EntityManager

class NodeDatabaseImpl(url : String, username: String, password: String, wellKnownPartyFromX500Name: (CordaX500Name) -> Party?, wellKnownPartyFromAnonymous: (AbstractParty) -> Party?, additionalManagedClasses : List<Class<*>>) : NodeDatabase {

    private val entityManager : EntityManager = JpaEntityManagerFactory(url, username, password, wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous, additionalManagedClasses).entityManager

    override fun readCoreCordaData(): CoreCordaData {
        val transactions = getTransactions()
        val persistentParties = getPersistentParties()
        val vaultLinearStates = getVaultLinearStates()
        val vaultStates = getVaultStates()
        val dbAttachments = getDbAttachments()
        return CoreCordaData(transactions, persistentParties, vaultLinearStates, vaultStates, dbAttachments)
    }

    override fun writeCoreCordaData(coreCordaData: CoreCordaData) {
        entityManager.transaction.begin()
        coreCordaData.transactions.forEach {
            entityManager.persist(it)
        }
        coreCordaData.persistentParties.forEach {
            entityManager.persist(it)
        }
        coreCordaData.vaultLinearStates.forEach {
            entityManager.persist(it)
        }
        coreCordaData.vaultStates.forEach {
            entityManager.persist(it)
        }
        coreCordaData.dbAttachments.forEach {
            //@todo the below line is a hack how to fetch the lazily loaded list. Without it it won't work
            println("${it.contractClassNames}")
            entityManager.merge(it)
        }
        entityManager.transaction.commit()
    }

    override fun getNarrowDb(): NodeDb {
        return NodeDbImpl(entityManager)
    }

    override fun readNetworkParametersHash(): SecureHash {
        val query = entityManager.createQuery("SELECT e FROM DBNetworkParametersStorage\$PersistentNetworkParameters e")
        return SecureHash.parse((query.resultList.single() as DBNetworkParametersStorage.PersistentNetworkParameters).hash)
    }

    private fun getDbAttachments(): List<NodeAttachmentService.DBAttachment> {
        val query = entityManager.createQuery("SELECT e FROM NodeAttachmentService\$DBAttachment e")
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<NodeAttachmentService.DBAttachment>)
    }

    private fun getVaultStates(): List<VaultSchemaV1.VaultStates> {
        val query = entityManager.createQuery("SELECT e FROM VaultSchemaV1\$VaultStates e")
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<VaultSchemaV1.VaultStates>
    }

    private fun getVaultLinearStates(): List<VaultSchemaV1.VaultLinearStates> {
        val query = entityManager.createQuery("SELECT e FROM VaultSchemaV1\$VaultLinearStates e")
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<VaultSchemaV1.VaultLinearStates>
    }

    private fun getPersistentParties(): List<VaultSchemaV1.PersistentParty> {
        val query = entityManager.createQuery("SELECT e FROM VaultSchemaV1\$PersistentParty e")
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<VaultSchemaV1.PersistentParty>
    }

    private fun getTransactions(): List<DBTransactionStorage.DBTransaction> {
        val query = entityManager.createQuery("SELECT e FROM DBTransactionStorage\$DBTransaction e")
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<DBTransactionStorage.DBTransaction>
    }

    class NodeDbImpl(val entityManager: EntityManager) : NodeDb {

        override fun <T> readEntities(): List<T> {
            TODO("Not yet implemented")
        }

        override fun <T> writeEntities(entities: List<T>) {
            TODO("Not yet implemented")
        }

    }


}