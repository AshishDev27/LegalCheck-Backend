package com.example.repositories

import com.example.models.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object UserRepository {
    fun findByInspectorId(inspectorId: String) = transaction {
        UserTable.selectAll().where { UserTable.inspectorId eq inspectorId }
            .map { it.toUserResult() }
            .singleOrNull()
    }

    fun findById(id: Int) = transaction {
        UserTable.selectAll().where { UserTable.id eq id }
            .map { it.toUserResult() }
            .singleOrNull()
    }

    fun create(fullName: String, inspectorId: String, division: String, email: String, passcodeHash: String) = transaction {
        UserTable.insert {
            it[UserTable.fullName] = fullName
            it[UserTable.inspectorId] = inspectorId
            it[UserTable.division] = division
            it[UserTable.email] = email
            it[UserTable.passcode] = passcodeHash
            it[UserTable.createdAt] = LocalDateTime.now()
        } get UserTable.id
    }

    private fun ResultRow.toUserResult() = UserResult(
        id = this[UserTable.id],
        fullName = this[UserTable.fullName],
        inspectorId = this[UserTable.inspectorId],
        division = this[UserTable.division],
        email = this[UserTable.email],
        passcode = this[UserTable.passcode],
        createdAt = this[UserTable.createdAt].toString()
    )
}

data class UserResult(
    val id: Int,
    val fullName: String,
    val inspectorId: String,
    val division: String,
    val email: String,
    val passcode: String,
    val createdAt: String
)
