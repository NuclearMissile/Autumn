package org.example.autumn.db.orm

import jakarta.persistence.*
import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.db.JdbcTemplate
import org.example.autumn.db.orm.entity.EventEntity
import org.example.autumn.db.orm.entity.PasswordAuthEntity
import org.example.autumn.db.orm.entity.UserEntity
import org.example.autumn.utils.ConfigProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import kotlin.io.path.Path
import kotlin.test.*

enum class TestEnum { ENUM1, ENUM2 }

@Entity
@Table(name = "test_entities")
data class TestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    var id: Long,
    @Column(nullable = false)
    var string: String,
    @Column
    var nullableString: String?,
    @Column(nullable = false)
    var enum: TestEnum,
    @Column
    var nullableEnum: TestEnum?,
    @Column(nullable = false)
    var long: Long,
    @Column
    var nullableLong: Long?,
    @Column(nullable = false)
    var int: Int,
    @Column
    var nullableInt: Int?,
    @Column(nullable = false)
    var short: Short,
    @Column
    var nullableShort: Short?,
    @Column(nullable = false)
    var double: Double,
    @Column
    var nullableDouble: Double?,
    @Column(nullable = false)
    var float: Float,
    @Column
    var nullableFloat: Float?,
    @Column(nullable = false)
    var boolean: Boolean,
    @Column
    var nullableBoolean: Boolean?,
    @Column
    var blob: ByteArray?,
    @Column
    var timestamp: Timestamp?,
    @Column
    var time: Time?,
    @Column
    var date: Date?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestEntity

        if (string != other.string) return false
        if (nullableString != other.nullableString) return false
        if (enum != other.enum) return false
        if (nullableEnum != other.nullableEnum) return false
        if (long != other.long) return false
        if (nullableLong != other.nullableLong) return false
        if (int != other.int) return false
        if (nullableInt != other.nullableInt) return false
        if (short != other.short) return false
        if (nullableShort != other.nullableShort) return false
        if (double != other.double) return false
        if (nullableDouble != other.nullableDouble) return false
        if (float != other.float) return false
        if (nullableFloat != other.nullableFloat) return false
        if (boolean != other.boolean) return false
        if (nullableBoolean != other.nullableBoolean) return false
        if (blob != null) {
            if (other.blob == null) return false
            if (!blob.contentEquals(other.blob)) return false
        } else if (other.blob != null) return false
        if (timestamp != other.timestamp) return false
        if (time != other.time) return false
        if (date != other.date) return false

        return true
    }

    override fun hashCode(): Int {
        var result = string.hashCode()
        result = 31 * result + (nullableString?.hashCode() ?: 0)
        result = 31 * result + enum.hashCode()
        result = 31 * result + (nullableEnum?.hashCode() ?: 0)
        result = 31 * result + long.hashCode()
        result = 31 * result + (nullableLong?.hashCode() ?: 0)
        result = 31 * result + int
        result = 31 * result + (nullableInt ?: 0)
        result = 31 * result + short
        result = 31 * result + (nullableShort ?: 0)
        result = 31 * result + double.hashCode()
        result = 31 * result + (nullableDouble?.hashCode() ?: 0)
        result = 31 * result + float.hashCode()
        result = 31 * result + (nullableFloat?.hashCode() ?: 0)
        result = 31 * result + boolean.hashCode()
        result = 31 * result + (nullableBoolean?.hashCode() ?: 0)
        result = 31 * result + (blob?.contentHashCode() ?: 0)
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        result = 31 * result + (time?.hashCode() ?: 0)
        result = 31 * result + (date?.hashCode() ?: 0)
        return result
    }
}

class OrmTest {
    companion object {
        const val CREATE_USERS =
            "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, createdAt BIGINT NOT NULL, type INTEGER NOT NULL);"
        const val CREATE_EVENTS =
            "CREATE TABLE events (sequenceId INTEGER PRIMARY KEY AUTOINCREMENT, previousId BIGINT NOT NULL, data TEXT NOT NULL, createdAt BIGINT NOT NULL);"
        const val CREATE_PASSWORD_AUTHS =
            "CREATE TABLE password_auths (userId INTEGER PRIMARY KEY AUTOINCREMENT, random TEXT NOT NULL, passwd TEXT NOT NULL);"
        const val CREATE_TEST_ENTITIES =
            "CREATE TABLE test_entities (id INTEGER PRIMARY KEY AUTOINCREMENT, string TEXT NOT NULL, nullableString TEXT, enum TEXT NOT NULL, nullableEnum TEXT," +
                "long INTEGER NOT NULL, nullableLong INTEGER, int INTEGER NOT NULL, nullableInt INTEGER, " +
                "short INTEGER NOT NULL, nullableShort INTEGER, double REAL NOT NULL, nullableDouble REAL, " +
                "float REAL NOT NULL, nullableFloat REAL, boolean BOOLEAN NOT NULL, nullableBoolean BOOLEAN, blob BLOB, " +
                "timestamp TIMESTAMP, time TIMESTAMP, date DATE);"
    }

