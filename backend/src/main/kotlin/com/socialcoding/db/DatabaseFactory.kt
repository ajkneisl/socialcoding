package com.socialcoding.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {

    fun init(jdbcUrl: String, driver: String, user: String, password: String) {
        Database.connect(jdbcUrl, driver = driver, user = user, password = password)
        transaction {
            SchemaUtils.create(Users, Projects, ProjectMembers, ProjectTasks)
            // SchemaUtils.create doesn't alter existing tables; bring older databases up to date.
            exec("ALTER TABLE projects ADD COLUMN IF NOT EXISTS team_lead_id BIGINT")
            exec("ALTER TABLE projects ADD COLUMN IF NOT EXISTS design_doc TEXT")
        }
    }
}
