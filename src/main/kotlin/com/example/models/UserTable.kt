package com.example.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserTable : Table("users") {
    val id = integer("id").autoIncrement()
    val fullName = varchar("full_name", 255)
    val inspectorId = varchar("inspector_id", 50).uniqueIndex()
    val division = varchar("division", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passcode = varchar("passcode", 255)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