    private val config = ConfigProperties.load()

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path("test_jdbc.db"))
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getBean<JdbcTemplate>("jdbcTemplate")
            jdbcTemplate.update(CREATE_USERS)
            jdbcTemplate.update(CREATE_EVENTS)
            jdbcTemplate.update(CREATE_PASSWORD_AUTHS)
            jdbcTemplate.update(CREATE_TEST_ENTITIES)
        }
    }

    @Test
    fun testEntity() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val e1 = TestEntity(
                -1, "e1", "e1", TestEnum.ENUM1, TestEnum.ENUM2, 1L, 1,
                1, 1, 1, 1, 1.0, 1.0,
                1.0f, 1.0f, true, false, "e1".toByteArray(), Timestamp(1),
                Time(1), Date(1)
            )
            naiveOrm.insert(e1)
            assertNotEquals(-1, e1.id)
            val e11 = naiveOrm.selectById<TestEntity>(e1.id)!!
            val enum =
                naiveOrm.jdbcTemplate.querySingle<TestEnum>("select enum from test_entities where id = ?", e1.id)
            val e111 =
                naiveOrm.jdbcTemplate.querySingle<TestEntity>("select * from test_entities where id = ?", e1.id)
            assertEquals(e1, e11)
            assertEquals(Timestamp(1), e11.timestamp)
            assertEquals(Time(1), e11.time)
            assertEquals(Date(1), e11.date)
            assertEquals(TestEnum.ENUM1, enum)
            assertEquals(e1, e111)

            val e2 = TestEntity(
                -1, "e2", null, TestEnum.ENUM1, null, 2L, null,
                2, null, 2, null, 2.0, null,
                2.0f, null, true, null, null, null, null, null
            )
            naiveOrm.insert(e2)
            assertNotEquals(-1, e2.id)
            val e22 = naiveOrm.selectById<TestEntity>(e2.id)!!
            val nullableEnum =
                naiveOrm.jdbcTemplate.query<TestEnum>("select nullableEnum from test_entities where id = ?", e2.id)
            val e222 = naiveOrm.jdbcTemplate.query<TestEntity>("select * from test_entities where id = ?", e2.id)
            assertEquals(e2, e22)
            assertNull(e22.timestamp)
            assertNull(e22.time)
            assertNull(e22.date)
            assertNull(nullableEnum)
            assertNull(e22.timestamp)
            assertEquals(e2, e222)
        }
    }

    @Test
    fun testExportDDL() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val ddl = naiveOrm.exportDDL()
            println(ddl.slice(0 until 500))
            assertTrue(ddl.startsWith("CREATE TABLE api_key_auths ("))
        }
    }

    @Test
    fun testInsert() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            val selected = naiveOrm.selectById<UserEntity>(user.id)!!
            assertEquals(timestamp, selected.createdAt)
        }
    }

    @Test
    fun testBatchInsert() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val users = (0 until 1000).map { UserEntity(-1, it, it.toLong()) }
            naiveOrm.batchInsert(users)
            val usersResult = naiveOrm.selectFrom<UserEntity>().orderBy("id").query()
            assertContentEquals(usersResult.map { it.id }, users.map { it.id })
            assertEquals(1000, usersResult.count())
            assertEquals(999, usersResult.last().id - usersResult.first().id)
            assertEquals(0, usersResult.first().type)

            val events = (0 until 1000L).map { EventEntity(it, it, "test", it) }
            naiveOrm.batchInsert(events)
            val eventsResult = naiveOrm.selectFrom<EventEntity>()
                .where("events.data = ?", "test")
                .query()
            assertEquals(1000, eventsResult.count())
        }
    }

    @Test
    fun testQueries() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val users = (0 until 1000).map { UserEntity(-1, 1, it.toLong()) }
            val paes = (1000 until 2000).map {
                PasswordAuthEntity(
                    it.toLong(), if (it % 2 == 0) "1" else "2", it.toString()
                )
            }
            naiveOrm.batchInsert(users)
            naiveOrm.batchInsert(paes)

            val result0 = naiveOrm.selectFrom<UserEntity>()
                .orderBy("id", true)
                .first()!!
            assertEquals(1000, result0.id)

            val result1 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .where("random = ?", "1")
                .query()
            assertEquals(500, result1.count())

            val result2 = naiveOrm.selectFrom<UserEntity>()
                .limit(Long.MAX_VALUE, 950)
                .query()
            assertEquals(50, result2.count())

            val result3 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .orderBy("random")
                .limit(501)
                .query()
            result3.slice(0 until 500).forEach { assertEquals("1", it.random) }
            assertEquals("2", result3.last().random)

            val result4 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .join("users ON users.type = password_auths.random AND users.id % 2 = ?", 0)
                .where("users.type = password_auths.random")
                .query()
            result4.forEach { assertEquals("1", it.random) }

            val result5 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .join("users ON users.type = password_auths.random AND users.id % 2 = ?", 0)
                .where("users.type = ?", "a")
                .query()
            assertEquals(0, result5.count())
        }
    }

    @Test
    fun testUpdate() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            val timestamp2 = System.currentTimeMillis()
            user.createdAt = timestamp2
            assertThrows<IllegalArgumentException> { naiveOrm.update(user) }

            val pae = PasswordAuthEntity(999, "-1", "foo")
            naiveOrm.insert(pae)
            pae.passwd = "bar"
            naiveOrm.update(pae)
            assertEquals("bar", naiveOrm.selectById<PasswordAuthEntity>(999)!!.passwd)
        }
    }

    @Test
    fun testDelete() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            assertTrue(naiveOrm.selectById<UserEntity>(user.id) != null)
            naiveOrm.delete(user)
            assertNull(naiveOrm.selectById<UserEntity>(user.id))
        }
    }


    @Test
    fun testDeleteById() {
        AnnotationApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            assertTrue(naiveOrm.selectById<UserEntity>(user.id) != null)
            naiveOrm.deleteById<UserEntity>(user.id)
            assertNull(naiveOrm.selectById<UserEntity>(user.id))
        }
    }
}