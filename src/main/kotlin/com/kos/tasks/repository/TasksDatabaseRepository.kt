package com.kos.tasks.repository

import com.kos.common.fold
import com.kos.common.getOrThrow
import com.kos.tasks.Task
import com.kos.tasks.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime

class TasksDatabaseRepository(private val db: Database) : TasksRepository {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    object Tasks : Table("tasks") {
        val id = text("id")
        val type = text("type")
        val taskStatus = text("status")
        val inserted = text("inserted")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToTask(row: ResultRow) = Task(
        row[Tasks.id],
        TaskType.fromString(row[Tasks.type]).getOrThrow(),
        json.decodeFromString(row[Tasks.taskStatus]),
        OffsetDateTime.parse(row[Tasks.inserted])
    )

    override suspend fun insertTask(task: Task) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Tasks.insert {
                it[id] = task.id
                it[type] = task.type.toString()
                it[taskStatus] = json.encodeToString(task.taskStatus)
                it[inserted] = task.inserted.toString()
            }
        }
    }

    override suspend fun getTasks(taskType: TaskType?): List<Task> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val baseQuery = Tasks.selectAll()
            val filteredQuery = taskType.fold(
                { baseQuery },
                { baseQuery.adjustWhere { Tasks.type eq it.toString() } }
            )
            filteredQuery.map { resultRowToTask(it) }
        }
    }

    override suspend fun getTask(id: String): Task? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Tasks.selectAll().where { Tasks.id.eq(id) }.map { resultRowToTask(it) }.singleOrNull()
        }
    }

    override suspend fun deleteOldTasks(olderThanDays: Long): Int {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Tasks.deleteWhere { inserted.less(OffsetDateTime.now().minusDays(olderThanDays).toString()) }
        }
    }

    override suspend fun getLastExecution(taskType: TaskType): Task? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Tasks.selectAll().where { Tasks.type.eq(taskType.toString()) }
                .orderBy(Tasks.inserted, SortOrder.DESC)
                .limit(1)
                .map { resultRowToTask(it) }.firstOrNull()
        }
    }

    override suspend fun state(): List<Task> {
        return newSuspendedTransaction(Dispatchers.IO, db) { Tasks.selectAll().map { resultRowToTask(it) } }
    }

    override suspend fun withState(initialState: List<Task>): TasksRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            Tasks.batchInsert(initialState) {
                this[Tasks.id] = it.id
                this[Tasks.type] = it.type.toString()
                this[Tasks.taskStatus] = json.encodeToString(it.taskStatus)
                this[Tasks.inserted] = it.inserted.toString()
            }
        }
        return this
    }

}